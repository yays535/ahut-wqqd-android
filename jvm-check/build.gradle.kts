// 纯 JVM 联调模块：直接编译 app 里那份 AhutEngine.kt（它不依赖任何 Android API），
// 打真实服务器验证六步链路。这样 Android 端没有真机也能先自证引擎正确。
//
// 用法：ID=学号 PWD=密码 gradle :jvm-check:run
plugins {
    kotlin("jvm")
    application
}

kotlin {
    jvmToolchain(17)
}

sourceSets {
    main {
        kotlin.srcDir("../app/src/main/java/cn/ahutwqqd/app/engine")
    }
}

dependencies {
    implementation(project(":sign-core"))
    implementation("org.json:json:20240303")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
}

application {
    mainClass.set("cn.ahutwqqd.app.engine.LiveCheckKt")
}

tasks.named<JavaExec>("run") {
    // 让 run 任务继承父进程环境变量，凭据不落盘
    environment(System.getenv())
}
