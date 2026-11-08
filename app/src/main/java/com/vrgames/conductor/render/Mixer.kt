package com.vrgames.conductor.render

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import android.util.Log
import java.nio.ByteOrder

/**
 * Plays the stems of a piece sample for sample with a volume per stem. Separate media players drift apart; one track mixed
 * here cannot. The clock comes from the track itself, so the score and the sound stay together; if a device reports no
 * progress (an emulator without audio does this), it falls back to wall time.
 */
class Mixer(private val context: Context) {
    private var stems: List<ShortArray> = emptyList()
    private val gains = FloatArray(MAX_STEMS)
    private val shown = FloatArray(MAX_STEMS)
    private var thread: Thread? = null
    @Volatile private var running = false
    @Volatile private var paused = false
    @Volatile private var track: AudioTrack? = null
    @Volatile private var startedAt = 0L
    @Volatile private var pausedFor = 0L
    @Volatile private var pausedSince = 0L
    @Volatile private var headMoved = false
    private var lastHead = 0
    private var lastHeadAt = 0L
    private var lastSeconds = 0.0

    /** Decodes every stem. Slow: call off the main thread. */
    fun load(files: List<String>) {
        stop()
        stems = files.map { decode(it) }
        Log.i(TAG, "loaded ${files.size} stems, ${stems.sumOf { it.size } / 1024 / 512} MB")
    }

    fun setGain(i: Int, g: Float) { if (i in gains.indices) gains[i] = g }

    fun start() {
        if (stems.isEmpty()) return
        stop()
        val min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(maxOf(min, BLOCK * 4 * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        running = true
        paused = false
        headMoved = false
        lastHead = 0
        lastHeadAt = 0L
        lastSeconds = 0.0
        startedAt = SystemClock.elapsedRealtime()
        pausedFor = 0
        shown.fill(0f)
        t.play()
        thread = Thread({ loop(t) }, "mixer").also { it.priority = Thread.MAX_PRIORITY; it.start() }
    }

    private fun loop(t: AudioTrack) {
        val block = ShortArray(BLOCK * 2)
        val data = stems
        val total = data.maxOf { it.size }
        var at = 0
        while (running) {
            if (paused) {
                runCatching { Thread.sleep(20) }
                continue
            }
            if (at >= total) {
                block.fill(0)
            } else {
                val n = minOf(block.size, total - at)
                for (i in 0 until block.size) block[i] = 0
                // The volume moves across the block, so a change never clicks.
                for (s in data.indices) {
                    val from = shown[s]
                    val to = gains[s]
                    val pcm = data[s]
                    var i = 0
                    while (i < n) {
                        val src = at + i
                        if (src >= pcm.size) break
                        val g = from + (to - from) * i / n
                        val v = block[i] + (pcm[src] * g).toInt()
                        block[i] = v.coerceIn(-32768, 32767).toShort()
                        i++
                    }
                    shown[s] = to
                }
                at += n
            }
            t.write(block, 0, block.size)
        }
        runCatching { t.stop() }
        runCatching { t.release() }
    }

    /**
     * Seconds of music played so far. The track's play head can move in coarse steps (about a second on the emulator,
     * a buffer's length on some devices), and the cues are judged to 0.15 s, so between steps the clock runs on with the
     * real time since the head last moved, capped, and never goes back.
     */
    fun seconds(): Double {
        val t = track ?: return 0.0
        val head = runCatching { t.playbackHeadPosition }.getOrDefault(0)
        val now = SystemClock.elapsedRealtime()
        if (head > 0) headMoved = true
        if (!headMoved) {
            val paused0 = if (paused) now - pausedSince else 0
            return (now - startedAt - pausedFor - paused0) / 1000.0
        }
        if (head != lastHead) {
            lastHead = head
            lastHeadAt = now
        }
        if (paused) return lastSeconds
        val run = minOf((now - lastHeadAt) / 1000.0, MAX_EXTRAPOLATE_S)
        val s = maxOf(lastSeconds, head.toDouble() / RATE + run)
        lastSeconds = s
        return s
    }

    fun pause() {
        if (!running || paused) return
        paused = true
        pausedSince = SystemClock.elapsedRealtime()
        runCatching { track?.pause() }
    }

    fun resume() {
        if (!running || !paused) return
        pausedFor += SystemClock.elapsedRealtime() - pausedSince
        // The head has not moved while paused: run on from now, not from before the pause.
        lastHeadAt = SystemClock.elapsedRealtime()
        paused = false
        runCatching { track?.play() }
    }

    fun stop() {
        running = false
        thread?.let { runCatching { it.join(300) } }
        thread = null
        track = null
    }

    private fun decode(name: String): ShortArray {
        val fd = context.assets.openFd(name)
        val ex = MediaExtractor()
        ex.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
        fd.close()
        ex.selectTrack(0)
        val format = ex.getTrackFormat(0)
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()
        var out = ShortArray(RATE * 2 * 60)
        var size = 0
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var done = false
        while (!done) {
            if (!inputDone) {
                val i = codec.dequeueInputBuffer(5000)
                if (i >= 0) {
                    val buf = codec.getInputBuffer(i)!!
                    val n = ex.readSampleData(buf, 0)
                    if (n < 0) {
                        codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0)
                        ex.advance()
                    }
                }
            }
            val o = codec.dequeueOutputBuffer(info, 5000)
            if (o >= 0) {
                val buf = codec.getOutputBuffer(o)!!
                buf.position(info.offset)
                buf.limit(info.offset + info.size)
                val shorts = buf.order(ByteOrder.nativeOrder()).asShortBuffer()
                val n = shorts.remaining()
                if (size + n > out.size) out = out.copyOf(maxOf(out.size * 2, size + n))
                shorts.get(out, size, n)
                size += n
                codec.releaseOutputBuffer(o, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) done = true
            }
        }
        codec.stop()
        codec.release()
        ex.release()
        return out.copyOf(size)
    }

    companion object {
        private const val TAG = "Mixer"
        const val RATE = 44100
        // Longer than the coarsest head step seen (about 1 s on the emulator); a stall longer than this holds the clock.
        const val MAX_EXTRAPOLATE_S = 1.5
        private const val BLOCK = 1024
        private const val MAX_STEMS = 8
    }
}

/** A narrator or effect clip that needs no mixing. */
enum class Sfx(val file: String, val volume: Float) {
    PERFECT("perfect", 0.7f),
    GOOD("good", 0.55f),
    MISS("miss", 0.4f),
    FANFARE("fanfare", 0.9f),
}
