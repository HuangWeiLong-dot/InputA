package com.inputa.reader.domain.bionic

import com.inputa.reader.domain.text.Tokenizer
import kotlin.math.ceil

/**
 * 仿生阅读：把一个词的前几个字符加粗，给眼睛一个锚点。
 *
 * 算法取自 LingKuma 的 `createHighlightData`（其 src/plugin/bionic.js，MIT，见 NOTICE）：
 * 取词长的 40%、**向上取整**作为加粗位数 ——
 *
 *   长度 1 → 1   长度 3 → 2   长度 5 → 2   长度 6 → 3
 *
 * 它没有任何按词长分档、短词例外或元音边界的修饰，这里也不加：换一套规则会让
 * 效果和参考实现不一样，而对不对本来就是个偏好问题。
 *
 * 一处与 LingKuma 的分歧（Web 版已经做过，这里沿用）：它用绝对定位的渐变覆盖层
 * **模拟**加粗，这里渲染真正的粗体字形。
 */
object Bionic {

    /**
     * 加粗位数：词长的 40%，向上取整。
     *
     * 必须走浮点：`word.length * 4 / 10` 是整数除法，`"the"` 会得 1，而参考实现是 2。
     * 另外 `String.length` 在 JS 与 Kotlin 上都是 UTF-16 码元数，所以 `café` 两边都算
     * 4 —— 不要「顺手改进」成码点数，那会让两端结果不一致。
     */
    fun boldLength(word: String): Int = ceil(word.length * 0.4).toInt()

    /**
     * 把词切成「加粗的前段」与「其余」。
     *
     * 中日韩文字整段跳过（bold 为空）：它们的「词首」没有意义，而且仿生阅读的
     * 效果本来就来自拉丁字母的形状。
     */
    fun split(word: String): BionicParts {
        if (word.isEmpty() || Tokenizer.containsCjk(word)) return BionicParts("", word)
        val boldLength = boldLength(word)
        return BionicParts(
            bold = word.substring(0, boldLength.coerceAtMost(word.length)),
            rest = word.substring(boldLength.coerceAtMost(word.length)),
        )
    }

    /** 这个词该不该套仿生阅读。含中日韩就整段跳过。 */
    fun isEligible(word: String): Boolean = word.isNotEmpty() && !Tokenizer.containsCjk(word)
}

data class BionicParts(
    /** 加粗的词首。 */
    val bold: String,
    /** 其余部分。 */
    val rest: String,
)
