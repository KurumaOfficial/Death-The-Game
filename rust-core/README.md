# rust-core

Нативное ядро Death (Rust crate `death-native`).

## Что это

Production артефакт — `cdylib` (`libdeath_native.so` / `death_native.dll` /
`libdeath_native.dylib`), который Java-сторона
(`:rust-bridge` → `WeTTeA.native_bridge.rust.RustNativeLibrary`) загружает
через `System.loadLibrary("death_native")`.

## Stage 1 (текущее состояние)

- Скелет crate'а с двумя JNI-экспортами (`nativeInit` / `nativeShutdown`).
- Все модули (`physics`, `ecs`, `pathfinding`, `scripting`) — пустые
  объявления, см. `src/<module>/mod.rs`.
- Артефакт **не собран** автоматически; Java-сторона при вызове
  `RustCore.initialize(...)` упадёт с `UnsatisfiedLinkError`. Это
  трекается в `PROGRESS.md` строка `RustCore` как `INTEGRATION_MISSING`.

## Сборка вручную

```bash
cd rust-core
cargo check          # smoke (без линковки cdylib зависимостей)
cargo build          # debug build
cargo build --release
```

Результат лежит в `target/<profile>/libdeath_native.{so,dll,dylib}`.

Чтобы Java-сторона его нашла:

```bash
java -Djava.library.path=rust-core/target/debug -jar build/libs/desktop.jar
```

## Stage 2 план

1. `physics` — Rapier3D backend.
2. `ecs` — `bevy_ecs` или `hecs` storage.
3. `pathfinding` — A* / HPA* для тактической сетки боя.
4. `scripting` — `mlua` (Lua) или `rhai` интерпретатор.
5. Cargo hook в Gradle (`:rust-bridge` exec task → `cargo build`).
6. Android: cargo-ndk + правильные таргеты (`aarch64-linux-android`).
7. iOS: статическая линковка (`crate-type = ["staticlib"]`).
