package com.vrgames.conductor.game

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.vrgames.conductor.core.Pieces

/**
 * The test hook (debug builds only): adb shell am broadcast -a com.vrgames.conductor.TEST -p com.vrgames.conductor
 * --es cmd walk. The walk conducts every piece with a perfect bot through the game's own calls, so it proves the rules, the
 * mixer's clock and the rendering, not that a pointing hand can reach a section or how late the sound feels.
 *
 * cmd: walk · piece --es id ode · begin · pinch --es id 0 · hold --es id 0,0.9 · letgo --es id 0 · grant --es id 3 · show
 * · reset · stop · view --es id "x,y,z,yaw,scale,pitch"|reset · ray · replace --es id 3 · card
 */
object AutoPilot {
    const val ACTION = "com.vrgames.conductor.TEST"
    const val TAG = "HallTest"
    private val main = Handler(Looper.getMainLooper())
    var debug = false; private set
    var walking = false; private set
    private var at = 0

    fun register(context: Context) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        debug = true
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                // 2026-11-19: the menu's 退出, for the emulator test (every window of the app should go).
                if (i.getStringExtra("cmd") == "quit") { android.util.Log.i(TAG, "cmd quit"); com.vrgames.conductor.platform.AppExit.finishAll(c) }
                else command(i.getStringExtra("cmd") ?: "", i.getStringExtra("id"))
            }
        }
        ContextCompat.registerReceiver(context.applicationContext, receiver, IntentFilter(ACTION), ContextCompat.RECEIVER_EXPORTED)
        Log.i(TAG, "ready")
    }

    fun ready() = Log.i(TAG, "hall ready")

    private fun command(cmd: String, id: String?) {
        Log.i(TAG, "cmd $cmd ${id.orEmpty()}")
        when (cmd) {
            "walk" -> { main.removeCallbacksAndMessages(null); walking = true; at = 0; main.postDelayed(::walkPiece, 500) }
            "piece" -> Pieces.ALL.firstOrNull { it.id == id }?.let { Game.grant(1); Game.intro(it) }
            "begin" -> Game.begin()
            "pinch" -> id?.toIntOrNull()?.let { Game.pinch(it) }
            "hold" -> id?.split(",")?.let { Game.hold(it[0].toInt(), it[1].toDouble()) }
            "letgo" -> id?.toIntOrNull()?.let { Game.letGo(it) }
            "grant" -> Game.grant(id?.toIntOrNull() ?: 3)
            "show" -> Game.show()
            "view" -> Game.debugView = id?.takeIf { it != "reset" }
            // v0.3.2: rays from the eyes through the title card's buttons; logs whether a pinchable instrument is in the way.
            "ray" -> com.vrgames.conductor.content.debugRays()
            // v0.3.2: measure the eye height again (core.EyeHeight), the head reading zeros for the first id seconds.
            "replace" -> com.vrgames.conductor.content.debugReplace(id?.toDoubleOrNull() ?: 0.0)
            // The title card again (to test its buttons).
            "card" -> { stop(); Game.toCard() }
            "reset" -> { stop(); Game.wipe() }
            "stop" -> stop()
            else -> Log.w(TAG, "unknown command $cmd")
        }
    }

    private fun stop() {
        main.removeCallbacksAndMessages(null)
        walking = false
    }

    private fun walkPiece() {
        if (at >= Pieces.ALL.size) {
            Log.i(TAG, "walk done stars=${Game.progress.stars}/${Game.progress.maxStars} trophies=${Game.progress.trophies}")
            walking = false
            return
        }
        Game.grant(1)
        Game.intro(Pieces.ALL[at])
        main.postDelayed({ Game.begin() }, 1500)
    }

    fun finished(id: String, stars: Int, accuracy: Double) {
        if (!walking) return
        Log.i(TAG, "result $id stars=$stars acc=${"%.3f".format(accuracy)}")
        at++
        main.postDelayed(::walkPiece, 2000)
    }
}
