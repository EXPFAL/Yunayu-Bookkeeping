// 根构建脚本：所有插件版本经版本目录集中管理，各模块按需 apply
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ktlint) apply false
}

// 对所有模块统一落地 ktlint 门禁（SCAFFOLD §1.4 验收项 5），并显式锁定 ktlint 0.50.0
val ktlintVersion: String = libs.versions.ktlint.get()

// Kotlin 2.3 元数据（mv=2.3.0）兼容：Dagger/Hilt 2.57.2 的编译器声明旧版
// kotlin-metadata-jvm（2.1.21），而 Room 2.8.4 传递引入 2.2.x，二者都无法读取
// Kotlin 2.3.21 产出的 2.3.0 元数据（报“maximum supported version is 2.2.0”）。
// Dagger 2.57 起已 unshade 该库，因此统一强制为与 Kotlin 同版本即可解决。
// 新增注解处理器 / KSP 依赖时须复核此 force（新处理器可能依赖不同版本的 kotlin-metadata-jvm）。
val kotlinMetadataVersion: String = libs.versions.kotlin.get()

allprojects {
    configurations.configureEach {
        resolutionStrategy {
            force("org.jetbrains.kotlin:kotlin-metadata-jvm:$kotlinMetadataVersion")
        }
    }
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set(ktlintVersion)
    }
}
