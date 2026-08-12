// :domain 纯 Kotlin 模块：不依赖 Android 框架，依赖方向只能向内
plugins {
    alias(libs.plugins.kotlin.jvm)
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

dependencies {
    // 仅允许 Kotlin stdlib + Coroutines（见 SCAFFOLD.md 1.2）
    implementation(libs.kotlinx.coroutines.core)
}
