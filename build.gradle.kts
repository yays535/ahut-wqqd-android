plugins {
    kotlin("jvm") version "2.4.0" apply false
    id("org.jetbrains.kotlin.android") version "2.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0" apply false
    id("com.android.application") version "8.13.1" apply false
}

// services.gradle.org 现在 302 到 GitHub，wrapper 任务的 URL 校验会误报失败。
// 本机已有 9.3.1 分发包缓存，关掉校验即可，不影响 ./gradlew 使用缓存。
tasks.wrapper {
    validateDistributionUrl = false
}
