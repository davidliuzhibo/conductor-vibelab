#!/usr/bin/env python3
"""Writes the pieces of 手势指挥家: stems and the score the game judges against.

All the notes are entered here by hand from out-of-copyright melodies, with our own simple arrangement.
Each piece has four sections. For each one: a MIDI file rendered by FluidSynth with the FluidR3 sound font
(MIT licence), loudness-matched, then an .ogg; plus assets/scores/<id>.json with bars, tempo and the cues.

uv run -q --with mido python -I tools/make_scores.py <assets dir> <work dir> <Pieces.kt>
Needs ~/.local/fluid (fluidsynth and FluidR3_GM.sf2) and ffmpeg.
"""
import json
import os
import subprocess
import sys

import mido

ASSETS, WORK = sys.argv[1], sys.argv[2]
FLUID = os.path.expanduser("~/.local/fluid/root")
SF2 = FLUID + "/usr/share/sounds/sf2/FluidR3_GM.sf2"
SR = 44100
TPB = 480

# General MIDI programs.
VIOLIN, VIOLA, CELLO, STRINGS, HARP = 40, 41, 42, 48, 46
FLUTE, OBOE, CLARINET = 73, 68, 71
TRUMPET, HORN, TROMBONE = 56, 60, 57
TIMPANI = 47
KOTO, SHAKU, PIPA, TAIKO = 107, 77, 105, 116

SCALE = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}
CHORD = {
    "C": (0, 4, 7), "Cm": (0, 3, 7), "D": (2, 6, 9), "Dm": (2, 5, 9), "E": (4, 8, 11), "Em": (4, 7, 11), "F": (5, 9, 0), "G": (7, 11, 2),
    "G7": (7, 11, 5), "A": (9, 1, 4), "Am": (9, 0, 4), "Bb": (10, 2, 5), "B7": (11, 3, 9), "A7": (9, 1, 7), "D7": (2, 6, 0), "Bm": (11, 2, 6),
}


def notes(text, octave=5):
    """'E4 D8 C2 r4': note and length in fractions of a whole note; a dot adds half. Returns [(midi or None, beats)]."""
    out = []
    for tok in text.split():
        i = 1
        name = tok[0]
        acc = 0
        while tok[i:i + 1] in ("#", "b"):
            acc += 1 if tok[i] == "#" else -1
            i += 1
        oct_ = octave
        while tok[i:i + 1] in ("'", ","):
            oct_ += 1 if tok[i] == "'" else -1
            i += 1
        rest = tok[i:]
        dot = rest.endswith(".")
        den = int(rest.rstrip("."))
        beats = 4.0 / den * (1.5 if dot else 1.0)
        out.append((None if name == "r" else 12 * (oct_ + 1) + SCALE[name] + acc, beats))
    return out


def seq(mel):
    """[(midi, beats)] to [(start beat, midi, beats)]."""
    t = 0.0
    out = []
    for n, b in mel:
        if n is not None:
            out.append((t, n, b))
        t += b
    return out


def root(ch, base=48):
    return base + CHORD[ch][0]


def bass(chords, per_bar, base=36, pattern=(1,)):
    """One long or a pattern of root and fifth per bar."""
    out = []
    for k, ch in enumerate(chords):
        step = per_bar / len(pattern)
        for j, kind in enumerate(pattern):
            pitch = base + CHORD[ch][0] if kind == 1 else base + CHORD[ch][2]
            if pitch > base + 14:
                pitch -= 12
            out.append((k * per_bar + j * step, pitch, step * 0.95))
    return out


def arp(chords, per_bar, base=60, sub=1.0, order=(0, 1, 2, 1)):
    out = []
    for k, ch in enumerate(chords):
        tones = sorted(base + (c - base % 12) % 12 for c in CHORD[ch])
        t = 0.0
        i = 0
        while t < per_bar - 1e-6:
            out.append((k * per_bar + t, tones[order[i % len(order)]], sub * 0.95))
            t += sub
            i += 1
    return out


def pads(chords, per_bar, base=60):
    out = []
    for k, ch in enumerate(chords):
        for c in CHORD[ch]:
            out.append((k * per_bar, base + (c - base % 12) % 12, per_bar * 0.97))
    return out


def beats_hits(bars, per_bar, pitch, every=None, offbeat=False):
    every = every or per_bar
    out = []
    t = 0.0
    total = bars * per_bar
    while t < total - 1e-6:
        out.append((t, pitch, every * 0.6))
        t += every
    return out


def repeat(events, length, times):
    return [(t + length * k, n, b) for k in range(times) for t, n, b in events]


def entries(seconds_of, events, base_vel):
    return events


def piece(pid, title, composer, chapter, lore, bpm, per_bar, bars, sections, cues):
    return dict(id=pid, title=title, composer=composer, chapter=chapter, lore=lore, bpm=bpm, perBar=per_bar, bars=bars, sections=sections, cues=cues)


def section(sid, name, program, events, kind, vel=86):
    return dict(id=sid, name=name, program=program, events=events, kind=kind, vel=vel)


PIECES = []

# ---- 1 茉莉花 (Chinese folk song; pentatonic, in G) ----
jasmine = notes(
    "B4 B8 D'8 E'8 G'8 G'8 E'8 D'4 D'8 E'8 D'2 "
    "B4 B8 D'8 E'8 G'8 G'8 E'8 D'4 D'8 E'8 D'2 "
    "D'4 D'4 D'4 B8 D'8 E'4 E'4 D'2 "
    "B4 A8 B8 D'4 B8 A8 G4 G8 A8 G2 ", 4)
jas_ch = ["G", "G", "C", "G", "G", "G", "C", "G", "G", "G", "C", "G", "G", "D", "G", "G"]
jas_bars = 16
PIECES.append(piece("jasmine", "茉莉花", "江南民歌", 1,
    "《茉莉花》是流传了几百年的江南民歌。两百多年前，它随旅行者的记录传到欧洲，后来被写进了歌剧《图兰朵》。先让笛声唱起旋律，再一组一组请乐器进来。",
    84, 4, jas_bars,
    [section("melody", "笛", FLUTE, seq(jasmine * 2), "melody"),
     section("harmony", "弦乐", STRINGS, pads(jas_ch, 4, 60), "pad", 62),
     section("pluck", "竖琴", HARP, arp(jas_ch, 4, 55, 1.0), "arp", 72),
     section("bass", "大提琴", CELLO, bass(jas_ch, 4, 36), "bass", 76)],
    [("enter", "melody", 0.0), ("enter", "bass", 16.0), ("enter", "pluck", 32.0), ("enter", "harmony", 48.0)]))

# ---- 2 欢乐颂 (Beethoven, 9th symphony; d. 1827) ----
ode_a = "E4 E4 F4 G4 G4 F4 E4 D4 C4 C4 D4 E4 E4. D8 D2 "
ode_b = "E4 E4 F4 G4 G4 F4 E4 D4 C4 C4 D4 E4 D4. C8 C2 "
ode_c = "D4 D4 E4 C4 D4 E8 F8 E4 C4 D4 E8 F8 E4 D4 C4 D4 G,2 "
ode = notes(ode_a + ode_b + ode_c + ode_b)
ode_ch = ["C", "G", "C", "G", "C", "G", "C", "C", "G", "C", "G", "C", "C", "G", "C", "C"]
PIECES.append(piece("ode", "欢乐颂", "贝多芬", 1,
    "贝多芬写第九交响曲时，耳朵已经完全听不见了。首演结束，他背对观众，是歌手把他转过身，他才看到全场起立。今天轮到你把这首歌带出来。",
    104, 4, 16,
    [section("melody", "小提琴", VIOLIN, seq(ode), "melody"),
     section("winds", "木管", CLARINET, arp(ode_ch, 4, 60, 2.0, (0, 2)), "arp", 72),
     section("brass", "铜管", HORN, pads(ode_ch, 4, 55), "pad", 70),
     section("bass", "大提琴", CELLO, bass(ode_ch, 4, 36, (1, 2)), "bass", 78)],
    [("enter", "melody", 0.0), ("enter", "bass", 8.0), ("enter", "winds", 16.0), ("enter", "brass", 32.0), ("swell", "brass", 48.0, 64.0, 0.5, 1.0)]))

# ---- 3 小夜曲 (Mozart, K. 525; d. 1791) ----
serenade = notes(
    "G4 r8 D8 G4 r8 D8 G8 D8 G8 B8 D'4 r4 "
    "C'4 r8 A8 C'4 r8 A8 C'8 A8 F#8 A8 D4 r4 "
    "G4 G4. B8 A8 G8 G8 F#8 F#4 r4 "
    "A4 A4. C'8 B8 A8 A8 G8 G4 r4 ", 4)
ser_ch = ["G", "G", "D", "D", "G", "D", "D", "G"]
PIECES.append(piece("serenade", "G 大调小夜曲", "莫扎特", 2,
    "这是莫扎特三十一岁时写的小夜曲，当时没有留下任何演出记录，乐谱在他去世三十多年后才出版。如今它成了世界上最常被演奏的古典乐之一。",
    132, 4, 24,
    [section("melody", "小提琴", VIOLIN, seq(serenade * 3), "melody"),
     section("harmony", "中提琴", VIOLA, arp(ser_ch * 3, 4, 55, 0.5, (0, 2, 1, 2)), "arp", 66),
     section("winds", "长笛", FLUTE, seq([(n + 12 if n else None, b) for n, b in serenade * 3]), "melody", 62),
     section("bass", "大提琴", CELLO, bass(ser_ch * 3, 4, 36, (1, 2, 1, 2)), "bass", 76)],
    [("enter", "melody", 0.0), ("enter", "bass", 16.0), ("enter", "harmony", 32.0), ("swell", "melody", 48.0, 64.0, 0.6, 1.0), ("enter", "winds", 64.0)]))

# ---- 4 蓝色多瑙河 (Johann Strauss II; d. 1899) ----
danube = notes(
    "D2. D4 F#4 A4 A2. A2. r4 A'4 A'4 r4 F#'4 F#'4 r2. r2. "
    "D2. D4 F#4 A4 A2. A2. r4 B'4 B'4 r4 G'4 G'4 r2. r2. "
    "C#2. C#4 E4 B4 B2. B2. r4 B'4 B'4 r4 G'4 G'4 r2. r2. "
    "C#2. C#4 E4 B4 B2. B2. r4 A'4 A'4 r4 F#'4 F#'4 r2. r2. ", 5)
dan_ch = ["D", "D", "D", "D", "D", "D", "D", "D", "A", "A", "A", "A", "A", "A", "D", "D", "A", "A", "A", "A", "A", "A", "D", "D"]
dan_bars = 32
dan_ch = (["D"] * 8 + ["A7"] * 8 + ["A7"] * 8 + ["D"] * 8)
PIECES.append(piece("danube", "蓝色多瑙河", "约翰·施特劳斯", 2,
    "这支圆舞曲首演时并不算成功，可半年后在巴黎世博会上一奏，就风靡了欧洲。在维也纳，每年新年音乐会都要加演它，全场的掌声还没落下，琴弓就已经抬起来了。",
    160, 3, dan_bars,
    [section("melody", "小提琴", VIOLIN, seq(danube), "melody"),
     section("harmony", "弦乐", STRINGS, [e for k, ch in enumerate(dan_ch) for e in ((k * 3 + 1, 62 + (CHORD[ch][1] - 2) % 12, 0.7), (k * 3 + 2, 62 + (CHORD[ch][2] - 2) % 12, 0.7))], "arp", 64),
     section("winds", "圆号", HORN, pads(dan_ch[::2], 6, 55), "pad", 60),
     section("bass", "低音", CELLO, [(k * 3, 38 + (CHORD[ch][0] - 2) % 12, 0.9) for k, ch in enumerate(dan_ch)], "bass", 80)],
    [("enter", "bass", 0.0), ("enter", "harmony", 12.0), ("enter", "melody", 24.0), ("swell", "melody", 48.0, 72.0, 0.55, 1.0), ("enter", "winds", 72.0)]))

# ---- 5 新世界第二乐章 (Dvořák; d. 1904) ----
largo = notes(
    "E4. G8 G2 E4. D8 C2 D4 E4 G4 E4 D2. r4 "
    "E4. G8 G2 E4. D8 C2 D4 E4 D4 C4 C2. r4 "
    "G4. C'8 C'2 A4. G8 E2 D4 E4 G4 D4 C2. r4 ", 5)
lar_ch = ["C", "G", "F", "C", "C", "G", "F", "C", "C", "F", "G", "C"]
PIECES.append(piece("largo", "自新大陆 · 广板", "德沃夏克", 3,
    "德沃夏克在纽约想家时写下了这段旋律。英国管独自吹出来，像是远方有人在轻轻唱。后来他的学生给它配上歌词，就成了《念故乡》。",
    60, 4, 12,
    [section("melody", "英国管", 69, seq(largo), "melody", 88),
     section("harmony", "弦乐", STRINGS, pads(lar_ch, 4, 55), "pad", 58),
     section("winds", "木管", CLARINET, arp(lar_ch, 4, 60, 2.0, (2, 1)), "arp", 56),
     section("bass", "低音", CELLO, bass(lar_ch, 4, 36), "bass", 66)],
    [("enter", "harmony", 0.0), ("enter", "melody", 4.0), ("enter", "bass", 16.0), ("release", "harmony", 28.0), ("enter", "harmony", 32.0), ("enter", "winds", 32.0), ("swell", "melody", 32.0, 44.0, 1.0, 0.5)]))

# ---- 6 卡门 · 斗牛士之歌 (Bizet; d. 1875) ----
toreador = notes(
    "C4 D8 C8 A4 A4 A8 G8 A8 Bb8 A2 "
    "Bb4 G8 C'8 A4 F4 D8 G8 E8 C8 F2 "
    "C4 D8 C8 A4 A4 A8 G8 A8 Bb8 A2 "
    "G4 C'8 C'8 B4 A4 G4. A8 G2 ", 5)
tor_ch = ["F", "F", "Bb", "F", "F", "F", "G7", "C"]
PIECES.append(piece("toreador", "斗牛士之歌", "比才", 3,
    "歌剧《卡门》首演时被批评得一塌糊涂，比才三个月后就去世了，没能看到它成为全世界上演最多的歌剧之一。斗牛士一出场，铜管和小军鼓就要抢着亮相。",
    112, 4, 24,
    [section("melody", "小号", TRUMPET, seq(toreador * 3), "melody", 92),
     section("harmony", "弦乐", STRINGS, arp(tor_ch * 3, 4, 60, 1.0, (0, 1, 2, 1)), "arp", 66),
     section("winds", "圆号", HORN, pads(tor_ch * 3, 4, 55), "pad", 66),
     section("bass", "定音鼓", TIMPANI, bass(tor_ch * 3, 4, 41, (1, 2)), "bass", 92)],
    [("enter", "bass", 0.0), ("enter", "melody", 16.0), ("enter", "harmony", 32.0), ("release", "bass", 47.0), ("enter", "bass", 48.0), ("enter", "winds", 48.0), ("swell", "melody", 64.0, 80.0, 0.6, 1.0), ("release", "harmony", 92.0)]))

# ---- 7 康定情歌 (Sichuan folk song) ----
kangding = notes(
    "E8 G8 A8 A8 G8 A8 E8 D8 "
    "E8 G8 A8 A8 G8 A8 E4 "
    "G8 G8 E8 D8 C8 D8 E4 "
    "A,8 C8 D8 E8 C8 A,8 A,4 ", 5)
kan_ch = ["Am", "Am", "C", "Am"]
PIECES.append(piece("kangding", "康定情歌", "四川民歌", 4,
    "跑马溜溜的山上，一朵溜溜的云。这首歌来自川西的康定，那里是茶马古道上的要塞。琵琶和古筝先走出来，再把笛声请进山谷。",
    92, 4, 16,
    [section("melody", "笛", SHAKU, seq(kangding * 4), "melody", 84),
     section("pluck", "古筝", KOTO, arp(kan_ch * 4, 4, 60, 0.5, (0, 1, 2, 1)), "arp", 76),
     section("harmony", "琵琶", PIPA, arp(kan_ch * 4, 4, 55, 1.0, (0, 2)), "arp", 72),
     section("bass", "鼓", TAIKO, beats_hits(16, 4, 40, 2.0), "bass", 84)],
    [("enter", "pluck", 0.0), ("enter", "melody", 8.0), ("enter", "bass", 16.0), ("enter", "harmony", 32.0), ("swell", "melody", 40.0, 56.0, 0.6, 1.0)]))

# ---- 8 送别 (Melody: J. P. Ordway, d. 1880; known in China as 送别) ----
songbie = notes(
    "G2 E4 G4 C'2. r4 A2 C'4 A4 G2. r4 "
    "G2 C4 D4 E2 D4 C4 D1 "
    "G2 E4 G4 C'2. B4 A2 C'4 A4 G2. r4 "
    "G2 D4 E4 F2. B,4 C1 ", 5)
son_ch = ["C", "C", "F", "C", "C", "C", "G", "G", "C", "C", "F", "C", "C", "G", "G", "C"]
PIECES.append(piece("songbie", "送别", "奥德威 曲", 4,
    "长亭外，古道边。这段旋律原本来自一位美国作曲家，传到日本，再由李叔同填上中文词，成了几代中国人记忆里的歌。最后这一首，请你把所有乐器慢慢送走。",
    72, 4, 16,
    [section("melody", "笛", SHAKU, seq(songbie), "melody", 84),
     section("pluck", "古筝", KOTO, arp(son_ch, 4, 60, 1.0, (0, 1, 2, 1)), "arp", 74),
     section("harmony", "弦乐", STRINGS, pads(son_ch, 4, 55), "pad", 58),
     section("bass", "大提琴", CELLO, bass(son_ch, 4, 36), "bass", 66)],
    [("enter", "pluck", 0.0), ("enter", "melody", 8.0), ("enter", "bass", 16.0), ("enter", "harmony", 32.0), ("swell", "harmony", 32.0, 48.0, 0.5, 1.0), ("release", "bass", 56.0), ("release", "harmony", 60.0)]))


def write_midi(path, program, events, vel, bpm, channel=0):
    m = mido.MidiFile(ticks_per_beat=TPB)
    t = mido.MidiTrack()
    m.tracks.append(t)
    t.append(mido.MetaMessage("set_tempo", tempo=mido.bpm2tempo(bpm), time=0))
    t.append(mido.Message("program_change", program=program, channel=channel, time=0))
    t.append(mido.Message("control_change", control=91, value=48, channel=channel, time=0))
    t.append(mido.Message("control_change", control=93, value=10, channel=channel, time=0))
    msgs = []
    for start, n, length in events:
        msgs.append((int(start * TPB), 1, n))
        msgs.append((int((start + length) * TPB), 0, n))
    msgs.sort(key=lambda x: (x[0], x[1]))
    now = 0
    for tick, on, n in msgs:
        t.append(mido.Message("note_on" if on else "note_off", note=n, velocity=vel if on else 0, channel=channel, time=tick - now))
        now = tick
    m.save(path)


def render(pid, sec, bpm, total_beats):
    wav = f"{WORK}/{pid}_{sec['id']}.wav"
    mid = f"{WORK}/{pid}_{sec['id']}.mid"
    write_midi(mid, sec["program"], sec["events"], sec["vel"], bpm)
    env = dict(os.environ, LD_LIBRARY_PATH=FLUID + "/usr/lib/x86_64-linux-gnu")
    subprocess.run([FLUID + "/usr/bin/fluidsynth", "-ni", "-g", "0.8", "-r", str(SR), "-F", wav, SF2, mid], env=env, check=True, capture_output=True)
    return wav


def write_kotlin(path):
    """The score as code, from the very data the stems came from, so the two can never drift apart."""
    def q(t):
        return '"' + t.replace('"', '\\"') + '"'
    out = ["package com.vrgames.conductor.core", "", "// Written by tools/make_scores.py. Do not edit by hand.", "", "object Pieces {", "    val ALL: List<Piece> = listOf("]
    for p in PIECES:
        total = p["bars"] * p["perBar"]
        secs = ", ".join(f"Section({q(s['id'])}, {q(s['name'])})" for s in p["sections"])
        cues = []
        for c in p["cues"]:
            if c[0] == "swell":
                cues.append(f"Cue.Swell({q(c[1])}, {c[2]}, {c[3]}, {c[4]}, {c[5]})")
            elif c[0] == "enter":
                cues.append(f"Cue.Enter({q(c[1])}, {c[2]})")
            else:
                cues.append(f"Cue.Release({q(c[1])}, {c[2]})")
        out.append(f"        Piece({q(p['id'])}, {q(p['title'])}, {q(p['composer'])}, {p['chapter']}, {q(p['lore'])}, {p['bpm']}.0, {p['perBar']}, {p['bars']}, {total * 60.0 / p['bpm'] + 2.5:.3f},")
        out.append(f"            listOf({secs}),")
        out.append("            listOf(" + ", ".join(cues) + ")),")
    out += ["    )", "}", ""]
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(out))


def main():
    os.makedirs(WORK, exist_ok=True)
    os.makedirs(f"{ASSETS}/stems", exist_ok=True)
    for p in PIECES:
        total = p["bars"] * p["perBar"]
        seconds = total * 60.0 / p["bpm"] + 2.5
        for s in p["sections"]:
            wav = render(p["id"], s, p["bpm"], total)
            ogg = f"{ASSETS}/stems/{p['id']}_{s['id']}.ogg"
            # Every stem exactly as long as the piece, stereo, same rate: the mixer plays them sample for sample.
            subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", wav, "-af", f"apad,atrim=0:{seconds:.3f}", "-ar", str(SR), "-ac", "2", "-c:a", "libvorbis", "-q:a", "3", ogg], check=True)
        last = max(max(t + b for t, _, b in s["events"]) for s in p["sections"])
        assert last <= total + 1, (p["id"], last, total)
        ids = [s["id"] for s in p["sections"]]
        for c in p["cues"]:
            assert c[1] in ids, (p["id"], c)
            assert c[2] < total, (p["id"], c)
        print(p["id"], f"{seconds:.1f}s", f"{len(p['cues'])} cues", "melody ends", round(max(t + b for t, _, b in p["sections"][0]["events"]) / p["perBar"], 1), "of", p["bars"], "bars")
    write_kotlin(sys.argv[3])
    print("done")


main()
