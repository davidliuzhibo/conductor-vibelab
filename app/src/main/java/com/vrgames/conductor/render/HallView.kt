package com.vrgames.conductor.render

import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.EnvironmentLightingSettingsComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.ImageBasedLightComponent
import com.pico.spatial.core.ecs.ImageBasedLightReceiverComponent
import com.pico.spatial.core.ecs.ImageBasedLightSource
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.vrgames.conductor.core.Cue
import com.vrgames.conductor.core.Grade
import com.vrgames.conductor.core.Performance
import com.vrgames.conductor.core.Piece
import com.vrgames.conductor.core.Vec3
import kotlin.math.cos
import kotlin.math.sin

/**
 * The floating orchestra: four groups of instruments in an arc before the podium. Each group has a halo that says what the
 * score wants of it, a brass rod beside it showing its volume, and a marker for the volume a swell asks for. Instruments sway
 * and throw sparks with their own stem's level, so what you see follows what you hear.
 */
class HallView(private val models: Map<String, Entity>) {
    val root = Entity()
    private val iblSource = Entity()
    private val groups = Array(4) { Group(it) }
    private val sparks = Sparks("spark", 90, MeshResource.createBox(Vector3(0.012f, 0.012f, 0.012f)), glow(color(1f, 0.85f, 0.5f, 1f)))
    private val trophyShelf = ArrayList<Entity>()
    private var piece: Piece? = null
    private var clock = 0.0
    private var showT = -1.0
    private val haloOff = glow(color(0.5f, 0.45f, 0.4f, 0.3f)).shared()
    private val haloOn = glow(color(0.7f, 0.85f, 1f, 0.55f)).shared()
    private val haloWant = glow(color(1f, 0.8f, 0.3f, 0.95f)).shared()
    /** Get ready: the cue is coming, not yet. A dim gold; it turns bright [haloWant] when it is time. */
    private val haloReady = glow(color(1f, 0.8f, 0.3f, 0.28f)).shared()
    private val haloHold = glow(color(0.4f, 0.85f, 1f, 0.9f)).shared()
    private val levelOn = glow(color(1f, 0.82f, 0.45f, 0.95f)).shared()
    private val markerMat = glow(color(0.5f, 1f, 0.85f, 1f)).shared()

    private inner class Group(val index: Int) {
        val node = Entity()
        val swayer = Entity()
        val bows = ArrayList<Entity>()
        val halo = model(MeshResource.createCylinder(0.012f, 0.42f), haloOff0)
        val level = model(MeshResource.createCylinder(1f, 0.012f), levelOn0)
        val marker = model(MeshResource.createBox(Vector3(0.09f, 0.012f, 0.03f)), marker0)
        var flash = 0.0
        var state = -1

        init {
            val a = Math.toRadians(ANGLES[index])
            node.setPos(Vector3((RADIUS * sin(a)).toFloat(), BASE_Y, (-RADIUS * cos(a)).toFloat()))
            // Faces the podium.
            node.transform().eulerAngles = EulerAngles(0f, -ANGLES[index].toFloat(), 0f)
            node.addChild(swayer)
            halo.setPos(Vector3(0f, -0.03f, 0f))
            node.addChild(halo)
            level.setPos(Vector3(0.5f, 0.3f, 0f))
            node.addChild(level)
            marker.setPos(Vector3(0.5f, 0.3f, 0f))
            marker.enabled = false
            node.addChild(marker)
            node.components.set(CollisionComponent(listOf(ShapeResource.createBox(Vector3(0.95f, 1.05f, 0.7f)).offsetByTranslation(Vector3(0f, 0.42f, 0f))), PhysicsMaterialResource()))
            node.components.set(InteractableComponent())
            node.components.set(HoverEffectComponent())
            root.addChild(node)
        }

        fun fill(names: List<Instrument>) {
            swayer.getChildren().toList().forEach { swayer.removeChild(it) }
            bows.clear()
            names.forEachIndexed { k, ins ->
                val m = models[ins.model]?.clone(Entity.CloneOptions(true, true)) ?: model(MeshResource.createBox(Vector3(0.2f, ins.size, 0.1f)), pbr(color(0.6f, 0.4f, 0.2f), 0.3f, 0.5f))
                m.setScale(if (models[ins.model] != null) ins.size else 1f)
                val spread = names.size - 1
                val dx = if (spread == 0) 0f else (k - spread / 2f) * ins.gap
                m.setPos(Vector3(dx, ins.lift + (if (k % 2 == 0) 0f else 0.04f), if (k % 2 == 0) 0f else -0.12f))
                m.transform().eulerAngles = EulerAngles(ins.pitch, ins.yaw + (if (spread == 0) 0f else (k - spread / 2f) * -8f), ins.roll)
                swayer.addChild(m)
                if (ins.bowed) {
                    val bow = model(MeshResource.createCylinder(ins.size * 0.8f, 0.004f), pbr(color(0.25f, 0.15f, 0.08f), 0.2f, 0.5f))
                    bow.transform().eulerAngles = EulerAngles(0f, 0f, 70f)
                    swayer.addChild(bow)
                    bows += bow
                    bow.setPos(Vector3(dx, ins.lift + ins.size * 0.42f, 0.06f))
                }
            }
            receive(swayer)
        }

        fun show(state: Int) {
            if (state == this.state) return
            this.state = state
            halo.components[ModelComponent::class.java]?.materials?.let { it[0] = when (state) { 1 -> haloOn; 2 -> haloWant; 3 -> haloHold; 4 -> haloReady; else -> haloOff } }
        }

        fun update(dt: Double, gain: Double, levelNow: Double, want: Boolean, holding: Boolean, on: Boolean, target: Double?, due: Boolean = want) {
            flash = maxOf(0.0, flash - dt)
            // Dim gold while the cue is coming, bright gold with a quick pulse when it is time to pinch.
            show(if (holding) 3 else if (due) 2 else if (want) 4 else if (on) 1 else 0)
            val breath = if (due) 1f + 0.18f * sin(clock * 9).toFloat() else if (want) 1f + 0.05f * sin(clock * 3).toFloat() else 1f
            halo.setScale(breath + flash.toFloat() * 0.5f)
            val t = clock * 2.2 + index
            swayer.setPos(Vector3((0.015 * sin(t * 0.6)).toFloat(), (0.03 * sin(t) + gain * 0.05 * sin(t * 3.0)).toFloat(), 0f))
            swayer.transform().eulerAngles = EulerAngles(0f, 0f, (gain * 3.5 * sin(t * 1.5)).toFloat())
            swayer.setScale((1.0 + (if (on) 0.05 * beatPulse else 0.0)).toFloat())
            for ((k, b) in bows.withIndex()) {
                val cur = b.transform().position
                b.setPos(Vector3(cur.x, cur.y, 0.06f + (gain * 0.05 * sin(clock * 6.5 + k)).toFloat()))
            }
            val h = (0.02 + LEVEL_H * levelNow * (if (on) 1.0 else 0.0)).toFloat()
            level.transform().setScaleVector(Vector3(1f, h, 1f))
            level.setPos(Vector3(0.5f, h / 2, 0f))
            marker.enabled = target != null
            if (target != null) marker.setPos(Vector3(0.5f, (0.02 + LEVEL_H * target).toFloat(), 0f))
            if (gain > 0.1 && (clock * 5).toInt() != ((clock - dt) * 5).toInt()) {
                val p = node.transform().position
                sparks.emit(Vec3(p.x.toDouble(), p.y + 0.55, p.z.toDouble()), 1 + (gain * 2).toInt(), 0.12, 1.1, 0.012f, Vec3(0.0, 0.18, 0.0))
            }
        }
    }

    // These must exist before the groups are built.
    private val haloOff0 get() = glow(color(0.5f, 0.45f, 0.4f, 0.3f))
    private val levelOn0 get() = glow(color(1f, 0.82f, 0.45f, 0.95f))
    private val marker0 get() = glow(color(0.5f, 1f, 0.85f, 1f))

    init {
        root.addChild(iblSource)
        root.addChild(sparks.entity)
        models["podium"]?.clone(Entity.CloneOptions(true, true))?.let {
            it.setScale(1.1f)
            it.setPos(Vector3(0f, -0.04f, -0.05f))
            root.addChild(it)
        }
        models["stand"]?.clone(Entity.CloneOptions(true, true))?.let {
            it.setScale(1.0f)
            it.setPos(Vector3(0f, 0f, -0.62f))
            root.addChild(it)
        }
    }

    fun groupOf(e: Entity?): Int? {
        var x = e
        while (x != null) {
            for (g in groups) if (g.node === x) return g.index
            x = x.getParent()
        }
        return null
    }

    fun build(p: Piece) {
        piece = p
        p.sections.forEachIndexed { i, s -> groups[i].fill(Instrument.forSection(s.name)) }
    }

    fun flash(i: Int, g: Grade?) {
        if (i !in groups.indices) return
        groups[i].flash = if (g == Grade.PERFECT) 1.0 else 0.5
    }

    /** An on-time conducting beat: every playing group answers with a little lift. */
    private var beatPulse = 0.0
    fun beat() { beatPulse = 1.0 }

    /** Every frame, with the run if one is on. */
    private var perf: Performance? = null
    fun follow(run: Performance?) { perf = run }

    fun update(dt: Double) {
        clock += dt
        beatPulse = maxOf(0.0, beatPulse - dt * 4)
        sparks.update(dt)
        if (showT >= 0) {
            showT += dt
            groups.forEachIndexed { i, g -> g.update(dt, 0.9 + 0.1 * sin(showT + i), 0.9, false, false, true, null) }
            if ((showT * 2).toInt() != ((showT - dt) * 2).toInt()) {
                val a = showT * 1.3
                sparks.emit(Vec3(1.2 * sin(a), 2.2, -1.5 - 0.5 * cos(a)), 16, 0.8, 1.6, 0.016f, Vec3(0.0, -0.1, 0.0))
            }
            return
        }
        val run = perf
        val p = piece ?: return
        p.sections.forEachIndexed { i, s ->
            if (run == null) {
                groups[i].update(dt, 0.0, 0.0, false, false, false, null)
            } else {
                val w = run.wants(s.id)
                val swelling = w is Cue.Swell
                val asking = w != null && !(swelling && run.isHolding(s.id))
                groups[i].update(dt, run.gain(s.id), run.level(s.id), asking, run.isHolding(s.id), run.isOn(s.id), run.swellTarget(s.id), asking && run.due(s.id) != null)
            }
        }
    }

    fun celebrate(stars: Int) {
        for (i in 0 until stars.coerceAtLeast(1)) sparks.emit(Vec3(-0.8 + 0.8 * i, 1.7, -1.4), 28, 0.7, 1.4, 0.016f, Vec3(0.0, 0.1, 0.0))
    }

    fun trophies(n: Int) {
        while (trophyShelf.size < n) {
            val i = trophyShelf.size
            val t = models["trophy"]?.clone(Entity.CloneOptions(true, true)) ?: return
            t.setScale(0.2f + 0.03f * i)
            t.setPos(Vector3(0.7f + i * 0.24f, 0f, -0.25f))
            root.addChild(t)
            receive(t)
            trophyShelf += t
        }
    }

    fun startShow() { showT = 0.0 }
    fun endShow() { showT = -1.0; sparks.clear() }

    fun useIbl(t: TextureResource) {
        iblSource.components.set(ImageBasedLightComponent(ImageBasedLightSource.Single(t), 7f))
        receive(root)
    }

    private fun receive(e: Entity) {
        if (iblSource.components[ImageBasedLightComponent::class.java] == null) return
        if (e.components[ModelComponent::class.java] != null && e.components[ImageBasedLightReceiverComponent::class.java] == null) {
            e.components.set(ImageBasedLightReceiverComponent(iblSource))
            e.components.set(EnvironmentLightingSettingsComponent(0f))
        }
        e.getChildren().forEach { receive(it) }
    }

    companion object {
        const val RADIUS = 1.7
        const val BASE_Y = 0.75f
        const val LEVEL_H = 0.85
        val ANGLES = doubleArrayOf(-54.0, -18.0, 18.0, 54.0)
    }
}

/** A player in a group: which model, how big and how it hangs in the air. */
class Instrument(val model: String, val size: Float, val lift: Float, val gap: Float = 0.28f, val pitch: Float = 0f, val yaw: Float = 0f, val roll: Float = 0f, val bowed: Boolean = false) {
    companion object {
        private val VIOLIN = Instrument("violin", 0.6f, 0.15f, 0.3f, 0f, 0f, 0f, true)
        private val CELLO = Instrument("cello", 0.95f, 0f, 0.42f, 0f, 0f, 0f, true)
        private val HARP = Instrument("harp", 1f, 0f)
        private val FLUTE = Instrument("flute", 0.7f, 0.42f, 0.24f, 0f, 90f, 0f)
        private val CLARINET = Instrument("clarinet", 0.7f, 0.1f, 0.26f, 0f, 0f, 14f)
        private val HORN = Instrument("horn", 0.5f, 0.28f, 0.42f)
        private val TRUMPET = Instrument("trumpet", 0.55f, 0.38f, 0.3f, 0f, 90f, 0f)
        private val TIMPANI = Instrument("timpani", 0.62f, 0f, 0.5f)
        private val DIZI = Instrument("dizi", 0.65f, 0.42f, 0.24f, 0f, 90f, 0f)
        private val GUZHENG = Instrument("guzheng", 1f, 0.28f)
        private val PIPA = Instrument("pipa", 0.75f, 0.1f, 0.36f)
        private val DRUM = Instrument("drum", 0.7f, 0f)

        /** What a section of this name looks like. Unknown names show a single violin. */
        fun forSection(name: String): List<Instrument> = when (name) {
            "小提琴" -> List(3) { VIOLIN }
            "中提琴" -> List(2) { VIOLIN }
            "弦乐" -> List(3) { VIOLIN }
            "大提琴", "低音" -> List(2) { CELLO }
            "竖琴" -> listOf(HARP)
            "长笛" -> List(2) { FLUTE }
            "木管", "英国管" -> List(2) { CLARINET }
            "铜管", "圆号" -> List(2) { HORN }
            "小号" -> List(3) { TRUMPET }
            "定音鼓" -> List(2) { TIMPANI }
            "笛" -> List(2) { DIZI }
            "古筝" -> listOf(GUZHENG)
            "琵琶" -> List(2) { PIPA }
            "鼓" -> listOf(DRUM)
            else -> listOf(VIOLIN)
        }
    }
}
