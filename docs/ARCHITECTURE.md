# Архитектура Death

> Stage 1 документ. Описывает целевую архитектуру и текущее состояние.

## 1. Слои

```
+-------------------------------------------------------------+
| :desktop      :android       :ios       (UI / launcher)     |  <-- platform layer
| WeTTeA.platform.{desktop,android,ios}                       |
+-------------------------------------------------------------+
| :rust-bridge  (Java JNI side)                               |  <-- native bridge layer
| WeTTeA.native_bridge.rust                                   |
+----+--------------------------------------------------------+
     |  JNI  v
+-------------------------------------------------------------+
| rust-core/  (death-native cdylib)                           |  <-- native core
| physics, ecs, pathfinding, scripting                        |
+-------------------------------------------------------------+

           ^
           |
+-------------------------------------------------------------+
| :core  (gameplay, render abstract, content, tools)          |  <-- domain layer
| WeTTeA.api.*  (контракты)                                   |
| WeTTeA.core.* (реализация ядра — time, loop, scene, ...)    |
| WeTTeA.gameplay/render/ui/...  (домен)                      |
+-------------------------------------------------------------+
```

## 2. Принципы

- **Контракт-first.** Любой межплатформенный обмен идёт только через интерфейсы
  в `WeTTeA.api.*`. Платформенные модули реализуют их, ядро потребляет.
- **Никаких "тихих заглушек".** Каждый класс либо runtime-wired (используется в
  boot-цепочке), либо явно помечен в `PROGRESS.md` как `INTEGRATION_MISSING`.
- **WeTTeA — внутренний namespace, Death — пользовательский бренд.** В коде
  пакетов и классов слово "Death" не появляется (кроме user-facing строк
  типа `applicationName="Death"` в `:desktop` или `<application android:label="Death">`).
- **Service locator.** Boot-цепочка регистрирует ключевые сервисы в
  `ServiceContainer` (`Time`, `EventBus`, `SceneManager`, `GameLoop`).
  Платформенные расширения добавляют свои (`PlatformInfo`, `PlatformFileSystem`,
  будущий `RenderContext`).
- **Fixed-step game loop.** Игровой тик — детерминированный 60 Hz fixed-step;
  рендер — variable rate. См. `WeTTeA.core.loop.FixedStepGameLoop`.
- **Single-process event bus.** `WeTTeA.core.events.SimpleEventBus` —
  синхронный pub/sub в одном процессе. Никакого reflection.

## 3. Boot-цепочка

```
DesktopLauncher.main()
   │
   ├── CoreBootstrap.boot()
   │      ├── new SystemNanoTime
   │      ├── new SimpleEventBus
   │      ├── new ServiceContainer
   │      ├── new SceneManager
   │      ├── new FixedStepGameLoop(time)
   │      └── register(..) каждый в ServiceContainer
   │
   ├── DesktopPlatformInfo.detect()        → PlatformInfo
   ├── new DesktopPlatformFileSystem(info) → PlatformFileSystem
   │
   ├── new GlfwWindow(...).init()/show()    [skip если --headless]
   ├── new VulkanInstanceBootstrap().create() [skip если --no-vulkan]
   │
   ├── boot.loop().start()
   ├── while (!shouldClose): pollEvents + tick
   │
   └── dispose: vulkan, window, loop.stop()
```

Будущая интеграция Rust-ядра (`RustCore.initialize(info, fs)`) встанет между
"Platform setup" и "GLFW window".

## 4. Stage 1 — что есть и чего нет

| Слой | Статус | Что есть |
|------|--------|----------|
| `:core` контракты `api/*` | OK | все интерфейсы с Russian JavaDoc |
| `:core` реализация (time, loop, scene, events, services, boot) | OK | компилируется |
| `:desktop` launcher | OK | GLFW + VkInstance smoke, headless mode |
| `:rust-bridge` Java side | OK | RustNativeLibrary + RustCore facade |
| `rust-core/` | SKELETON | Cargo.toml + JNI-экспорты no-op + пустые модули |
| `:android` | INTEGRATION_MISSING | placeholder + manifest stub |
| `:ios` | INTEGRATION_MISSING | placeholder |
| Render-пайплайн (swapchain, render-passes) | INTEGRATION_MISSING | — |
| Input pump (GLFW callbacks → InputState) | INTEGRATION_MISSING | — |
| Audio (OpenAL) | INTEGRATION_MISSING | LWJGL бандл подключён, кода нет |
| Asset loading | INTEGRATION_MISSING | `openAsset` ищет classpath; контента нет |
| ECS / физика / pathfinding | INTEGRATION_MISSING | rust modules пустые |
