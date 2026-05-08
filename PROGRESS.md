# Death — PROGRESS

> Stage 1 (skeleton) → Stage 2 (вертикальный срез: render + input + audio +
> rust-core minimal physics) → Stage 3+ (gameplay системы).
>
> **Текущая стадия: 2.1a + 2.2 + 2.5 завершены.** Stage 2.1a — Vulkan render
> stack: `VulkanDevice` (physical + logical + queues), `VulkanSurface`
> (glfwCreateWindowSurface), `VulkanSwapchain` (FIFO + image views),
> `VulkanRenderPass` (1 color attachment, clear → present_src),
> `VulkanFramebuffers`, `VulkanCommandBuffers` (pool + N=2 буферов),
> `VulkanFrameSync` (imgAvail/renderDone semaphores + inFlight fences),
> `VulkanRenderer` (acquire → record clear → submit → present с cycling
> HSV clear color). Smoke через lavapipe (`lvp_icd.x86_64.json`) +
> DISPLAY=:0: 10 кадров презентовано за 0.044с (~225 FPS), llvmpipe
> LLVM 15.0.7, 4 swapchain images, format B8G8R8A8_UNORM, чистый
> shutdown через `vkDeviceWaitIdle`. Stage 2.2 (input pipeline) и 2.5
> (Rapier3D physics) — без изменений, headless smoke оба зелёные.
> Следующая — 2.1b (shaders + pipeline + triangle) или 2.3 (OpenAL audio).

## Таблица 1 — Модули

| Модуль          | Статус       | Собирается | Что есть                                                                                              | Что дальше                                            |
|-----------------|--------------|------------|-------------------------------------------------------------------------------------------------------|--------------------------------------------------------|
| `:core`         | OK (stage 2.2)| да         | api/* контракты, core/* реализация (time, loop, scene, events, services, boot, input router/bindings/state) | unit-тесты на стадии 2                                |
| `:desktop`      | OK (stage 2.1a)| да        | LWJGL3 launcher, GLFW окно, VkInstance, RustCore wired, GlfwInputBackend, **VulkanDevice + Surface + Swapchain + RenderPass + Framebuffers + CommandBuffers + FrameSync + Renderer (clear-color)**, флаги `--headless`/`--no-vulkan`/`--no-native`/`--render-frames N` | shaders + pipeline + triangle (stage 2.1b)            |
| `:rust-bridge`  | OK (stage 2.5)| да         | RustNativeLibrary, RustCore, **RustPhysicsWorld (JNI-impl PhysicsWorld)**, cargoBuild + copyNativeArtifact | расширение `NativeBridgeInfo`, дополнительные тела/коллайдеры |
| `rust-core/`    | OK (stage 2.5) | `cargo build --release` | Cargo.toml (rapier3d 0.21 + nalgebra 0.32), lib.rs, jni_exports (RustCore + 5 RustPhysicsWorld), `physics::PhysicsWorld` (Rapier pipeline), пустые ecs/pathfinding/scripting | реальные ECS/pathfinding/scripting (stage 3.x), расширенный physics API |
| `:android`      | INTEGRATION_MISSING | пропущен по умолчанию | placeholder Java + AndroidManifest stub                                            | подключить AGP, реализовать DeathActivity              |
| `:ios`          | INTEGRATION_MISSING | пропущен по умолчанию | placeholder Java                                                                   | подключить RoboVM, реализовать DeathIosLauncher        |

## Таблица 2 — Файлы / классы (по компонентам ядра)

| Файл / класс                                              | Слой    | Статус | Wired в boot?                          |
|-----------------------------------------------------------|---------|--------|----------------------------------------|
| `WeTTeA.api.events.EventBus`                              | api     | OK     | через `SimpleEventBus` в `CoreBootstrap` |
| `WeTTeA.api.events.GameEvent`                             | api     | OK     | базовый тип для подписок               |
| `WeTTeA.api.platform.PlatformInfo`                        | api     | OK     | `DesktopPlatformInfo.detect()`         |
| `WeTTeA.api.platform.PlatformFileSystem`                  | api     | OK     | `DesktopPlatformFileSystem`            |
| `WeTTeA.api.platform.PlatformAdapter`                     | api     | OK     | контракт; реализация — stage 2         |
| `WeTTeA.api.nativebridge.NativeLibraryLoader`             | api     | OK     | `RustNativeLibrary`                    |
| `WeTTeA.api.nativebridge.NativeBridgeInfo`                | api     | OK     | record, заполняется в loader           |
| `WeTTeA.api.nativebridge.NativeModule`                    | api     | OK     | enum модулей Rust-стороны              |
| `WeTTeA.core.Time`                                        | core    | OK     | `SystemNanoTime` зарегистрирован       |
| `WeTTeA.core.SystemNanoTime`                              | core    | OK     | в `ServiceContainer`                   |
| `WeTTeA.core.loop.GameLoop`                               | core    | OK     | контракт                               |
| `WeTTeA.core.loop.FixedStepGameLoop`                      | core    | OK     | в `ServiceContainer`                   |
| `WeTTeA.core.scene.Scene`                                 | core    | OK     | контракт                               |
| `WeTTeA.core.scene.SceneManager`                          | core    | OK     | в `ServiceContainer`                   |
| `WeTTeA.core.service.ServiceContainer`                    | core    | OK     | возвращается из `CoreBootstrap.boot()` |
| `WeTTeA.core.events.SimpleEventBus`                       | core    | OK     | в `ServiceContainer`                   |
| `WeTTeA.core.CoreBootstrap`                               | core    | OK     | вызывается из `DesktopLauncher.main()` |
| `WeTTeA.platform.desktop.DesktopPlatformInfo`             | desktop | OK     | `detect()` вызывается из launcher      |
| `WeTTeA.platform.desktop.DesktopPlatformFileSystem`       | desktop | OK     | в launcher                             |
| `WeTTeA.platform.desktop.GlfwWindow`                      | desktop | OK     | в launcher (skip при `--headless`)     |
| `WeTTeA.platform.desktop.VulkanInstanceBootstrap`         | desktop | OK     | в launcher (skip при `--no-vulkan`)    |
| `WeTTeA.platform.desktop.VulkanDevice`                    | desktop | OK (stage 2.1a) | физическое устройство (graphics + present queue families) + логическое VkDevice + VkQueue; `VulkanRenderer` создаёт |
| `WeTTeA.platform.desktop.VulkanSurface`                   | desktop | OK (stage 2.1a) | обёртка над VkSurfaceKHR через `glfwCreateWindowSurface`; уничтожается ПОСЛЕ swapchain |
| `WeTTeA.platform.desktop.VulkanSwapchain`                 | desktop | OK (stage 2.1a) | VkSwapchainKHR (FIFO present, B8G8R8A8_UNORM/SRGB color space) + image views; clamps extent к caps |
| `WeTTeA.platform.desktop.VulkanRenderPass`                | desktop | OK (stage 2.1a) | 1 color attachment (loadOp=CLEAR, finalLayout=PRESENT_SRC_KHR), 1 graphics subpass, EXTERNAL→0 dependency на COLOR_ATTACHMENT_OUTPUT |
| `WeTTeA.platform.desktop.VulkanFramebuffers`              | desktop | OK (stage 2.1a) | по одному `VkFramebuffer` на каждый swapchain image view |
| `WeTTeA.platform.desktop.VulkanCommandBuffers`            | desktop | OK (stage 2.1a) | `VkCommandPool` (RESET_COMMAND_BUFFER_BIT, graphics family) + N primary cmd buffers; ресет на кадре |
| `WeTTeA.platform.desktop.VulkanFrameSync`                 | desktop | OK (stage 2.1a) | imageAvailable/renderFinished semaphores + inFlight fences (создаются SIGNALED) + imagesInFlight для пересечений |
| `WeTTeA.platform.desktop.VulkanRenderer`                  | desktop | OK (stage 2.1a) | оркестратор: владеет всеми Vulkan ресурсами; `renderFrame(t)` = wait fence → acquire → record clear (HSV cycling) → submit → present; чистый shutdown через `vkDeviceWaitIdle` |
| `WeTTeA.platform.desktop.DesktopLauncher`                 | desktop | OK     | main() + RustCore lifecycle + input smoke (stage 2.2) + physics smoke (stage 2.5) + **render loop (stage 2.1a) с `--render-frames N`** |
| `WeTTeA.platform.desktop.GlfwInputBackend`                | desktop | OK (stage 2.2) | реализует `InputBackend`, регистрирует 4 GLFW callback'а, буферизует raw events; вызывается из main loop через `InputRouter.pollFrom()` |
| `WeTTeA.api.input.RawInputEvent`                          | api     | OK     | record (source, keyCode, pressed, axis, value, x, y, ts) |
| `WeTTeA.api.input.InputSource`                            | api     | OK     | enum (KEYBOARD, MOUSE, GAMEPAD, TOUCH, MOTION) |
| `WeTTeA.api.input.InputAction`                            | api     | OK     | enum (MOVE_*, ATTACK_*, DODGE_ROLL, FOCUS, CONFIRM, CANCEL, OPEN_MENU, ...) |
| `WeTTeA.api.input.InputAxis`                              | api     | OK     | enum (MOVE_X/Y, CAMERA_X/Y/ZOOM, ATTACK_*_FORCE) |
| `WeTTeA.api.input.InputContext`                           | api     | OK     | enum (GAMEPLAY, UI_MENU, NARRATIVE_DIALOG, CINEMATIC, BLOCKED) |
| `WeTTeA.api.input.ActionEventListener`                    | api     | OK     | `@FunctionalInterface onAction(action, pressed, strength)` |
| `WeTTeA.api.input.InputBackend`                           | api     | OK     | контракт; реализация — `GlfwInputBackend` |
| `WeTTeA.api.input.KeyCodes`                               | api     | OK (stage 2.2) | int constants для GLFW key/mouse кодов (платформонезависимые биндинги) |
| `WeTTeA.core.input.ActionBinding`                         | core    | OK (stage 2.2) | record (context, source, keyCode, action) |
| `WeTTeA.core.input.InputBindings`                         | core    | OK (stage 2.2) | immutable набор биндингов + `defaults()` (WASD/Esc/LMB/RMB/...); регистрируется в `ServiceContainer` |
| `WeTTeA.core.input.InputState`                            | core    | OK (stage 2.2) | snapshot: pressed actions, axis values, mouse position/delta; в `ServiceContainer` |
| `WeTTeA.core.input.InputRouter`                           | core    | OK (stage 2.2) | consume(RawInputEvent) → applies bindings → `ActionEventListener` + `EventBus`; стек контекстов; в `ServiceContainer` |
| `WeTTeA.api.physics.Vec3`                                 | api     | OK     | record `(x,y,z)`, передаётся в `RustPhysicsWorld`        |
| `WeTTeA.api.physics.PhysicsBodyHandle`                    | api     | OK     | record `(raw)`, обёртка над raw-handle                   |
| `WeTTeA.api.physics.PhysicsWorld`                         | api     | OK     | контракт; реализация — `RustPhysicsWorld`                |
| `WeTTeA.native_bridge.rust.RustNativeLibrary`             | bridge  | OK     | вызывается из `RustCore.initialize`; classpath extract в `userDataDir/native/` |
| `WeTTeA.native_bridge.rust.RustCore`                      | bridge  | OK     | вызывается из `DesktopLauncher`         |
| `WeTTeA.native_bridge.rust.RustPhysicsWorld`              | bridge  | OK     | JNI-impl `PhysicsWorld`, lifecycle через AutoCloseable; вызывается из smoke в `DesktopLauncher` |
| `:rust-bridge` cargoBuild + copyNativeArtifact            | bridge  | OK     | hooked в `processResources`             |
| `rust-core/src/jni_exports.rs` (RustCore)                 | native  | OK (no-op) | вызывается через JNI из `RustCore`  |
| `rust-core/src/jni_exports.rs` (RustPhysicsWorld)         | native  | OK (stage 2.5) | 5 экспортов: nativeCreate / nativeDestroy / nativeAddDynamicBody / nativeStep / nativeBodyPosition |
| `rust-core/src/physics/mod.rs`                            | native  | OK (stage 2.5) | `PhysicsWorld` (Rapier pipeline + gravity + ball collider), `pack/unpack_body_handle` |
| `rust-core/src/{ecs,pathfinding,scripting}/mod.rs`        | native  | EMPTY  | пустые объявления (stage 3.x)         |

## Таблица 3 — INTEGRATION_MISSING

| Что отсутствует                                             | Где трекается                                                | Конкретный следующий шаг                                                |
|-------------------------------------------------------------|--------------------------------------------------------------|--------------------------------------------------------------------------|
| ~~Cargo build hook из Gradle~~                              | _stage 2.4 done_                                             | _DONE: `cargoBuild` + `copyNativeArtifact` в `:rust-bridge`_              |
| ~~Загрузка нативной библиотеки в runtime~~                  | _stage 2.4 done_                                             | _DONE: classpath extract → `System.load`; вызов из `DesktopLauncher`_     |
| ~~Vulkan logical device + queues~~                          | _stage 2.1a done_                                            | _DONE: `VulkanDevice.pick()` + `createLogical()` (graphics + present queue families)_ |
| ~~Vulkan swapchain~~                                        | _stage 2.1a done_                                            | _DONE: `VulkanSurface` + `VulkanSwapchain` (FIFO, B8G8R8A8_UNORM, image views)_ |
| ~~Vulkan render-pass + framebuffers~~                       | _stage 2.1a done_                                            | _DONE: `VulkanRenderPass` (1 color attachment, clear → present_src) + `VulkanFramebuffers`_ |
| Shader pipeline (SPIR-V)                                    | `:desktop` render (stage 2.1b)                               | glslc → `.spv`, `VkPipelineLayout` + `VkPipeline`                        |
| Hello-triangle draw                                         | `:desktop` render (stage 2.1b)                               | vertex/fragment shader, `vkCmdBindPipeline` + `vkCmdDraw(3,1,0,0)` в `VulkanRenderer.recordClear` |
| Swapchain recreate-on-resize                                | `:desktop` render (stage 2.1b)                               | `glfwSetFramebufferSizeCallback` → `VulkanRenderer.recreateSwapchain()` |
| ~~Input adapter GLFW~~                                      | _stage 2.2 done_                                             | _DONE: `GlfwInputBackend` + `InputRouter` + `InputBindings` + `InputState`; raw events → actions → listeners + EventBus_ |
| Audio (OpenAL)                                              | `:core` audio + `:desktop`                                   | `AudioContext`, source/buffer, OGG decode (stb_vorbis)                   |
| Asset формат + loader                                       | `:core` content                                              | `AssetIndex`, json manifest, type-specific loaders                       |
| ECS storage Rust                                            | `rust-core/src/ecs`                                          | bevy_ecs или hecs, world handle через JNI                                |
| ~~Физика (Rapier3D minimal)~~                               | _stage 2.5 done_                                             | _DONE: `PhysicsWorld` + 5 JNI экспортов; smoke: y=10 → 5.07 за 1с гравитации_ |
| Физика — расширенный API (forces, collider shapes, queries) | `rust-core/src/physics`                                      | apply_force / apply_impulse / set_velocity / cuboid+capsule / raycast / contacts |
| Pathfinding A*                                              | `rust-core/src/pathfinding`                                  | tactical grid, A* implementation                                         |
| Scripting (mlua)                                            | `rust-core/src/scripting`                                    | mlua dependency + script handle JNI                                      |
| Android Activity + AGP                                      | `:android` build.gradle.kts                                  | подключить `com.android.application`, `DeathActivity`                    |
| Android cargo-ndk                                           | `:android` + `rust-core`                                     | `cargo-ndk -t arm64-v8a` integration                                     |
| iOS RoboVM launcher                                         | `:ios`                                                       | подключить RoboVM, `DeathIosLauncher`                                    |
| iOS staticlib                                               | `rust-core/Cargo.toml`                                       | `crate-type = ["staticlib"]` для iOS таргета                              |
| `WeTTeA.api.platform.PlatformAdapter` реализация            | `:desktop`                                                   | `DesktopPlatformAdapter` с lifecycle hooks                                |
| Юнит-тесты `:core`                                           | `:core`                                                      | JUnit 5 уже подключён, пишем тесты                                        |
| **[Endpoint] UI/menu layer**                                 | `:core` ui + `:desktop`                                      | immediate-mode UI (label/button/slider) + рендер через Vulkan; main menu  |
| **[Endpoint] SceneManager transitions**                       | `:core` scene                                                | `pushScene/popScene/replaceScene` с lifecycle hooks (onEnter/onExit)      |
| **[Endpoint] Settings persistence**                          | `:core` config                                               | `Settings` record + JSON serialize в `userDataDir/settings.json`          |
| **[Endpoint] Camera (perspective + first-person)**           | `:core` math + `:desktop`                                    | `Mat4` perspective/lookAt, yaw/pitch, mouse-look                          |
| **[Endpoint] Mesh primitive (cuboid)**                       | `:core` render + `:desktop`                                  | vertex/index buffer, color uniform, draw cube                             |
| **[Endpoint] Kinematic character controller**                | `rust-core/src/physics` + JNI                                | Rapier `KinematicCharacterController`, WASD → velocity → step             |
| **[Endpoint] Input mapping (action → keys)**                 | `:core` input                                                | `ActionMap` (move_forward → W/Up, look_x → mouse_dx); сериализуемо         |

## Таблица 4 — Roadmap по стадиям

| Стадия | Цель                                                       | Acceptance critera                                                                          |
|--------|------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| 1      | Скелет, smoke-сборка                                       | `:core:compileJava`, `:desktop:compileJava`, `:rust-bridge:compileJava`, `cargo check` зелёные. `:desktop:run --headless` отрабатывает без ошибок. ZIP delivered. |
| 2.1a   | Vulkan device + swapchain + clear-color                    | **DONE**: `VulkanDevice` + `Surface` + `Swapchain` + `RenderPass` + `Framebuffers` + `CommandBuffers` + `FrameSync` + `Renderer.renderFrame(t)`; smoke через lavapipe — 10 кадров презентовано за 0.044с (~225 FPS), `BUILD SUCCESSFUL`, чистый shutdown через `vkDeviceWaitIdle`. |
| 2.1b   | Vulkan triangle (shaders + pipeline)                       | окно показывает цветной треугольник, 60 FPS, корректный shutdown; добавляется `VkPipeline` + SPIR-V shader compile, swapchain recreate-on-resize |
| 2.2    | Input adapter (desktop)                                    | **DONE**: `GlfwInputBackend` registers GLFW callbacks → `RawInputEvent`; `InputRouter` применяет `InputBindings` (WASD/Esc/LMB/...), диспетчеризирует `ActionEventListener` + `EventBus`, обновляет `InputState`; контексты GAMEPLAY/UI_MENU/NARRATIVE_DIALOG; headless smoke = 6 ассертов |
| 2.3    | Audio (desktop)                                            | OpenAL контекст, проиграть test sine wave / ogg sample                                       |
| 2.4    | Cargo hook + RustCore initialize                           | `:desktop:run` загружает `libdeath_native`, `RustCore.initialize()` отрабатывает            |
| 2.5    | rust-core minimal physics                                  | **DONE**: `PhysicsWorld` + 5 JNI экспортов; headless smoke падение y=10 → ~5.07 за 60×1/60s |
| 3.1    | Asset loading                                              | манифест `assets/death/manifest.json`, type-loaders, runtime cache                            |
| 3.2    | ECS / scene system                                         | spawn/despawn entity, system iterate                                                         |
| 3.3    | Battle scene prototype                                     | тактическая сетка, pathfinding, базовая боёвка                                              |
| 4      | Android вертикальный срез                                  | apk запускается, GLFW/Vulkan surface, тот же triangle                                       |
| 5      | iOS вертикальный срез                                      | ipa запускается, MoltenVK, тот же triangle                                                  |
| **E**  | **Engine vertical slice — почти-финальная стадия движка**  | Главное меню → Settings → Play. Play открывает сцену "white room" 50×50×50, kinematic character (placeholder cuboid mesh) ходит WASD, крутит камерой мышью; Esc возвращает в меню. Settings persist в `userDataDir/settings.json`. Стабильно ≥60 FPS на десктопе, без утечек при scene transitions. *Это последний шаг "базы движка" перед собственно gameplay/контент-наполнением (stage 3+).* |

## Smoke результаты — stage 2.1a (Vulkan device + swapchain + clear color)

| Команда                                                                              | Результат |
|--------------------------------------------------------------------------------------|-----------|
| `./gradlew :core:compileJava`                                                        | OK |
| `./gradlew :rust-bridge:compileJava`                                                 | OK |
| `./gradlew :desktop:compileJava`                                                     | OK — компилирует 8 новых Vulkan классов |
| `./gradlew :desktop:run --args="--headless"`                                         | OK — input + physics smokes (без изменений со stage 2.2/2.5) |
| `VK_ICD_FILENAMES=…/lvp_icd.x86_64.json DISPLAY=:0 ./gradlew :desktop:run --args="--render-frames 10"` | OK — 10 кадров презентовано через lavapipe, ~225 FPS, чистый shutdown |

Trace render smoke (`--render-frames 10`, lavapipe):

```
> Task :desktop:run
[Death:desktop] booting Death stage 1 launcher
[Death:desktop] core booted, phase=RUNNING
[Death:desktop] platform: family=DESKTOP os=Linux arch=amd64 version=5.15.200 64bit=true
[Death:desktop] userDataDir=/home/ubuntu/.local/share/Death
[Death:rust] native init (no-op stage 2.4)
[Death:desktop] native loaded file=libdeath_native.so version=0.0.0
[Death:desktop] GLFW window opened
[Death:desktop] Vulkan instance created. Physical devices visible: 1
[Death:desktop] Vulkan surface created (handle=7febe06d2710)
[Death:desktop] Vulkan device picked: llvmpipe (LLVM 15.0.7, 256 bits) (gfx=0, present=0)
[Death:desktop] Vulkan logical device created (queues: gfx + present, shared)
[Death:desktop] Vulkan swapchain created: 1280x720 format=44 presentMode=2 images=4
[Death:desktop] Vulkan render pass created (1 color attachment, format=44)
[Death:desktop] Vulkan framebuffers created: count=4
[Death:desktop] Vulkan command pool + buffers ready (count=2)
[Death:desktop] Vulkan frame sync ready: framesInFlight=2 swapchainImages=4
[Death:desktop] Vulkan renderer ready: device=llvmpipe (LLVM 15.0.7, 256 bits) 1280x720
[Death:desktop] input backend wired (GLFW callbacks)
[Death:desktop] render loop: presented=10 frames in 0.044s (~224.8 FPS)
[Death:desktop] Vulkan renderer disposed
[Death:rust] native shutdown (no-op stage 2.4)
[Death:desktop] native shutdown OK
[Death:desktop] shutdown OK

BUILD SUCCESSFUL in 57s
```

Что доказывает этот smoke (stage 2.1a):

1. **Surface + device pick.** GLFW VkSurfaceKHR создаётся (`handle=…2710`),
   `VulkanDevice.pick()` находит lavapipe llvmpipe с queue family 0,
   которая поддерживает И graphics И present (gfx=present=0 — типично для
   software/iGPU).
2. **Logical device + queues.** `vkCreateDevice` с включённым
   `VK_KHR_swapchain` extension, `VkQueue` достаются для обеих ролей
   (один и тот же handle, т.к. семейство одно).
3. **Swapchain.** 1280x720 (как окно), `format=44` = `VK_FORMAT_B8G8R8A8_UNORM`,
   `presentMode=2` = `VK_PRESENT_MODE_FIFO_KHR`, 4 images (lavapipe выдала
   `minImageCount+1`, ограниченное `maxImageCount`).
4. **Render pass + framebuffers.** 1 color attachment (clear → present_src),
   4 framebuffer'а (по одному на image view).
5. **Command pool + buffers + sync.** Pool на graphics family, 2 primary
   command buffer'а (`MAX_FRAMES_IN_FLIGHT=2`), 2 пары semaphore'ов +
   2 fence'а + per-image fence-tracking.
6. **Render loop.** 10 итераций `renderFrame(t)`: ждём `inFlight` fence →
   `vkAcquireNextImageKHR` → resolve image-fence collision → reset cmd buffer
   → record (`beginRenderPass` с CLEAR loadOp = автоматически закрашивает
   target в HSV-цвет; больше ничего не рисуем — это 2.1a, triangle
   придёт в 2.1b) → `vkQueueSubmit` (wait `imgAvail` на
   `COLOR_ATTACHMENT_OUTPUT`, signal `renderDone` + fence) →
   `vkQueuePresentKHR` (wait `renderDone`).
7. **Performance.** 10 кадров за 0.044с = ~225 FPS. Это software lavapipe
   — на реальном GPU FPS ограничен только VSync (FIFO, 60Hz).
8. **Clean shutdown.** `vkDeviceWaitIdle` гарантирует, что все frames
   завершены до начала dispose; ресурсы уничтожаются в обратном порядке
   (sync → cmd buffers → framebuffers → render pass → swapchain →
   device → surface). Никаких validation warnings (validation layers не
   включены в smoke; для dev-режима — `VK_INSTANCE_LAYERS=VK_LAYER_KHRONOS_validation`).

> На VM в этом сеансе: `apt-get install mesa-vulkan-drivers vulkan-tools`,
> ICD `/usr/share/vulkan/icd.d/lvp_icd.x86_64.json`. Перед запуском
> требуется `XDG_RUNTIME_DIR` (mesa создаёт там временный shm). Этот
> setup не нужен на машинах с реальным GPU драйвером — хватит дефолтного
> Vulkan loader'а.
>
> Endpoint **E** (главное меню → Settings → Play → white room с character
> controller + WASD + mouse-look) теперь имеет полный render-стек как
> фундамент: остаётся 2.1b (triangle/pipeline) → 2.1c (mesh/cuboid + camera) →
> UI/menu/settings persistence + kinematic character controller (расширенный
> physics API).

## Smoke результаты — stage 1

| Команда                                          | Результат |
|--------------------------------------------------|-----------|
| `./gradlew :core:compileJava`                    | OK (BUILD SUCCESSFUL) |
| `./gradlew :desktop:compileJava`                 | OK (BUILD SUCCESSFUL) |
| `./gradlew :rust-bridge:compileJava`             | OK (BUILD SUCCESSFUL) |
| `cd rust-core && cargo check`                    | OK (`Finished dev profile in 0.09s`) |
| `./gradlew :desktop:run --args="--headless"`     | OK — full boot trace ниже |

Полный (не-headless) smoke `./gradlew :desktop:run` потребует display server
с поддержкой Vulkan loader; на VM без дисплея пропускается. Локально
проверяется через ту же команду без `--args`.

## Smoke результаты — stage 2.4 (Cargo hook + native runtime)

| Команда                                                                   | Результат |
|---------------------------------------------------------------------------|-----------|
| `./gradlew :rust-bridge:cargoBuild`                                       | OK (`Finished release profile [optimized] target(s) in 9.61s`) |
| `./gradlew :rust-bridge:copyNativeArtifact`                               | OK (`libdeath_native.so` → `build/resources/main/native/linux/x86_64/`) |
| `./gradlew :rust-bridge:compileJava`                                      | OK (BUILD SUCCESSFUL) |
| `./gradlew :desktop:compileJava`                                          | OK (BUILD SUCCESSFUL) |
| `./gradlew :desktop:run --args="--headless"`                              | OK — boot trace + native load + native shutdown |
| `./gradlew :desktop:run --args="--headless --no-native"`                  | OK — native пропущен (smoke без cargo) |

Trace headless smoke с native (`:desktop:run --args="--headless"`):

```
> Task :rust-bridge:cargoBuild
   Compiling death-native v0.0.0 (/home/ubuntu/Death_The_Game/rust-core)
    Finished `release` profile [optimized] target(s) in 0.20s

> Task :rust-bridge:copyNativeArtifact
> Task :rust-bridge:compileJava
> Task :desktop:compileJava

> Task :desktop:run
[Death:desktop] booting Death stage 1 launcher
[Death:desktop] core booted, phase=RUNNING
[Death:desktop] platform: family=DESKTOP os=Linux arch=amd64 version=5.15.200 64bit=true
[Death:desktop] userDataDir=/home/ubuntu/.local/share/Death
[Death:rust] native init (no-op stage 2.4)
[Death:desktop] native loaded file=libdeath_native.so version=0.0.0
[Death:rust] native shutdown (no-op stage 2.4)
[Death:desktop] --headless: skipping GLFW + Vulkan smoke
[Death:desktop] native shutdown OK
[Death:desktop] shutdown OK

BUILD SUCCESSFUL in 9s
8 actionable tasks: 8 executed
```

Что произошло на этом запуске:
1. `processResources` зависит от `copyNativeArtifact` (зависит от `cargoBuild`).
2. `cargoBuild` собрал `rust-core` в release-режиме → `libdeath_native.so`.
3. `copyNativeArtifact` положил его в `build/resources/main/native/linux/x86_64/`,
   что добавлено в `sourceSets.main.resources.srcDir(...)` у `:rust-bridge`
   (т.е. classpath `:desktop`).
4. `RustNativeLibrary.loadIfNeeded` извлёк `/native/linux/x86_64/libdeath_native.so`
   во временный файл `/home/ubuntu/.local/share/Death/native/libdeath_native.so`
   и вызвал `System.load`.
5. `RustCore.nativeInit` отработал — Rust eprintln! выдал
   `[Death:rust] native init (no-op stage 2.4)` (доказательство, что
   символы реально вызвались, а не просто `dlopen` прошёл).
6. `RustCore.shutdown` → `RustCore.nativeShutdown` → ещё один Rust eprintln!.

> Порядок строк "Rust" / "Java" в трассе может разниться от запуска к
> запуску — `eprintln!` в Rust и `System.out` в Java идут в разные потоки
> (stderr / stdout), и Gradle merger их перемешивает. Главное — обе
> стороны действительно отработали и `BUILD SUCCESSFUL` (return code = 0).

`-PskipCargo=true` отключает `cargoBuild`/`copyNativeArtifact` — для CI без
Rust toolchain. В этом случае launcher нужно запускать с `--no-native`.

## Smoke результаты — stage 2.5 (rust-core minimal physics)

| Команда                                                                   | Результат |
|---------------------------------------------------------------------------|-----------|
| `./gradlew :rust-bridge:cargoBuild` (clean rapier compile)                | OK (`Finished release profile [optimized] target(s) in 42.77s` — холодная сборка с rapier3d/parry3d/nalgebra) |
| `./gradlew :rust-bridge:cargoBuild` (incremental)                         | OK (`Finished release profile [optimized] target(s) in 0.62s`) |
| `./gradlew :rust-bridge:copyNativeArtifact`                               | OK (`libdeath_native.so` → `build/resources/main/native/linux/x86_64/`) |
| `./gradlew :rust-bridge:compileJava`                                      | OK (BUILD SUCCESSFUL) — компилирует `RustPhysicsWorld` |
| `./gradlew :desktop:compileJava`                                          | OK (BUILD SUCCESSFUL) — компилирует `runPhysicsSmoke()` |
| `./gradlew :desktop:run --args="--headless"`                              | OK — body падает с y=10 до y≈5.07 за 1с гравитации |

Trace headless smoke с physics (`:desktop:run --args="--headless"`):

```
> Task :rust-bridge:cargoBuild
   Compiling death-native v0.0.0 (/home/ubuntu/Death_The_Game/rust-core)
    Finished `release` profile [optimized] target(s) in 0.62s

> Task :rust-bridge:copyNativeArtifact

> Task :desktop:run
[Death:desktop] booting Death stage 1 launcher
[Death:desktop] core booted, phase=RUNNING
[Death:desktop] platform: family=DESKTOP os=Linux arch=amd64 version=5.15.200 64bit=true
[Death:desktop] userDataDir=/home/ubuntu/.local/share/Death
[Death:rust] native init (no-op stage 2.4)
[Death:desktop] native loaded file=libdeath_native.so version=0.0.0
[Death:desktop] --headless: skipping GLFW + Vulkan smoke
[Death:desktop] physics body0 initial=(0.0000, 10.0000, 0.0000)
[Death:desktop] physics body0 after 60 ticks @ dt=1/60 final=(0.0000, 5.0746, 0.0000)
[Death:desktop] physics smoke: dy=-4.925436019897461 (ожидание ~ -4.9 от gravity)
[Death:desktop] native shutdown OK
[Death:desktop] shutdown OK
[Death:rust] native shutdown (no-op stage 2.4)

BUILD SUCCESSFUL in 8s
```

Что доказывает этот smoke:
1. `nativeCreate` действительно создал `Box<PhysicsWorld>` и вернул raw pointer
   (handle ≠ 0, иначе Java бросил бы `IllegalStateException`).
2. `nativeAddDynamicBody` упаковал `RigidBodyHandle (idx, generation)` в jlong;
   `bodyPosition` распаковал тот же long — round-trip сходится (initial=10).
3. 60 вызовов `nativeStep(handle, 1/60)` крутили `PhysicsPipeline::step` с
   gravity = (0, -9.81, 0) и применяли её к dynamic body (масса от ball collider
   плотностью 1.0 — без коллайдера Rapier держит `effective_inv_mass=0` и
   gravity не интегрируется).
4. Финальная высота 5.0746 совпадает с semi-implicit Euler аналитикой
   (y₆₀ ≈ 10 − 0.5·9.81·1² ≈ 5.1, расхождение от substep schedule Rapier).
5. `nativeDestroy` вызвался через `try-with-resources` (`AutoCloseable` в
   `RustPhysicsWorld`) — leak-санити обеспечен.

> JNI символы Rust-стороны: `Java_WeTTeA_native_1bridge_rust_RustPhysicsWorld_nativeCreate`,
> `..._nativeDestroy`, `..._nativeAddDynamicBody`, `..._nativeStep`,
> `..._nativeBodyPosition` (mangling `_1` для `-` в package, который у нас
> заменён на `_` уровень → `native_bridge` остаётся как есть).

## Smoke результаты — stage 2.2 (input adapter)

| Команда                                                                   | Результат |
|---------------------------------------------------------------------------|-----------|
| `./gradlew :core:compileJava`                                             | OK — компилирует `ActionBinding`, `InputBindings`, `InputState`, `InputRouter` |
| `./gradlew :desktop:compileJava`                                          | OK — компилирует `GlfwInputBackend` + `runInputSmoke()` |
| `./gradlew :rust-bridge:compileJava`                                      | OK — без изменений в stage 2.2 |
| `./gradlew :desktop:run --args="--headless"`                              | OK — 6 ассертов input smoke + 60-tick physics smoke (выживший с stage 2.5) |

Trace headless smoke (input + physics, `:desktop:run --args="--headless"`):

```
> Task :desktop:run
[Death:desktop] booting Death stage 1 launcher
[Death:desktop] core booted, phase=RUNNING
[Death:desktop] platform: family=DESKTOP os=Linux arch=amd64 version=5.15.200 64bit=true
[Death:desktop] userDataDir=/home/ubuntu/.local/share/Death
[Death:rust] native init (no-op stage 2.4)
[Death:desktop] native loaded file=libdeath_native.so version=0.0.0
[Death:desktop] --headless: skipping GLFW + Vulkan smoke
[Death:desktop] input listener@GAMEPLAY: action=MOVE_UP pressed=true strength=1.0
[Death:desktop] input EventBus: ctx=GAMEPLAY action=MOVE_UP pressed=true
[Death:desktop] input listener@GAMEPLAY: action=MOVE_UP pressed=false strength=0.0
[Death:desktop] input EventBus: ctx=GAMEPLAY action=MOVE_UP pressed=false
[Death:desktop] input listener@GAMEPLAY: action=OPEN_MENU pressed=true strength=1.0
[Death:desktop] input EventBus: ctx=GAMEPLAY action=OPEN_MENU pressed=true
[Death:desktop] input listener@GAMEPLAY: action=OPEN_MENU pressed=false strength=0.0
[Death:desktop] input EventBus: ctx=GAMEPLAY action=OPEN_MENU pressed=false
[Death:desktop] input listener@UI_MENU: action=CANCEL pressed=true
[Death:desktop] input EventBus: ctx=UI_MENU action=CANCEL pressed=true
[Death:desktop] input smoke: gameplay-listener=4 calls, menu-listener=1 calls, lastGameplayAction=OPEN_MENU (контекстные биндинги работают)
[Death:desktop] physics body0 initial=(0.0000, 10.0000, 0.0000)
[Death:desktop] physics body0 after 60 ticks @ dt=1/60 final=(0.0000, 5.0746, 0.0000)
[Death:desktop] physics smoke: dy=-4.925436019897461 (ожидание ~ -4.9 от gravity)
[Death:desktop] native shutdown OK
[Death:desktop] shutdown OK
[Death:rust] native shutdown (no-op stage 2.4)

BUILD SUCCESSFUL in 57s
```

Что доказывает этот smoke (6 внутренних ассертов в `runInputSmoke()`):

1. **KEY_W press → MOVE_UP active.** Синтетический `RawInputEvent.button(KEYBOARD, KEY_W, true)`
   проходит через `InputRouter.consume()`, лукапится в `InputBindings.lookup(GAMEPLAY, KEYBOARD, KEY_W)` →
   `MOVE_UP`. `InputState.isActionDown(MOVE_UP) == true`, gameplay-listener вызвался, EventBus опубликовал `ActionEvent`.
2. **KEY_W release → MOVE_UP inactive.** Асимметрия press/release работает корректно.
3. **Mouse pointer move (100, 200).** `InputState.mouseX/Y` обновились, `mouseDeltaX/Y` аккумулируются, `consumeMouseDelta()` обнуляет.
4. **Scroll axis CAMERA_ZOOM = 1.5.** `InputState.axis(CAMERA_ZOOM) == 1.5`.
5. **KEY_ESCAPE в GAMEPLAY → OPEN_MENU.** Подтверждает, что в игровом контексте Esc открывает меню.
6. **Переключение в UI_MENU → KEY_ESCAPE = CANCEL.** Доказательство контекстного mapping'а:
   одна и та же клавиша роутится в разные actions в зависимости от `currentContext()`. Это фундамент для endpoint E
   (меню «Нажми Esc → открыться / Нажми Esc ␲ меню → закрыться»).

> Полный интеграционный smoke (`GlfwInputBackend` реально регистрирует
> callback'и на GLFW window) требует display server и происходит в обычном
> (не `--headless`) режиме; на VM без дисплея проверяем через synth-events
> (путь `RawInputEvent` → `InputRouter.consume()` битово повторяет путь из GLFW callback).
