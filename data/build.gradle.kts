import java.util.Properties

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

        // 在线 NL 解析配置：读取根 local.properties（缺省/空白值回退默认值），key 绝不出现在提交文件
        val localProps = loadLocalProperties()
        val nlBaseUrl = localProps.getProperty("NL_API_BASE_URL")?.takeIf { it.isNotBlank() }
            ?: "https://api.deepseek.com"
        val nlModel = localProps.getProperty("NL_API_MODEL")?.takeIf { it.isNotBlank() }
            ?: "deepseek-chat"
        val nlApiKey = localProps.getProperty("NL_API_KEY")?.takeIf { it.isNotBlank() } ?: ""
        buildConfigField(
            "String",
            "NL_API_BASE_URL",
            nlBaseUrl.asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "NL_API_MODEL",
            nlModel.asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "NL_API_KEY",
            nlApiKey.asBuildConfigString(),
        )
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
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
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.room.testing)
}

/** 读取根目录 local.properties；文件不存在时返回空配置。 */
fun loadLocalProperties(): Properties {
    val props = Properties()
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.reader().use { props.load(it) }
    }
    return props
}

/** 转义反斜杠/引号/控制字符，产出可嵌入生成 BuildConfig（Java）源码的字符串字面量内容。 */
fun String.escapeStringLiteral(): String = buildString {
    for (ch in this@escapeStringLiteral) {
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(ch)
        }
    }
}

/** 包装为带双引号的合法字符串字面量。 */
fun String.asBuildConfigString(): String = "\"" + escapeStringLiteral() + "\""
