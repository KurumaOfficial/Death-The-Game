// =============================================================================
// Death — settings.gradle.kts
//
// Multi-module Gradle проект.
// Внутренний namespace кода: WeTTeA.
// User-facing название: Death.
// =============================================================================

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
        // Sonatype для snapshot релизов LWJGL, если потребуются
        maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots/") }
    }
}

rootProject.name = "Death"

// -----------------------------------------------------------------------------
// Базовые модули — включены всегда.
//
// :core         — общая игровая логика, не зависящая от платформы
// :desktop      — LWJGL3 launcher, desktop-специфика (Windows/Linux/macOS)
// :rust-bridge  — JNI Java-сторона, загрузка нативной Rust-библиотеки
// -----------------------------------------------------------------------------
include(":core")
include(":desktop")
include(":rust-bridge")

// -----------------------------------------------------------------------------
// Условные модули — мобильные платформы.
// Включаются только при наличии toolchain'а:
//   :android — требует ANDROID_HOME и Android SDK; включается при
//              -PenableAndroid=true ИЛИ при наличии ANDROID_HOME в env.
//   :ios     — требует macOS + Xcode + RoboVM/MobiVM; включается при
//              -PenableIOS=true.
//
// Источники модулей физически присутствуют в репозитории и подлежат
// поэтапной интеграции — статус в PROGRESS.md.
// -----------------------------------------------------------------------------

val enableAndroidProperty: String? = providers.gradleProperty("enableAndroid").orNull
val enableIOSProperty: String?     = providers.gradleProperty("enableIOS").orNull

val androidEnabled: Boolean =
    enableAndroidProperty.toBoolean() || System.getenv("ANDROID_HOME") != null

val iosEnabled: Boolean =
    enableIOSProperty.toBoolean()

if (androidEnabled) {
    include(":android")
    println("[Death:settings] :android module ENABLED " +
            "(enableAndroid=$enableAndroidProperty, ANDROID_HOME=${System.getenv("ANDROID_HOME") ?: "<not set>"})")
} else {
    println("[Death:settings] :android module skipped — ANDROID_HOME отсутствует и enableAndroid != true")
}

if (iosEnabled) {
    include(":ios")
    println("[Death:settings] :ios module ENABLED (enableIOS=$enableIOSProperty)")
} else {
    println("[Death:settings] :ios module skipped — enableIOS != true")
}
