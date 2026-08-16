import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 签名信息从本地 keystore.properties 读取（该文件已被 .gitignore 排除，不会进入代码仓库）
// 模板见 keystore.properties.example；未配置时 release 构建会因空密码失败，详见 README「构建」
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "org.dalanben.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.dalanben.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 28000115
        versionName = "28.0.15"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(keystoreProps.getProperty("storeFile", "dalanben.keystore"))
            storePassword = keystoreProps.getProperty("storePassword", "")
            keyAlias = keystoreProps.getProperty("keyAlias", "")
            keyPassword = keystoreProps.getProperty("keyPassword", "")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        // Media3 Transformer / Effect 等 API 标注了 @UnstableApi (@RequiresOptIn)
        freeCompilerArgs += listOf("-Xopt-in=androidx.media3.common.util.UnstableApi")
    }

    lint {
        checkReleaseBuilds = false
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// 构建时把仓库源码同步进 assets/opensource，供 App 内置「开源代码」浏览（完全本地化，无需联网）
// 排除了构建产物、签名、本地配置与二进制资源；产物目录 app/src/main/assets/opensource 已被 .gitignore 排除
tasks.register<Copy>("syncOpenSourceAssets") {
    from(rootProject.file(".")) {
        include(
            "**/*.kt", "**/*.kts", "**/*.md", "**/*.xml", "**/*.properties",
            "**/*.pro", "**/gradlew", "**/gradlew.bat", "LICENSE"
        )
        exclude(
            ".git", ".gradle", ".kotlin", ".idea", "build", "app/build",
            "**/*.keystore", "**/*.jks", "**/local.properties", "**/keystore.properties",
            "**/*.apk", "**/*.aab", "**/*.jar", "**/*.png", "**/*.jpg", "**/*.jpeg", "**/*.webp",
            "**/*.log", "app/src/main/assets/opensource"
        )
    }
    into(layout.projectDirectory.dir("src/main/assets/opensource"))
    includeEmptyDirs = false
}
tasks.named("preBuild").configure { dependsOn("syncOpenSourceAssets") }

dependencies {
    // Core AndroidX
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // SplashScreen
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Images (Coil)
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0") // 支持 GIF 动画(图形验证码为动态星空云)

    // Video (Media3 ExoPlayer)
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    // Video transcoding (720p / 25fps / 码率压缩)
    implementation("androidx.media3:media3-transformer:1.4.1")
    implementation("androidx.media3:media3-effect:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")

    // Permissions
    implementation("com.google.accompanist:accompanist-permissions:0.36.0")

    // QR Code
    implementation("com.google.zxing:core:3.5.3")

    // CameraX + ML Kit (QR scanning)
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.mlkit:barcode-scanning:17.2.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // DataStore (preferences)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
