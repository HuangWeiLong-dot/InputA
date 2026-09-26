plugins {
    alias(libs.plugins.android.application)
    // 没有 kotlin-android：AGP 9 内置 Kotlin，见 gradle/libs.versions.toml 的注释。
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.inputa.reader"
    // 37 不是拍脑袋选的：core-ktx 1.19.x / okhttp-android 5.5 / Compose 1.12 都要求
    // 编译目标 >= 37，低于它 AGP 直接拒绝构建。本机装的是 platforms/android-37.2。
    compileSdk = 37

    defaultConfig {
        applicationId = "com.inputa.reader"
        // minSdk 26：Web 版没有任何原生依赖，26 能覆盖绝大多数在用设备，
        // 而且 java.time 这类不用再 desugar。
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
        // 日志与调试开关用 BuildConfig.DEBUG 包着，AGP 8 起它默认不生成，要显式打开。
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
        )
    }

    // Room 导出的 schema JSON 提交进仓库，将来写迁移时有据可依（MigrationTestHelper 也读它）。
    sourceSets["androidTest"].assets.directories.add("$projectDir/schemas")

    testOptions {
        unitTests {
            // Robolectric 读 assets 的前提。不开的话 `context.assets.open(...)` 直接抛
            // FileNotFoundException —— 而内置样书就是从一个 asset 读的。
            isIncludeAndroidResources = true
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
