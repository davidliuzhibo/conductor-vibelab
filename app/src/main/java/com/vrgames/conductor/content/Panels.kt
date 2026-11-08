package com.vrgames.conductor.content

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.PhysicalLengthConverter
import com.vrgames.conductor.core.Cue
import com.vrgames.conductor.core.Pieces
import com.vrgames.conductor.game.Game

const val PANEL_CARD = "card"
const val PANEL_INTRO = "intro"
const val PANEL_HUD = "hud"
const val PANEL_RESULT = "result"
const val PANEL_MENU = "menu"
const val PANEL_NOTE = "note"
const val PANEL_SHOW = "show"
const val PANEL_LOADING = "loading"
const val LEVELS_ID = "Pieces"
fun labelId(i: Int) = "label$i"

val Ink = Color(0xFFF6EEDD)
val Dim = Color(0xFFD4C6B4)
val Gold = Color(0xFFE9C46A)
val Sky = Color(0xFF9CD6EC)
private val ButtonBg = Color(0xFF4A2231)
private val ButtonLine = Color(0xFFB08A48)
private val GoldText = Color(0xFF2A1A08)
val WindowBg = Color(0xF41E121A)

val FRAME_SIZE = mapOf(
    PANEL_CARD to (0.56f to 0.4f),
    PANEL_INTRO to (0.62f to 0.46f),
    PANEL_RESULT to (0.56f to 0.4f),
    PANEL_HUD to (0.34f to 0.26f),
    PANEL_MENU to (0.34f to 0.3f),
)

@Composable
fun PhysicalLengthConverter.m(meters: Float): Dp = lengthToDp(meters, LengthUnit.Meters)

@Composable
fun PhysicalLengthConverter.sz(meters: Float) = m(meters).value.sp

/** Text on the velvet inside a gilded frame; the frame model is added by the stage. */
@Composable
private fun Framed(c: PhysicalLengthConverter, id: String, content: @Composable () -> Unit) {
    val (w, h) = FRAME_SIZE.getValue(id)
    Column(
        Modifier.width(c.m(w)).height(c.m(h)).padding(horizontal = c.m(0.02f), vertical = c.m(0.01f)),
        verticalArrangement = Arrangement.spacedBy(c.m(0.008f), Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
fun GoldButton(c: PhysicalLengthConverter, text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(c.m(0.012f))
    Text(
        text,
        Modifier.clip(shape).background(Gold).border(c.m(0.002f), Color(0xFFFFF0C8), shape).clickable { onClick() }
            .padding(horizontal = c.m(0.03f), vertical = c.m(0.014f)),
        fontSize = c.sz(0.026f),
        color = GoldText,
    )
}

@Composable
fun PlainButton(c: PhysicalLengthConverter, text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(c.m(0.01f))
    Text(
        text,
        Modifier.clip(shape).background(ButtonBg).border(c.m(0.0015f), ButtonLine, shape).clickable { onClick() }
            .padding(horizontal = c.m(0.016f), vertical = c.m(0.01f)),
        fontSize = c.sz(0.019f),
        color = Ink,
    )
}

@Composable
private fun Body(c: PhysicalLengthConverter, text: String, color: Color = Ink, size: Float = 0.02f) =
    Text(text, Modifier.fillMaxWidth(), fontSize = c.sz(size), color = color, textAlign = TextAlign.Center)

@Composable
fun CardPanel(c: PhysicalLengthConverter, openPieces: () -> Unit) {
    Game.version
    val p = Game.progress
    Framed(c, PANEL_CARD) {
        Text("手势指挥家", fontSize = c.sz(0.046f), color = Gold)
        Body(c, "乐器悬在空中，等你示意。共 ${Pieces.ALL.size} 首曲子。")
        Body(c, "捏一下：请这一组进来。捏住抬手或放手：让它更响或更轻。乐句结束时松手：让它收住。", Sky, 0.019f)
        Body(c, "已得 ${p.stars} / ${p.maxStars} 颗星", Dim, 0.018f)
        GoldButton(c, if (p.stars == 0) "开始第 1 首" else "继续") { Game.start() }
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.012f))) {
            PlainButton(c, "选曲和奖杯") { openPieces() }
            if (p.gold) PlainButton(c, "隐藏演出") { Game.show() }
        }
    }
}

@Composable
fun IntroPanel(c: PhysicalLengthConverter) {
    Game.version
    val p = Game.piece
    Framed(c, PANEL_INTRO) {
        Text("第 ${Game.index + 1} / ${Pieces.ALL.size} 首 · ${p.composer}", fontSize = c.sz(0.019f), color = Dim)
        Text(p.title, fontSize = c.sz(0.038f), color = Gold)
        Body(c, p.lore)
        Body(c, "亮起金光的那一组，就是该你示意的时候。音乐不会停，错过了也可以补进来。", Sky, 0.018f)
        GoldButton(c, "开始指挥") { Game.begin() }
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.012f))) {
            PlainButton(c, if (Game.speaking) "停止讲解" else "听讲解") { Game.replayLore() }
            if ((Game.progress.best[p.id] ?: 0) > 0) PlainButton(c, "自由指挥") { Game.begin(free = true) }
            PlainButton(c, "返回") { Game.toCard() }
        }
    }
}

@Composable
fun LoadingPanel(c: PhysicalLengthConverter) {
    Text("乐团正在就位……", fontSize = c.sz(0.03f), color = Gold)
}

@Composable
fun HudPanel(c: PhysicalLengthConverter) {
    Game.version
    val run = Game.perf
    Framed(c, PANEL_HUD) {
        Text("第 ${Game.index + 1} / ${Pieces.ALL.size} 首 · ${Game.piece.title}", fontSize = c.sz(0.021f), color = Gold, textAlign = TextAlign.Center)
        val left = ((Game.piece.seconds - (run?.time ?: 0.0)).coerceAtLeast(0.0)).toInt()
        Text(if ((run?.time ?: 0.0) < 0) "预备……" else "还有 $left 秒", fontSize = c.sz(0.02f), color = Ink)
        if (Game.freePlay) Text("自由指挥，不计分", fontSize = c.sz(0.018f), color = Sky)
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.008f))) {
            PlainButton(c, "重来") { Game.retry() }
            PlainButton(c, "菜单") { Game.menuOpen = true }
        }
    }
}

/** Beside each group: its name and, in the early chapters, what the light is asking for. */
@Composable
fun SectionLabel(c: PhysicalLengthConverter, i: Int) {
    Game.version
    val p = Game.piece
    val s = p.sections.getOrNull(i) ?: return
    val run = Game.perf
    val want = run?.wants(s.id)
    val now = run?.due(s.id) != null
    val ask = if (p.chapter > 2 || Game.phase != Game.Phase.PLAYING) null else when (want) {
        is Cue.Enter -> if (run.isOn(s.id)) null else if (now) "现在捏！" else "准备，光变亮时捏"
        is Cue.Swell -> if (want.to > want.from) "捏住，慢慢抬手" else "捏住，慢慢放手"
        is Cue.Release -> "到时松手"
        null -> null
    }
    val shape = RoundedCornerShape(c.m(0.012f))
    Column(
        Modifier.clip(shape).background(Color(0xCC1E121A)).padding(horizontal = c.m(0.02f), vertical = c.m(0.008f)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(s.name, fontSize = c.sz(0.034f), color = if (want != null) Gold else Ink)
        if (ask != null) Text(ask, fontSize = c.sz(0.026f), color = Sky)
    }
}

@Composable
fun ResultPanel(c: PhysicalLengthConverter, openPieces: () -> Unit) {
    Game.version
    val s = Game.resultStars
    val now = Game.progress.trophies
    Framed(c, PANEL_RESULT) {
        Text(if (s > 0) "全场掌声" else "这一遍还没合上", fontSize = c.sz(0.036f), color = Gold)
        if (Game.freePlay) Body(c, "自由指挥不计分。") else {
            Text("★".repeat(s) + "☆".repeat(3 - s), fontSize = c.sz(0.04f), color = Gold)
            Body(c, "准确度 ${(Game.resultAccuracy * 100).toInt()}%")
        }
        if (now > Game.trophiesBefore) Body(c, "得到${listOf("", "铜", "银", "金")[now]}杯！" + if (now == 3) "隐藏演出已开启。" else "", Sky)
        if (Game.hasNext && (s > 0 || (Game.progress.best[Game.piece.id] ?: 0) > 0)) GoldButton(c, "下一首：第 ${Game.index + 2} / ${Pieces.ALL.size} 首") { Game.next() }
        else if (!Game.hasNext) GoldButton(c, "全部演完了，回到开头") { Game.toCard() }
        else GoldButton(c, "再来一次") { Game.retry() }
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.012f))) {
            PlainButton(c, "再来一次") { Game.retry() }
            PlainButton(c, "选曲") { openPieces() }
            if (Game.progress.gold) PlainButton(c, "隐藏演出") { Game.show() }
        }
    }
}

@Composable
fun ShowPanel(c: PhysicalLengthConverter) {
    PlainButton(c, "结束演出，返回") { Game.endShow() }
}

@Composable
fun NotePanel(c: PhysicalLengthConverter) {
    val shape = RoundedCornerShape(c.m(0.012f))
    Text(
        Game.note ?: "",
        Modifier.width(c.m(0.5f)).clip(shape).background(Color(0xEE1E121A)).border(c.m(0.0015f), ButtonLine, shape).padding(c.m(0.012f)),
        fontSize = c.sz(0.024f),
        color = Ink,
        textAlign = TextAlign.Center,
    )
}

@Composable
fun MenuPanel(c: PhysicalLengthConverter, openPieces: () -> Unit, faceMe: () -> Unit, quit: () -> Unit, version: String) {
    Game.version
    Framed(c, PANEL_MENU) {
        Text("菜单", fontSize = c.sz(0.026f), color = Gold)
        if (Game.confirmWipe) {
            Body(c, "清除全部星星和奖杯？", Color(0xFFF08A6A))
            Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.01f))) {
                PlainButton(c, "清除") { Game.confirmWipe = false; Game.menuOpen = false; Game.wipe() }
                PlainButton(c, "返回") { Game.confirmWipe = false }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.008f))) {
                PlainButton(c, "结束这一首") { Game.quitPiece() }
                PlainButton(c, "选曲") { Game.menuOpen = false; openPieces() }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.008f))) {
                PlainButton(c, "摆到我正前方") { faceMe() }
                PlainButton(c, "清除进度") { Game.confirmWipe = true }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.008f))) {
                PlainButton(c, "退出游戏") { quit() }
                PlainButton(c, "返回") { Game.menuOpen = false }
            }
            Text("版本 $version", fontSize = c.sz(0.014f), color = Dim)
        }
    }
}

/** 选曲和奖杯: every piece with its stars, by chapter. */
@Composable
fun PiecesWindow() {
    Game.version
    val p = Game.progress
    val chapters = listOf("入门", "强弱", "合奏", "东方")
    Column(Modifier.fillMaxSize().background(WindowBg).padding(28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("选曲和奖杯", fontSize = 32.sp, color = Gold)
            Text(
                "返回",
                Modifier.clip(RoundedCornerShape(10.dp)).background(ButtonBg).clickable { Game.closeWindow() }.padding(horizontal = 22.dp, vertical = 10.dp),
                fontSize = 22.sp,
                color = Ink,
            )
        }
        Text("总星数 ${p.stars} / ${p.maxStars}", fontSize = 22.sp, color = Ink)
        Text("铜杯：全部指挥过${if (p.bronze) "（已得）" else ""}", fontSize = 20.sp, color = if (p.bronze) Sky else Dim)
        Text("银杯：一半曲目三星${if (p.silver && p.bronze) "（已得）" else ""}", fontSize = 20.sp, color = if (p.silver && p.bronze) Sky else Dim)
        Text("金杯：全部三星，开启隐藏演出${if (p.gold) "（已得）" else ""}", fontSize = 20.sp, color = if (p.gold) Sky else Dim)
        for ((k, x) in Pieces.ALL.withIndex()) {
            if (k == 0 || Pieces.ALL[k - 1].chapter != x.chapter) Text("第 ${x.chapter} 章 · ${chapters[x.chapter - 1]}", fontSize = 24.sp, color = Sky)
            val open = p.open(x)
            val st = p.best[x.id] ?: 0
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x264A2231))
                    .clickable(enabled = open) { Game.closeWindow(); Game.intro(x) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("${k + 1}. ${x.title} · ${x.composer}", fontSize = 23.sp, color = if (open) Ink else Dim)
                Text(if (open) "★".repeat(st) + "☆".repeat(3 - st) else "未开放", fontSize = 23.sp, color = Gold)
            }
        }
    }
}
