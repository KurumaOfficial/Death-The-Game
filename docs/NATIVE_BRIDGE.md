# Native bridge Death

> Stage 1.

## Что это

Мост между Java side (`:rust-bridge` модуль) и Rust ядром (`rust-core/`).

```
Java                                              Rust
WeTTeA.native_bridge.rust.RustCore                death-native cdylib
   ├── nativeInit()    ────────────── JNI ───►   Java_WeTTeA_native_1bridge_rust_RustCore_nativeInit
   └── nativeShutdown() ───────────── JNI ───►   Java_WeTTeA_native_1bridge_rust_RustCore_nativeShutdown
```

## JNI symbol mangling

JVM вычисляет имя символа из FQN класса по правилам:
- Точки в имени пакета → слэши, потом → подчёркивания.
- Подчёркивания в имени пакета или класса → `_1`.
- Префикс `Java_`, потом полное имя класса, потом `_<метод>`.

Класс `WeTTeA.native_bridge.rust.RustCore` (содержит `_` в `native_bridge`):
- `WeTTeA.native_bridge.rust.RustCore` → `WeTTeA/native_bridge/rust/RustCore`
- → `WeTTeA_native_bridge_rust_RustCore` → mangling `_` → `WeTTeA_native_1bridge_rust_RustCore`
- + метод `nativeInit` → `Java_WeTTeA_native_1bridge_rust_RustCore_nativeInit`

Это то, что мы видим в `rust-core/src/jni_exports.rs`.

## Загрузка библиотеки

`WeTTeA.native_bridge.rust.RustNativeLibrary.loadIfNeeded`:

1. Принимает `PlatformInfo` + `PlatformFileSystem`.
2. Вызывает `System.loadLibrary("death_native")`.
3. JVM ищет в `java.library.path` файл:
   - Linux: `libdeath_native.so`;
   - Windows: `death_native.dll`;
   - macOS: `libdeath_native.dylib`;
   - Android: `libdeath_native.so` в `lib/<abi>/` apk;
   - iOS: статически слинкована, `loadLibrary` no-op.

## Stage 1 — что есть

- Java side контракт + реализация (`RustNativeLibrary`, `RustCore`).
- Rust side cdylib скелет с двумя no-op экспортами.
- JNI symbol mangling задокументирован.

## Stage 1 — чего нет

- **Cargo build не запускается из Gradle.** Сборка `rust-core` — ручная
  (`cd rust-core && cargo build`).
- **Артефакт не копируется в `java.library.path`.** Запускать с
  `-Djava.library.path=rust-core/target/debug` вручную.
- **Cross-compilation для Android (`cargo-ndk`) не настроена.**
- **Static link для iOS (`crate-type=["staticlib"]`) не настроен.**

Все пункты — INTEGRATION_MISSING, см. `PROGRESS.md`.

## Stage 2 интеграция

1. Gradle exec task `:rust-bridge:cargoBuild` → `cd $rootDir/rust-core && cargo build`.
2. Gradle copy task `:rust-bridge:copyNatives` → копирует `libdeath_native.*` в
   `rust-bridge/build/resources/main/native/<os>-<arch>/`.
3. `RustNativeLibrary` распаковывает из classpath в
   `PlatformFileSystem.userDataDirectory()/native/` и вызывает
   `System.load(path)` (вместо `loadLibrary`).
4. Android: дополнительный `crate-type = ["cdylib"]` + `cargo-ndk -t arm64-v8a`.
5. iOS: `crate-type = ["staticlib"]` + статическая линковка в RoboVM сборке.

## Стиль JNI

- Все экспорты — в `rust-core/src/jni_exports.rs`. Логика — в модулях
  (`physics::*`, `ecs::*`); экспорты только тонкие обёртки.
- Все Rust functions, экспортируемые через JNI: `#[no_mangle] pub extern "system" fn`.
- Никаких `unsafe { ... }` без явного `// SAFETY: ...` комментария.
- Любой panic Rust-стороны должен быть пойман `panic::catch_unwind` и
  переведён в Java exception (план stage 2).
