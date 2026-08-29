import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/** CI 注入构建号，本地构建默认 1。 */
val buildNumber: Int = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

/** 仅当 CI 解码出 keystore 时才配置签名，否则 release 产出未签名包。 */
val releaseKeystore: File? = System.getenv("KEYSTORE_FILE")
    ?.let { File(it) }
    ?.takeIf { it.isFile }

android {
    namespace = "com.xfqiu.floatball"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.xfqiu.floatball"
        minSdk = 24
        targetSdk = 30
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("STORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // 零第三方依赖、代码量极小，混淆收益低于 Service/View 被误裁剪的风险
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = false
    }

    lint {
        // 本应用只给 Android 11 墨水屏电纸书侧载，target 30 是兼容性设计而非上架配置。
        // 升到 33+ 会引入通知权限与后台前台服务行为变化，不能为 Play 检查盲目升级。
        disable += "ExpiredTargetSdkVersion"
    }
}

dependencies {
    // 发布 APK 有意保持零第三方运行时依赖：墨水屏设备 CPU 弱、存储小，
    // AndroidX / Compose 带来的体积与动画开销都不划算。
    // JUnit 仅在 CI 的宿主 JVM 中运行，不会打进 APK。
    testImplementation("junit:junit:4.13.2")
}
