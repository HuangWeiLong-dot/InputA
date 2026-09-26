package com.inputa.reader.domain.json

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 安全读取 JSON 的小工具。
 *
 * 存在的理由：Web 版的 `normalize*` 系列函数一律「丢弃脏数据、保留能救的」，
 * 判据是 JS 的 `typeof`。kotlinx.serialization 的 `jsonPrimitive` / `jsonObject`
 * 访问器在类型不符时会**抛异常**，而导入一份旧备份或手改过的 JSON 不能抛异常。
 * 所以这里全部用安全转换，返回 null 由调用方当作「这个字段没有」。
 */

internal fun JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject

internal fun JsonElement?.asArrayOrNull(): JsonArray? = this as? JsonArray

/** 等价于 JS 的 `typeof value === 'string'`。数字、布尔、null、对象、数组都返回 null。 */
internal fun JsonElement?.stringOrNull(): String? {
    val primitive = this as? JsonPrimitive ?: return null
    if (primitive is JsonNull || !primitive.isString) return null
    return primitive.content
}

/**
 * 等价于 JS 的 `Number.isInteger(value)` 且模板是数字：`3.5` 与 `"3"` 都返回 null。
 *
 * **不是数字字面量就一律拒绝**：字符串 "3" 在 JS 那边过不了 `typeof value === 'number'`，
 * 这里同样不该过，否则两端对同一份备份的处理会不一致。
 */
internal fun JsonElement?.intOrNull(): Int? {
    val primitive = this as? JsonPrimitive ?: return null
    // isString 为真就不是数字字面量（kotlinx 没有 isNumber，用它的否定）。
    // JsonNull 单独挡掉：它同样 isString == false，但 content 是 "null"。
    if (primitive is JsonNull || primitive.isString) return null
    // content 对 3.5 是 "3.5"，toIntOrNull 返回 null —— 正是我们要的。
    // 对 true/false 同理返回 null。
    return primitive.content.toIntOrNull()
}

/** 同 [intOrNull]，但用于时间戳这类可能超出 Int 范围的值。 */
internal fun JsonElement?.longOrNull(): Long? {
    val primitive = this as? JsonPrimitive ?: return null
    if (primitive is JsonNull || primitive.isString) return null
    return primitive.content.toLongOrNull()
}

/**
 * 等价于 JS 的 `value === true`。
 *
 * 刻意**只认真正的布尔字面量**：ECDICT 的 `oxford` 列是 0/1 的整数，而 Web 版
 * 那边写的是 `raw.oxford === true`，于是 `1` 不算「牛津核心词」。这是已发布行为，
 * 不要「顺手」放宽成接受数字 —— 那会让一批词突然多出一个标记。
 */
internal fun JsonElement?.booleanOrNull(): Boolean? {
    val primitive = this as? JsonPrimitive ?: return null
    if (primitive is JsonNull || primitive.isString) return null
    return primitive.content.toBooleanStrictOrNull()
}
