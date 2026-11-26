// Framework-free: the logic, the UI host and the console client. No Compose here on purpose.
plugins {
    kotlin("jvm")
    application
}

kotlin { jvmToolchain(21) }

dependencies {
    api("com.genovich.components:action:0.2.0")
    api("com.genovich.components:uistate:0.2.0")
    api("com.genovich.components:while-active:0.2.0")
    api("com.genovich.components:parallel:0.2.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

application { mainClass.set("guessgame.cli.MainKt") }

tasks.named<JavaExec>("run") { standardInput = System.`in` }
