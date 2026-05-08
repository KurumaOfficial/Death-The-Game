// =============================================================================
// :android — Android платформенный модуль Death.
//
// Stage 1: скелет с placeholder Activity. Модуль НЕ ВКЛЮЧАЕТСЯ в сборку
// по умолчанию (см. settings.gradle.kts) — нужен ANDROID_HOME или
// -PenableAndroid=true. Это позволяет десктопной CI собираться без
// Android SDK, а мобильным разработчикам — собирать всё.
//
// Stage 2 план:
//   - подключить Android Gradle Plugin (com.android.application);
//   - настроить namespace WeTTeA.platform.android;
//   - подключить :core и :rust-bridge;
//   - настроить cargo-ndk для сборки .so на arm64-v8a / armeabi-v7a / x86_64;
//   - реализовать DeathActivity (Vulkan / GLES surface, GameLoop).
//
// Текущий build.gradle.kts намеренно держится минимальным: применять
// `com.android.application` без Android SDK невозможно, поэтому реальное
// тело модуля — INTEGRATION_MISSING.
// =============================================================================

plugins {
    // Применяется только если модуль реально включён через settings.gradle.kts.
    // На стадии 2 раскомментировать:
    // id("com.android.application")
    java
}

description = "Death — Android платформа (stage 1 placeholder)"
