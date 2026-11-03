import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 签名信息放在 local.properties（已被 .gitignore 排除）。
// 没有配置时 release 仍可构建，只是产物未签名。
val keystoreProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "cn.ahutwqqd.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "cn.ahutwqqd.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 16
        versionName = "0.4.6"
    }

    signingConfigs {
        if (!keystoreProps.getProperty("ahut.storeFile").isNullOrBlank()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("ahut.storeFile"))
                storePassword = keystoreProps.getProperty("ahut.storePassword")
                keyAlias = keystoreProps.getProperty("ahut.keyAlias")
                keyPassword = keystoreProps.getProperty("ahut.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // 关于页直接读 BuildConfig.VERSION_NAME，避免像以前那样写了一行 0.4.0 忘了改
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":sign-core"))

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
