package com.inputa.reader.domain.util

/**
 * 时间的唯一来源。
 *
 * 存在的理由很具体：翻页收录会给每一行盖上同一个时间戳，而撤销要靠这个时间戳
 * 判断「读者有没有在撤销窗口内改过这个词」。测试里必须能控制时间才能验这条规则 ——
 * 直接调 `System.currentTimeMillis()` 的话，那个「改过就撤不掉」的分支只能靠
 * 运气撞上，或者靠 sleep 把测试拖慢。
 */
fun interface Clock {
    fun nowMillis(): Long
}

/** 生产用的实现。 */
object SystemClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
