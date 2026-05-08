// =============================================================================
// :ios — iOS платформенный модуль Death.
//
// Stage 1: скелет с placeholder. Модуль НЕ ВКЛЮЧАЕТСЯ в сборку по умолчанию
// (см. settings.gradle.kts) — нужен -PenableIOS=true и macOS + Xcode +
// RoboVM/MobiVM toolchain.
//
// Stage 2 план:
//   - подключить RoboVM Gradle plugin (com.mobidevelop.robovm);
//   - настроить robovm.xml + Info.plist.xml;
//   - подключить :core и :rust-bridge (через статически слинкованный
//     libdeath_native);
//   - реализовать DeathIosLauncher → UIApplicationMain с Vulkan/Metal surface.
// =============================================================================

plugins {
    java
}

description = "Death — iOS платформа (stage 1 placeholder)"
