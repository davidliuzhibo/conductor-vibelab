package com.vrgames.conductor.core

// Written by tools/make_scores.py. Do not edit by hand.

object Pieces {
    val ALL: List<Piece> = listOf(
        Piece("jasmine", "茉莉花", "江南民歌", 1, "《茉莉花》是流传了几百年的江南民歌。两百多年前，它随旅行者的记录传到欧洲，后来被写进了歌剧《图兰朵》。先让笛声唱起旋律，再一组一组请乐器进来。", 84.0, 4, 16, 48.214,
            listOf(Section("melody", "笛"), Section("harmony", "弦乐"), Section("pluck", "竖琴"), Section("bass", "大提琴")),
            listOf(Cue.Enter("melody", 0.0), Cue.Enter("bass", 16.0), Cue.Enter("pluck", 32.0), Cue.Enter("harmony", 48.0))),
        Piece("ode", "欢乐颂", "贝多芬", 1, "贝多芬写第九交响曲时，耳朵已经完全听不见了。首演结束，他背对观众，是歌手把他转过身，他才看到全场起立。今天轮到你把这首歌带出来。", 104.0, 4, 16, 39.423,
            listOf(Section("melody", "小提琴"), Section("winds", "木管"), Section("brass", "铜管"), Section("bass", "大提琴")),
            listOf(Cue.Enter("melody", 0.0), Cue.Enter("bass", 8.0), Cue.Enter("winds", 16.0), Cue.Enter("brass", 32.0), Cue.Swell("brass", 48.0, 64.0, 0.5, 1.0))),
        Piece("serenade", "G 大调小夜曲", "莫扎特", 2, "这是莫扎特三十一岁时写的小夜曲，当时没有留下任何演出记录，乐谱在他去世三十多年后才出版。如今它成了世界上最常被演奏的古典乐之一。", 132.0, 4, 24, 46.136,
            listOf(Section("melody", "小提琴"), Section("harmony", "中提琴"), Section("winds", "长笛"), Section("bass", "大提琴")),
            listOf(Cue.Enter("melody", 0.0), Cue.Enter("bass", 16.0), Cue.Enter("harmony", 32.0), Cue.Swell("melody", 48.0, 64.0, 0.6, 1.0), Cue.Enter("winds", 64.0))),
        Piece("danube", "蓝色多瑙河", "约翰·施特劳斯", 2, "这支圆舞曲首演时并不算成功，可半年后在巴黎世博会上一奏，就风靡了欧洲。在维也纳，每年新年音乐会都要加演它，全场的掌声还没落下，琴弓就已经抬起来了。", 160.0, 3, 32, 38.500,
            listOf(Section("melody", "小提琴"), Section("harmony", "弦乐"), Section("winds", "圆号"), Section("bass", "低音")),
            listOf(Cue.Enter("bass", 0.0), Cue.Enter("harmony", 12.0), Cue.Enter("melody", 24.0), Cue.Swell("melody", 48.0, 72.0, 0.55, 1.0), Cue.Enter("winds", 72.0))),
        Piece("largo", "自新大陆 · 广板", "德沃夏克", 3, "德沃夏克在纽约想家时写下了这段旋律。英国管独自吹出来，像是远方有人在轻轻唱。后来他的学生给它配上歌词，就成了《念故乡》。", 60.0, 4, 12, 50.500,
            listOf(Section("melody", "英国管"), Section("harmony", "弦乐"), Section("winds", "木管"), Section("bass", "低音")),
            listOf(Cue.Enter("harmony", 0.0), Cue.Enter("melody", 4.0), Cue.Enter("bass", 16.0), Cue.Release("harmony", 28.0), Cue.Enter("harmony", 32.0), Cue.Enter("winds", 32.0), Cue.Swell("melody", 32.0, 44.0, 1.0, 0.5))),
        Piece("toreador", "斗牛士之歌", "比才", 3, "歌剧《卡门》首演时被批评得一塌糊涂，比才三个月后就去世了，没能看到它成为全世界上演最多的歌剧之一。斗牛士一出场，铜管和小军鼓就要抢着亮相。", 112.0, 4, 24, 53.929,
            listOf(Section("melody", "小号"), Section("harmony", "弦乐"), Section("winds", "圆号"), Section("bass", "定音鼓")),
            listOf(Cue.Enter("bass", 0.0), Cue.Enter("melody", 16.0), Cue.Enter("harmony", 32.0), Cue.Release("bass", 47.0), Cue.Enter("bass", 48.0), Cue.Enter("winds", 48.0), Cue.Swell("melody", 64.0, 80.0, 0.6, 1.0), Cue.Release("harmony", 92.0))),
        Piece("kangding", "康定情歌", "四川民歌", 4, "跑马溜溜的山上，一朵溜溜的云。这首歌来自川西的康定，那里是茶马古道上的要塞。琵琶和古筝先走出来，再把笛声请进山谷。", 92.0, 4, 16, 44.239,
            listOf(Section("melody", "笛"), Section("pluck", "古筝"), Section("harmony", "琵琶"), Section("bass", "鼓")),
            listOf(Cue.Enter("pluck", 0.0), Cue.Enter("melody", 8.0), Cue.Enter("bass", 16.0), Cue.Enter("harmony", 32.0), Cue.Swell("melody", 40.0, 56.0, 0.6, 1.0))),
        Piece("songbie", "送别", "奥德威 曲", 4, "长亭外，古道边。这段旋律原本来自一位美国作曲家，传到日本，再由李叔同填上中文词，成了几代中国人记忆里的歌。最后这一首，请你把所有乐器慢慢送走。", 72.0, 4, 16, 55.833,
            listOf(Section("melody", "笛"), Section("pluck", "古筝"), Section("harmony", "弦乐"), Section("bass", "大提琴")),
            listOf(Cue.Enter("pluck", 0.0), Cue.Enter("melody", 8.0), Cue.Enter("bass", 16.0), Cue.Enter("harmony", 32.0), Cue.Swell("harmony", 32.0, 48.0, 0.5, 1.0), Cue.Release("bass", 56.0), Cue.Release("harmony", 60.0))),
    )
}
