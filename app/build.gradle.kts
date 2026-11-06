import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.trickcal.crayon.master"
    compileSdk = 36

    // 正式包签名材料不入库：keystore.properties 与 keystore/ 都在 .gitignore 内。
    // 别人克隆后没有这两样，release 会退化成未签名包，但 debug 构建不受影响。
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

    defaultConfig {
        applicationId = "com.trickcal.crayon.master"
        minSdk = 26
        targetSdk = 36
        versionCode = 202
        versionName = "0.2-beta"
        vectorDrawables { useSupportLibrary = true }
        ndk {
            // 只保留真机主力架构 arm64-v8a 与 64 位模拟器架构 x86_64。
            // 去掉 x86 与 armeabi-v7a 可直接砍掉约 71MB 原生库体积。
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 没有 keystore.properties 时为 null，产物是未签名包 —— 明确可见，不静默降级。
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // AGP 8 默认关闭 BuildConfig 生成；AppViewModel 要从 BuildConfig 读版本号，
        // 不开这一项会直接编译不过。
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // 识图导入：离线中文 OCR
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    // OpenCV：自适应二值化 + 轮廓层级 + 模板匹配，是本项目灰卡识别的正解
    implementation("org.opencv:opencv:4.9.0")

    implementation("androidx.compose.ui:ui:1.7.6")
    implementation("androidx.compose.ui:ui-graphics:1.7.6")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.6")
    implementation("androidx.compose.foundation:foundation:1.7.6")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.material:material-icons-core:1.7.6")

    testImplementation("junit:junit:4.13.2")

    debugImplementation("androidx.compose.ui:ui-tooling:1.7.6")
}
