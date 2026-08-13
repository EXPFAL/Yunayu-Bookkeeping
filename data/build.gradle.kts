// :data 数据层：Room（KSP）+ DataStore + Hilt，实现 :domain 仓储接口
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    id("jacoco")
}

android {
    namespace = "com.expfal.yunayu.data"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // 本地单元测试启用 JUnit5（JUnit Jupiter）；isReturnDefaultValues 使 android.util.Log
    // 等 Android stub 方法返回默认值而非抛「not mocked」，供映射层告警路径单测使用
    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
        unitTests.isReturnDefaultValues = true
    }

    sourceSets {
        // MigrationTestHelper 需要将 Room 导出 schema 作为 androidTest assets 暴露
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

// Room 开启 exportSchema，schema 输出至 data/schemas 供 migration 追溯
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

// JVM 单测覆盖率报告（依赖 testDebugUnitTest，报告可用即可，不设硬性门禁）
val testCoverage by tasks.registering(JacocoReport::class) {
    dependsOn("testDebugUnitTest")
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    executionData.setFrom(
        layout.buildDirectory.dir("jacoco").map { dir ->
            fileTree(dir) { include("*.exec") }
        },
    )
    sourceDirectories.setFrom(files("src/main/kotlin", "src/main/java"))
    classDirectories.setFrom(
        layout.buildDirectory.dir("tmp/kotlin-classes/debug").map { dir -> fileTree(dir) },
    )
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.room.testing)
}
