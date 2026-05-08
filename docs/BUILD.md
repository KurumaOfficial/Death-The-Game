# Build Death

> Stage 1.

## Требования

- JDK 21 (toolchain auto-резолвится Gradle'ом, см. `core/build.gradle.kts`).
- Gradle wrapper (входит в репозиторий, `./gradlew`).
- Rust toolchain 1.83+ (для `rust-core/`, см. `rustup default stable`).
- Опционально:
  - Android SDK + `cargo-ndk` — для `:android`.
  - macOS + Xcode + RoboVM — для `:ios`.

## Команды

### Java side

```bash
./gradlew :core:compileJava
./gradlew :desktop:compileJava
./gradlew :rust-bridge:compileJava
./gradlew :desktop:run                 # запуск GLFW + VkInstance smoke
./gradlew :desktop:run --args="--headless"   # CI-режим
./gradlew :desktop:run --args="--no-vulkan"  # без Vulkan instance
```

### Rust side

```bash
cd rust-core
cargo check        # быстрая проверка типов и публичных символов
cargo build        # debug build → target/debug/libdeath_native.so
cargo build --release
```

После сборки `rust-core` — указать `java.library.path` при запуске:

```bash
./gradlew :desktop:run -Djava.library.path=rust-core/target/debug
```

### Условные модули

```bash
ANDROID_HOME=/path/to/sdk ./gradlew :android:assembleDebug
./gradlew -PenableIOS=true :ios:robovmIPad
```

## Smoke-test stage 1

Что должно собираться без ошибок:

| Команда                              | Должно работать |
|--------------------------------------|-----------------|
| `./gradlew :core:compileJava`        | Да              |
| `./gradlew :desktop:compileJava`     | Да              |
| `./gradlew :rust-bridge:compileJava` | Да              |
| `cd rust-core && cargo check`        | Да              |
| `./gradlew :desktop:run --args=--headless` | Да         |
| `./gradlew :desktop:run`             | Да на машине с display, иначе skip |
| `cd rust-core && cargo build`        | Должно собирать `libdeath_native.so` |

## Артефакты

| Цель                      | Где лежит                                  |
|---------------------------|--------------------------------------------|
| `:core` jar               | `core/build/libs/core-0.1.0-SNAPSHOT.jar`  |
| `:desktop` runnable jar   | `desktop/build/libs/desktop-...jar`        |
| `:rust-bridge` jar        | `rust-bridge/build/libs/...`               |
| `death-native` cdylib     | `rust-core/target/<profile>/libdeath_native.<ext>` |
