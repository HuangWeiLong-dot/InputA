package com.inputa.reader.domain.vocab

import com.inputa.reader.domain.model.WordStatus

/**
 * 词汇规则的**可纯函数化的那部分**。
 *
 * 规则本身写在 [WordLevels] 的类注释里。这里只放不依赖存储的三条判定，好让它们
 * 在纯 JVM 上就能测 —— 剩下需要「读当前词库」的部分（是否已收录）由仓储在
 * 事务里完成，测试在 :app 的内存 Room 上跑。
 *
 * 把判定拆到这里不是为了好看：这些条件每一条都对应一个会静默毁数据的场景，
 * 而它们原本埋在 Web 版的 store 方法里，只能靠跑整个 store 才能验。
 */
object VocabularyRules {

    /** 点击一个词时写入的状态：5 级「生词」，最深的一档。 */
    val CLICK_STATUS: WordStatus = WordStatus.L5

    /** 翻页时给未点击的词写入的状态。 */
    val PAGE_TURN_STATUS: WordStatus = WordStatus.MASTERED

    /**
     * 翻页时该「考虑收录」哪些词：规范化键、去重、丢掉单字母。
     *
     * 单字母是噪声（冠词 a、代词 I），不是词汇 —— 与 Web 版
     * `markPageWordsAsMastered` 里那句 `clean.length < 2` 是同一条规则。
     *
     * 这里**不**判断词是否已收录：那需要读存储，由仓储在事务里做。返回的顺序保持
     * 首次出现的顺序，好让「本页收录了哪几个词」在日志与撤销里是可读的。
     */
    fun pageTurnCandidates(pageWords: List<String>): List<String> {
        val seen = LinkedHashSet<String>()
        for (raw in pageWords) {
            val clean = WordLevels.normalizeKey(raw)
            if (clean.length < 2) continue
            seen += clean
        }
        return seen.toList()
    }
}
