package com.inputa.reader.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * 除颜色之外的全部设计标度。
 *
 * **为什么单独一个文件、而不是加进 [ThemeTokens]。** 三条理由，都是这个仓库里已经踩过的：
 *
 *   1. `ThemeTokens` 是 data class，而且它在**每帧路径上被当作 `remember` 的键**
 *      （`WordParagraph` 里 `remember(measurer, tokens, fontSizeSp)`）。往里加非颜色字段，
 *      间距变一分就会让那个缓存失效 —— 而间距与那段几何毫无关系。同一个坑这个文件里
 *      已经绕过一次：熟练度底色的缓存是 `by lazy` 属性而不是构造参数，见 [ThemeTokens.levelBackground]。
 *   2. `ThemePalettes` 有三个实例。把间距放进构造，就等于宣称间距随主题变 —— 它不变。
 *   3. `LevelContrastTest` 遍历 `ThemePalettes.all`。几何留在外面，那个测试的题目才恰好是「颜色」。
 *
 * **取值不是新发明的一套 4dp 栅格，而是把已经在用的值收敛成档位。** 改造前横向内边距散在
 * 7/8/9/10/12/14/16/18/20 九个数上；若改成全新栅格，每一处都会同时挪位，「哪一屏被动过」
 * 就看不出来了。按下面这套映射，单处位移不超过 ±2dp（7→8、9→8、10→12、14→12、18→16或20），
 * 前后两张截图就能对照着看完 —— 这正是第一阶段想要的性质。
 *
 * **与 Web 版的关系**：Web 版对 `*, *::before, *::after` 设了 `border-radius: 0`，并且全局
 * 没有阴影 / 高度标度。本端**有意偏离**：界面走卡片式（圆角 + 1px 描边 + 极淡阴影），
 * 正文仍然通栏。理由与边界写在 `CLAUDE.md` 的「刻意分歧」一节。
 */
object Space {

    val xs: Dp = 4.dp
    val sm: Dp = 6.dp
    val md: Dp = 8.dp
    val lg: Dp = 12.dp
    val xl: Dp = 16.dp
    val xxl: Dp = 20.dp
    val xxxl: Dp = 28.dp
}

/**
 * 圆角只有这一套。
 *
 * 这个仓库原先的规矩是「全局零圆角，遇到会画圆角的 M3 组件就显式传 `RectangleShape`」，
 * 依据是「`Shapes` 的构造函数在当前 Material3 版本里是 internal，覆盖不了
 * `MaterialTheme.shapes`」—— **那条依据是错的**：`Shapes()` 的无参构造与
 * `copy(extraSmall = …, …)` 都是公开且未废弃的 API，本端实测覆盖生效（去掉
 * `ModalBottomSheet` 上那个显式的 `shape =` 之后，底部面板的上圆角确实按 `shapes.extraLarge` 画了出来）。
 *
 * 所以规矩改成：**圆角由这里定，经 `MaterialTheme.shapes` 下发；某个组件上再出现裸的
 * `shape = …` 参数就是坏味道** —— 它只会在某处悄悄压掉全局设定，就像当初那个面板一样。
 */
object Radius {

    /** 徽标、选中框、考试标签这类小元件。 */
    val chip: Dp = 6.dp

    /** 按钮、输入框、分段控件。 */
    val control: Dp = 10.dp

    /** 卡片、面板、底部面板的上缘。 */
    val card: Dp = 14.dp

    val chipShape: CornerBasedShape = RoundedCornerShape(chip)
    val controlShape: CornerBasedShape = RoundedCornerShape(control)
    val cardShape: CornerBasedShape = RoundedCornerShape(card)

    /** 底部面板的拖拽把手：胶囊。M3 的默认值本来就是它，所以这里只是把形状写明白。 */
    val pill: Shape = RoundedCornerShape(percent = 50)
}

/**
 * 高度。
 *
 * **2dp 的阴影很淡，而且在夜间主题里根本看不见**（`bgMain` 是 `#101012`，黑影落在黑底上）。
 * 真正撑起「这是一张卡片」的是那 1px 描边，阴影只是浅色主题里的一点润饰。
 * 写在这里是为了挡住下一次「夜间卡片看不出来，把高度调大点」—— 调大也还是看不见。
 */
object Elevation {

    val card: Dp = 2.dp
    val none: Dp = 0.dp
}

/**
 * 字号。按**角色**命名而不是按大小，取值就是改造前散在各处的那些 `sp`。
 *
 * 权重单独说一句：Web 版用的 `font-semibold` 是 600，而 Roboto 只有 500 和 700，
 * 600 会走字体匹配的兜底路径、结果不确定。所以控件标签用 [labelWeight]（Medium），
 * 标题用 [titleWeight]（Bold），不写 600。
 *
 * [labelTracking] / [buttonTracking] 是 Web 版那两条 `tracking-[0.16em]` 与
 * `tracking-[0.08em]` —— 本端原先完全没有字距，而它是「看起来更完整」的一部分。
 */
object TypeScale {

    /** 小标题（Web 的 `SECTION_LABEL`）、徽标、脚注。 */
    val label: TextUnit = 11.sp

    /** 次级正文、按钮文字。 */
    val body: TextUnit = 12.sp

    /** 主正文、输入框。 */
    val bodyLg: TextUnit = 13.sp

    /** 列表标题、释义正文。 */
    val bodyStrong: TextUnit = 14.sp

    /** 章标题。 */
    val title: TextUnit = 15.sp

    /** 面板标题。 */
    val panel: TextUnit = 18.sp

    /** 释义面板里的词头，全应用最大的一处。 */
    val headword: TextUnit = 28.sp

    val labelWeight: FontWeight = FontWeight.Medium
    val titleWeight: FontWeight = FontWeight.Bold

    val labelTracking: TextUnit = 0.16.em
    val buttonTracking: TextUnit = 0.08.em
}

/**
 * 动效。时长与曲线照抄 Web 版 `index.css` 的 `@theme` 块，两端的开合手感才是同一个。
 *
 * 曲线 `cubic-bezier(0.16, 1, 0.3, 1)` 是「快出慢收」—— 面板滑入用它，
 * `overlay-in` / `toast-in` 用的是普通的 ease-out，所以两个都在这里。
 */
object Motion {

    /** 遮罩淡入。对应 CSS `overlay-in` 140ms。 */
    const val overlayMillis: Int = 140

    /** 面板进出。对应 CSS `panel-in` 170ms。 */
    const val panelMillis: Int = 170

    /** 侧边抽屉。对应 CSS `drawer-right` 220ms。 */
    const val drawerMillis: Int = 220

    /** 底部面板。对应 CSS `drawer-bottom` 240ms。 */
    const val bottomSheetMillis: Int = 240

    /** 提示条。对应 CSS `toast-in` 160ms。 */
    const val toastMillis: Int = 160

    val curve: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    val easeOut: Easing = EaseOut
}
