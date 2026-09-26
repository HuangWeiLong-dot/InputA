package com.inputa.reader.ui.icons

import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 手写图标的守门人。
 *
 * 手写路径最要命的一点是**错了也编译得过**：坐标抄错一位跑到画布外、两个方向相反的图标
 * 互换、整条路径退化成一点 —— 这些在代码评审里都看不出来，在设备上只表现为「那个图标有点怪」。
 *
 * **这批断言是「几何层」的，不是「像素层」的。** 本想真的把每个图标光栅化再数墨迹，但
 * Compose 的矢量光栅化入口（`createVectorPainterFromImageVector` 与 `VectorPainter` 的构造）
 * 都是 compose-ui 模块的 `internal`，跨模块拿不到 —— 于是改成直接读 `ImageVector` 的节点树：
 * 逐点解析出绝对坐标，再断言它们落在哪里、指向哪边。
 *
 * 这层能抓住的：坐标越界、图标整体退化（画得比什么都小）、以及**方向反了**（雪佛龙互换、
 * 加减号写反、对勾起笔在左边）—— 后者是覆盖率类断言也抓不到的那一半。
 * 这层抓不住的：描边粗细在真机上是否好看、颜色是否协调。那些靠 `android/build-screenshots/`
 * 里逐屏目视，测试替代不了。
 */
class InputaIconsTest {

    // ------------------------------------------------------------ 规格

    @Test
    fun `every icon is on the agreed grid`() {
        InputaIcons.all.forEach { icon ->
            assertEquals("${icon.name} 的视口宽", IconViewport, icon.viewportWidth, 0f)
            assertEquals("${icon.name} 的视口高", IconViewport, icon.viewportHeight, 0f)
            assertEquals("${icon.name} 的默认宽", InputaIcons.defaultSize, icon.defaultWidth)
            assertEquals("${icon.name} 的默认高", InputaIcons.defaultSize, icon.defaultHeight)
        }
    }

    @Test
    fun `every icon is stroked with one spec`() {
        val paths = InputaIcons.all.flatMap { icon -> pathsOf(icon).map { icon to it } }
        assertTrue("一个图标都没有？", paths.isNotEmpty())
        paths.forEach { (icon, path) ->
            assertTrue("${icon.name} 漏了描边", path.stroke != null)
            assertEquals("${icon.name} 的描边粗细", IconStrokeWidth, path.strokeLineWidth, 0f)
            assertEquals("${icon.name} 的端头", StrokeCap.Round, path.strokeLineCap)
            assertEquals("${icon.name} 的拐角", StrokeJoin.Round, path.strokeLineJoin)
        }
    }

    @Test
    fun `no coordinate escapes the viewport`() {
        val runaway = mutableListOf<String>()
        InputaIcons.all.forEach { icon ->
            pointsOf(icon).forEach { (x, y) ->
                if (x < 0f || x > IconViewport || y < 0f || y > IconViewport) {
                    runaway += "${icon.name}: ($x, $y)"
                }
            }
        }
        assertTrue("这些坐标跑出了 ${IconViewport}×${IconViewport} 的画布：$runaway", runaway.isEmpty())
    }

    /**
     * 没有退化的图标。
     *
     * 每条路径至少要有两个**不同**的点 —— 只有一个点（或几个重合点）的路径画出来是一条
     * 零长度的线，什么也看不见，而这在源码里完全看不出来。
     */
    @Test
    fun `every icon has real geometry`() {
        InputaIcons.all.forEach { icon ->
            val points = pointsOf(icon)
            assertTrue("${icon.name} 一个点都没有", points.size >= 2)
            assertTrue(
                "${icon.name} 的点全是重合的 —— 画出来是空白",
                points.distinct().size >= 2,
            )
            val width = points.maxOf { it.first } - points.minOf { it.first }
            val height = points.maxOf { it.second } - points.minOf { it.second }
            // 门槛压到 8 而不是「接近 24」：弧只取了端点，鼓出来的那一块不在点集里，
            // 所以弧多的图标（Moon、Sun 的圆）量出来天然偏小 —— Moon 实测 9.5×9.5，
            // 而它实际画满整幅。这条的门槛只要拦住「塌成一点」就够。
            assertTrue(
                "${icon.name} 只占了 ${width}×${height}，最长边不足 8 个单位，画出来会明显偏小",
                maxOf(width, height) >= 8f,
            )
        }
    }

    // ------------------------------------------------------------ 形状与方向

    /**
     * 方向敏感的几个图标逐条对形。
     *
     * 断言的是**路径数据里的点**，不是像素：例如雪佛龙的尖在「指向那一侧的竖直中点」，
     * 所以左侧雪佛龙的最小 x 一定出现在 y=12。互换两个雪佛龙会让这一条立刻失败。
     */
    @Test
    fun `direction-sensitive icons point the right way`() {
        val left = pointsOf(InputaIcons.ChevronLeft)
        val right = pointsOf(InputaIcons.ChevronRight)
        assertEquals("ChevronLeft 的尖端该在左侧竖直中点", listOf(12f), left.withMinX().map { it.second })
        assertEquals("ChevronRight 的尖端该在右侧竖直中点", listOf(12f), right.withMaxX().map { it.second })
        assertTrue("ChevronLeft 的开口侧该分上下两处", left.withMaxX().map { it.second }.containsAll(listOf(6f, 18f)))

        // 减号只有一条横线；加号是十字，两个方向都要有跨度。
        val minus = pointsOf(InputaIcons.Minus)
        assertTrue("Minus 应当只有一条横线", minus.all { it.second == 12f } && spanX(minus) >= 12f)
        val plus = pointsOf(InputaIcons.Plus)
        assertTrue("Plus 应当在两个方向都有跨度", spanX(plus) >= 12f && spanY(plus) >= 12f)

        // 对勾起笔在右上：最高的那个点必须在右半边。
        val check = pointsOf(InputaIcons.Check)
        assertTrue("Check 的方向反了", check.withMinY().all { it.first >= 15f })

        // 垃圾桶的桶盖是一条从 x=3 到 x=21 的横线，桶身在它下面。
        val trash = pointsOf(InputaIcons.Trash2)
        // 桶盖是 `M3 6h18` 那条横线。**不能**拿「最上面那个点」当盖子 —— 提手
        // (`V4a…2-2`) 比它更高，(3,6) 会被提手盖住。
        val lid = trash.filter { it.second == 6f }
        assertTrue("Trash2 缺少桶盖那条横线", lid.size >= 2)
        assertTrue("Trash2 的桶盖该横跨整幅", lid.maxOf { it.first } - lid.minOf { it.first } >= 16f)
        assertTrue("Trash2 该有桶身（横线以下还有别的点）", trash.any { it.second > 12f })

        // 太阳的八道光芒：四个象限都得有。
        val sun = pointsOf(InputaIcons.Sun)
        listOf(6f to 6f, 18f to 6f, 6f to 18f, 18f to 18f).forEach { (qx, qy) ->
            assertTrue(
                "Sun 在 ($qx, $qy) 这个象限里没有光芒",
                sun.any { (x, y) -> (x < 12f) == (qx < 12f) && (y < 12f) == (qy < 12f) && x != 12f && y != 12f },
            )
        }

        // 放大镜的把手伸向右下角。
        assertTrue("Search 的把手应当在右下", pointsOf(InputaIcons.Search).any { it.first > 19f && it.second > 19f })

        // 摊开的书有一条书脊在中线上。
        val spine = pointsOf(InputaIcons.BookOpen).filter { it.first == 12f }
        assertTrue("BookOpen 缺少中间那条书脊", spine.size >= 2)
    }

    @Test
    fun `the icon list has no duplicates and no strays`() {
        assertEquals(
            "InputaIcons.all 里有重名条目",
            InputaIcons.all.size,
            InputaIcons.all.map { it.name }.toSet().size,
        )
        assertTrue("InputaIcons.all 不该为空", InputaIcons.all.size >= 16)
    }

    // ------------------------------------------------------------ 解析

    private fun pathsOf(icon: ImageVector): List<VectorPath> = icon.root.mapNotNull { it as? VectorPath }

    /**
     * 把图标的所有路径节点解成绝对坐标。
     *
     * **这一版 Compose 把相对指令原样保留** 成 `Relative…` 节点（`dx`／`dy`），而不是在解析时
     * 就换算成绝对坐标 —— 所以这里必须自己累加当前点。不累加的话，`a2.34 2.34 0 0 1 4.659 0`
     * 会被当成坐标 (4.659, 0)，整个图标的范围塌成一小块（`Settings` 就会误报「画得明显偏小」，
     * 写这组断言时正是这么发现的）。
     *
     * 曲线的控制点也算进去：它们与曲线同域，取范围时是有效的外界。弧只取了端点、没算鼓出来的
     * 那一块，所以弧的极值可能被低估一点点 —— 对「别画太小」与「别跑出画布」两条都够用。
     */
    private fun pointsOf(icon: ImageVector): List<Pair<Float, Float>> {
        val out = mutableListOf<Pair<Float, Float>>()

        pathsOf(icon).forEach { path ->
            // **每条 path 各自从原点起算。** 矢量格式里每个 `<path>` 是一条独立的路径，
            // 它的当前点是 (0,0)，而不是上一条 path 的终点 —— 从 X 的 `M18 6 6 18` 之后接
            // `m6 6 12 12` 就能看出来：第二条的 `m6 6` 是相对原点，落在 (6,6)，不是相对
            // 上一条的终点。跨 path 累加会把第二个笔画整条推到画布外（写这组断言时正是
            // 这么发现的，X 的两个点跑到了 (24,36)）。
            var x = 0f
            var y = 0f
            path.pathData.forEach { node ->
                when (node) {
                    // —— 绝对指令：坐标就是画布坐标。
                    is PathNode.MoveTo -> { x = node.x; y = node.y; out += x to y }
                    is PathNode.LineTo -> { x = node.x; y = node.y; out += x to y }
                    is PathNode.HorizontalTo -> { x = node.x; out += x to y }
                    is PathNode.VerticalTo -> { y = node.y; out += x to y }
                    is PathNode.ReflectiveQuadTo -> { x = node.x; y = node.y; out += x to y }
                    // 绝对弧的 arcStartX/Y 是**终点**的绝对坐标（实测确认过）。
                    is PathNode.ArcTo -> { x = node.arcStartX; y = node.arcStartY; out += x to y }
                    is PathNode.CurveTo -> {
                        out += node.x1 to node.y1
                        out += node.x2 to node.y2
                        x = node.x3; y = node.y3
                        out += x to y
                    }
                    is PathNode.ReflectiveCurveTo -> {
                        out += node.x1 to node.y1
                        x = node.x2; y = node.y2
                        out += x to y
                    }
                    is PathNode.QuadTo -> {
                        out += node.x1 to node.y1
                        x = node.x2; y = node.y2
                        out += x to y
                    }

                    // —— 相对指令：一律以当前点为基准累加。
                    is PathNode.RelativeMoveTo -> { x += node.dx; y += node.dy; out += x to y }
                    is PathNode.RelativeLineTo -> { x += node.dx; y += node.dy; out += x to y }
                    is PathNode.RelativeHorizontalTo -> { x += node.dx; out += x to y }
                    is PathNode.RelativeVerticalTo -> { y += node.dy; out += x to y }
                    is PathNode.RelativeReflectiveQuadTo -> { x += node.dx; y += node.dy; out += x to y }
                    is PathNode.RelativeArcTo -> { x += node.arcStartDx; y += node.arcStartDy; out += x to y }
                    is PathNode.RelativeCurveTo -> {
                        out += (x + node.dx1) to (y + node.dy1)
                        out += (x + node.dx2) to (y + node.dy2)
                        x += node.dx3; y += node.dy3
                        out += x to y
                    }
                    is PathNode.RelativeReflectiveCurveTo -> {
                        out += (x + node.dx1) to (y + node.dy1)
                        x += node.dx2; y += node.dy2
                        out += x to y
                    }
                    is PathNode.RelativeQuadTo -> {
                        out += (x + node.dx1) to (y + node.dy1)
                        x += node.dx2; y += node.dy2
                        out += x to y
                    }

                    // Close 没有坐标。将来 Compose 再引入新的节点类型时这里会落到 else，
                    // 那时「坐标越界」那条断言仍会兜住画到画布外的错误。
                    else -> Unit
                }
            }
        }
        return out
    }

    private fun List<Pair<Float, Float>>.withMinX() = filter { it.first == minOf { p -> p.first } }
    private fun List<Pair<Float, Float>>.withMaxX() = filter { it.first == maxOf { p -> p.first } }
    private fun List<Pair<Float, Float>>.withMinY() = filter { it.second == minOf { p -> p.second } }

    private fun spanX(points: List<Pair<Float, Float>>) = points.maxOf { it.first } - points.minOf { it.first }
    private fun spanY(points: List<Pair<Float, Float>>) = points.maxOf { it.second } - points.minOf { it.second }
}
