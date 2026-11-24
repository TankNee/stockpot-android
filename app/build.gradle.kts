import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// 签名配置放在 keystore.properties，仓库里带了一份默认的：
// 同一个 key 才能覆盖安装，别删 keystore/ 目录。
val keystoreFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystoreFile.exists()) keystoreFile.inputStream().use { load(it) }
}
val hasKeystore = keystoreFile.exists() && keystoreProps.getProperty("storeFile") != null

// 同步服务器地址不写死在代码里：local.properties（已被 git 忽略）或环境变量都能改，
// 换自己的服务器不用动源码。
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun configValue(env: String, fallback: String): String {
    System.getenv(env)?.takeIf { it.isNotBlank() }?.let { return it.trim() }
    providers.gradleProperty(env).orNull?.takeIf { it.isNotBlank() }?.let { return it.trim() }
    localProps.getProperty(env)?.takeIf { it.isNotBlank() }?.let { return it.trim() }
    return fallback
}

val defaultServer = configValue("STOCKPOT_SERVER", "https://api.stockpot.neeto.cn")

android {
    namespace = "cn.neeto.stockpot"
    compileSdk = 35

    defaultConfig {
        applicationId = "cn.neeto.stockpot"
        minSdk = 26
        targetSdk = 35
        versionCode = 39
        versionName = "0.17.1"
        vectorDrawables.useSupportLibrary = true
        buildConfigField("String", "DEFAULT_SERVER", "\"$defaultServer\"")
    }

    signingConfigs {
        create("release") {
            if (hasKeystore) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // 调试版才放行明文 http：本机/局域网起一个同步服务方便对着调
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            signingConfig = if (hasKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
            "/META-INF/LICENSE*",
            "/META-INF/NOTICE*",
        )
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // 同步用：行情那边用的是 JDK 的 HttpURLConnection，账号和同步走 OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    // 同步协议的测试：本机起一个假的 HTTP 服务，把请求方法/报文都对着发一遍
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
