# Death

Кросс-платформенная игра на Java 21 + Rust + Vulkan.

> User-facing название проекта — **Death**.
> Внутренний namespace кода — **WeTTeA**. В классах, пакетах и переменных
> слово "Death" не появляется (только в строках лейблов и заголовках окон).

## Стек

| Слой               | Технологии                                            |
|--------------------|-------------------------------------------------------|
| Build              | Gradle 8.14.3 (Kotlin DSL), Java 21 toolchain         |
| Desktop            | LWJGL 3.3.6 (Vulkan, GLFW, OpenAL, STB, Assimp)       |
| Android (stage 2)  | Android Gradle Plugin + cargo-ndk                     |
| iOS (stage 2)      | RoboVM/MobiVM + MoltenVK                              |
| Native ядро        | Rust 2021 (`death-native` cdylib через JNI)           |
| Render API         | Vulkan 1.2+ (на iOS — поверх Metal через MoltenVK)    |
| Audio              | OpenAL Soft (через LWJGL)                             |

## Структура

```
Death_The_Game/
├── core/                    # :core (платформенно-независимое ядро)
│   └── src/main/java/WeTTeA/
│       ├── api/             # контракты (interfaces, records, enums)
│       └── core/            # реализация (time, loop, scene, events, services)
├── desktop/                 # :desktop (LWJGL3 launcher, GLFW + Vulkan)
├── rust-bridge/             # :rust-bridge (Java JNI side для rust-core)
├── android/                 # :android (placeholder, stage 2)
├── ios/                     # :ios (placeholder, stage 2)
├── rust-core/               # death-native crate (cdylib + JNI exports)
├── assets/death/            # runtime ассеты (текстуры, модели, аудио, ...)
├── docs/                    # документация (ARCHITECTURE / CODING / ...)
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
└── PROGRESS.md              # четыре таблицы прогресса (см. ниже)
```

## Stage 1 — что готово

- `:core` — все контракты `api/*` с подробным русским JavaDoc + базовая
  реализация (`Time`, `FixedStepGameLoop`, `SimpleEventBus`, `SceneManager`,
  `ServiceContainer`, `CoreBootstrap`).
- `:desktop` — LWJGL3 launcher: GLFW окно + Vulkan instance smoke. Поддержка
  флагов `--headless` (CI режим) и `--no-vulkan`.
- `:rust-bridge` — Java-сторона JNI: `RustNativeLibrary` + `RustCore` фасад.
- `rust-core/` — Cargo crate скелет: `Cargo.toml`, `src/lib.rs`,
  `src/jni_exports.rs` (no-op nativeInit/nativeShutdown), пустые модули
  `physics`, `ecs`, `pathfinding`, `scripting`.
- `:android` / `:ios` — placeholder модули с `INTEGRATION_MISSING` маркерами.
- `assets/death/` — дерево папок с README в каждой (контента нет).
- `docs/` — `ARCHITECTURE.md`, `CODING.md`, `INPUT.md`, `RENDER.md`,
  `BUILD.md`, `NATIVE_BRIDGE.md`.

## Сборка

```bash
./gradlew :core:compileJava
./gradlew :desktop:compileJava
./gradlew :rust-bridge:compileJava

./gradlew :desktop:run --args="--headless"   # smoke в headless
./gradlew :desktop:run                        # GLFW + VkInstance

cd rust-core && cargo check                   # rust skeleton smoke
```

Подробности — в `docs/BUILD.md`.

## Stage 1 — чего НЕТ (INTEGRATION_MISSING)

См. `PROGRESS.md` для полного списка с конкретным следующим шагом.
Вкратце:
- render-пайплайн (swapchain, render-pass, shaders, draw);
- input adapters (GLFW callbacks → InputState);
- audio (OpenAL источники / буферы);
- asset loading (классы есть, контента и форматов нет);
- ECS / физика / pathfinding / scripting (Rust модули пусты);
- Android / iOS интеграция (только placeholder);
- Cargo build hook в Gradle.

## Лицензия

Proprietary. © Kuruma. Все права защищены.
