# Coding standards Death

> Stage 1.

## Язык / версии

- Java 21 (toolchain через Gradle).
- Rust edition 2021 (`rust-core/`).
- Kotlin DSL для Gradle (`*.gradle.kts`).

## Стиль

### Java

- 4 пробела, без табов.
- Открывающая фигурная скобка на той же строке.
- Имена пакетов: `WeTTeA.<layer>[.<feature>]`.
  - `WeTTeA.api.*` — контракты (только интерфейсы / records / enums);
  - `WeTTeA.core.*` — реализация платформенно-независимого ядра;
  - `WeTTeA.platform.{desktop,android,ios}.*` — платформенный код;
  - `WeTTeA.native_bridge.rust.*` — Java-сторона JNI.
- Имена классов:
  - `*Bootstrap` — однократная инициализация (`CoreBootstrap`,
    `VulkanInstanceBootstrap`);
  - `*Manager` — координатор stateful поведения (`SceneManager`);
  - `*Loader` — загрузчик ресурсов / библиотек (`NativeLibraryLoader`);
  - `*Info` — value record / контекст (`PlatformInfo`, `NativeBridgeInfo`).
- `final` по умолчанию для конкретных классов; интерфейсы — без `final`.
- `private static native` в JNI-классах; имена символов — JNI mangled.
- `record` для value-типов; `enum` для замкнутых множеств.

### JavaDoc

- **Все публичные классы / интерфейсы / методы — на русском.**
- Шаблон класса: краткое описание, контракт, инварианты, stage-1 ограничения,
  `@author Kuruma`, `@since 0.1.0`.
- `package-info.java` обязателен в каждом пакете и описывает что лежит в
  пакете и его границы.

### Rust

- `rustfmt` дефолт.
- Модули `pub mod x;` и `mod.rs` в каждом sub-каталоге.
- JNI-экспорты — только в `src/jni_exports.rs`. Никаких `extern "system" fn`
  в других модулях.
- Все public API — `#[no_mangle] pub extern "system" fn`.

## Запреты

- **Нельзя** писать "Death" в namespace, package, class, var. Только в
  user-facing строках (`applicationName`, `<application android:label>`).
- **Нельзя** оставлять `TODO` / `FIXME` без записи в `PROGRESS.md` строки
  `INTEGRATION_MISSING`.
- **Нельзя** прямые вызовы платформенных API (`org.lwjgl.glfw.*`,
  `android.app.*`) из `:core`. Только через `WeTTeA.api.*` контракты.
- **Нельзя** silent stubs (метод существует, ничего не делает, нигде не
  упомянут). Либо удалить, либо явно отметить как `INTEGRATION_MISSING`.
- **Нельзя** reflection в hot path (loop, render, event bus).

## Тесты

- Stage 1: smoke-сборка (`:core:compileJava`, `:desktop:compileJava`,
  `cargo check`).
- Stage 2: JUnit 5 для `:core` и `:rust-bridge`; Rust unit-тесты для
  `rust-core/`.
