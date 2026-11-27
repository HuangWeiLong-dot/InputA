package com.inputa.next.core.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 界面图标。几何取自 lucide（24×24、2px 描边），与 LingQ 的细线图标语言一致。
 */
object LqIcons {
    private fun line(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 20.dp,
            defaultHeight = 20.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            paths.forEach { d ->
                addPath(
                    pathData = PathParser().parsePathString(d).toNodes(),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    private fun filled(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 20.dp,
            defaultHeight = 20.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            paths.forEach { d ->
                addPath(
                    pathData = PathParser().parsePathString(d).toNodes(),
                    fill = SolidColor(Color.Black),
                )
            }
        }.build()

    val Home = line(
        "Home",
        "M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8",
        "M3 10a2 2 0 0 1 .709-1.528l7-5.999a2 2 0 0 1 2.582 0l7 5.999A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z",
    )

    val HomeFilled = filled(
        "HomeFilled",
        "M12 2.6 3.7 9.7A2 2 0 0 0 3 11.2V19a2 2 0 0 0 2 2h4.5v-6.5a1 1 0 0 1 1-1h3a1 1 0 0 1 1 1V21H19a2 2 0 0 0 2-2v-7.8a2 2 0 0 0-.7-1.5Z",
    )

    val Search = line("Search", "m21 21-4.34-4.34", "M3 11a8 8 0 1 0 16 0a8 8 0 1 0-16 0")

    val Bookmarked = line(
        "Bookmarked",
        "M10 2v7.751a.25.25 0 00.407.195l2.28-1.834a.5.5 0 01.627 0l2.28 1.834A.25.25 0 0016 9.751V2",
        "M4 19.5v-15A2.5 2.5 0 016.5 2H19a1 1 0 011 1v18a1 1 0 01-1 1H6.5a1 1 0 010-5H20",
    )

    val Headphones = line(
        "Headphones",
        "M3 14h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H4a1 1 0 0 1-1-1v-7a9 9 0 0 1 18 0v7a1 1 0 0 1-1 1h-2a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2h3",
    )

    val Plus = line("Plus", "M5 12h14", "M12 5v14")
    val Minus = line("Minus", "M5 12h14")

    val Settings = line(
        "Settings",
        "M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915",
        "M9 12a3 3 0 1 0 6 0a3 3 0 1 0-6 0",
    )

    /** 通用 AI 图标，用来替换 LingQ 的狐狸。 */
    val Bot = line(
        "Bot",
        "M12 8V4H8",
        "M4 8h16a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2z",
        "M2 14h2",
        "M20 14h2",
        "M15 13v2",
        "M9 13v2",
    )

    /** 阅读器右下角的狐狸 AI，形状对齐 LingQ。 */
    val Fox = filled(
        "Fox",
        "M4 3.4 9.1 6h5.8L20 3.4l1.2 6.1a8.2 8.2 0 0 1-2.1 7.6A8.2 8.2 0 0 1 12 21a8.2 8.2 0 0 1-7.1-3.9A8.2 8.2 0 0 1 2.8 9.5Z",
    )

    /** 底栏 Vocabulary / LingQs：带 A 的对话卡片，对齐 LingQ 图标。 */
    val LingqBubble = line(
        "LingqBubble",
        "M4.5 3.5h15A2.5 2.5 0 0 1 22 6v9a2.5 2.5 0 0 1-2.5 2.5h-9L5 21v-3.5H4.5A2.5 2.5 0 0 1 2 15V6a2.5 2.5 0 0 1 2.5-2.5Z",
        "M7.5 13.5 10 6.5l2.5 7",
        "M8.4 11.2h3.2",
    )

    /** 底栏 Playlist：音符卡片。 */
    val MusicNote = line(
        "MusicNote",
        "M9 18V5l12-2v13",
        "M9 18a3 3 0 1 1-6 0 3 3 0 0 1 6 0",
        "M21 16a3 3 0 1 1-6 0 3 3 0 0 1 6 0",
    )

    val Check = line("Check", "M20 6 9 17l-5-5")
    val X = line("X", "M18 6 6 18", "m6 6 12 12")

    val EyeOff = line(
        "EyeOff",
        "M9.88 9.88a3 3 0 1 0 4.24 4.24",
        "M10.73 5.08A10.43 10.43 0 0 1 12 5c7 0 10 7 10 7a13.16 13.16 0 0 1-1.67 2.68",
        "M6.61 6.61A13.526 13.526 0 0 0 2 12s3 7 10 7a9.74 9.74 0 0 0 5.39-1.61",
        "M2 2l20 20",
    )

    val ArrowLeft = line("ArrowLeft", "m12 19-7-7 7-7", "M19 12H5")
    val ArrowRight = line("ArrowRight", "M5 12h14", "m12 5 7 7-7 7")
    val ChevronDown = line("ChevronDown", "m6 9 6 6 6-6")
    val ChevronRight = line("ChevronRight", "m9 18 6-6-6-6")
    val ChevronUp = line("ChevronUp", "m18 15-6-6-6 6")
    val MoreHorizontal = line("More", "M5 12h.01", "M12 12h.01", "M19 12h.01")

    val Play = filled("Play", "M7 4.5a1 1 0 0 1 1.53-.85l11 7.5a1 1 0 0 1 0 1.7l-11 7.5A1 1 0 0 1 7 19.5Z")
    val Pause = filled("Pause", "M7 5h3a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1Zm7 0h3a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1h-3a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1Z")
    val SkipBack = filled("SkipBack", "M18 5.5v13a1 1 0 0 1-1.55.83L9 13.9V18a1 1 0 0 1-2 0V6a1 1 0 0 1 2 0v4.1l7.45-5.43A1 1 0 0 1 18 5.5Z")
    val SkipForward = filled("SkipForward", "M6 5.5v13a1 1 0 0 0 1.55.83L15 13.9V18a1 1 0 0 0 2 0V6a1 1 0 0 0-2 0v4.1L7.55 4.67A1 1 0 0 0 6 5.5Z")

    val Volume = line(
        "Volume",
        "M11 5 6 9H2v6h4l5 4z",
        "M15.54 8.46a5 5 0 0 1 0 7.07",
        "M19.07 4.93a10 10 0 0 1 0 14.14",
    )

    val Repeat = line(
        "Repeat",
        "m17 2 4 4-4 4",
        "M3 11v-1a4 4 0 0 1 4-4h14",
        "m7 22-4-4 4-4",
        "M21 13v1a4 4 0 0 1-4 4H3",
    )

    val RepeatOne = line(
        "RepeatOne",
        "m17 2 4 4-4 4",
        "M3 11v-1a4 4 0 0 1 4-4h14",
        "m7 22-4-4 4-4",
        "M21 13v1a4 4 0 0 1-4 4H3",
        "M11 10h1v4",
    )

    val RepeatOff = line(
        "RepeatOff",
        "m2 9 3-3 3 3",
        "M13 18H7a2 2 0 0 1-2-2V6",
        "m22 15-3 3-3-3",
        "M11 6h6a2 2 0 0 1 2 2v10",
        "m17 5 4 4-4 4",
        "M4 4l16 16",
    )

    val Flame = line(
        "Flame",
        "M12 3q1 4 4 6.5t3 5.5a1 1 0 0 1-14 0 5 5 0 0 1 1-3 1 1 0 0 0 5 0c0-2-1.5-3-1.5-5q0-2 2.5-4",
    )

    val Trophy = line(
        "Trophy",
        "M6 9H4.5a2.5 2.5 0 0 1 0-5H6",
        "M18 9h1.5a2.5 2.5 0 0 0 0-5H18",
        "M4 22h16",
        "M10 14.66V17c0 .55-.47.98-.97 1.21C7.85 18.75 7 20.24 7 22",
        "M14 14.66V17c0 .55.47.98.97 1.21C16.15 18.75 17 20.24 17 22",
        "M18 2H6v7a6 6 0 0 0 12 0V2Z",
    )

    val Clock = line("Clock", "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18", "M12 7v5l3 2")

    val BarChart = line("BarChart", "M3 3v18h18", "M18 17V9", "M13 17V5", "M8 17v-3")

    val Sparkles = line(
        "Sparkles",
        "m12 3-1.9 5.8a2 2 0 0 1-1.3 1.3L3 12l5.8 1.9a2 2 0 0 1 1.3 1.3L12 21l1.9-5.8a2 2 0 0 1 1.3-1.3L21 12l-5.8-1.9a2 2 0 0 1-1.3-1.3Z",
        "M5 3v4",
        "M19 17v4",
        "M3 5h4",
        "M17 19h4",
    )

    val Globe = line(
        "Globe",
        "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18",
        "M2 12h20",
        "M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z",
    )

    val LogOut = line(
        "LogOut",
        "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4",
        "m16 17 5-5-5-5",
        "M21 12H9",
    )

    val Refresh = line("Refresh", "M21 12a9 9 0 1 1-2.64-6.36", "M21 3v6h-6")

    val Layers = line(
        "Layers",
        "m12.83 2.18a2 2 0 0 0-1.66 0L2.6 6.08a1 1 0 0 0 0 1.83l8.58 3.91a2 2 0 0 0 1.66 0l8.58-3.9a1 1 0 0 0 0-1.83Z",
        "m22 12.18-9.17 4.16a2 2 0 0 1-1.66 0L2 12.18",
        "m22 17.18-9.17 4.16a2 2 0 0 1-1.66 0L2 17.18",
    )

    val List = line("List", "M8 6h13", "M8 12h13", "M8 18h13", "M3 6h.01", "M3 12h.01", "M3 18h.01")

    val Grid = line("Grid", "M3 3h7v7H3z", "M14 3h7v7h-7z", "M14 14h7v7h-7z", "M3 14h7v7H3z")

    val Filter = line("Filter", "M4 6h16", "M7 12h10", "M10 18h4")

    val TrendingUp = line("TrendingUp", "M22 7 13.5 15.5 8.5 10.5 2 17", "M16 7h6v6")

    val BookOpen = line(
        "BookOpen",
        "M12 7v14",
        "M3 18a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h5a4 4 0 0 1 4 4 4 4 0 0 1 4-4h5a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1h-6a3 3 0 0 0-3 3 3 3 0 0 0-3-3z",
    )

    val Type = line("Type", "M4 7V4h16v3", "M9 20h6", "M12 4v16")

    val Bookmark = line("Bookmark", "m19 21-7-4-7 4V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16z")

    val Cards = line(
        "Cards",
        "M8 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h2",
        "M16 3h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-2",
        "M12 3v18",
    )

    val Info = line("Info", "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18", "M12 11v5", "M12 8h.01")

    val Bell = line(
        "Bell",
        "M10.268 21a2 2 0 0 0 3.464 0",
        "M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 4.499-1.411 5.956-2.738 7.326",
    )

    val Expand = line(
        "Expand",
        "M15 3h6v6",
        "M9 21H3v-6",
        "M21 3l-7 7",
        "M3 21l7-7",
    )

    /** 更换头像。 */
    val Camera = line(
        "Camera",
        "M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3l-2.5-3z",
        "M12 17a4 4 0 1 0 0-8a4 4 0 0 0 0 8z",
    )
}
