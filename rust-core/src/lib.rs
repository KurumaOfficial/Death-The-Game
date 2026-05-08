// =============================================================================
// death-native — корневой модуль нативного ядра Death.
//
// Stage 1: предоставляет два JNI-экспорта — nativeInit / nativeShutdown,
//          которые вызываются из WeTTeA.native_bridge.rust.RustCore.
//          Логика модулей (физика, ECS, pathfinding, scripting) — на стадии 2.
//
// Архитектура:
//   - jni_exports        — точки входа JNI (extern "system" fn);
//   - physics            — будущий модуль интеграции Rapier3D;
//   - ecs                — будущий ECS storage (Bevy ECS / hecs);
//   - pathfinding        — будущий A*/HPA* для тактической сетки;
//   - scripting          — будущие mlua / rhai hooks.
//
// Все модули stage 1 — пустые объявления для дальнейшего наполнения.
// =============================================================================

#![allow(clippy::needless_doctest_main)]

pub mod jni_exports;
pub mod physics;
pub mod ecs;
pub mod pathfinding;
pub mod scripting;

/// Сводная информация о crate'е, доступная на Java стороне через
/// будущий JNI-экспорт `nativeBridgeInfo` (пока заглушка).
pub fn crate_version() -> &'static str {
    env!("CARGO_PKG_VERSION")
}
