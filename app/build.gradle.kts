import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    jacoco
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
        versionCode = 61
        versionName = "0.19.17"
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
    // 同步并发行为的测试：虚拟时间 + 可控取消
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    // 同步协议的测试：本机起一个假的 HTTP 服务，把请求方法/报文都对着发一遍
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

// 单测和压测都在 JVM 上跑。50k 条账本的 JSON 编解码本身就要几十 MB 内存，
// 堆给小了 GC 会把计时抖得没法看。
tasks.withType<Test>().configureEach {
    maxHeapSize = "2g"
    // 压测报告直接落到 build/reports/perf，CI 读它贴进 job summary
    systemProperty("perf.report.dir", layout.buildDirectory.dir("reports/perf").get().asFile.absolutePath)
    // perfTest 不在 jacoco 的覆盖范围里，拿不到扩展就不动它
    extensions.findByType(org.gradle.testing.jacoco.plugins.JacocoTaskExtension::class.java)?.apply {
        // JaCoCo 的 agent 认不出 JDK 内部的一些类，不排除会刷警告
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
    testLogging {
        events("failed")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    // 压测单独一条任务：单测不该被性能数字绑架——压测超标不能让覆盖率算不出来，
    // 反过来改一行 UI 也不该等 45 个场景跑完。
    if (name == "perfTest") {
        filter { includeTestsMatching("cn.neeto.stockpot.perf.*") }
        maxParallelForks = 1
    } else {
        filter { excludeTestsMatching("cn.neeto.stockpot.perf.*") }
    }
}

// 压测任务复用单测的编译产物和 classpath，只是换一套过滤条件。
// 依赖直接从 testDebugUnitTest 上抄一份（去掉它自己）：AGP 改任务名的时候
// 这里不用跟着改，也不会因为「多跑一遍单测」白等两分钟。
afterEvaluate {
    val unitTest = tasks.named<Test>("testDebugUnitTest")
    tasks.register<Test>("perfTest") {
        group = "verification"
        description = "账单量级压测：1k / 20k / 50k 条下的筛选、汇总、合并与同步编解码"
        dependsOn(unitTest.get().taskDependencies.getDependencies(null) - unitTest.get())
        testClassesDirs = unitTest.get().testClassesDirs
        classpath = unitTest.get().classpath
    }
}

jacoco {
    toolVersion = "0.8.12"
}

// 覆盖率报告：只统计 JVM 单测能覆盖到的部分。Compose 界面、Activity 这些
// 得跑仪器测试才有数字，混进来只会让百分比失真，索性排除掉。
//
// HTML 默认不生成：656 个类要摊成几千个小文件，Windows 上被杀软一个个扫过去
// 能卡上十分钟。CI 里加 -PcoverageHtml=true 才出网页版，本地只留 XML（快得多）。
tasks.register<org.gradle.testing.jacoco.tasks.JacocoReport>("coverageReport") {
    dependsOn("testDebugUnitTest")
    group = "verification"
    description = "单元测试覆盖率（JVM）"
    reports {
        xml.required.set(true)
        html.required.set(providers.gradleProperty("coverageHtml").isPresent)
    }
    sourceDirectories.setFrom(files("src/main/java"))
    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) {
            exclude(
                "**/R.class",
                "**/R$*.class",
                "**/BuildConfig.*",
                "**/Manifest*.*",
                "**/*Test*.*",
                // Compose 编译器生成的壳子，没有可执行的真代码
                "**/ComposableSingletons*.*",
                "**/*\$Composable*.*",
                "**/ui/theme/**",
            )
        },
    )
    executionData.setFrom(fileTree(layout.buildDirectory) { include("**/*.exec") })
}
