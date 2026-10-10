package com.ai.videoplayer

enum class FilterMode(
    val id: Int,
    val shortName: String,
    val title: String,
    val desc: String
) {
    OFF(
        0, "滤镜",
        "关闭（原始画面）",
        "不做任何增强，播放原始画质"
    ),
    REALISM(
        1, "写实",
        "写实增强 · DLSS 风",
        "对比自适应锐化 + 光影微对比 + 轻泛光去雾，游戏录屏、实拍视频更锐利真实"
    ),
    ANIME(
        2, "动漫",
        "动漫超分 · Anime4K 风",
        "线条检测加深 + 色块净化，低码率动画、老番专用"
    ),
    ULTRA(
        3, "超清晰",
        "超清晰",
        "强锐化 + 细节还原，网课、监控、高压缩视频救星"
    ),
    HDR(
        4, "HDR 光影",
        "HDR 光影增强",
        "大半径局部对比 + 泛光 + 高光塑形，强化光感与立体感"
    ),
    CINEMA(
        5, "影院",
        "影院胶片",
        "青橙电影调色 + 暗角 + 胶片颗粒"
    );

    companion object {
        fun fromId(id: Int): FilterMode =
            values().firstOrNull { it.id == id } ?: OFF
    }
}
