package com.inputa.reader.domain.dict

import com.inputa.reader.domain.model.DefinitionItem
import com.inputa.reader.domain.model.DictionaryEntry
import com.inputa.reader.domain.model.DictionaryExtra
import com.inputa.reader.domain.model.DictionarySource
import com.inputa.reader.domain.model.Meaning
import com.inputa.reader.domain.model.PartOfSpeechShare
import com.inputa.reader.domain.model.WordForm
import com.inputa.reader.domain.model.WordFrequency
import com.inputa.reader.domain.model.WordStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 释义面板的展示逻辑。
 *
 * 这批断言是 Web 版**没有**的：那部分逻辑长在 `DefinitionDrawer.tsx`（453 行）里，
 * 而 Web 的测试跑在无 DOM 的 node 环境、零组件渲染覆盖。搬到领域层是这次移植
 * 少数几处「覆盖度净增」之一 —— 下面每条规则原本都只能靠肉眼看界面验证。
 */
class DefinitionPresenterTest {

    private fun entry(
        word: String = "very",
        extra: DictionaryExtra? = null,
        meanings: List<Meaning> = listOf(
            Meaning("adverb", listOf(DefinitionItem("To a high degree."))),
        ),
    ) = DictionaryEntry(word = word, phonetic = "'veri", meanings = meanings, extra = extra)

    private fun present(
        entry: DictionaryEntry,
        status: WordStatus? = null,
        clickedWord: String = entry.word,
    ) = DefinitionPresenter.present(entry, DictionarySource.LOCAL_ECDICT, status, clickedWord)

    // ---------------------------------------------------------------- 中文释义

    @Test
    fun `translation is split into trimmed, non-empty lines`() {
        val presentation = present(
            entry(extra = DictionaryExtra(translation = "  adv. 非常  \n\n  a. 真正的  \n")),
        )

        assertEquals(listOf("adv. 非常", "a. 真正的"), presentation.translationLines)
        assertTrue(presentation.hasTranslation)
    }

    @Test
    fun `an entry without a translation reports none`() {
        val presentation = present(entry(extra = DictionaryExtra(translation = "   ")))
        assertEquals(emptyList<String>(), presentation.translationLines)
        assertTrue(!presentation.hasTranslation)
    }

    // ---------------------------------------------------------------- 考试标签与词频

    @Test
    fun `exam tags pass through in their source order`() {
        val presentation = present(entry(extra = DictionaryExtra(examTags = listOf("中考", "高考"))))
        assertEquals(listOf("中考", "高考"), presentation.examTags)
    }

    /** 顺序固定：柯林斯 → 牛津 → BNC → COCA。读者每次看到的排列应当一致。 */
    @Test
    fun `meta bits are assembled in a fixed order`() {
        val presentation = present(
            entry(
                extra = DictionaryExtra(
                    frequency = WordFrequency(collins = 5, oxford = true, bnc = 80, frq = 105),
                ),
            ),
        )
        assertEquals(listOf("柯林斯 ★★★★★", "牛津核心词", "BNC #80", "COCA #105"), presentation.metaBits)
    }

    @Test
    fun `each frequency field is optional`() {
        assertEquals(
            listOf("BNC #80"),
            present(entry(extra = DictionaryExtra(frequency = WordFrequency(bnc = 80)))).metaBits,
        )
        assertEquals(emptyList<String>(), present(entry(extra = DictionaryExtra())).metaBits)
    }

    /** 柯林斯星级被夹在 1-5 —— 数据坏成 0 或 99 时不该画出 99 个星。 */
    @Test
    fun `collins stars are clamped`() {
        assertTrue(
            present(entry(extra = DictionaryExtra(frequency = WordFrequency(collins = 99))))
                .metaBits.first().count { it == '★' } == 5,
        )
    }

    // ---------------------------------------------------------------- 词性分布与词形

    @Test
    fun `the pos line joins shares and drops zero-percent entries`() {
        val presentation = present(
            entry(
                extra = DictionaryExtra(
                    partsOfSpeech = listOf(
                        PartOfSpeechShare("副词", "adv.", 92),
                        PartOfSpeechShare("形容词", "adj.", 8),
                        // percent 为 0 是 ECDICT 的解析残留，不是真的占比。
                        PartOfSpeechShare("名词", "n.", 0),
                    ),
                ),
            ),
        )
        assertEquals("副词 92% · 形容词 8%", presentation.posLine)
    }

    @Test
    fun `there is no pos line when every share is zero`() {
        assertNull(
            present(entry(extra = DictionaryExtra(partsOfSpeech = listOf(PartOfSpeechShare("名词", "n.", 0)))))
                .posLine,
        )
    }

    @Test
    fun `the form line joins labels with their words`() {
        val presentation = present(
            entry(
                extra = DictionaryExtra(
                    forms = listOf(
                        WordForm("过去式", listOf("ran")),
                        WordForm("复数", listOf("runnings")),
                    ),
                ),
            ),
        )
        assertEquals("过去式 ran / 复数 runnings", presentation.formLine)
    }

    /**
     * 有原形时隐藏词形变化：变形信息已经在「原形 run（现在分词）」那一行里了，
     * 再列一遍是重复。
     */
    @Test
    fun `the form line is hidden when a lemma is present`() {
        val presentation = present(
            entry(
                extra = DictionaryExtra(
                    lemma = "run",
                    inflection = "现在分词",
                    forms = listOf(WordForm("复数", listOf("runnings"))),
                ),
            ),
        )
        assertNull(presentation.formLine)
        assertEquals("run", presentation.lemma)
        assertEquals("现在分词", presentation.inflection)
    }

    @Test
    fun `the form line keeps at most four groups`() {
        val presentation = present(
            entry(extra = DictionaryExtra(forms = (1..6).map { WordForm("形式$it", listOf("w$it")) })),
        )
        assertEquals(4, presentation.formLine?.split(" / ")?.size)
    }

    // ---------------------------------------------------------------- 释义分组

    /** 每组最多四条 —— 再多会把面板撑爆，读者要看全部可以去查词典。 */
    @Test
    fun `each meaning keeps at most four definitions`() {
        val presentation = present(
            entry(
                meanings = listOf(
                    Meaning("noun", (1..7).map { DefinitionItem("definition $it") }),
                ),
            ),
        )
        assertEquals(4, presentation.meanings.first().definitions.size)
        assertEquals("definition 1", presentation.meanings.first().definitions.first().definition)
    }

    /**
     * 来源没给词性时**不显示徽章**。印一个字面的 "unknown" 什么也没告诉读者，
     * 而且看起来像个缺陷。
     */
    @Test
    fun `a meaning with no stated part of speech gets no badge`() {
        val presentation = present(
            entry(meanings = listOf(Meaning(UNKNOWN_PART_OF_SPEECH, listOf(DefinitionItem("text"))))),
        )
        assertNull(presentation.meanings.first().partOfSpeechBadge)
    }

    @Test
    fun `a stated part of speech becomes a badge`() {
        val presentation = present(entry(meanings = listOf(Meaning("adverb", listOf(DefinitionItem("x"))))))
        assertEquals("adverb", presentation.meanings.first().partOfSpeechBadge)
    }

    @Test
    fun `examples are carried through`() {
        val presentation = present(
            entry(meanings = listOf(Meaning("adverb", listOf(DefinitionItem("def", "an example"))))),
        )
        assertEquals("an example", presentation.meanings.first().definitions.first().example)
    }

    // ---------------------------------------------------------------- 词库词条

    /**
     * 只有点开的词与词库词条**不是同一个字符串**时才显示那一行。
     *
     * 典型情形是 ECDICT 的 `sw` 兜底命中：查 `a couchpotato` 得到 `a couch potato`。
     * 两者相同时显示它只是一行与标题重复的内容。
     */
    @Test
    fun `the headword is shown only when it differs from what was clicked`() {
        val couchPotato = entry(word = "a couch potato")

        assertEquals("a couch potato", present(couchPotato, clickedWord = "a couchpotato").headword)
        assertNull(present(couchPotato, clickedWord = "a couch potato").headword)
        // 大小写不同不算不同 —— 词库的键本来就是小写的。
        assertNull(present(couchPotato, clickedWord = "A Couch Potato").headword)
    }

    // ---------------------------------------------------------------- 状态标签

    @Test
    fun `the status label distinguishes uncollected from a level`() {
        assertEquals("未标记", DefinitionPresenter.statusLabel(null))
        assertEquals("已掌握", DefinitionPresenter.statusLabel(WordStatus.MASTERED))
        assertEquals("生词", DefinitionPresenter.statusLabel(WordStatus.L5))
        assertEquals("一般", DefinitionPresenter.statusLabel(WordStatus.L3))
    }

    /**
     * 未收录显示「未标记」而不是「生词」—— 虽然正文里它被涂成生词色，
     * 但词库里确实没有记录。面板是要说准话的地方。
     */
    @Test
    fun `an uncollected word is labelled unmarked, not as the newest level`() {
        val presentation = present(entry(), status = null)
        assertEquals("未标记", presentation.statusLabel)
        assertNull(presentation.status)
    }

    @Test
    fun `the source label is carried through`() {
        assertEquals("本地词库", present(entry()).sourceLabel)
        assertEquals(
            "Wiktionary",
            DefinitionPresenter.present(
                entry(),
                DictionarySource.WIKTIONARY,
                status = null,
                clickedWord = "very",
            ).sourceLabel,
        )
    }
}
