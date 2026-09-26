import java.util.Properties

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

        // 留空＝用 :domain 的 DEFAULT_SERVER_BASE_URL（线上地址），只有 debug 构建在
        // 下面把它覆盖成模拟器地址。线上值刻意不在这里再写一份：Gradle 读不到 Kotlin 的
        // `const`，抄一遍就是第二个会悄悄漂移的副本。
        buildConfigField("String", "DEBUG_SERVER_BASE_URL", "\"\"")
    }

    /**
     * release 签名凭证从 `android/keystore.properties` 读，那个文件和 keystore 本身都不进
     * 仓库（见 .gitignore）。
     *
     * 文件不存在时**不**让构建失败：别人克隆下来仍要能跑 assembleDebug / assembleRelease，
     * 只是 release 会是未签名的 APK（装不上），并且打一条明确的警告 —— 这比拿到一个装不上
     * 的包却不知为什么好。
     */
    val keystorePropsFile = rootProject.file("keystore.properties")
    val keystoreProps = Properties().apply {
        if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // 模拟器里 10.0.2.2 指向宿主机 —— 开发时后端就跑在自己机器上（npm run server）。
            buildConfigField("String", "DEBUG_SERVER_BASE_URL", "\"http://10.0.2.2:8787/\"")
        }

        release {
            val releaseSigning = signingConfigs.findByName("release")
            if (releaseSigning != null) {
                signingConfig = releaseSigning
            } else {
                logger.warn(
                    "android/keystore.properties 不存在：release 包将是未签名的，装不上。" +
                        " 生成方式见 android/.gitignore 里的说明。",
                )
            }

            // 刻意不开 R8/minify：Room、Retrofit、Hilt、kotlinx.serialization 各自都需要
            // keep 规则，没写规则就开混淆，故障会以「装完一打开就崩」的形式出现，而且只在
            // release 上复现。真要开，先把规则写全再单独验证一遍。
            isMinifyEnabled = false
        }
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
