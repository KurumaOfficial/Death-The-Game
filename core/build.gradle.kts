// =============================================================================
// :core — общая игровая логика, не зависящая от платформы.
//
// Здесь живёт только то, что должно работать одинаково на всех 5 платформах:
//   api/contracts       — интерфейсы и DTO
//   core                — игровой цикл, сцены, сервисы, конфиг, сейвы
//   gameplay            — сущности, компоненты, боевая/RPG/bullet hell логика
//   ui                  — HUD/диалоги/меню (абстракции, рендер делает :render)
//   render              — render backend абстракция и pipeline
//   platform            — абстрактные платформенные адаптеры
//   native_bridge       — JNI-контракты к Rust ядру (Java сторона)
//   content             — data-driven контентные модели
//   tools               — отладочные инструменты
//
// Запрещено в этом модуле:
//   - LWJGL/GLFW/Vulkan конкретика (это в :desktop через render backend impl)
//   - Android SDK типы (это в :android)
//   - RoboVM/UIKit типы (это в :ios)
// =============================================================================

dependencies {
    api(libs.joml)
    api(libs.bundles.logging)
    api(libs.bundles.jackson)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.platform.launcher)
}
