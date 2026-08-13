// :domain 纯 Kotlin 模块：不依赖 Android 框架，依赖方向只能向内
plugins {
    alias(libs.plugins.kotlin.jvm)
    id("jacoco")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

tasks.test {
    useJUnitPlatform()
}

dependencies {
    // 实现依赖仅允许 Kotlin stdlib + Coroutines（见 SCAFFOLD.md 1.2）
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
}
