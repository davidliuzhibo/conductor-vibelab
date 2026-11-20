package com.vrgames.conductor.game

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vrgames.conductor.core.Bot
import com.vrgames.conductor.core.Ev
import com.vrgames.conductor.core.Grade
import com.vrgames.conductor.core.Performance
import com.vrgames.conductor.core.Piece
import com.vrgames.conductor.core.Pieces
import com.vrgames.conductor.core.Progress
import com.vrgames.conductor.render.HallView
import com.vrgames.conductor.render.Mixer
import com.vrgames.conductor.render.Sfx
import com.vrgames.conductor.render.Sound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** State the stage, its panels and the pieces window share. Stars and free play are saved. */
object Game {
    private const val PREFS = "conductor"
    private const val TAG = "HallGame"

    enum class Phase { CARD, INTRO, LOADING, PLAYING, RESULT, SHOW }

    private var prefs: android.content.SharedPreferences? = null
    private var app: Context? = null
    var progress = Progress(); private set
    var piece by mutableStateOf(Pieces.ALL[0]); private set
    var perf by mutableStateOf<Performance?>(null); private set
    var phase by mutableStateOf(Phase.CARD)
    var version by mutableIntStateOf(0); private set
    var pieceVersion by mutableIntStateOf(0); private set
    var note by mutableStateOf<String?>(null)
    var noteVersion by mutableIntStateOf(0); private set
    var freePlay by mutableStateOf(false); private set
    var menuOpen by mutableStateOf(false)
    var confirmWipe by mutableStateOf(false)
    var faceRequest by mutableIntStateOf(0)
    var windowOpen by mutableStateOf(false)
    var closeWindow: () -> Unit = {}
    var view: HallView? = null
    var sound: Sound? = null
    var mixer: Mixer? = null
    var trophiesBefore = 0
    var bot: Bot? = null
    var debugView by mutableStateOf<String?>(null)
    var speaking by mutableStateOf(false); private set
    var resultStars by mutableIntStateOf(0); private set
    var resultAccuracy by mutableStateOf(0.0); private set
    private var player: MediaPlayer? = null
    private var ownClock = 0.0
    private var musicStarted = false
    private var showLeft = 0.0
    private val scope = CoroutineScope(Dispatchers.Main)

    val index get() = Pieces.ALL.indexOf(piece)
    val hasNext get() = index + 1 < Pieces.ALL.size

    fun init(context: Context) {
        if (prefs != null) return
        app = context.applicationContext
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { p ->
            progress = Progress.load(p.getString("best", "") ?: "")
        }
        Log.i(TAG, "stars ${progress.stars}/${progress.maxStars}")
        AutoPilot.register(context)
    }

    fun say(text: String) {
        note = text
        noteVersion++
    }

    fun start() = intro(Pieces.ALL.firstOrNull { (progress.best[it.id] ?: 0) == 0 } ?: Pieces.ALL.last())

    fun intro(p: Piece) {
        if (!progress.open(p)) {
            say("这一首还没开放：先指挥完前面的。")
            return
        }
        stop()
        piece = p
        pieceVersion++
        perf = Performance(p)
        freePlay = false
        phase = Phase.INTRO
        speak(p)
        version++
    }

    /** Decodes the stems, then counts in. */
    fun begin(free: Boolean = false) {
        stopVoice()
        stop()
        freePlay = free
        phase = Phase.LOADING
        version++
        val p = piece
        val m = mixer
        scope.launch {
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                runCatching { m?.load(p.sections.map { "stems/${p.id}_${it.id}.ogg" }) }.onFailure { Log.w(TAG, "stems: ${it.message}") }
            }
            if (phase != Phase.LOADING || piece !== p) return@launch
            val run = Performance(p)
            perf = run
            bot = if (AutoPilot.walking) Bot(run) else null
            ownClock = run.time
            musicStarted = false
            if (free) for (s in p.sections) run.pinch(s.id).also { run.letGo(s.id) }
            run.drain()
            pieceVersion++
            phase = Phase.PLAYING
            version++
            Log.i(TAG, "begin ${p.id} free=$free")
        }
    }

    fun stop() {
        mixer?.stop()
        musicStarted = false
        bot = null
    }

    fun retry() = begin(freePlay)
    fun next() = if (hasNext) intro(Pieces.ALL[index + 1]) else toCard()

    fun toCard() {
        stopVoice()
        stop()
        phase = Phase.CARD
        version++
    }

    fun quitPiece() {
        stop()
        menuOpen = false
        intro(piece)
    }

    // ---- The hand ----

    fun pinch(i: Int) {
        val run = perf ?: return
        if (phase != Phase.PLAYING) return
        val id = piece.sections.getOrNull(i)?.id ?: return
        run.pinch(id)
        drain(run)
    }

    fun hold(i: Int, level: Double) {
        val run = perf ?: return
        val id = piece.sections.getOrNull(i)?.id ?: return
        run.hold(id, level)
    }

    fun letGo(i: Int) {
        val run = perf ?: return
        val id = piece.sections.getOrNull(i)?.id ?: return
        run.letGo(id)
        drain(run)
    }

    private fun drain(run: Performance) {
        for (e in run.drain()) when (e) {
            is Ev.Entered -> {
                view?.flash(run.piece.sections.indexOfFirst { it.id == e.section }, e.grade)
                when (e.grade) {
                    Grade.PERFECT -> { sound?.play(Sfx.PERFECT); say("${sectionName(e.section)}：${Grade.PERFECT.word}") }
                    Grade.GOOD -> { sound?.play(Sfx.GOOD); say("${sectionName(e.section)}：${Grade.GOOD.word}") }
                    else -> Unit
                }
            }
            is Ev.Released -> {
                view?.flash(run.piece.sections.indexOfFirst { it.id == e.section }, e.grade)
                sound?.play(if (e.grade == Grade.PERFECT) Sfx.PERFECT else Sfx.GOOD)
                e.grade?.let { say("${sectionName(e.section)}收住：${it.word}") }
            }
            is Ev.Missed -> {
                sound?.play(Sfx.MISS)
                say("${sectionName(e.section)}：${Grade.MISS.word}。随时可以捏它补进来。")
            }
            is Ev.Early -> say("${sectionName(e.section)}：早了一点，等光圈变亮再捏。")
            is Ev.SwellDone -> say("${sectionName(e.section)}的强弱：跟上了 ${(e.score * 100).toInt()}%")
            is Ev.Beat -> { if (e.onTime) view?.beat() }
            Ev.Finished -> finish(run)
        }
        version++
    }

    private fun sectionName(id: String) = piece.sections.firstOrNull { it.id == id }?.name ?: id

    private fun finish(run: Performance) {
        stop()
        trophiesBefore = progress.trophies
        resultStars = run.stars()
        resultAccuracy = run.accuracy()
        if (!freePlay) {
            progress.record(piece.id, resultStars)
            save()
        }
        phase = Phase.RESULT
        sound?.play(Sfx.FANFARE)
        view?.celebrate(resultStars)
        Log.i(TAG, "finish ${piece.id} stars=$resultStars acc=${"%.3f".format(resultAccuracy)} free=$freePlay")
        AutoPilot.finished(piece.id, resultStars, resultAccuracy)
    }

    /** A conducting beat from the stage's hand tracking. */
    fun handBeat() {
        if (phase != Phase.PLAYING) return
        perf?.conductBeat()
    }

    fun tick(dt: Double) {
        view?.update(dt)
        if (phase == Phase.SHOW) {
            showLeft -= dt
            if (showLeft <= 0) endShow()
            return
        }
        if (phase != Phase.PLAYING) return
        val run = perf ?: return
        val m = mixer
        val now: Double
        if (run.time < 0) {
            ownClock += dt
            now = ownClock
            if (now >= 0 && !musicStarted) {
                musicStarted = true
                m?.start()
            }
        } else {
            if (!musicStarted) {
                musicStarted = true
                m?.start()
            }
            now = m?.seconds() ?: (run.time + dt)
        }
        bot?.act()
        run.advance(maxOf(now, run.time), dt)
        for ((i, s) in run.piece.sections.withIndex()) m?.setGain(i, (run.gain(s.id) * run.soloFactor(s.id)).toFloat().coerceAtMost(1.25f) * 0.8f * run.master().toFloat())
        drain(run)
    }

    private fun speak(p: Piece) {
        stopVoice()
        val a = app ?: return
        runCatching {
            val fd = a.assets.openFd("narration/${p.id}.ogg")
            player = MediaPlayer().apply {
                setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                fd.close()
                setOnCompletionListener { stopVoice() }
                prepare()
                start()
            }
            speaking = true
        }.onFailure { Log.w(TAG, "voice ${p.id}: ${it.message}") }
    }

    fun replayLore() {
        if (speaking) stopVoice() else speak(piece)
        version++
    }

    fun stopVoice() {
        player?.let { runCatching { it.stop() }; runCatching { it.release() } }
        player = null
        speaking = false
    }

    fun show() {
        if (!progress.gold && !AutoPilot.debug) return
        stopVoice()
        stop()
        phase = Phase.SHOW
        showLeft = 22.0
        sound?.play(Sfx.FANFARE)
        view?.startShow()
        version++
    }

    fun endShow() {
        view?.endShow()
        toCard()
    }

    fun pause() {
        mixer?.pause()
        stopVoice()
    }

    fun resume() = mixer?.resume()

    private fun save() {
        prefs?.edit()?.putString("best", progress.save())?.apply()
    }

    fun wipe() {
        progress = Progress()
        save()
        phase = Phase.CARD
        version++
    }

    fun grant(n: Int) {
        for (p in Pieces.ALL) progress.record(p.id, n)
        save()
        version++
    }
}
