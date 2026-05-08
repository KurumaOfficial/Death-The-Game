// =============================================================================
// :desktop — LWJGL3 launcher для Windows / Linux / macOS.
//
// Что здесь:
//   - DesktopLauncher main()
//   - GLFW окно
//   - Vulkan instance bootstrap (smoke-тест на стадии 1)
//   - Реализации PlatformAdapter / PlatformFileSystem для desktop
//
// Stage 1: реальный render-pass и swapchain — INTEGRATION_MISSING
// (см. PROGRESS.md строка RenderBackend).
// =============================================================================

import org.gradle.internal.os.OperatingSystem

val lwjglNativesClassifier: String = when {
    OperatingSystem.current().isWindows -> "natives-windows"
    OperatingSystem.current().isMacOsX  -> if (System.getProperty("os.arch")
            ?.lowercase()?.contains("aarch64") == true) "natives-macos-arm64" else "natives-macos"
    else                                -> "natives-linux"
}

dependencies {
    api(project(":core"))
    api(project(":rust-bridge"))

    api(libs.bundles.lwjgl.jvm)

    // Нативные библиотеки для LWJGL — нужный classifier для текущей ОС.
    runtimeOnly(variantOf(libs.lwjgl.natives)        { classifier(lwjglNativesClassifier) })
    runtimeOnly(variantOf(libs.lwjgl.glfw.natives)   { classifier(lwjglNativesClassifier) })
    runtimeOnly(variantOf(libs.lwjgl.openal.natives) { classifier(lwjglNativesClassifier) })
    runtimeOnly(variantOf(libs.lwjgl.assimp.natives) { classifier(lwjglNativesClassifier) })
    runtimeOnly(variantOf(libs.lwjgl.stb.natives)    { classifier(lwjglNativesClassifier) })
    runtimeOnly(variantOf(libs.lwjgl.shaderc.natives){ classifier(lwjglNativesClassifier) })

    // Vulkan natives нужны только для macOS (MoltenVK). Linux/Windows используют
    // системный Vulkan loader.
    if (OperatingSystem.current().isMacOsX) {
        runtimeOnly(variantOf(libs.lwjgl.vulkan.natives) { classifier(lwjglNativesClassifier) })
    }

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Application plugin — для удобного запуска `./gradlew :desktop:run`.
plugins {
    application
}

application {
    mainClass.set("WeTTeA.platform.desktop.DesktopLauncher")
    applicationName = "Death"
}

// macOS требует -XstartOnFirstThread для GLFW.
tasks.named<JavaExec>("run") {
    if (OperatingSystem.current().isMacOsX) {
        jvmArgs("-XstartOnFirstThread")
    }
}
