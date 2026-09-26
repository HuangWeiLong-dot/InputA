plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * 纯 Kotlin/JVM 模块 —— 这里刻意不出现 android/androidx 依赖。
 *
 * Web 版那 10 个 vitest 套件测的是算法与数据层规则，没有一行组件渲染。把那些算法搬到
 * 这个模块里，`./gradlew :domain:test` 就是纯 JVM 测试：秒级、不需要模拟器、不需要
 * Robolectric。这条边界是本模块存在的唯一理由，加依赖前先问一句「这真的属于领域层吗」。
 */
kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // 只用于 JSON 解析（备份文件、词典上游响应）。不引入任何网络或持久化库 ——
    // 那些是 :app 的事，领域层只描述形状与规则。
    implementation(libs.kotlinx.serialization.json)

    // 仓储接口返回 Flow。用 api 而不是 implementation：:app 实现这些接口时也要
    // 看得见 Flow 类型，否则它的实现类无法编译。
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
