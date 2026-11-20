package com.vrgames.conductor.content

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.pico.spatial.core.ecs.DirectionalLightComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.LoadType
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.event.SceneEvents
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.TextureCreateOption
import com.pico.spatial.core.ecs.resource.TextureEncoding
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.lifecycle.Cancellable
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.tracking.DataProvider
import com.pico.spatial.tracking.hmd.HMDTrackingProvider
import com.pico.spatial.tracking.hand.HandJoint
import com.pico.spatial.tracking.hand.HandTrackingProvider
import com.vrgames.conductor.core.BeatHand
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.content.SpatialViewAttachments
import com.pico.spatial.ui.foundation.content.toColor4
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialDragGesture
import com.pico.spatial.ui.foundation.gesture.detectSpatialTapGesture
import com.pico.spatial.ui.foundation.gesture.detectSpatialPointerEvent
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.LocalPhysicalLengthConverter
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import com.vrgames.conductor.core.EyeHeight
import com.vrgames.conductor.core.Vec3
import com.vrgames.conductor.game.AutoPilot
import com.vrgames.conductor.game.Game
import com.vrgames.conductor.render.HallView
import com.vrgames.conductor.render.Mixer
import com.vrgames.conductor.render.Sound
import com.vrgames.conductor.render.color
import com.vrgames.conductor.render.model
import com.vrgames.conductor.render.setPos
import com.vrgames.conductor.render.setScale
import com.vrgames.conductor.render.shared
import com.vrgames.conductor.render.transform
import com.vrgames.conductor.render.unlit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private const val TAG = "HallStage"

/** The podium in the middle of the hall, the orchestra floating in an arc before it. Everything is set down once and stays. */
@Composable
fun HallStage() {
    val context = LocalContext.current
    val density = LocalDensity.current
    val converter = LocalPhysicalLengthConverter.current
    val navigator = LocalSpatialNavigator.current
    val main = remember { Handler(Looper.getMainLooper()) }
    val head = remember { HMDTrackingProvider() }
    // Both hands' palms, for beats conducted by the hand's own motion (2026-11-12 review).
    val hands = remember { HandTrackingProvider() }
    val beatHands = remember { arrayOf(BeatHand(), BeatHand()) }
    /** When the last pinch on an instrument counted: a tap and a drag from the same pinch count once. */
    val lastPinch = remember { LongArray(1) }
    val handClock = remember { doubleArrayOf(0.0) }
    val subscription = remember { arrayOfNulls<Cancellable>(1) }
    var view by remember { mutableStateOf<HallView?>(null) }
    val seat = remember { Seat() }
    val panels = remember { HashMap<String, Entity>() }
    val frames = remember { HashMap<String, Entity>() }
    val appVersion = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "" }
    // Which group the pinch is on, and the height it started at.
    val held = remember { intArrayOf(-1) }
    val height = remember { doubleArrayOf(0.0) }
    val skies = remember { HashMap<String, TextureResource>() }
    val skyEntity = remember { arrayOfNulls<Entity>(1) }

    val openPieces: () -> Unit = {
        Game.windowOpen = true
        navigator.openWindowContainer(LEVELS_ID)
    }

    com.vrgames.conductor.platform.AppExit.TrackStage()
    DisposableEffect(Unit) {
        Game.init(context)
        com.vrgames.conductor.platform.AppExit.stageAlive = true
        Game.closeWindow = {
            navigator.closeWindowContainer(LEVELS_ID)
            Game.windowOpen = false
        }
        Game.mixer = Mixer(context.applicationContext)
        runCatching { hands.start() }
        val hd = runCatching { head.start() }
        Log.i(TAG, "head ${hd.getOrNull() ?: hd.exceptionOrNull()?.message}")
        onDispose {
            com.vrgames.conductor.platform.AppExit.stageAlive = false
            runCatching { head.stop() }
            runCatching { hands.stop() }
            subscription[0]?.cancel()
            Game.closeWindow()
            Game.closeWindow = {}
            Game.stopVoice()
            Game.stop()
        }
    }
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) Game.resume() else if (e == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) Game.pause()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Game.pieceVersion, view) {
        val v = view ?: return@LaunchedEffect
        v.build(Game.piece)
        v.follow(Game.perf)
        // The east chapter plays in a garden.
        val t = skies[if (Game.piece.chapter == 4) "sky_garden" else "sky_hall"]
        val e = skyEntity[0]
        if (t != null && e != null) setSky(e, UnlitMaterial.create().apply { setBaseColorTexture(t) })
    }
    LaunchedEffect(Game.progress.trophies, Game.version, view) { view?.trophies(Game.progress.trophies) }
    LaunchedEffect(Game.faceRequest) { if (Game.faceRequest > 0) seat.again() }
    LaunchedEffect(Game.noteVersion) {
        val n = Game.note ?: return@LaunchedEffect
        kotlinx.coroutines.delay(if (n.length > 16) 3500 else 1800)
        if (Game.note == n) Game.note = null
    }

    fun refresh() {
        val ph = Game.phase
        fun show(id: String, on: Boolean) = panels[id]?.let { if (it.enabled != on) it.enabled = on }
        val menu = Game.menuOpen
        show(PANEL_CARD, ph == Game.Phase.CARD)
        show(PANEL_INTRO, ph == Game.Phase.INTRO)
        show(PANEL_LOADING, ph == Game.Phase.LOADING)
        show(PANEL_RESULT, ph == Game.Phase.RESULT)
        show(PANEL_SHOW, ph == Game.Phase.SHOW)
        show(PANEL_HUD, ph == Game.Phase.PLAYING && !menu)
        show(PANEL_MENU, menu)
        show(PANEL_NOTE, Game.note != null)
        for (i in 0 until 4) show(labelId(i), ph == Game.Phase.PLAYING || ph == Game.Phase.INTRO)
        // v0.3.2: the instruments take pinches only while a piece is played; on the cards a pinch is for their buttons.
        view?.pinchable(ph == Game.Phase.PLAYING)
    }

    /** What a pinch landed on, for the log: an instrument group, or the entity's own name. */
    fun nameOf(e: Entity?): String {
        if (e == null) return "null"
        view?.groupOf(e)?.let { return "section$it" }
        var x: Entity? = e
        while (x != null) { val n = runCatching { x!!.getName() }.getOrNull(); if (!n.isNullOrEmpty()) return n; x = x.getParent() }
        return "${e.javaClass.simpleName}@${Integer.toHexString(System.identityHashCode(e))}"
    }

    // Test hook (AutoPilot "ray"): from the eyes through the title card's two buttons, what the eyes' pinch would meet.
    debugRays = { seat.rays(view, ::nameOf) }
    debugReplace = { z -> seat.remeasure(z) }

    SideEffect { refresh() }

    SpatialView(
        Modifier.pointerInput(0) {
            // A quick pinch that does not move comes as a tap, not a drag (2026-11-13 device test: more than eight pinches
            // in ten on an instrument did nothing, because only drags were heard). Whichever comes first counts, once.
            detectSpatialTapGesture(context, TargetEntity.any()) { value ->
                Log.i(TAG, "tap target=${nameOf(value.targetEntity)} phase=${Game.phase} kind=${value.interactionKind} pinchable=${view?.isPinchable}")
                val i = view?.groupOf(value.targetEntity) ?: return@detectSpatialTapGesture
                val now = android.os.SystemClock.uptimeMillis()
                if (now - lastPinch[0] < SAME_PINCH_MS) return@detectSpatialTapGesture
                lastPinch[0] = now
                Log.i(TAG, "tap group=$i phase=${Game.phase}")
                Game.pinch(i)
                Game.letGo(i)
            }
        }.pointerInput(1) {
            fun meters(px: Float) = with(density) { converter.dpToLength(px.toDp(), LengthUnit.Meters) }.toDouble()
            detectSpatialDragGesture(
                context = context,
                targetedToEntity = TargetEntity.any(),
                onDragStart = { _, value ->
                    val i = view?.groupOf(value.targetEntity)
                    Log.i(TAG, "dragStart target=${nameOf(value.targetEntity)} group=$i phase=${Game.phase} kind=${value.interactionKind} dev=${value.inputDevicePose.rawPosition} pinchable=${view?.isPinchable}")
                    val now = android.os.SystemClock.uptimeMillis()
                    if (i != null && now - lastPinch[0] < SAME_PINCH_MS) { held[0] = i; return@detectSpatialDragGesture }
                    if (i != null) {
                        lastPinch[0] = now
                        held[0] = i
                        val run = Game.perf
                        val id = Game.piece.sections.getOrNull(i)?.id
                        height[0] = if (run != null && id != null) run.level(id) else 0.8
                        Game.pinch(i)
                    }
                },
                onDragEnd = {
                    if (held[0] >= 0) Game.letGo(held[0])
                    held[0] = -1
                },
                onDragCancel = {
                    if (held[0] >= 0) Game.letGo(held[0])
                    held[0] = -1
                },
            ) { value ->
                if (held[0] < 0) return@detectSpatialDragGesture
                // Compose y points down; a hand moved up by HAND_RANGE takes the volume from silent to full.
                height[0] = (height[0] - meters(value.dragAmount.y) / HAND_RANGE).coerceIn(0.0, 1.0)
                Game.hold(held[0], height[0])
            }
        }.pointerInput(2) {
            // v0.3.2: every finger-down, what it landed on and in which phase (device 2026-11-19: the title card's
            // buttons did not answer; the instruments behind it took the pinch). Logging only: never consumed.
            detectSpatialPointerEvent(context, TargetEntity.any()) { infos ->
                for (i in infos) if (i.isDownEvent()) Log.i(TAG, "down target=${nameOf(i.targetedEntity)} phase=${Game.phase} kind=${i.kind} dev=${i.inputDevicePose.rawPosition} pinchable=${view?.isPinchable}")
                false
            }
        },
        attachments = {
            AttachmentPanel(PANEL_CARD) { CardPanel(converter, openPieces) }
            AttachmentPanel(PANEL_INTRO) { IntroPanel(converter) }
            AttachmentPanel(PANEL_LOADING) { LoadingPanel(converter) }
            AttachmentPanel(PANEL_HUD) { HudPanel(converter) }
            AttachmentPanel(PANEL_RESULT) { ResultPanel(converter, openPieces) }
            AttachmentPanel(PANEL_SHOW) { ShowPanel(converter) }
            AttachmentPanel(PANEL_NOTE) { NotePanel(converter) }
            AttachmentPanel(PANEL_MENU) {
                MenuPanel(converter, openPieces, faceMe = { Game.faceRequest++ }, quit = { com.vrgames.conductor.platform.AppExit.finishAll(context) }, version = appVersion)
            }
            for (i in 0 until 4) AttachmentPanel(labelId(i)) { SectionLabel(converter, i) }
        },
        update = { _, attachments ->
            view?.let {
                attach(seat, attachments, panels, frames)
                refresh()
            }
        },
        initial = { content, attachments ->
            // One warm light from the stage, no real-time shadows; the image-based light does the rest.
            content.addEntity(Entity().apply {
                components.set(DirectionalLightComponent(Color(0xFFFFE6C4).toColor4(), 1200f, false))
                components[TransformComponent::class.java]?.setEulerAngles(EulerAngles(-50f, 15f, 0f))
            })
            var ibl: TextureResource? = null
            withContext(Dispatchers.IO) {
                for (name in listOf("sky_hall", "sky_garden")) {
                    runCatching { TextureResource.load("textures/$name.jpg", LoadType.FROM_ASSETS, TextureCreateOption()).shared() }.onSuccess { skies[name] = it }
                }
                ibl = runCatching { TextureResource.load("textures/ibl_hall.ktx", LoadType.FROM_ASSETS, TextureCreateOption().apply { textureEncoding = TextureEncoding.LINEAR }).shared() }.getOrNull()
            }
            val models = HashMap<String, Entity>()
            for (name in MODELS) {
                runCatching { Entity.loadSuspend("asset://models/$name.glb") }
                    .onSuccess { models[name] = it }
                    .onFailure { Log.i(TAG, "model $name: ${it.message}") }
            }
            for ((id, _) in FRAME_SIZE) {
                runCatching { Entity.loadSuspend("asset://panels/panel_$id.glb") }
                    .onSuccess { frames[id] = it }
                    .onFailure { Log.i(TAG, "frame $id: ${it.message}") }
            }
            val world = Entity()
            val sky = models["sky"]?.clone(Entity.CloneOptions(true, true))
                ?: model(com.pico.spatial.core.ecs.resource.MeshResource.createSphere(1f), unlit(color(0.08f, 0.05f, 0.06f)).apply { setCullingMode(MaterialCullingMode.FRONT) })
            sky.setScale(if (models["sky"] != null) 18f / 12f else 18f)
            sky.setPos(Vector3(0f, 1.2f, 0f))
            skies["sky_hall"]?.let { t -> setSky(sky, UnlitMaterial.create().apply { setBaseColorTexture(t) }) }
            skyEntity[0] = sky
            world.addChild(sky)
            content.addEntity(world)
            val group = Entity()
            world.addChild(group)
            val v = HallView(models)
            group.addChild(v.root)
            seat.world = world
            seat.group = group
            seat.board = v.root
            attach(seat, attachments, panels, frames)
            ibl?.let { v.useIbl(it) }
            Game.view = v
            Game.sound = Sound(v.root).also { it.load() }
            view = v
            v.build(Game.piece)
            AutoPilot.ready()
            subscription[0] = content.subscribe(SceneEvents.Update::class.java) { e ->
                val dt = e.deltaTime.toDouble().coerceIn(0.0, 0.1)
                val pose = runCatching { if (head.state == DataProvider.State.STARTED) head.latestData?.hmdPose else null }.getOrNull()
                seat.frame(pose?.position, pose?.rotation, dt)
                handClock[0] += dt
                val hd = runCatching { if (hands.state == DataProvider.State.STARTED) hands.latestData else null }.getOrNull()
                if (hd != null) for ((k, h) in listOf(hd.left, hd.right).withIndex()) {
                    val palm = runCatching { h?.joint(HandJoint.Index.PALM)?.position }.getOrNull()
                    if (palm == null) { beatHands[k].reset(); continue }
                    if (beatHands[k].feed(handClock[0], palm.y.toDouble())) main.post { Game.handBeat() }
                }
                main.post { Game.tick(dt); view?.pinchable(Game.phase == Game.Phase.PLAYING) }
            }
        },
    )
}

private fun setSky(e: Entity, m: Material) {
    e.components[ModelComponent::class.java]?.materials?.let { mats -> for (i in 0 until mats.size) mats[i] = m }
    e.getChildren().forEach { setSky(it, m) }
}

private val MODELS = listOf("sky", "podium", "trophy", "violin", "cello", "harp", "flute", "clarinet", "horn", "trumpet", "timpani", "dizi", "guzheng", "pipa", "drum")
private const val HAND_RANGE = 0.4

private fun attach(seat: Seat, a: SpatialViewAttachments, panels: HashMap<String, Entity>, frames: HashMap<String, Entity>) {
    val group = seat.group ?: return
    fun put(id: String, pos: Vector3, pitch: Float, yaw: Float = 0f) {
        val e = a.entity(id) ?: return
        if (panels[id] === e) return
        panels[id] = e
        group.addChild(e)
        e.setPos(pos)
        e.transform().eulerAngles = EulerAngles(pitch, yaw, 0f)
        frames[id]?.let { f ->
            f.setPos(Vector3(0f, 0f, -0.006f))
            e.addChild(f)
        }
    }
    // Eye height is taken as 1.5 m in the group: the whole group is moved to the measured eyes (Seat, core.EyeHeight).
    put(PANEL_CARD, Vector3(0f, 1.5f, -1.0f), 0f)
    put(PANEL_INTRO, Vector3(0f, 1.5f, -1.0f), 0f)
    put(PANEL_RESULT, Vector3(0f, 1.5f, -1.0f), 0f)
    put(PANEL_LOADING, Vector3(0f, 1.5f, -1.0f), 0f)
    // Low at the left like a music stand: at 1.1 m it stood right in front of the left group (the flute), device test 2026-11-08.
    put(PANEL_HUD, Vector3(-0.5f, 0.86f, -0.5f), -35f, 45f)
    put(PANEL_MENU, Vector3(-0.5f, 0.86f, -0.5f), -35f, 45f)
    put(PANEL_NOTE, Vector3(0f, 1.0f, -0.7f), -20f)
    put(PANEL_SHOW, Vector3(0f, 1.0f, -0.7f), -20f)
    for (i in 0 until 4) {
        val r = Math.toRadians(HallView.ANGLES[i])
        put(labelId(i), Vector3((HallView.RADIUS * sin(r)).toFloat(), HallView.BASE_Y + 1.12f, (-HallView.RADIUS * cos(r)).toFloat()), 0f, -HallView.ANGLES[i].toFloat())
    }
}

/**
 * Where the player sits: the world turned once to the mean heading of the first second of tracking (and on
 * 「摆到我正前方」), and the desk brought to the player's own eye height. Then it stays, whatever the head does.
 */
private class Seat {
    var world: Entity? = null
    var group: Entity? = null
    var board: Entity? = null
    private val facing = com.vrgames.conductor.core.Facing()
    private var waited = 0.0
    private var shown = false
    val yaw get() = facing.yaw
    /**
     * v0.3.2: the eye height measured the same way in every game (core.EyeHeight: all-zero and out-of-range poses are
     * ignored, it waits for steady readings, falls back to a seated eye and corrects once), never the raw first pose.
     */
    private var eyes = EyeHeight()
    private var eyeY: Float? = null
    private var placed = false
    /** Test hook ("replace"): the head reads all zeros until then, as a headset may right after tracking starts. */
    private var zeroUntil = 0L
    /** The eyes (room frame) from the last valid head pose. */
    var lastEye: Vector3? = null; private set

    fun again() = facing.again()

    /** Test hook ("replace"): measure the eyes and place the seat again, the head reading zeros for [zeroS] seconds. */
    fun remeasure(zeroS: Double) {
        eyes = EyeHeight()
        eyeY = null
        zeroUntil = android.os.SystemClock.uptimeMillis() + (zeroS * 1000).toLong()
        facing.again()
        Log.i(TAG, "eyes: measuring again, head reads zeros for $zeroS s")
    }

    fun frame(eyeRaw: Vector3?, rot: Quat?, dt: Double) {
        val eye = if (android.os.SystemClock.uptimeMillis() < zeroUntil) Vector3(0f, 0f, 0f) else eyeRaw
        if (eyes.feed(eye?.x, eye?.y, eye?.z, dt)) {
            val y = eyes.value!!
            eyeY = y
            if (placed) group?.setPos(Vector3(0f, y - SEAT_EYE, 0f))
            Log.i(TAG, "eyes at $y (${if (eyes.guessed) "fallback" else "measured"}): group y ${y - SEAT_EYE}, card centre at $y m (room)")
        }
        val valid = eye != null && EyeHeight.valid(eye.x, eye.y, eye.z)
        if (valid) lastEye = eye
        if (!shown) {
            waited += dt
            if (facing.placed || waited >= com.vrgames.conductor.core.Facing.GIVE_UP_S) shown = true
            world?.let { if (it.enabled != shown) it.enabled = shown }
        }
        val d = Game.debugView?.split(",")?.mapNotNull { it.trim().toFloatOrNull() }
        if (d != null && d.size >= 6) {
            board?.setPos(Vector3(d[0], d[1], d[2]))
            board?.transform()?.eulerAngles = EulerAngles(d[5], d[3], 0f)
            board?.transform()?.setScaleVector(Vector3(d[4]))
            return
        }
        if (rot == null || eye == null) return
        val f = rot.rotateVector(Vector3(0f, 0f, -1f))
        // Looking nearly straight up or down says nothing about the heading; nor does a pose of zeros.
        val head = if (!valid || hypot(f.x, f.z) < 0.2f) null else com.vrgames.conductor.core.Facing.heading(f.x.toDouble(), f.z.toDouble())
        if (facing.update(head, dt)) {
            shown = true
            placed = true
            world?.enabled = true
            world?.let {
                it.setPos(Vector3(eye.x, 0f, eye.z))
                it.transform().eulerAngles = EulerAngles(0f, facing.yaw.toFloat(), 0f)
            }
            // The card's middle (1.5 m in the group) at the measured eyes; the seated fallback until they are measured.
            val y = eyeY ?: EyeHeight.FALLBACK
            group?.setPos(Vector3(0f, y - SEAT_EYE, 0f))
            board?.setPos(Vector3(0f, 0f, 0f))
            board?.transform()?.eulerAngles = EulerAngles(0f, 0f, 0f)
            board?.transform()?.setScaleVector(Vector3(1f))
            Log.i(TAG, "facing placed at ${facing.yaw}, eye $eye, eye height ${eyeY ?: "not yet measured, ${EyeHeight.FALLBACK}"}: group y ${y - SEAT_EYE}")
        }
    }

    /** Turns (x, z) by [deg] about y, as an entity's yaw does (+90 takes +x to -z). */
    private fun rotY(x: Float, y: Float, z: Float, deg: Double): Vector3 {
        val a = Math.toRadians(deg)
        return Vector3((x * cos(a) + z * sin(a)).toFloat(), y, (-x * sin(a) + z * cos(a)).toFloat())
    }

    /** A room point in the group frame (the frame the panels and the orchestra stand in). */
    private fun toGroup(p: Vector3): Vector3 {
        val wp = world?.transform()?.position ?: Vector3(0f, 0f, 0f)
        val gp = group?.transform()?.position ?: Vector3(0f, 0f, 0f)
        val q = rotY(p.x - wp.x, p.y - wp.y, p.z - wp.z, -yaw)
        return Vector3(q.x - gp.x, q.y - gp.y, q.z - gp.z)
    }

    /** A group-frame point in the room frame: [toGroup] undone. */
    private fun toRoom(p: Vector3): Vector3 {
        val wp = world?.transform()?.position ?: Vector3(0f, 0f, 0f)
        val gp = group?.transform()?.position ?: Vector3(0f, 0f, 0f)
        val q = rotY(p.x + gp.x, p.y + gp.y, p.z + gp.z, yaw)
        return Vector3(q.x + wp.x, q.y + wp.y, q.z + wp.z)
    }

    /**
     * Test hook (AutoPilot "ray"): rays from the eyes through the title card's two buttons (the card's middle is at
     * 0, 1.5, -1.0 in the group; 「开始第 1 首」 about 7 cm under it, 「选曲和奖杯」 about 13 cm under it), each 6 cm left, in
     * the middle and 6 cm right, 4 m long. Logs every collider met and whether it takes pinches: one that does, on any
     * of these rays, would take the eyes' pinch from the button.
     */
    fun rays(view: HallView?, nameOf: (Entity?) -> String) {
        val g = group ?: return
        val eye = lastEye ?: run { Log.i(TAG, "ray: no head pose yet"); return }
        val sc = g.scene ?: run { Log.i(TAG, "ray: no scene"); return }
        val eg = toGroup(eye)
        Log.i(TAG, "ray: phase=${Game.phase} pinchable=${view?.isPinchable} eye room=$eye group=$eg")
        fun takesPinch(e: Entity?): Boolean {
            var x = e
            while (x != null) { if (x.components[com.pico.spatial.core.ecs.InteractableComponent::class.java] != null) return true; x = x.getParent() }
            return false
        }
        val all = com.pico.spatial.core.ecs.simulation.CollisionCastHitMode.ALL
        val cg = com.pico.spatial.core.ecs.simulation.CollisionGroup(com.pico.spatial.core.ecs.simulation.CollisionGroup.COLLISION_GROUP_ALL)
        var rays = 0; var blockedRel = 0; var blockedRoom = 0; var anyHit = 0
        for ((label, dy) in listOf("开始第 1 首" to -0.07f, "选曲和奖杯" to -0.13f)) for (dx in floatArrayOf(-0.06f, 0f, 0.06f)) {
            val t = Vector3(dx, 1.5f + dy, -1.0f)
            val v = Vector3(t.x - eg.x, t.y - eg.y, t.z - eg.z)
            val len = kotlin.math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z).coerceAtLeast(1e-4f)
            val dirG = Vector3(v.x / len, v.y / len, v.z / len)
            // The same ray in the room frame, as a cross-check of the frames.
            val tr = toRoom(t)
            val vr = Vector3(tr.x - eye.x, tr.y - eye.y, tr.z - eye.z)
            val lr = kotlin.math.sqrt(vr.x * vr.x + vr.y * vr.y + vr.z * vr.z).coerceAtLeast(1e-4f)
            val dirR = Vector3(vr.x / lr, vr.y / lr, vr.z / lr)
            val rel = runCatching { sc.rayCast(eg, dirG, 4f, all, cg, g).results.sortedBy { it.distance } }
                .onFailure { Log.w(TAG, "ray $label: ${it.message}") }.getOrNull().orEmpty()
            val room = runCatching { sc.rayCast(eye, dirR, 4f, all, cg, null).results.sortedBy { it.distance } }
                .onFailure { Log.w(TAG, "ray $label (room): ${it.message}") }.getOrNull().orEmpty()
            rays++
            if (rel.isNotEmpty() || room.isNotEmpty()) anyHit++
            if (rel.any { takesPinch(it.entity) }) blockedRel++
            if (room.any { takesPinch(it.entity) }) blockedRoom++
            fun list(h: List<com.pico.spatial.core.ecs.simulation.CollisionCastResult>) =
                if (h.isEmpty()) "none" else h.joinToString { "${nameOf(it.entity)}@${"%.2f".format(it.distance)}${if (takesPinch(it.entity)) "(PINCHABLE)" else "(not pinchable)"}" }
            Log.i(TAG, "ray $label dx=$dx (card ${"%.2f".format(len)} m away): relative ${list(rel)} | room ${list(room)}")
        }
        val ok = blockedRel == 0 && blockedRoom == 0
        Log.i(TAG, "ray RESULT phase=${Game.phase}: $rays rays, $anyHit met a collider, pinchable in the way relative=$blockedRel room=$blockedRoom -> ${if (ok) "BUTTONS CLEAR" else "BLOCKED"}")
    }
}

/** The group frame puts the eyes at this height (the card's middle). */
private const val SEAT_EYE = 1.5f

/** Set by the stage: the test rays through the title card's buttons (AutoPilot "ray"). */
var debugRays: () -> Unit = {}
/** Set by the stage: measure the eyes again (AutoPilot "replace"), the head reading zeros for the given seconds. */
var debugReplace: (Double) -> Unit = {}

private fun android.content.Context.findActivity(): android.app.Activity? {
    var c: android.content.Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}

/** A tap and a drag start this close together are the same pinch. */
private const val SAME_PINCH_MS = 350L
