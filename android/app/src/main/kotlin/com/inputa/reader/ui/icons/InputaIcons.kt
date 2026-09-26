package com.inputa.reader.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 应用内图标。
 *
 * **为什么是手写的 `ImageVector`，不是图标库。** `material-icons` 不在本工程的 classpath 上，
 * 而且在 material3 1.4.0 下已经不再通过依赖传递进来了 —— `Icons.Default.Settings` 根本编译不过。
 * 加依赖是一条路，但本工程对依赖的态度是写下来的（`res/values/themes.xml` 为了一个主题
 * 都不肯引 AppCompat；`NOTICE.md` 逐件说明每个第三方字节的来历），而这里只需要十几个字形。
 *
 * **几何来自 Web 版用的 lucide**（`lucide-react` v1.46.0，ISC 许可），逐条抄自
 * `node_modules/lucide-react` 里每个图标文件中 `d` 属性的原文。这样做是因为两端的图标
 * 应当是**同一族**：Web 版那一套是 24×24 视口、2 描边、圆头圆角连接、`currentColor`，
 * 在 20dp 上渲染。规格照抄，形状才不会「看着差不多但不一样」。出处与许可记在 `NOTICE.md`。
 *
 * **规格写在一处**（见 [strokeIcon]），所以不存在「某个图标描边 1.5、某个 2」这种漂移 ——
 * 这也是选 Kotlin 而不是 `res/drawable` 里十几个 XML 的原因：规格成了代码，还能被测试断言。
 *
 * **没有搬过来的那些**：`Sparkles`、`Volume2`、`Play`、`Send`、`Languages`、`FileText`、
 * `Loader2`、`Download`、`Upload`、`Library`、`RotateCcw`、`BookmarkPlus` —— 它们对应的功能
 * （朗读、AI 分析、笔记、例句、词爆）在本端只有数据层、还没有界面。不预先造这些字形，
 * 免得仓库里躺着一批没有调用点的资源。
 */
object InputaIcons {

    /** 与 Web 版 `h-5 w-5` 一致。 */
    val defaultSize: Dp = 20.dp

    val BookOpen: ImageVector = strokeIcon(
        "BookOpen",
        "M12 5v16",
        "M20.001 19A2 2 0 0022 17V5a2 2 0 00-1.999-2L16 3.002A5 5 0 0012 5a5 5 0 00-4-2H4a2 2 0 00-2 2v12a2 2 0 001.999 2H8a5 5 0 014 2 5 5 0 014-2z",
    )

    /** lucide 里 `book-marked` 是 `book-bookmark` 的别名，几何取自后者。 */
    val BookMarked: ImageVector = strokeIcon(
        "BookMarked",
        "M10 2v7.751a.25.25 0 00.407.195l2.28-1.834a.5.5 0 01.627 0l2.28 1.834A.25.25 0 0016 9.751V2",
        "M4 19.5v-15A2.5 2.5 0 016.5 2H19a1 1 0 011 1v18a1 1 0 01-1 1H6.5a1 1 0 010-5H20",
    )

    val Settings: ImageVector = strokeIcon(
        "Settings",
        "M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915",
        // lucide 这里是个 <circle cx=12 cy=12 r=3>；ImageVector 没有圆形节点，所以写成
        // 两段半圆弧 —— 同一个圆，换一种说法。
        "M9 12a3 3 0 1 0 6 0a3 3 0 1 0-6 0",
    )

    val Search: ImageVector = strokeIcon(
        "Search",
        "m21 21-4.34-4.34",
        // <circle cx=11 cy=11 r=8>
        "M3 11a8 8 0 1 0 16 0a8 8 0 1 0-16 0",
    )

    val X: ImageVector = strokeIcon("X", "M18 6 6 18", "m6 6 12 12")

    val Check: ImageVector = strokeIcon("Check", "M20 6 9 17l-5-5")

    val ChevronLeft: ImageVector = strokeIcon("ChevronLeft", "m15 18-6-6 6-6")

    val ChevronRight: ImageVector = strokeIcon("ChevronRight", "m9 18 6-6-6-6")

    val Minus: ImageVector = strokeIcon("Minus", "M5 12h14")

    val Plus: ImageVector = strokeIcon("Plus", "M5 12h14", "M12 5v14")

    val RefreshCw: ImageVector = strokeIcon(
        "RefreshCw",
        "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8",
        "M21 3v5h-5",
        "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16",
        "M8 16H3v5",
    )

    /** 汉堡：顶栏那三个入口收进它里面。lucide 的 `menu`。 */
    val Menu: ImageVector = strokeIcon(
        "Menu",
        "M4 5h16",
        "M4 12h16",
        "M4 19h16",
    )

    /** 撤销：翻页收录提示条上那个「撤销」。语义上就是 lucide 的 `rotate-ccw`。 */
    val RotateCcw: ImageVector = strokeIcon(
        "RotateCcw",
        "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8",
        "M3 3v5h5",
    )

    /** lucide 里 `trash-2` 是 `trash` 的别名（两个名字指向同一份几何）。 */
    val Trash2: ImageVector = strokeIcon(
        "Trash2",
        "M10 11v6",
        "M14 11v6",
        "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6",
        "M3 6h18",
        "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2",
    )

    /** lucide 里 `alert-triangle` 是 `triangle-alert` 的别名。 */
    val AlertTriangle: ImageVector = strokeIcon(
        "AlertTriangle",
        "m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3",
        "M12 9v4",
        "M12 17h.01",
    )

    val Sun: ImageVector = strokeIcon(
        "Sun",
        // <circle cx=12 cy=12 r=4>
        "M8 12a4 4 0 1 0 8 0a4 4 0 1 0-8 0",
        "M12 2v2",
        "M12 20v2",
        "m4.93 4.93 1.41 1.41",
        "m17.66 17.66 1.41 1.41",
        "M2 12h2",
        "M20 12h2",
        "m6.34 17.66-1.41 1.41",
        "m19.07 4.93-1.41 1.41",
    )

    val Moon: ImageVector = strokeIcon(
        "Moon",
        "M20.985 12.486a9 9 0 1 1-9.473-9.472c.405-.022.617.46.402.803a6 6 0 0 0 8.268 8.268c.344-.215.825-.004.803.401",
    )

    /** Web 版用咖啡杯表示「羊皮纸」主题 —— 比一个色块更能说明那是什么。 */
    val Coffee: ImageVector = strokeIcon(
        "Coffee",
        "M10 2v2",
        "M14 2v2",
        "M16 8a1 1 0 0 1 1 1v8a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4V9a1 1 0 0 1 1-1h14a4 4 0 1 1 0 8h-1",
        "M6 2v2",
    )

    /**
     * 全部图标，供测试遍历。
     *
     * 新增图标**必须挂进这个列表** —— 测试是按它遍历的，漏挂就等于没有断言，
     * 而手写路径最容易犯的错（坐标跑出画布、形状画反）恰恰只有那些断言能发现。
     */
    val all: List<ImageVector> = listOf(
        BookOpen, BookMarked, Settings, Menu, Search, X, Check,
        ChevronLeft, ChevronRight, Minus, Plus,
        RefreshCw, RotateCcw, Trash2, AlertTriangle, Sun, Moon, Coffee,
    )
}

/** 图标规格里唯一的「视口」尺寸。lucide 的几何都是画在 24×24 上的。 */
internal const val IconViewport: Float = 24f

/** 描边粗细，同样是 lucide 的规格。它在视口单位下声明，由绘制端按尺寸缩放。 */
internal const val IconStrokeWidth: Float = 2f

/**
 * 按统一规格造一个图标。**规格只有这一处**。
 *
 * 描边色写死成黑色，看着奇怪但无所谓：`Icon` 用 `ColorFilter` 上色，这里填什么都会被覆盖。
 * 之所以不写成 `Color.Unspecified`，是因为 `equals` 参与 `ImageVector` 的缓存判断，
 * 一个确定的常量比 Unspecified 更容易推理。
 *
 * 一个图标内所有子路径合并进**同一个** `path` 节点：它们共享同一套描边参数，
 * 分开写只会让节点数变多而没有区别。
 */
private fun strokeIcon(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = InputaIcons.defaultSize,
        defaultHeight = InputaIcons.defaultSize,
        viewportWidth = IconViewport,
        viewportHeight = IconViewport,
    ).apply {
        // 用 `addPath(节点列表)` 而不是那个 `path { … }` 语法糖：后者接的是一串
        // `moveTo` / `lineTo` 命令，而这里的几何是以 SVG 的 `d` 字符串形式抄过来的
        // （见文件头：与原版逐字对应才好核对），所以要先用 `PathParser` 解析成节点。
        //
        // **每个 `d` 各成一条路径，不许合并。** SVG 里每个 `<path>` 是独立的一条：它的
        // 当前点是原点，所以 `m6 6` 落在 (6,6)。把两个 `d` 的节点拼进同一条路径之后，
        // 后一个开头的相对指令就变成相对前一条的终点了 —— 图标会整块错位，而源码上看不出来
        // （X 的两笔、Sun 的八道光芒都是这个形状：后一条以 `m` 开头）。`InputaIconsTest`
        // 的坐标越界断言就是为了抓这个。
        paths.forEach { d ->
            addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = IconStrokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()
