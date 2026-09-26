pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "InputA"

// :domain 是纯 Kotlin/JVM 模块，刻意不依赖 android/androidx —— 模块边界本身就是
// 「Web 版那批算法能在纯 JVM 上单测」的保证（见 android/README 与计划文件）。
include(":app")
include(":domain")
