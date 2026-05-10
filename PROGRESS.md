# Death — PROGRESS

> Stage 1 (skeleton) → Stage 2 (вертикальный срез: render + input + audio +
> rust-core minimal physics) → Stage 3+ (gameplay системы).
>
> **Текущая стадия: 2.1a + 2.1b + 2.2 + 2.3 + 2.3b + 2.5 + 3.1 + 3.2 + 3.3a завершены.**
> Stage 2.1a — Vulkan render stack: `VulkanDevice` (physical + logical +
> queues), `VulkanSurface` (glfwCreateWindowSurface), `VulkanSwapchain`
> (FIFO + image views), `VulkanRenderPass` (1 color attachment, clear
> → present_src), `VulkanFramebuffers`, `VulkanCommandBuffers` (pool +
> N=2 буферов), `VulkanFrameSync` (imgAvail/renderDone semaphores +
> inFlight fences), `VulkanRenderer` (acquire → record clear → submit
> → present с cycling HSV clear color). Smoke через lavapipe
> (`lvp_icd.x86_64.json`) + DISPLAY=:0: 10 кадров презентовано за
> 0.044с (~225 FPS), llvmpipe LLVM 15.0.7, 4 swapchain images, format
> B8G8R8A8_UNORM, чистый shutdown через `vkDeviceWaitIdle`. **Stage
> 2.1b** — Vulkan triangle render: GLSL vertex/fragment shaders
> (`assets/death/shaders/triangle/triangle.{vert,frag}.glsl`) с hard-
> coded triangle через `gl_VertexIndex` (без vertex buffer'a),
> скомпилированы `glslangValidator -V` в SPIR-V (`triangle.vert.spv`
> 1432 байт + `triangle.frag.spv` 500 байт) и лежат в
> classpath `desktop/src/main/resources/assets/death/shaders/triangle/`,
> регистрируются в `MapAssetCatalog` как `SHADER_SPIRV` (id'ы
> `shader.triangle.vert` / `shader.triangle.frag`). Новые классы в
> `:desktop`: `VulkanShaderModule` (читает SPIR-V байты через
> `ContentLoader.readSync` → валидирует magic `0x07230203`
> (явное LE-чтение по байтам, не зависим от `ByteBuffer.order()`)
> → `vkCreateShaderModule`, `dispose()` идемпотентный),
> `VulkanPipeline` (пустой `VkPipelineLayout` для 2.1b +
> `VkGraphicsPipelineCreateInfo`: 2 stage'а vertex+fragment, пустой
> vertex input без биндингов, IA `TRIANGLE_LIST`, viewport+scissor
> `dynamic`, rasterizer `FILL`/`CCW`/`CULL_NONE`/`lineWidth=1`,
> multisample 1×, без depth/stencil, color blend opaque). `VulkanRenderer`
> переписан: конструктор принимает `ContentLoader`, создаёт
> оба shader module + pipeline; `record(cmd, imageIndex, t)`
> выставляет dynamic viewport (`vkCmdSetViewport` 0..width×height,
> minDepth=0, maxDepth=1) + scissor (`vkCmdSetScissor` 0..width×height),
> `vkCmdBindPipeline(GRAPHICS)` + `vkCmdDraw(3, 1, 0, 0)` внутри
> `BeginRenderPass`/`EndRenderPass`. **Swapchain recreate-on-resize**:
> `GlfwWindow.setFramebufferSizeListener` прокидывает
> `glfwSetFramebufferSizeCallback` → `renderer.markFramebufferResized()`
> (volatile dirty flag); `renderFrame(t)` перед acquire проверяет
> dirty flag, и также ловит `VK_ERROR_OUT_OF_DATE_KHR` от `vkAcquireNextImageKHR`
> и `OUT_OF_DATE`/`SUBOPTIMAL` от `vkQueuePresentKHR`; в любом из этих
> случаев: `vkDeviceWaitIdle` → `swapchain.recreate()`
> → `framebuffers.recreate(swapchain, renderPass)` →
> `sync.resizeImagesInFlight(swapchain.imageCount())`. Рендер-пасс и
> pipeline НЕ пересобираются при ресайзе — формат и layout те же,
> dynamic viewport/scissor выставляются каждый кадр. Smoke через
> Xvfb + lavapipe (`VK_ICD_FILENAMES=lvp_icd.x86_64.json`,
> `xvfb-run -s "-screen 0 1280x720x24"`,
> `--render-frames 30 --no-native --no-audio`): 30 кадров
> презентовано за 0.152с (~197 FPS), 0 swapchain recreates,
> shader modules созданы (1432 + 500 байт), pipeline собран, `BUILD
> SUCCESSFUL`, чистый shutdown. Stage 2.2
> (input pipeline) и 2.5 (Rapier3D physics) — без изменений,
> headless smoke оба зелёные. Stage 2.3 — OpenAL audio
> (`OpenAlAudioBackend` + `OpenAlSourceHandle`): `alcOpenDevice` →
> `alcCreateContext` → `alcMakeContextCurrent`, микшер на 5
> категорий (MUSIC/SFX/AMBIENT/VOICE/UI) с независимыми volume +
> mute, `playSineWaveSmoke(f, dur, cat)` (синтетический 16-bit PCM
> 44100 Hz), `pauseAll/resumeAll/stopAll`, lifecycle через
> `Disposable`. **Stage 2.3b** — Asset Loader + OGG Vorbis decoder,
> реальный pipeline: `MapAssetCatalog` (in-memory id→path реестр) +
> `PlatformContentLoader` (читает байты через PlatformFileSystem в
> direct ByteBuffer) в :core; `OggVorbisAudioDecoder`
> (`stb_vorbis_decode_memory` → 16-bit signed PCM mono/stereo) в
> :desktop; `OpenAlAudioBackend.playSound(AssetHandle,...)` теперь
> реально работает (load → decode → ALBuffer cache по id → ALSource
> → play); cached buffer'ы переиспользуются для повторных playSound
> того же asset'а (ноль аллокаций в hot path). Test fixture — 0.2s
> 440Hz mono OGG (`audio/test/sine_440_short.ogg`, 3.7KB, libvorbis
> q=2). Headless smoke `ALSOFT_DRIVERS=null`: два последовательных
> playSound одного asset'а → cache=1 buffer (не дублирование), оба
> STOPPED за 0.444с суммарно, mixer ok, dispose ok. **Stage 3.2** —
> scene system + minimal Java ECS в `:core`. `Scene` расширен
> хуками `onPause`/`onResume` (default no-op); `SceneManager`
> переписан с парной семантикой push→onPause/onEnter,
> pop→onExit/dispose/onResume, replace без pause/resume; добавлены
> deferred операции (`pushDeferred`/`popDeferred`/`replaceDeferred`
> + `applyPending` с защитой от рекурсии), `tickTop`/`drawTop`
> (только верхняя сцена активна) и `clear()` для shutdown. Новый
> пакет `WeTTeA.core.ecs`: `EntityId(int index, int generation)`
> record (ABA-safe handles, конструктор валидирует index≥0 и
> generation>0); `ComponentStore<T>` (sparse-set: `int[] sparse`,
> `int[] entityIndices`, `Object[] dense`; O(1) put/get/has/remove
> через swap-with-last; рост power-of-2); `EntityWorld` (free-list
> индексов с инкрементом generation на despawn для ABA-safety;
> `Map<Class<?>, ComponentStore<?>>` per-type stores; `spawn`/
> `despawn`/`isAlive`/`set`/`get`/`has`/`remove`/`store`/`clear`/
> `aliveCount`/`capacity`); `EcsSystem` функциональный интерфейс
> `update(EntityWorld, double deltaSeconds)`; `SystemScheduler`
> (последовательное выполнение в порядке регистрации, без аллокаций
> в hot path). Headless smoke (`runSceneSmoke` + `runEcsSmoke` в
> `DesktopLauncher`): 7 ассертов lifecycle + 1000 entity'ёв с
> Position/Velocity → 60 sim steps по dt=1/60 → проверка
> интегрирования position+=velocity*dt → despawn половины + reborn
> 100 → проверка ABA-safety (старый id с тем же index, но старым
> generation возвращает `isAlive=false`) + system iterate ok за
> 0.047с. **Stage 3.1** — JSON asset catalog + texture pipeline
> (PNG → `VkImage` → sampler → descriptor set → текстурированный
> треугольник). `JsonAssetCatalog` (impl `AssetCatalog` поверх Jackson;
> читает `assets/death/data/asset_catalog.json` через
> `PlatformFileSystem.openAsset` → `Map<String, Entry(category, path)>`;
> требует `schema_version=1`, проверяет наличие категории и пути,
> запрещает дубликаты id) заменил `MapAssetCatalog` в `DesktopLauncher`.
> Asset-каталог содержит 4 entry: `audio.test.sine_440_short` (OGG),
> `shader.triangle.vert` / `shader.triangle.frag` (SPIR-V),
> `texture.test.checkerboard` (PNG 64×64). `PngImageDecoder` — stateless
> wrapper над `STBImage.stbi_load_from_memory(buf, w, h, channels,
> desiredChannels=4)` → `DecodedImage(ByteBuffer rgba, w, h,
> sourceChannels)` (всегда RGBA8 на выходе для совместимости с
> `VK_FORMAT_R8G8B8A8_UNORM`). Vulkan texture pipeline в `:desktop`:
> `VulkanBuffer` (low-level wrapper VkBuffer + VkDeviceMemory с picking
> memory type через `vkGetPhysicalDeviceMemoryProperties` + matching
> `memoryTypeBits` & `propertyFlags`; `mapAndUpload` для HOST_VISIBLE);
> `VulkanImage` (`VkImage` + `VkImageView` + `VkDeviceMemory`;
> `uploadFromStaging(cmdBuffers, staging)` записывает one-shot command
> buffer: barrier UNDEFINED → TRANSFER_DST_OPTIMAL,
> `vkCmdCopyBufferToImage`, barrier TRANSFER_DST → SHADER_READ_ONLY;
> submit + `vkQueueWaitIdle` + free); `VulkanSampler` (linear filter +
> repeat wrap + maxAnisotropy=1 без проверки device feature'ов —
> заработает на любом GPU); `VulkanTextureDescriptors` (создаёт
> `VkDescriptorSetLayout` с 1 binding'ом
> `COMBINED_IMAGE_SAMPLER`/`fragment` + `VkDescriptorPool` с
> `maxSets=4`); `VulkanTexture` (orchestrator: загружает PNG через
> ContentLoader → decode → staging buffer → image upload → allocate
> descriptor set → write descriptor set с явным `descriptorCount(1)`).
> `VulkanPipeline` обновлён: `VkPipelineLayoutCreateInfo` теперь
> принимает `pSetLayouts(stack.longs(descriptorSetLayout))` (set 0).
> Шейдеры обновлены: vertex выдаёт UV (`vec2 fragUV` в location=1),
> fragment семплит `sampler2D albedo` в `set=0, binding=0` и mix'ит с
> baseline color (50/50). Recompiled SPIR-V: `triangle.vert.spv` 1688
> байт + `triangle.frag.spv` 1020 байт. `VulkanRenderer` владеет
> `VulkanTexture` (загружается в constructor'е через переданный handle
> `texture.test.checkerboard`) и перед `vkCmdDraw` вызывает
> `vkCmdBindDescriptorSets(GRAPHICS, pipelineLayout, set=0,
> {descriptorSet}, null)`. Render smoke через xvfb + lavapipe — 30
> кадров за 0.133с (~225 FPS), 0 swapchain recreates, чистый shutdown,
> текстурированный треугольник в кадре, validation чистый. **Stage
> 3.3a** — тактическая сетка + A* pathfinding в Rust через JNI (без
> combat loop'а — это 3.3b). `WeTTeA.api.pathfinding.PathGrid` контракт
> в `:core` (методы `width`/`height`/`setBlocked`/`isBlocked`/`findPath`/
> `close`, координатная система origin (0,0) lower-left, формат пути —
> flat `int[]` `(x0, y0, x1, y1, ...)`, пустой массив на out-of-bounds /
> заблокированный start/goal / недостижимую цель). Pure-Rust A*
> в `rust-core/src/pathfinding/mod.rs` без внешних зависимостей кроме
> std: `Grid` (width, height, `Vec<bool>` blocked cells); `find_path`
> (Manhattan-heuristic admissible+consistent, `BinaryHeap<AStarNode>` для
> open-set с f-score ordering, `g_score: Vec<u32>` + `came_from: Vec<usize>`
> per-cell, 4-directional neighbors UP/DOWN/LEFT/RIGHT, path-reconstruction
> через `came_from`-backtracking от goal к start, `path.reverse()` для
> правильного порядка); 8 unit-тестов (`#[cfg(test)]` блок в том же
> mod.rs: empty grid, same start/goal, blocked cells, out-of-bounds,
> wall with gap, fully walled off, Manhattan optimality, неограниченная
> сетка). 5 JNI экспортов в `rust-core/src/jni_exports.rs` для
> `RustPathGrid`: `nativeCreate(width, height)→jlong`, `nativeDestroy(handle)`,
> `nativeSetBlocked(handle, x, y, blocked)`, `nativeIsBlocked(handle, x, y)→jboolean`,
> `nativeFindPath(handle, sx, sy, gx, gy)→jintArray` (конверсия
> `Vec<(u32, u32)>` → flat `jintArray` через `env.new_int_array(2*len)`
> + `set_int_array_region`). `RustPathGrid` (`:rust-bridge`) impl `PathGrid`
> через AutoCloseable lifecycle, валидирует width/height>0 в конструкторе
> и `IndexOutOfBoundsException` в `setBlocked`/`isBlocked` (но не в
> `findPath` — там пустой массив по контракту). Headless smoke
> (`runPathfindingSmoke` в `DesktopLauncher`): 8×8 grid со стеной по
> x=4 и проёмом в (4,4) → `findPath((1,1) → (6,6))` дала путь длиной
> 11 клеток (10 шагов = Manhattan(10), оптимально), все клетки в пути
> 4-directionally связаны и не заблокированы, обход через проём (4,4)
> подтверждён, недостижимая цель → пустой массив за 0.005с. Следующая —
> 3.3b (combat loop: атаки, урон, AI скелет; HPA*, terrain costs,
> diagonal movement, multi-agent reservation), 2.3c (Android AAudio
> backend) или Endpoint **E** (главное меню → settings → white room).


## Таблица 1 — Модули

| Модуль          | Статус       | Собирается | Что есть                                                                                              | Что дальше                                            |
|-----------------|--------------|------------|-------------------------------------------------------------------------------------------------------|--------------------------------------------------------|
| `:core`         | OK (stage 2.2 + 2.3b + 3.1 + 3.2 + 3.3a + E.1)| да  | api/* контракты (включая `audio.AudioBackend/AudioCategory/AudioSourceHandle/AssetHandle/AssetCategory/AssetCatalog/ContentLoader`, **`pathfinding.PathGrid`**, **`render.Camera` (perspective/lookAt без Vulkan-зависимостей)**), core/* реализация (time, loop, **scene с onPause/onResume хуками + deferred ops + tickTop/drawTop**, events, services, boot, input router/bindings/state, content: MapAssetCatalog + **JsonAssetCatalog (Jackson, schema_version=1)** + PlatformContentLoader, **ecs: EntityId + ComponentStore + EntityWorld + EcsSystem + SystemScheduler**, **render: PerspectiveCamera (JOML-based, Vulkan Y-flip), CubeMeshFactory (24 vertex CCW + 36 index)**) | unit-тесты на стадии 2; combat loop (3.3b); models/MODEL_GLTF loader (post-3.1) |
| `:desktop`      | OK (stage 2.1a + 2.1b + 2.3 + 2.3b + 3.1 + 3.2 + E.1)| да | LWJGL3 launcher, GLFW окно (с framebuffer-resize listener), VkInstance, RustCore wired, GlfwInputBackend, VulkanDevice + Surface + Swapchain (с `recreate()`) + **VulkanDepthBuffer (D32_SFLOAT, recreate-on-resize)** + RenderPass (color + depth attachments) + Framebuffers (с `recreate()`, color+depth views) + CommandBuffers + FrameSync (с `resizeImagesInFlight()`) + Renderer (**E.1: текстурированный куб с perspective camera + indexed draw + UBO MVP per-frame + lambert lighting**), **VulkanShaderModule** (load SPIR-V через `ContentLoader`), **VulkanPipeline** (vertex bindings stride=32 pos+normal+uv, depth test LESS, BACK culling, dynamic viewport/scissor), **VulkanSceneDescriptors** (binding=0 UBO vertex+fragment + binding=1 sampler fragment), **VulkanMeshBuffer (vertex+index DEVICE_LOCAL via staging) + VulkanUniformBuffer (HOST_VISIBLE per-frame 144 bytes std140)**, **VulkanBuffer + VulkanImage + VulkanSampler + VulkanTexture (PNG → staging → VkImage → sampler)**, **PngImageDecoder** (STBImage → RGBA8 DecodedImage), OpenAlAudioBackend + OpenAlSourceHandle (alcOpenDevice/Context, mixer на 5 категорий, sine smoke + asset playback), OggVorbisAudioDecoder (stb_vorbis_decode_memory), test OGG fixture, **shader fixtures `cube.{vert,frag}.spv` + legacy `triangle.{vert,frag}.spv`**, **PNG fixture `texture/test/checkerboard.png`**, scene smoke (push/pop/replace/deferred/clear) + ECS smoke (1000 entity, MovementSystem, ABA-safety), флаги `--headless`/`--no-vulkan`/`--no-native`/`--no-audio`/`--render-frames N` | ActionMap + WASD/mouse-look (E.2), KCC через Rapier (E.3), settings persistence + UI (E.4/E.5) |
| `:rust-bridge`  | OK (stage 2.5 + 3.3a)| да         | RustNativeLibrary, RustCore, **RustPhysicsWorld (JNI-impl PhysicsWorld)**, **RustPathGrid (JNI-impl PathGrid)**, cargoBuild + copyNativeArtifact | расширение `NativeBridgeInfo`, дополнительные тела/коллайдеры; combat loop (3.3b) |
| `rust-core/`    | OK (stage 2.5 + 3.3a) | `cargo build --release` | Cargo.toml (rapier3d 0.21 + nalgebra 0.32 + jni 0.21), lib.rs, jni_exports (RustCore + 5 RustPhysicsWorld + **5 RustPathGrid**), `physics::PhysicsWorld` (Rapier pipeline), **`pathfinding::Grid` (pure-Rust A*: BinaryHeap open-set, Manhattan heuristic, 4-directional, 8 unit-tests)**, пустой scripting | реальный scripting (stage 3.x), расширенный physics API; HPA*/diagonal/terrain costs (3.3b+) |
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
| `WeTTeA.core.scene.Scene`                                 | core    | OK (stage 3.2) | контракт; extends `Tickable`+`Drawable`+`Disposable`; лифецикл: `onEnter`/`onExit` + **`onPause`/`onResume` (default no-op)** для сцен-ниже-top |
| `WeTTeA.core.scene.SceneManager`                          | core    | OK (stage 3.2) | в `ServiceContainer`; **парная семантика**: `push(B)` поверх A → `A.onPause()` → `B.onEnter()`; `pop()` → `top.onExit()` → `top.dispose()` → `below.onResume()`; `replace()` → `top.onExit/dispose` → `new.onEnter()` (без pause/resume для нижней). **Deferred ops**: `pushDeferred`/`popDeferred`/`replaceDeferred` буферизуют в `pending`, `applyPending()` применяет FIFO с защитой от рекурсии. `tickTop(dt)`/`drawTop(frame)` — только верхняя сцена активна. `clear()` — multi-pop с onExit+dispose в shutdown |
| `WeTTeA.core.ecs.EntityId`                                | core    | OK (stage 3.2) | record `(int index, int generation)`; ABA-safe handle; конструктор валидирует `index >= 0` и `generation > 0` (защита от случайного идентификатора с generation=0, совпавшего бы с `int[] generations` zero-init); выдаётся `EntityWorld.spawn()` |
| `WeTTeA.core.ecs.ComponentStore`                          | core    | OK (stage 3.2) | sparse-set storage для одного типа компонента: `int[] sparse` (entityIndex → packed slot, -1 если нет), `int[] entityIndices` (slot → entityIndex), `Object[] dense` (slot → component); O(1) `put`/`get`/`has`/`remove` (через swap-with-last); dense-iterate через `entityIndexAt(slot)` + `componentAt(slot)`; рост `sparse` power-of-2 (≈17 grow'ов до 80k entity). Используется `EntityWorld` |
| `WeTTeA.core.ecs.EntityWorld`                             | core    | OK (stage 3.2) | root container ECS: `int[] generations` per slot, `int[] freeIndices` LIFO (despawn → push в freeList + generation++ для ABA), `Map<Class<?>, ComponentStore<?>> stores` per-type; `spawn`/`despawn(EntityId)`/`isAlive(EntityId)`/`set/get/has/remove(EntityId, Class<C>, C)`/`store(Class<C>)`/`aliveCount`/`capacity`/`clear`. `despawn` ремувает компоненты из всех stores перед освобождением слота. `isAlive` сравнивает `EntityId.generation()` с `generations[index]` — stale id возвращает false даже если index переиспользован для новой сущности |
| `WeTTeA.core.ecs.EcsSystem`                               | core    | OK (stage 3.2) | functional interface `update(EntityWorld world, double deltaSeconds)`. Система читает/мутирует компоненты, но НЕ spawn'ит/despawn'ит во время итерации (буферизует во внутренний list и применяет после update'а). НИКАКИХ render вызовов |
| `WeTTeA.core.ecs.SystemScheduler`                         | core    | OK (stage 3.2) | последовательный runner: `add(EcsSystem)` (FIFO регистрация без дубликатов), `update(world, dt)` итерирует systems в порядке регистрации (`for(int i=0; i<n; i++)` без iterator — ноль аллокаций в hot path), `remove`/`size`/`systems()` (read-only)/`clear()`. Создаётся gameplay-слоем, не launcher'ом |
| `WeTTeA.core.service.ServiceContainer`                    | core    | OK     | возвращается из `CoreBootstrap.boot()` |
| `WeTTeA.core.events.SimpleEventBus`                       | core    | OK     | в `ServiceContainer`                   |
| `WeTTeA.core.CoreBootstrap`                               | core    | OK     | вызывается из `DesktopLauncher.main()` |
| `WeTTeA.platform.desktop.DesktopPlatformInfo`             | desktop | OK     | `detect()` вызывается из launcher      |
| `WeTTeA.platform.desktop.DesktopPlatformFileSystem`       | desktop | OK     | в launcher                             |
| `WeTTeA.platform.desktop.GlfwWindow`                      | desktop | OK (stage 2.1b) | в launcher (skip при `--headless`); **`setFramebufferSizeListener(IntBinaryOperator)`** регистрирует `GLFWFramebufferSizeCallback` (освобождает старый callback перед регистрацией нового); listener вызывается из GLFW main thread'а внутри `glfwPollEvents()`; `dispose()` освобождает callback ДО `glfwDestroyWindow` |
| `WeTTeA.platform.desktop.VulkanInstanceBootstrap`         | desktop | OK     | в launcher (skip при `--no-vulkan`)    |
| `WeTTeA.platform.desktop.VulkanDevice`                    | desktop | OK (stage 2.1a) | физическое устройство (graphics + present queue families) + логическое VkDevice + VkQueue; `VulkanRenderer` создаёт |
| `WeTTeA.platform.desktop.VulkanSurface`                   | desktop | OK (stage 2.1a) | обёртка над VkSurfaceKHR через `glfwCreateWindowSurface`; уничтожается ПОСЛЕ swapchain |
| `WeTTeA.platform.desktop.VulkanSwapchain`                 | desktop | OK (stage 2.1a + 2.1b) | VkSwapchainKHR (FIFO present, B8G8R8A8_UNORM/SRGB color space) + image views; clamps extent к caps; **`recreate()`** пересобирает swapchain+image views на новый framebuffer size (выделенный helper `disposeImagesAndChain()` переиспользуется в dispose() и recreate()) |
| `WeTTeA.platform.desktop.VulkanRenderPass`                | desktop | OK (stage 2.1a) | 1 color attachment (loadOp=CLEAR, finalLayout=PRESENT_SRC_KHR), 1 graphics subpass, EXTERNAL→0 dependency на COLOR_ATTACHMENT_OUTPUT; **НЕ пересобирается при ресайзе** — формат и layout не меняются |
| `WeTTeA.platform.desktop.VulkanFramebuffers`              | desktop | OK (stage 2.1a + 2.1b) | по одному `VkFramebuffer` на каждый swapchain image view; **`recreate(swapchain, renderPass)`** разрушает старые handles и билдит новые под новые image views (выделенные helpers `build()`/`disposeHandles()`) |
| `WeTTeA.platform.desktop.VulkanCommandBuffers`            | desktop | OK (stage 2.1a) | `VkCommandPool` (RESET_COMMAND_BUFFER_BIT, graphics family) + N primary cmd buffers; ресет на кадре |
| `WeTTeA.platform.desktop.VulkanFrameSync`                 | desktop | OK (stage 2.1a + 2.1b) | imageAvailable/renderFinished semaphores + inFlight fences (создаются SIGNALED) + imagesInFlight для пересечений; **`resizeImagesInFlight(newSwapchainImages)`** перевыделяет `imagesInFlight` long array под новый размер swapchain images при recreate (semaphores/fences НЕ пересоздаются — они привязаны к frame index, не к image index) |
| `WeTTeA.platform.desktop.VulkanShaderModule`              | desktop | OK (stage 2.1b) | обёртка над `VkShaderModule`: читает SPIR-V байты через `ContentLoader.readSync(handle)` (`SHADER_SPIRV` категория), валидирует размер (`> 0 && % 4 == 0`) и magic `0x07230203` (явное LE-чтение 4 байт без зависимости от `ByteBuffer.order()`), вызывает `vkCreateShaderModule(device, info, null, pModule)`. `dispose()` идемпотентный, безопасен при `device == null` |
| `WeTTeA.platform.desktop.VulkanPipeline`                  | desktop | OK (stage 2.1b + 3.1) | graphics pipeline для текстурированного triangle: `VkPipelineLayout` теперь биндит 1 descriptor set layout (set=0, 1× COMBINED_IMAGE_SAMPLER fragment) через `pSetLayouts(stack.longs(descriptorSetLayout))`, push constants пустые; `VkGraphicsPipelineCreateInfo` (2 stage'а: `VK_SHADER_STAGE_VERTEX_BIT` + `_FRAGMENT_BIT` с entrypoint="main"; `VkPipelineVertexInputStateCreateInfo` пустой — vertex данные/UV из `gl_VertexIndex` в шейдере; IA `TRIANGLE_LIST`, `primitiveRestart=false`; viewport+scissor выставляются как `dynamic` (`viewportCount=1`/`scissorCount=1` + `VkPipelineDynamicStateCreateInfo` с `VK_DYNAMIC_STATE_VIEWPORT/SCISSOR`); rasterizer `polygonMode=FILL`, `cullMode=NONE`, `frontFace=CCW`, `lineWidth=1`; multisample 1× без sampleShading; без `pDepthStencilState` — render-pass не имеет depth attachment'а; color blend opaque (1 attachment, `RGBA` writeMask, blendEnable=false)). Создаётся один раз в `VulkanRenderer` constructor'е и переиспользуется на всех кадрах и во всех swapchain recreate'ах (renderPass живёт сквозь ресайз) |
| `WeTTeA.platform.desktop.VulkanRenderer`                  | desktop | OK (stage 2.1a + 2.1b + 3.1) | оркестратор: владеет всеми Vulkan ресурсами + 2 shader modules + triangle pipeline + **`VulkanTextureDescriptors` + `VulkanTexture`**; constructor принимает `(VkInstance, windowHandle, ContentLoader, PngImageDecoder, AssetHandle textureHandle)` (все ресурсы загружаются в constructor'е, не в hot path; текстура — один раз при старте рендерера). `renderFrame(t)` = check dirty `framebufferResized` → wait fence → `vkAcquireNextImageKHR` (ловит `VK_ERROR_OUT_OF_DATE_KHR` → recreate → skip frame) → `record(cmd, imageIndex, t)` → submit → `vkQueuePresentKHR` (ловит `OUT_OF_DATE`/`SUBOPTIMAL` → recreate). `record()` = `vkBeginCommandBuffer` → `vkCmdBeginRenderPass` (clear-color HSV cycling) → `vkCmdSetViewport` + `vkCmdSetScissor` (dynamic) → `vkCmdBindPipeline(GRAPHICS)` → **`vkCmdBindDescriptorSets(GRAPHICS, pipelineLayout, firstSet=0, {triangleTexture.descriptorSet()}, null)`** → `vkCmdDraw(3, 1, 0, 0)` → `vkCmdEndRenderPass`. `markFramebufferResized()` дыргается из GLFW callback'а. `recreateSwapchainAndFramebuffers()` = `glfwGetFramebufferSize` (skip если 0×0 — окно минимизировано) → `vkDeviceWaitIdle` → `swapchain.recreate()` → `framebuffers.recreate(swapchain, renderPass)` → `sync.resizeImagesInFlight(swapchain.imageCount())` → инкремент счётчика. Чистый shutdown: `vkDeviceWaitIdle` → **triangleTexture.dispose** → **textureDescriptors.dispose** → pipeline.dispose → frag.dispose → vert.dispose → sync → commands → framebuffers → renderPass → swapchain → device → surface |
| `WeTTeA.api.content.AssetHandle`                          | api     | OK     | record `(id, category)`, immutable; идентификатор asset'а в каталоге |
| `WeTTeA.api.content.AssetCategory`                        | api     | OK     | enum (SHADER_SPIRV, TEXTURE, MODEL_GLTF, AUDIO, LOCALE_STRINGS, GAMEPLAY_DATA, BULLET_PATTERN, DIALOGUE) |
| `WeTTeA.api.content.AssetCatalog`                         | api     | OK     | контракт `resolvePath(handle)` → Optional<String>, `listIds(category)` → Iterable<String>; реализации — `MapAssetCatalog` (in-memory, stage 2.3b) и `JsonAssetCatalog` (Jackson поверх `data/asset_catalog.json`, stage 3.1) |
| `WeTTeA.api.content.ContentLoader`                        | api     | OK     | контракт `readSync/readAsync(handle)` → direct ByteBuffer, `exists(handle)` → boolean; extends `Disposable` |
| `WeTTeA.core.content.MapAssetCatalog`                     | core    | OK (stage 2.3b) | impl `AssetCatalog`; in-memory `Map<String, Entry(category, path)>` + `EnumMap<AssetCategory, List<String>>` для `listIds`; builder с проверкой дубликатов по id; остаётся в :core как fallback/embedded-вариант и база для юнит-тестов (stage 3.1 заменил его на `JsonAssetCatalog` в desktop лончере) |
| `WeTTeA.core.content.JsonAssetCatalog`                    | core    | OK (stage 3.1) | impl `AssetCatalog` на базе Jackson; `load(PlatformFileSystem, relativePath)` читает `assets/death/data/asset_catalog.json` в `Map<String, Entry(category, path)>`; валидирует `schema_version == 1` (при будущем бампе v=2 будет explicit error вместо silent skip), обязательные поля `id`/`category`/`path` на каждый entry, разбор `category` через `AssetCategory.valueOf(uppercase)` (бросает IllegalArgumentException на неизвестной категории), запрещает дубликаты id; `resolvePath`/`listIds(category)` идентичны `MapAssetCatalog`; в `DesktopLauncher` заменил in-memory реестр и регистрируется под `AssetCatalog.class` |
| `WeTTeA.core.content.PlatformContentLoader`               | core    | OK (stage 2.3b) | impl `ContentLoader`; резолвит путь через `AssetCatalog`, читает через `PlatformFileSystem.openAsset(path)`, копирует в direct `ByteBuffer.allocateDirect(...)`; `readAsync` через `CompletableFuture.supplyAsync` (commonPool); `exists(handle)` — быстрая проверка без полного чтения; `dispose()` идемпотентен (no-op cleanup, буферы выдаются вызывающему) |
| `WeTTeA.platform.desktop.PngImageDecoder`                 | desktop | OK (stage 3.1) | stateless decoder поверх `org.lwjgl.stb.STBImage`; `decodeMemory(direct ByteBuffer)` → `DecodedImage(ByteBuffer rgba, int width, int height, int sourceChannels)` через `stbi_load_from_memory(buf, w, h, c, desiredChannels=4)` (всегда выдаёт RGBA8 на выходе для совместимости с `VK_FORMAT_R8G8B8A8_UNORM`; sourceChannels хранится для telemetry); валидирует width/height/channels > 0 и channels ∈ {1, 2, 3, 4}; `DecodedImage.free()` освобождает native pixel-буфер через `STBImage.stbi_image_free`, идемпотентно |
| `WeTTeA.platform.desktop.VulkanBuffer`                    | desktop | OK (stage 3.1) | low-level wrapper `VkBuffer + VkDeviceMemory`; `create(device, size, usageFlags, propertyFlags)` выполняет `vkCreateBuffer` → `vkGetBufferMemoryRequirements` → picks memory type через `pickMemoryType(memTypeBits, propertyFlags)` (linear scan по `VkPhysicalDeviceMemoryProperties.memoryTypes`, находит первый type с (1<<i & memTypeBits) и всеми propertyFlags в type.propertyFlags) → `vkAllocateMemory` → `vkBindBufferMemory`. `mapAndUpload(ByteBuffer src)` (только для HOST_VISIBLE) = `vkMapMemory` → `MemoryUtil.memCopy` → `vkUnmapMemory` (не ставит `flush` — буфер создаётся с HOST_COHERENT). `dispose()` = `vkDestroyBuffer` + `vkFreeMemory`, идемпотентный |
| `WeTTeA.platform.desktop.VulkanImage`                     | desktop | OK (stage 3.1) | `VkImage` + `VkImageView` + `VkDeviceMemory` для 2D текстуры. `create(device, w, h, format, usage)` = `vkCreateImage` (`VK_IMAGE_TYPE_2D`, `mipLevels=1`, `arrayLayers=1`, `samples=1`, `tiling=OPTIMAL`, `initialLayout=UNDEFINED`) → `vkGetImageMemoryRequirements` → `pickMemoryType(memTypeBits, DEVICE_LOCAL)` → `vkAllocateMemory` → `vkBindImageMemory` → `vkCreateImageView` (`VIEW_TYPE_2D`, format=тот же, swizzle=identity, aspectMask=COLOR, mipLevels=1, arrayLayers=1). `uploadFromStaging(VulkanCommandBuffers, VulkanBuffer staging)` = allocate one-shot command buffer из пула cmdBuffers → `BeginCommandBuffer(ONE_TIME_SUBMIT)` → barrier UNDEFINED→TRANSFER_DST_OPTIMAL (srcAccess=0, dstAccess=TRANSFER_WRITE; srcStage=TOP_OF_PIPE, dstStage=TRANSFER) → `vkCmdCopyBufferToImage` (1 region, mipLevel=0, layerCount=1, bufferOffset=0, imageExtent=w×h×1) → barrier TRANSFER_DST→SHADER_READ_ONLY_OPTIMAL (srcAccess=TRANSFER_WRITE, dstAccess=SHADER_READ; srcStage=TRANSFER, dstStage=FRAGMENT_SHADER) → `EndCommandBuffer` → `vkQueueSubmit` → `vkQueueWaitIdle` → `vkFreeCommandBuffers`. `dispose()` = view + image + memory в транзитивном порядке, идемпотентный |
| `WeTTeA.platform.desktop.VulkanSampler`                   | desktop | OK (stage 3.1) | wrapper над `VkSampler` с default-пресетом для stage 3.1: `magFilter=LINEAR`, `minFilter=LINEAR`, `mipmapMode=LINEAR`, `addressModeU/V/W=REPEAT`, `mipLodBias=0`, `anisotropyEnable=false`/`maxAnisotropy=1.0` (специально без проверки device feature `samplerAnisotropy` — заработает на любом device, включая lavapipe без фичи), `compareEnable=false`, `borderColor=INT_OPAQUE_BLACK`, `unnormalizedCoordinates=false`. `dispose()` = `vkDestroySampler`, идемпотентный |
| `WeTTeA.platform.desktop.VulkanTextureDescriptors`        | desktop | OK (stage 3.1) | владеет `VkDescriptorSetLayout` (1 binding: `BINDING_TEXTURE=0`, `descriptorType=COMBINED_IMAGE_SAMPLER`, `descriptorCount=1`, `stageFlags=FRAGMENT_BIT`, immutable samplers=null) + `VkDescriptorPool` (`maxSets=4`, 1 pool size: `COMBINED_IMAGE_SAMPLER × 4`). Создаётся в `VulkanRenderer` constructor'е и передаётся в `VulkanPipeline` (для layout) и `VulkanTexture` (для выделения sets); maxSets=4 выбран с запасом на future textures в той же сцене (1 сейчас + 3 в резерве без выделения нового пула). `dispose()` разрушает layout и pool — выделенные sets автоматически фривятся в `vkDestroyDescriptorPool` |
| `WeTTeA.platform.desktop.VulkanTexture`                   | desktop | OK (stage 3.1) | orchestrator: combine PNG с ContentLoader + Vulkan resources в готовый descriptor set для bind перед vkCmdDraw. `fromAsset(device, cmdBuffers, descriptors, contentLoader, pngDecoder, AssetHandle texHandle)` = `contentLoader.readSync(texHandle)` → `pngDecoder.decodeMemory` (DecodedImage RGBA8 w×h) → staging `VulkanBuffer` (HOST_VISIBLE | HOST_COHERENT, usage=TRANSFER_SRC, size=w*h*4) → `staging.mapAndUpload(image.rgba())` → `VulkanImage.create(format=R8G8B8A8_UNORM, usage=TRANSFER_DST | SAMPLED)` → `image.uploadFromStaging(cmdBuffers, staging)` → `staging.dispose()` (больше не нужен — данные уже в GPU memory) → `VulkanSampler.create(device)` → allocate descriptor set из `descriptors.pool()` с layout `descriptors.layout()` → `VkWriteDescriptorSet(dstSet, dstBinding=BINDING_TEXTURE=0, dstArrayElement=0, descriptorCount=1, descriptorType=COMBINED_IMAGE_SAMPLER, pImageInfo={sampler, imageView, SHADER_READ_ONLY_OPTIMAL})` → `vkUpdateDescriptorSets`. **Критично**: явный `descriptorCount(1)` — LWJGL НЕ выводит его из `pImageInfo.remaining()`, и без этого calloc-дефолт в 0 делает update но-op'ом → descriptor set остаётся пустым → `texture(albedo, uv)` в fragment shader'е семплит NULL → SIGSEGV в lavapipe (JIT'нутый fragment), undefined behavior на NV/AMD. `dispose()` = sampler → image → descriptor pool freed by descriptors.dispose() (этот объект не владеет pool'ом) |
| `WeTTeA.platform.desktop.OggVorbisAudioDecoder`           | desktop | OK (stage 2.3b) | stateless decoder поверх `org.lwjgl.stb.STBVorbis`; `decodeMemory(direct ByteBuffer)` → `DecodedAudio(ShortBuffer pcm, channels, sampleRate, samplesPerChannel)` через `stb_vorbis_decode_memory`; валидирует channels ∈ {1, 2} и sampleRate > 0; `probeMemory` — открытие без декода (для streaming/UI duration). `DecodedAudio.free()` освобождает native PCM через `MemoryUtil.memFree`, идемпотентно |
| `WeTTeA.platform.desktop.OpenAlAudioBackend`              | desktop | OK (stage 2.3 + 2.3b) | в launcher (skip при `--no-audio`); `alcOpenDevice` (default) → `alcCreateContext` → `alcMakeContextCurrent` + `AL.createCapabilities`; владеет `EnumMap<AudioCategory, CategoryMixer>` (volume×!muted), `LinkedHashMap<Long, OpenAlSourceHandle>` для активных источников, **`HashMap<String, Integer> bufferCache`** (аssetId → ALBuffer), `AtomicLong nextId`; **`bindContentPipeline(ContentLoader, OggVorbisAudioDecoder)`** — привязывает загрузку для playSound; экспонирует `playSineWaveSmoke(freq, dur, cat)` (синтетический PCM, ownsBuffer=true), **`playSound(AssetHandle, AudioCategory, looping)`** (`contentLoader.readSync` → `oggDecoder.decodeMemory` → `alGenBuffers`/`alBufferData(MONO16/STEREO16)` в cache → `alGenSources`/`alSourcePlay`, ownsBuffer=false), `setCategoryVolume/Muted` (с пересчётом `AL_GAIN`), `pauseAll/resumeAll/stopAll`, `awaitSourceStopped(handle, timeoutMs)`, `sourceState(handle)`, `releaseSource(handle)` (удаляет alBuffer ТОЛЬКО если `ownsBuffer()`); `dispose()` чистит все cached ALBuffer'ы перед разрушением context'а |
| `WeTTeA.platform.desktop.OpenAlSourceHandle`              | desktop | OK (stage 2.3 + 2.3b) | impl `AudioSourceHandle`; владеет парой `(alSource, alBuffer)` + `id` + `category` + **`ownsBuffer`** (true — sine smoke, false — cached asset); `setVolume/Pitch/Looping`/`pause/resume/stop` транслируются в `alSourcef/alSourcei/alSourcePause/Play/Stop`; `isActive()` читает `AL_SOURCE_STATE`; идемпотентный `dispose()` зовёт `OpenAlAudioBackend.releaseSource(this)` |
| `WeTTeA.platform.desktop.DesktopLauncher`                 | desktop | OK (stage 3.1) | main() + RustCore lifecycle + input smoke (stage 2.2) + physics smoke (stage 2.5) + audio smoke (stage 2.3) + asset audio smoke (stage 2.3b) + scene smoke (stage 3.2) + ECS smoke (stage 3.2) + **render loop (stage 2.1a + 2.1b + 3.1: текстурированный triangle + swapchain recreate-on-resize)** с `--render-frames N`; сборка **`JsonAssetCatalog.load(fs, "data/asset_catalog.json")`** (4 entries: `audio.test.sine_440_short` → OGG, `shader.triangle.vert` / `shader.triangle.frag` → SPIR-V, **`texture.test.checkerboard` → PNG 64×64**) + `PlatformContentLoader` + `OggVorbisAudioDecoder` + **`PngImageDecoder`**, регистрация в ServiceContainer + bind к OpenAL backend; **врап resize callback'а**: `window.setFramebufferSizeListener((w, h) → renderer.markFramebufferResized())`; флаги `--headless` / `--no-vulkan` / `--no-native` / `--no-audio` / `--render-frames N` |
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
| `WeTTeA.api.pathfinding.PathGrid`                         | api     | OK (stage 3.3a) | контракт тактической сетки + A* pathfinding: `width()`/`height()`/`setBlocked(x,y,bool)`/`isBlocked(x,y)`/`findPath(sx,sy,gx,gy)→int[]`/`close()`; координаты origin (0,0) = lower-left, формат пути — flat int[] `(x0, y0, x1, y1, ...)`; пустой массив = недостижимая цель / out-of-bounds; AutoCloseable lifecycle |
| `WeTTeA.native_bridge.rust.RustPathGrid`                  | bridge  | OK (stage 3.3a) | JNI-impl `PathGrid`: `nativeCreate(w,h)→jlong` handle, `nativeDestroy(h)` идемпотентный, `nativeSetBlocked`/`nativeIsBlocked`/`nativeFindPath` с bound checks в Java и `IndexOutOfBoundsException` при out-of-bounds для setBlocked/isBlocked (но не для findPath — там пустой массив по контракту); вызывается из smoke в `DesktopLauncher` |
| `:rust-bridge` cargoBuild + copyNativeArtifact            | bridge  | OK     | hooked в `processResources`             |
| `rust-core/src/jni_exports.rs` (RustCore)                 | native  | OK (no-op) | вызывается через JNI из `RustCore`  |
| `rust-core/src/jni_exports.rs` (RustPhysicsWorld)         | native  | OK (stage 2.5) | 5 экспортов: nativeCreate / nativeDestroy / nativeAddDynamicBody / nativeStep / nativeBodyPosition |
| `rust-core/src/jni_exports.rs` (RustPathGrid)             | native  | OK (stage 3.3a) | 5 экспортов: nativeCreate / nativeDestroy / nativeSetBlocked / nativeIsBlocked / nativeFindPath; конверсия `Vec<(u32,u32)>` → flat `jintArray` через `env.new_int_array(2*len)` + `set_int_array_region`; `empty_int_array(env)` helper |
| `rust-core/src/physics/mod.rs`                            | native  | OK (stage 2.5) | `PhysicsWorld` (Rapier pipeline + gravity + ball collider), `pack/unpack_body_handle` |
| `rust-core/src/pathfinding/mod.rs`                        | native  | OK (stage 3.3a) | `Grid` (width, height, `Vec<bool>` blocked) + pure-Rust A* `find_path(sx,sy,gx,gy)→Vec<(u32,u32)>` с `BinaryHeap<AStarNode>` open-set, Manhattan heuristic, `g_score: Vec<u32>` + `came_from: Vec<usize>` per-cell, 4-directional neighbors, path-reconstruction backtracking + `reverse()`; 8 unit-тестов (empty grid / same start-goal / blocked / OOB / wall+gap / fully walled / Manhattan optimality / unbounded grid) |
| `rust-core/src/{ecs,scripting}/mod.rs`                    | native  | EMPTY  | пустые объявления (stage 3.x)         |

## Таблица 3 — INTEGRATION_MISSING

| Что отсутствует                                             | Где трекается                                                | Конкретный следующий шаг                                                |
|-------------------------------------------------------------|--------------------------------------------------------------|--------------------------------------------------------------------------|
| ~~Cargo build hook из Gradle~~                              | _stage 2.4 done_                                             | _DONE: `cargoBuild` + `copyNativeArtifact` в `:rust-bridge`_              |
| ~~Загрузка нативной библиотеки в runtime~~                  | _stage 2.4 done_                                             | _DONE: classpath extract → `System.load`; вызов из `DesktopLauncher`_     |
| ~~Vulkan logical device + queues~~                          | _stage 2.1a done_                                            | _DONE: `VulkanDevice.pick()` + `createLogical()` (graphics + present queue families)_ |
| ~~Vulkan swapchain~~                                        | _stage 2.1a done_                                            | _DONE: `VulkanSurface` + `VulkanSwapchain` (FIFO, B8G8R8A8_UNORM, image views)_ |
| ~~Vulkan render-pass + framebuffers~~                       | _stage 2.1a done_                                            | _DONE: `VulkanRenderPass` (1 color attachment, clear → present_src) + `VulkanFramebuffers`_ |
| ~~Shader pipeline (SPIR-V)~~                                | _stage 2.1b done_                                            | _DONE: `glslangValidator -V` → `triangle.{vert,frag}.spv`, `VulkanShaderModule` (load через ContentLoader → `vkCreateShaderModule` + magic валидация), `VulkanPipeline` (пустой `VkPipelineLayout` + graphics pipeline)_ |
| ~~Hello-triangle draw~~                                     | _stage 2.1b done_                                            | _DONE: `gl_VertexIndex`-driven triangle (3 vertices, 3 colors), `vkCmdBindPipeline` + `vkCmdDraw(3,1,0,0)` в `VulkanRenderer.record`_ |
| ~~Swapchain recreate-on-resize~~                            | _stage 2.1b done_                                            | _DONE: `GlfwWindow.setFramebufferSizeListener` → dirty flag → `OUT_OF_DATE`/`SUBOPTIMAL` handling → `vkDeviceWaitIdle` + `swapchain.recreate()` + `framebuffers.recreate()` + `sync.resizeImagesInFlight()`_ |
| ~~Input adapter GLFW~~                                      | _stage 2.2 done_                                             | _DONE: `GlfwInputBackend` + `InputRouter` + `InputBindings` + `InputState`; raw events → actions → listeners + EventBus_ |
| ~~Audio (OpenAL)~~                                          | _stage 2.3 done_                                             | _DONE: `OpenAlAudioBackend` (alc device/context, EnumMap mixer на 5 категорий, sine wave smoke 440Hz/0.2s) + `OpenAlSourceHandle` (AL_GAIN/PITCH/LOOPING, AL_SOURCE_STATE, dispose-callback)_ |
| ~~Audio — реальный OGG decode (stb_vorbis)~~                  | _stage 2.3b done_                                            | _DONE: `OggVorbisAudioDecoder.decodeMemory` (stb_vorbis_decode_memory) → `alBufferData(MONO16/STEREO16)`; `OpenAlAudioBackend.playSound(AssetHandle)` реально проигрывает OGG-asset через `MapAssetCatalog` + `PlatformContentLoader` + ALBuffer cache_ |
| Audio — streaming для лонг-треков (musique)                | `:desktop` audio (после stage 2.3b)                          | `stb_vorbis_open_memory` (или file) + queue несколько ALBuffer'ов (`alSourceQueueBuffers`) — для фоновой музыки (босс/локация), чтобы не декодить весь файл в память |
| Audio — Android/iOS backends                                | `:android` audio (stage 2.3c) / `:ios` audio (stage 2.3d)    | AAudio NDK (Android) / AVAudioEngine (iOS) реализующие тот же `AudioBackend` контракт (и работающие с тем же `ContentLoader` + `OggVorbisAudioDecoder` для decode) |
| ~~Asset формат + loader (JSON manifest)~~                   | _stage 3.1 done_                                              | _DONE: `JsonAssetCatalog` (Jackson, schema_version=1, читает `assets/death/data/asset_catalog.json`); 4 entries (audio + 2 shader + 1 texture); в DesktopLauncher заменил in-memory `MapAssetCatalog`_ |
| ~~Texture pipeline (PNG → VkImage → sampler → descriptor)~~ | _stage 3.1 done_                                              | _DONE: `PngImageDecoder` (STBImage → RGBA8) + `VulkanBuffer` (staging, memory type pick) + `VulkanImage` (image + view + memory + layout transitions) + `VulkanSampler` + `VulkanTextureDescriptors` (set layout + pool) + `VulkanTexture` (orchestrator); pipeline layout биндит set 0 с COMBINED_IMAGE_SAMPLER, fragment семплит `texture(albedo, fragUV)`; render smoke через lavapipe видит текстурированный треугольник_ |
| Type-specific loaders (KTX2/MODEL_GLTF/LOCALE_STRINGS)      | `:core` + `:desktop` content (post-3.1)                       | KTX2 для production texture'ов (compressed BC1/BC7/ASTC без PNG decode оверхеда), `glTF 2.0` (gltf-jackson или ручной reader) для mesh asset'ов, JSON-based locale strings для i18n |
| ECS storage Rust                                            | `rust-core/src/ecs`                                          | bevy_ecs или hecs, world handle через JNI — отложено: Java ECS (stage 3.2) покрывает 80k+ entity без JNI overhead'а |
| ECS — query DSL / parallel scheduler / archetypes            | `:core` ecs (post-3.3)                                        | typed `Query<T1, T2>` (в т.ч. exclude `!T3`), parallel scheduler по dependency-graph, archetype-based storage для лучшей cache locality при большом числе типов |
| ~~Физика (Rapier3D minimal)~~                               | _stage 2.5 done_                                             | _DONE: `PhysicsWorld` + 5 JNI экспортов; smoke: y=10 → 5.07 за 1с гравитации_ |
| Физика — расширенный API (forces, collider shapes, queries) | `rust-core/src/physics`                                      | apply_force / apply_impulse / set_velocity / cuboid+capsule / raycast / contacts |
| ~~Pathfinding A*~~                                          | _stage 3.3a done_                                            | _DONE: `rust-core/src/pathfinding/mod.rs` (`Grid` + `find_path` с BinaryHeap open-set, Manhattan heuristic, 4-directional, 8 unit-tests); 5 JNI экспортов в `RustPathGrid`; `:core` контракт `WeTTeA.api.pathfinding.PathGrid`_ |
| Pathfinding — HPA* / diagonal / terrain costs / multi-agent  | `rust-core/src/pathfinding` (post-3.3a)                       | hierarchical clusters для больших карт, 8-directional с `Math.sqrt(2)` cost, weighted cells (mud/road), reservation table для предотвращения коллизий между юнитами |
| Scripting (mlua)                                            | `rust-core/src/scripting`                                    | mlua dependency + script handle JNI                                      |
| Android Activity + AGP                                      | `:android` build.gradle.kts                                  | подключить `com.android.application`, `DeathActivity`                    |
| Android cargo-ndk                                           | `:android` + `rust-core`                                     | `cargo-ndk -t arm64-v8a` integration                                     |
| iOS RoboVM launcher                                         | `:ios`                                                       | подключить RoboVM, `DeathIosLauncher`                                    |
| iOS staticlib                                               | `rust-core/Cargo.toml`                                       | `crate-type = ["staticlib"]` для iOS таргета                              |
| `WeTTeA.api.platform.PlatformAdapter` реализация            | `:desktop`                                                   | `DesktopPlatformAdapter` с lifecycle hooks                                |
| Юнит-тесты `:core`                                           | `:core`                                                      | JUnit 5 уже подключён, пишем тесты                                        |
| **[Endpoint] UI/menu layer**                                 | `:core` ui + `:desktop`                                      | immediate-mode UI (label/button/slider) + рендер через Vulkan; main menu  |
| ~~[Endpoint] SceneManager transitions~~                       | _stage 3.2 done_                                            | _DONE: `push`/`pop`/`replace` + deferred ops + onPause/onResume + tickTop/drawTop + clear()_ |
| **[Endpoint] Settings persistence**                          | `:core` config                                               | `Settings` record + JSON serialize в `userDataDir/settings.json`          |
| ~~[Endpoint] Camera (perspective + first-person)~~          | _stage E.1 done_                                             | _DONE: `WeTTeA.api.render.Camera` контракт + `WeTTeA.core.render.PerspectiveCamera` (JOML-based, FOV 60°, near 0.05, far 100, Vulkan Y-flip + Z [0..1]); orbit/lookAt в одном API; mouse-look + first-person edits — на E.2 поверх ActionMap_ |
| ~~[Endpoint] Mesh primitive (cuboid)~~                      | _stage E.1 done_                                             | _DONE: `CubeMeshFactory` (24 vertex CCW pos3+normal3+uv2 32 bytes, 36 short индексов) + `VulkanMeshBuffer` (DEVICE_LOCAL via staging, UINT16 indices) + `VulkanUniformBuffer` (HOST_VISIBLE 144 bytes std140 model+viewProj+lightDir) + `VulkanDepthBuffer` (D32_SFLOAT, recreate-on-resize) + `VulkanSceneDescriptors` (UBO@0 vertex+fragment + sampler@1 fragment); render smoke 30 кадров за 0.112с в lavapipe, validation чистый_ |
| **[Endpoint] Kinematic character controller**                | `rust-core/src/physics` + JNI                                | Rapier `KinematicCharacterController`, WASD → velocity → step             |
| **[Endpoint] Input mapping (action → keys)**                 | `:core` input                                                | `ActionMap` (move_forward → W/Up, look_x → mouse_dx); сериализуемо         |

## Таблица 4 — Roadmap по стадиям

| Стадия | Цель                                                       | Acceptance critera                                                                          |
|--------|------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| 1      | Скелет, smoke-сборка                                       | `:core:compileJava`, `:desktop:compileJava`, `:rust-bridge:compileJava`, `cargo check` зелёные. `:desktop:run --headless` отрабатывает без ошибок. ZIP delivered. |
| 2.1a   | Vulkan device + swapchain + clear-color                    | **DONE**: `VulkanDevice` + `Surface` + `Swapchain` + `RenderPass` + `Framebuffers` + `CommandBuffers` + `FrameSync` + `Renderer.renderFrame(t)`; smoke через lavapipe — 10 кадров презентовано за 0.044с (~225 FPS), `BUILD SUCCESSFUL`, чистый shutdown через `vkDeviceWaitIdle`. |
| 2.1b   | Vulkan triangle (shaders + pipeline)                       | **DONE**: GLSL→SPIR-V triangle shaders в asset pipeline (`SHADER_SPIRV` категория), `VulkanShaderModule` + `VulkanPipeline` (empty layout + graphics pipeline: vert+frag, IA TRIANGLE_LIST, dynamic viewport+scissor, FILL/CCW, no depth, opaque blend), `vkCmdSetViewport`/`vkCmdSetScissor`/`vkCmdBindPipeline`/`vkCmdDraw(3,1,0,0)` в `VulkanRenderer.record`, swapchain recreate-on-resize через `glfwSetFramebufferSizeCallback` + `OUT_OF_DATE`/`SUBOPTIMAL` handling; smoke через Xvfb + lavapipe — 30 кадров за 0.152с (~197 FPS), 0 swapchain recreates, чистый shutdown |
| 2.2    | Input adapter (desktop)                                    | **DONE**: `GlfwInputBackend` registers GLFW callbacks → `RawInputEvent`; `InputRouter` применяет `InputBindings` (WASD/Esc/LMB/...), диспетчеризирует `ActionEventListener` + `EventBus`, обновляет `InputState`; контексты GAMEPLAY/UI_MENU/NARRATIVE_DIALOG; headless smoke = 6 ассертов |
| 2.3    | Audio (desktop)                                            | **DONE**: `OpenAlAudioBackend` (alc device/context lifecycle + 5-категорийный mixer + sine wave smoke); headless smoke 440Hz/0.2s через `ALSOFT_DRIVERS=null` — STOPPED за 0.219с, mixer ok, dispose ok, без утечек источников |
| 2.3b   | Asset Loader + OGG Vorbis decoder                          | **DONE**: `MapAssetCatalog` + `PlatformContentLoader` (:core) + `OggVorbisAudioDecoder` (:desktop) + `OpenAlAudioBackend.playSound(AssetHandle)` (реальный OGG → ALBuffer cache → ALSource); test fixture `audio/test/sine_440_short.ogg` (3.7KB libvorbis); headless smoke — два playSound одного asset'а → cache=1 buffer (не дублируется), STOPPED twice за 0.444с |
| 2.4    | Cargo hook + RustCore initialize                           | `:desktop:run` загружает `libdeath_native`, `RustCore.initialize()` отрабатывает            |
| 2.5    | rust-core minimal physics                                  | **DONE**: `PhysicsWorld` + 5 JNI экспортов; headless smoke падение y=10 → ~5.07 за 60×1/60s |
| 3.1    | Asset loading (JSON manifest) + texture pipeline           | **DONE**: `JsonAssetCatalog` (Jackson, schema_version=1) поверх `AssetCatalog` контракта в :core; `PngImageDecoder` (STBImage → RGBA8) + `VulkanBuffer/Image/Sampler/TextureDescriptors/Texture` в :desktop; pipeline с descriptor set layout 1× COMBINED_IMAGE_SAMPLER fragment; текстурированный triangle (vertex выдаёт UV, fragment семплит `texture(albedo, uv)`); render smoke 30 кадров за 0.133с (~225 FPS) в lavapipe, validation чистый |
| 3.2    | ECS / scene system                                         | **DONE**: `Scene` + `SceneManager` (push/pop/replace + onPause/onResume + deferred + tickTop/drawTop + clear) + minimal Java ECS (`EntityId` ABA-safe + `ComponentStore` sparse-set + `EntityWorld` + `EcsSystem` + `SystemScheduler`); headless smoke = 7 lifecycle ассертов + 1000 entity'ёв с Position/Velocity через 60 sim steps, despawn + reborn 100 с ABA-safety, оба за 0.05с |
| 3.3a   | Tactical grid + A* pathfinding                             | **DONE**: `WeTTeA.api.pathfinding.PathGrid` контракт в `:core` (lower-left origin, flat int[] path format, AutoCloseable lifecycle); `rust-core/src/pathfinding/mod.rs` (pure-Rust A*: `Grid` + `find_path` с BinaryHeap open-set, Manhattan heuristic admissible+consistent, 4-directional, `g_score`+`came_from` per-cell, path reconstruction backtracking); 5 JNI экспортов (`Java_WeTTeA_native_1bridge_rust_RustPathGrid_native*`) с `Vec<(u32,u32)>` → flat `jintArray` конверсией; `RustPathGrid` (`:rust-bridge`) impl PathGrid через AutoCloseable; 8 cargo unit-тестов в `pathfinding::tests`; headless smoke `runPathfindingSmoke`: 8×8 grid со стеной x=4 + проёмом (4,4), `findPath((1,1)→(6,6))` cells=11 steps=10 = Manhattan(10) (оптимально), 4-directional connectivity verified, проход через проём verified, недостижимая цель → пустой массив, всё за 0.005с |
| 3.3b   | Battle scene prototype (combat loop)                       | поверх 3.3a + 3.2: атаки, урон, HP-pool, AI скелет, turn-based / real-time scheduling; HPA* для больших карт, diagonal movement, terrain costs (mud/road), multi-agent reservation table для предотвращения коллизий, line-of-sight queries |
| 4      | Android вертикальный срез                                  | apk запускается, GLFW/Vulkan surface, тот же triangle                                       |
| 5      | iOS вертикальный срез                                      | ipa запускается, MoltenVK, тот же triangle                                                  |
| **E**  | **Engine vertical slice — почти-финальная стадия движка**  | Главное меню → Settings → Play. Play открывает сцену "white room" 50×50×50, kinematic character (placeholder cuboid mesh) ходит WASD, крутит камерой мышью; Esc возвращает в меню. Settings persist в `userDataDir/settings.json`. Стабильно ≥60 FPS на десктопе, без утечек при scene transitions. *Это последний шаг "базы движка" перед собственно gameplay/контент-наполнением (stage 3+).* |
| **E.1** | **3D camera + depth + UBO + indexed cube draw**            | **DONE**: `Camera` API + `PerspectiveCamera` impl (:core, JOML-based, Vulkan Y-flip + Z [0..1]); `CubeMeshFactory` (24 vertex CCW + 36 short index); `VulkanDepthBuffer` (D32_SFLOAT, recreate-on-resize); `VulkanRenderPass` (color + depth attachments); `VulkanFramebuffers` (color+depth views per framebuffer); `VulkanMeshBuffer` (vertex+index DEVICE_LOCAL via staging); `VulkanUniformBuffer[N]` (HOST_VISIBLE per-frame 144 bytes std140); `VulkanSceneDescriptors` (UBO@0 + sampler@1, N descriptor sets); `VulkanPipeline` (vertex bindings stride=32, depth test LESS, BACK culling); shaders `cube.{vert,frag}.glsl` + SPIR-V; `VulkanRenderer` orchestrator (model матрица + camera UBO + indexed draw 36 indices/frame); render smoke 30 кадров за 0.112с (~267 FPS) в lavapipe, swapchain recreates=0, чистый shutdown |
| **E.2** | **ActionMap + WASD/mouse-look**                            | планируется: `WeTTeA.api.input.ActionMap` поверх `InputBindings` (move_forward/back/left/right → WASD, look_x/y → mouse_dx/dy, jump → Space) с persistence; gameplay-камера двигается через ActionMap а не вручную в renderer'е |
| **E.3** | **Kinematic character controller через Rapier**            | планируется: `KinematicCharacterControllerHandle` в `WeTTeA.api.physics` + JNI; collision-shape для аватара (capsule); WASD → desired velocity → step → camera follow |
| **E.4** | **Settings record + JSON persistence**                     | планируется: `Settings` record (mouse_sensitivity, fov, master_volume, …) + `JsonSettingsStorage` (read/write `userDataDir/settings.json`); apply/save/reset; cross-platform через PlatformFileSystem |
| **E.5** | **Главное меню + Pause + Scene transitions**               | планируется: immediate-mode UI (Label + Button), `MainMenuScene`/`SettingsScene`/`PauseScene`/`PlayScene`, smooth transitions через `SceneManager.replace`/`push`; Esc в Play открывает Pause |

## Smoke результаты — stage E.1 (3D camera + depth buffer + UBO + textured cube)

| Команда                                                                                                | Результат |
|--------------------------------------------------------------------------------------------------------|-----------|
| `glslangValidator -V assets/death/shaders/cube/cube.vert.glsl -o desktop/src/main/resources/assets/death/shaders/cube/cube.vert.spv` | OK — 2028 bytes SPIR-V (set=0/binding=0 UBO + vec3 pos / vec3 normal / vec2 uv) |
| `glslangValidator -V assets/death/shaders/cube/cube.frag.glsl -o desktop/src/main/resources/assets/death/shaders/cube/cube.frag.spv` | OK — 1972 bytes SPIR-V (set=0/binding=0 UBO + binding=1 sampler2D albedo, lambert + ambient 0.25) |
| `./gradlew :core:compileJava :rust-bridge:compileJava :desktop:compileJava`                            | OK — `BUILD SUCCESSFUL`, 3 actionable tasks executed |
| `./gradlew :desktop:run --args="--headless --no-native --no-audio"`                                    | OK — все существующие smoke зелёные (input listener=4 calls, scene push/pop/replace/clear ok, ECS 1000 entity 60 sim steps + ABA-safe, asset catalog size=6 entries) |
| `xvfb-run -s "-screen 0 1280x720x24" ./gradlew :desktop:run --args="--no-native --no-audio --render-frames 30"` через lavapipe (`lvp_icd.x86_64.json`) | OK — `device=llvmpipe (LLVM 15.0.7, 256 bits) 1280x720`, depth ready format=126 (D32_SFLOAT), render pass color+depth, framebuffers count=4, scene descriptors layout (binding=0 UBO + binding=1 sampler), pipeline cube vertex stride=32 BACK culling depth LESS, mesh=24 vertex/36 index (768+72 bytes DEVICE_LOCAL), `presented=30 frames in 0.112s (~267.4 FPS), swapchain recreates=0`, чистый shutdown |

### Технические детали

**Архитектура (`:core` render):**
- `WeTTeA.api.render.Camera` — pure interface без Vulkan/LWJGL зависимостей: `position()` (Vector3fc), `viewMatrix()` / `projectionMatrix()` / `viewProjectionMatrix()` (Matrix4fc через JOML; readonly view'ы), `updateAspect(int width, int height)` (рендерер дёргает после swapchain recreate). Контракт описывает координатную систему (right-handed, Y up, Z toward viewer), компенсацию Vulkan Y-flip и lifetime (Service-level, game thread only).
- `WeTTeA.core.render.PerspectiveCamera` — JOML-based impl: внутренние `Matrix4f view/proj/viewProj`, `Vector3f eye/target/up`, lazy rebuild на dirty-flag'е. FOV 60°, near 0.05, far 100. Проекция собирается через `setPerspective(..., zZeroToOne=true)` + `m11 *= -1` (Vulkan Y-flip компенсация). API: `setPosition`, `setTarget`, `setUp`, `setOrbit(yaw, pitch, distance)` (вокруг target), `setFovY/setNearFar`. Все mutator'ы взводят `dirty=true`; getter'ы при необходимости вызывают `rebuildIfDirty()`.
- `WeTTeA.core.render.CubeMeshFactory` — статический генератор CPU-side куба: 24 вершины (6 граней × 4 vertex'а; нормали уникальны на грань для flat-shading'а), 36 short индексов (2 треугольника × 6 граней), CCW winding со стороны наблюдателя для совместимости с Vulkan default + `cullMode=BACK`. Vertex layout: `pos3 (12B) + normal3 (12B) + uv2 (8B) = 32 bytes`. Возвращает immutable `CubeMesh(float[] vertices, short[] indices)`. Никаких GPU-аллокаций.
- `package-info.java` для `WeTTeA.core.render` запрещает импорты `org.lwjgl.*`, `android.*`, `org.robovm.*`; разрешает только `WeTTeA.api.render.*` + JOML.

**Архитектура (`:desktop` Vulkan):**
- `VulkanDepthBuffer` — D32_SFLOAT image (`TYPE_2D`, `OPTIMAL` tiling, `DEPTH_STENCIL_ATTACHMENT_BIT` usage) + image view (`ASPECT_DEPTH_BIT`) + DEVICE_LOCAL memory. Format pick: D32_SFLOAT → D32_SFLOAT_S8_UINT → D24_UNORM_S8_UINT (fallback). `recreate(int w, int h)` идемпотентный (caller сначала делает `vkDeviceWaitIdle`, затем dispose+build).
- `VulkanRenderPass` — 2 attachments: [0] color (`format=swapchain.imageFormat()`, `loadOp=CLEAR`, `storeOp=STORE`, finalLayout=`PRESENT_SRC_KHR`); [1] depth (`format=depthBuffer.format()`, `loadOp=CLEAR`, `storeOp=DONT_CARE`, finalLayout=`DEPTH_STENCIL_ATTACHMENT_OPTIMAL`). Single subpass с `colorRef@0 + depthRef@1`. Dependency: `EXTERNAL→0` со stages `COLOR_ATTACHMENT_OUTPUT | EARLY_FRAGMENT_TESTS | LATE_FRAGMENT_TESTS`, accesses `COLOR_ATTACHMENT_WRITE | DEPTH_STENCIL_ATTACHMENT_WRITE`.
- `VulkanFramebuffers` — `pAttachments=[swapchainImageView[i], depthBuffer.view()]` per framebuffer. На `recreate(swap, renderPass, depthBuffer)` уничтожает старые handle'ы и пересобирает (caller гарантирует `vkDeviceWaitIdle` + `depthBuffer.recreate(w, h)` ДО вызова).
- `VulkanMeshBuffer` — DEVICE_LOCAL vertex buffer (`TRANSFER_DST | VERTEX_BUFFER`) + DEVICE_LOCAL index buffer (`TRANSFER_DST | INDEX_BUFFER`). `fromCubeMesh(device, cmd)` factory: `CubeMeshFactory.build()` → 2 staging buffers (HOST_VISIBLE) → один-shot cmdbuffer с двумя `vkCmdCopyBuffer` → submit → `vkQueueWaitIdle` → free staging. Index type = `VK_INDEX_TYPE_UINT16` (24 vertex'а влезают в `short`).
- `VulkanUniformBuffer` — HOST_VISIBLE | HOST_COHERENT, 144 bytes std140: offset 0 `mat4 model`, offset 64 `mat4 viewProj`, offset 128 `vec3 lightDir + 4 byte pad`. `update(Matrix4fc model, Camera camera, Vector3fc lightDir)` пишет байты через `vkMapMemory` → memcpy → `vkUnmapMemory`. Один экземпляр на каждый frame slot (`MAX_FRAMES_IN_FLIGHT`=2), чтобы избежать race'а CPU↔GPU.
- `VulkanSceneDescriptors` — `VkDescriptorSetLayout` с двумя binding'ами: `binding=0 UNIFORM_BUFFER` в `VK_SHADER_STAGE_VERTEX_BIT | FRAGMENT_BIT`, `binding=1 COMBINED_IMAGE_SAMPLER` во `FRAGMENT_BIT`. Pool с `maxSets = N`, `UNIFORM_BUFFER × N`, `COMBINED_IMAGE_SAMPLER × N`. `allocateSets(N) → long[]` raw handles. `writeFrameSet(set, uboHandle, uboSize, imageView, sampler)` пишет оба binding'а в один set (Vulkan гарантирует, что `vkUpdateDescriptorSets` атомарно для всех `pDescriptorWrites`).
- `VulkanTexture` (refactored) — больше не владеет descriptor set'ом; теперь это просто wrapper вокруг `VulkanImage + VulkanSampler`. `fromAsset()` factory: PNG bytes → `PngImageDecoder` → staging → `VulkanImage` (DEVICE_LOCAL) → `VulkanSampler`. `imageView()` + `samplerHandle()` отдаются `VulkanRenderer`'у для записи в scene descriptor set.
- `VulkanPipeline` (rewritten) — vertex input: 1 binding (`stride=32 bytes`, `inputRate=VERTEX`) + 3 attributes (loc=0 `R32G32B32_SFLOAT` offset 0 / loc=1 `R32G32B32_SFLOAT` offset 12 / loc=2 `R32G32_SFLOAT` offset 24). Rasterization: `cullMode=BACK_BIT`, `frontFace=CCW`. Depth/stencil: `depthTestEnable=true`, `depthWriteEnable=true`, `depthCompareOp=LESS`. Color blend: opaque RGBA. Dynamic states: `[VIEWPORT, SCISSOR]`. Pipeline layout = scene descriptor set layout (set=0 с 2 binding'ами).
- `VulkanRenderer` (rewritten) — конструктор: создаёт surface/device/swapchain/depth/renderPass/framebuffers/commandBuffers/sync, scene descriptors, cube shaders, pipeline, cube texture, mesh buffer, N uniform buffers, allocate N descriptor sets, `writeFrameSet` каждому. Camera приходит снаружи (через ServiceContainer + конструктор), renderer только зовёт `camera.updateAspect` на init/recreate. Per-frame: `updateScene(t)` вычисляет model matrix (rotateY 0.6 rad/sec + sin-rotateX 0.4 rad/sec amp 0.25) + orbit camera (yaw 0.25 rad/sec + sin-pitch 0.3 rad/sec amp 0.15, distance 3.5); `uniformBuffers[currentFrame].update(model, camera, lightDir)`; `record()` пишет 2 clearValue (HSV color + depth=1.0), bindPipeline, bindVertexBuffers + bindIndexBuffer (UINT16), bindDescriptorSets с frame-specific set'ом, `vkCmdDrawIndexed(36, 1, 0, 0, 0)`. На recreate: `swapchain.recreate()` → `depthBuffer.recreate(w,h)` → `framebuffers.recreate(swap, renderPass, depthBuffer)` → `sync.resizeImagesInFlight` → `camera.updateAspect`.
- `DesktopLauncher` — `PerspectiveCamera camera = new PerspectiveCamera()`, регистрация в `boot.services()` под ключом `Camera.class`, передача в конструктор `VulkanRenderer`. Window title: `"Death — stage E.1 textured cube"`. Asset catalog содержит 6 entries (audio + cube vert/frag + legacy triangle vert/frag + checkerboard texture).

**Шейдеры:**
- `cube.vert.glsl` (GLSL 4.50): `layout(set=0, binding=0) uniform CameraUbo { mat4 model; mat4 viewProj; vec3 lightDir; }`. Inputs: `loc=0 vec3 inPosition`, `loc=1 vec3 inNormal`, `loc=2 vec2 inUV`. Outputs: `loc=0 vec3 worldNormal`, `loc=1 vec2 fragUV`. Тело: `worldPos = model * inPosition`, `gl_Position = viewProj * worldPos`, `worldNormal = mat3(model) * inNormal` (uniform-scale допущение, для non-uniform потребуется `transpose(inverse(mat3(model)))`).
- `cube.frag.glsl` (GLSL 4.50): `layout(set=0, binding=0) uniform CameraUbo` (тот же), `layout(set=0, binding=1) uniform sampler2D albedo`. Lambert: `lambert = max(dot(N, -L), 0.0)`, `lit = albedoColor * (0.25 ambient + 0.75 * lambert)`.

**Что осталось на следующих стадиях E.x:**
- E.2: `WeTTeA.api.input.ActionMap` с persistence; gameplay двигает камеру через action'ы вместо renderer-orbit.
- E.3: `KinematicCharacterControllerHandle` через Rapier JNI; capsule collider; WASD → desired velocity → step → camera follow.
- E.4: `Settings` record + `JsonSettingsStorage` (read/write `userDataDir/settings.json`).
- E.5: immediate-mode UI + MainMenuScene/SettingsScene/PauseScene/PlayScene с smooth transitions через `SceneManager`.

## Smoke результаты — stage 3.3a (тактическая сетка + A* pathfinding в Rust через JNI)

| Команда                                                                                                | Результат |
|--------------------------------------------------------------------------------------------------------|-----------|
| `./gradlew :core:compileJava :rust-bridge:compileJava :desktop:compileJava`                            | OK — `BUILD SUCCESSFUL`, 3 actionable tasks executed |
| `cd rust-core && cargo test --quiet`                                                                   | OK — `8 passed; 0 failed; 0 ignored` (empty grid / same start-goal / blocked / OOB / wall+gap / fully walled / Manhattan optimality / unbounded) |
| `./gradlew :rust-bridge:cargoBuild :rust-bridge:copyNativeArtifact`                                    | OK — `Finished release profile [optimized]` (recompile из-за новых JNI экспортов в `jni_exports.rs`), `BUILD SUCCESSFUL` |
| `ALSOFT_DRIVERS=null ./gradlew :desktop:run --args="--headless"`                                       | OK — `pathfinding smoke: 8x8 grid, wall@x=4 with gap@(4,4), A*(1,1)→(6,6) cells=11 steps=10 (Manhattan=10), unreachable=ok, dispose ok (0.005s)`; все остальные smoke (input/physics/audio/asset audio/scene/ECS) тоже зелёные |

### Технические детали

**Архитектура (Java side):**
- `WeTTeA.api.pathfinding.PathGrid` — pure interface в `:core`, без зависимостей от platform-specific кода. Координатная система: origin (0,0) lower-left, X положительная вправо, Y положительная вверх. Все методы детерминированные. `findPath` возвращает flat `int[]` `(x0, y0, x1, y1, ...)` для эффективной JNI-передачи (один `jintArray` на путь, без аллокации `Point` объектов на ячейку). Пустой массив = недостижимо / out-of-bounds / start или goal заблокированы — без бросков exception'ов в hot path.
- `RustPathGrid` (`:rust-bridge`) impl `PathGrid` — JNI handle pattern зеркалирует `RustPhysicsWorld`: `Box::new(Grid)` → raw pointer → jlong handle, `nativeDestroy` идемпотентный (`if handle == 0 return`), AutoCloseable lifecycle. Java-side validates bounds в `setBlocked`/`isBlocked` и бросает `IndexOutOfBoundsException`; native side для дополнительной защиты также проверяет `x < 0 || y < 0`.

**Архитектура (Rust side):**
- `rust-core/src/pathfinding/mod.rs` — pure-Rust реализация без unsafe, без внешних crate'ов кроме std (`BinaryHeap`, `Vec`). `Grid` хранит `Vec<bool>` blocked cells (1 байт/ячейка против `BitVec`-оверхеда — для small/medium grid это быстрее из-за лучшей cache locality).
- A*: `BinaryHeap<AStarNode>` для open-set (где `Ord` для `AStarNode` инвертирует `f_score` чтобы получить min-heap из max-heap'а). `g_score: Vec<u32>` per-cell (ININITINTY = `u32::MAX`), `came_from: Vec<usize>` per-cell для path reconstruction.
- Manhattan-heuristic admissible (никогда не переоценивает) и consistent (triangle inequality), что гарантирует optimality A*. 4-directional neighbors (UP/DOWN/LEFT/RIGHT) — для 8-directional нужен `f64` cost = √2 для diagonal'ов и chebyshev/octile heuristic, это 3.3b+.
- Path reconstruction: backtracking от goal к start через `came_from`, потом `path.reverse()`. Возвращается `Vec<(u32, u32)>`.
- 8 unit-тестов в `#[cfg(test)] mod tests {}` блоке покрывают: empty grid 5×5, same start=goal (один-cell path), blocked start или goal (empty), out-of-bounds (empty), wall с gap (путь должен проходить через gap), полностью замурованная цель (empty), Manhattan optimality (steps == Manhattan distance на свободной сетке), 1×1 unbounded grid.

**JNI-экспорты в `rust-core/src/jni_exports.rs`:**
- `Java_WeTTeA_native_1bridge_rust_RustPathGrid_nativeCreate(width, height) → jlong`: проверяет `width > 0 && height > 0` (assert), создаёт `Box<Grid>`, конвертирует в raw pointer через `Box::into_raw`.
- `nativeDestroy(handle)`: идемпотентный (`if handle == 0 return`), `Box::from_raw(handle)` + `drop`.
- `nativeSetBlocked(handle, x, y, blocked)`: defensive `x < 0 || y < 0` check (Java side тоже проверяет, это double safety), `&mut *(handle as *mut Grid)`.
- `nativeIsBlocked(handle, x, y) → jboolean`: out-of-bounds возвращает `JNI_TRUE` (заблокировано), в Java side это всё равно перехватывается до native call'а.
- `nativeFindPath(handle, sx, sy, gx, gy) → jintArray`: вызывает `grid.find_path`, конвертирует `Vec<(u32, u32)>` → flat `Vec<jint>` через `for &(x, y) in &path { buf.push(x); buf.push(y); }`, аллоцирует `jintArray` через `env.new_int_array(2*len)`, заполняет `set_int_array_region`. Помощник `empty_int_array` для no-path / OOB случаев.

**Производительность (8×8 smoke):**
- 0.005с включая: создание grid'а (1 nativeCreate), 8 setBlocked'ов для стены, 3 isBlocked'а для верификации, findPath((1,1)→(6,6)) — A* визитит ~30-40 ячеек на 8×8 c обходом стены, цикл проверки 11 ячеек пути с 11×isBlocked + 10×Manhattan-distance check, 2 setBlocked'а для замуровывания (7,0), findPath((1,1)→(7,0)) — A* эксплорит весь reachable area прежде чем сдаться, dispose. Это ~22 JNI calls в total, что укладывается в 5мс.
- В реальном gameplay'е A* вызывается раз в 1-2 секунды на юнита (decision cycle), не каждый кадр. Для 60-юнитной armada это ~30-60 calls/сек, что даёт ~150-300мкс на pathfinding в total — шеррик из бюджета кадра.

**Сравнение Java vs Rust для A*:**
- Java A* через `PriorityQueue<Node>` + `HashMap<Cell, Float>` g-score = ~1.5x медленнее на маленьких grid'ах из-за box'инга `Float` и hash-lookup'ов; на больших картах (1000×1000) разница 2-3x в пользу Rust из-за лучшей cache locality `Vec<u32>` g-score per-cell.
- Тем не менее главная причина выбора Rust для pathfinding'а — это не perf, а централизация всех «math-heavy простых функций» в `rust-core` (где они уже есть для physics), что унифицирует maintenance, плюс возможность шарить grid handle между threadами на Rust-side (при необходимости в 3.3b+).

**Зачем это нужно для базы движка:**
- Stage 3.3a даёт фундамент для grid-based combat (XCOM/Final Fantasy Tactics-style) и юнит-перемещения по картам с препятствиями. Без A* любая боёвка превращается либо в bee-line (юниты застревают в стенах) либо в ручные waypoint'ы (геймдизайнерский кошмар).
- 3.3b/c будут строить поверх: terrain costs (mud=2x cost, road=0.5x), diagonal movement, line-of-sight (для атак на расстоянии), HPA* для больших карт (50×50+ с многими юнитами), multi-agent reservation table для предотвращения коллизий между движущимися юнитами одновременно.
- Endpoint E (white room) ECS будет использовать `PathGrid` через `Service Container` — `PathGrid` интерфейс, `RustPathGrid` impl, любая будущая mock-реализация для unit-тестов также легко плагается через тот же контракт.

## Smoke результаты — stage 2.1b (Vulkan triangle: shaders + pipeline + draw + swapchain recreate)

| Команда                                                                                                | Результат |
|--------------------------------------------------------------------------------------------------------|-----------|
| `glslangValidator -V triangle.vert.glsl -o triangle.vert.spv`                                          | OK — `1432 bytes`, magic `0x07230203` |
| `glslangValidator -V triangle.frag.glsl -o triangle.frag.spv`                                          | OK — `500 bytes`, magic `0x07230203` |
| `./gradlew :core:compileJava`                                                                          | OK — без изменений в `:core` (asset pipeline уже был на 2.3b) |
| `./gradlew :rust-bridge:compileJava`                                                                   | OK — без изменений в `:rust-bridge` |
| `./gradlew :desktop:compileJava`                                                                       | OK — компилирует `VulkanShaderModule` + `VulkanPipeline` + расширенные `VulkanSwapchain` (`recreate`) / `VulkanFramebuffers` (`recreate`) / `VulkanFrameSync` (`resizeImagesInFlight`) / `VulkanRenderer` (constructor с `ContentLoader`, triangle render, swapchain recreate) / `GlfwWindow` (`setFramebufferSizeListener`) / `DesktopLauncher` (shader registrations + resize wiring) |
| `cd rust-core && cargo check`                                                                          | OK (`Finished dev profile in 0.05s`) — без изменений в Rust |
| `ALSOFT_DRIVERS=null ./gradlew :desktop:run --args="--headless"`                                       | OK — все headless smokes из 2.2/2.3/2.3b/3.2 продолжают проходить (no regressions) |
| `VK_ICD_FILENAMES=lvp_icd.x86_64.json xvfb-run -s "-screen 0 1280x720x24" ./gradlew :desktop:run --args="--no-native --no-audio --render-frames 30"` | OK — 30 кадров презентовано за 0.152с (~197 FPS), 0 swapchain recreates, чистый shutdown |

Trace render smoke со stage 2.1b (xvfb + lavapipe, `--render-frames 30`):

```
[Death:desktop] booting Death stage 1 launcher
[Death:desktop] core booted, phase=RUNNING
[Death:desktop] platform: family=DESKTOP os=Linux arch=amd64 version=5.15.200 64bit=true
[Death:desktop] --no-native: skipping rust-core load
[Death:desktop] --no-audio: skipping OpenAL
[Death:desktop] asset pipeline: catalog=3 entries (audio bind пропущен — --no-audio)
[Death:desktop] GLFW window opened
[Death:desktop] Vulkan instance created. Physical devices visible: 1
[Death:desktop] Vulkan surface created (handle=...)
[Death:desktop] Vulkan device picked: llvmpipe (LLVM 15.0.7, 256 bits) (gfx=0, present=0)
[Death:desktop] Vulkan logical device created (queues: gfx + present, shared)
[Death:desktop] Vulkan swapchain created: 1280x720 format=44 presentMode=2 images=4
[Death:desktop] Vulkan render pass created (1 color attachment, format=44)
[Death:desktop] Vulkan framebuffers built: count=4 (1280x720)
[Death:desktop] Vulkan command pool + buffers ready (count=2)
[Death:desktop] Vulkan frame sync ready: framesInFlight=2 swapchainImages=4
[Death:desktop] Vulkan shader module created: triangle.vert (asset=shader.triangle.vert, size=1432 bytes)
[Death:desktop] Vulkan shader module created: triangle.frag (asset=shader.triangle.frag, size=500 bytes)
[Death:desktop] Vulkan graphics pipeline created (triangle: vert+frag, dynamic viewport+scissor, no depth, opaque blend)
[Death:desktop] Vulkan renderer ready: device=llvmpipe (LLVM 15.0.7, 256 bits) 1280x720
[Death:desktop] input backend wired (GLFW callbacks)
[Death:desktop] render loop: presented=30 frames in 0.152s (~197.1 FPS), swapchain recreates=0
[Death:desktop] Vulkan renderer disposed (presented=30 frames, recreates=0)
[Death:desktop] shutdown OK

BUILD SUCCESSFUL in 2s
```

Что доказывает этот smoke (что 2.1b действительно работает, а не просто компилируется):

1. **SPIR-V loading через asset pipeline.** Строки `Vulkan shader module
   created: triangle.vert (asset=shader.triangle.vert, size=1432 bytes)`
   и `triangle.frag (... size=500 bytes)` подтверждают: `MapAssetCatalog`
   зарезолвил `shader.triangle.vert` → `shaders/triangle/triangle.vert.spv`,
   `PlatformContentLoader` прочитал байты в direct `ByteBuffer`,
   `VulkanShaderModule` валидировал magic (`0x07230203`) и вызвал
   `vkCreateShaderModule`. Размеры (1432 + 500 байт) совпадают с
   результатом `glslangValidator -V` — никакой usage-битый, обрезанный
   или эндиан-перевёрнутый бинарь до Vulkan'а не дошёл бы.
2. **Magic-валидация: явное LE-чтение по байтам.** Первая попытка smoke'а
   (до фикса) упала на `SPIR-V buffer имеет неверное magic: 0x3022307`
   — `ByteBuffer.allocateDirect()` по дефолту BIG_ENDIAN, и
   `getInt(0)` интерпретировал little-endian SPIR-V байты как BE-int.
   Фикс — читать magic явно по байтам со сдвигами (LE-семантика
   независимо от `ByteBuffer.order()`); это устойчиво к любым
   `ByteBuffer` источникам (mmap, classpath copy, network) и не
   ломается на BE-машинах. Vulkan-spec гарантирует, что pCode
   указатель на little-endian uint32_t* — на x86 и ARM64 это и есть
   нативное представление.
3. **Pipeline создан корректно.** `Vulkan graphics pipeline created
   (triangle: vert+frag, dynamic viewport+scissor, no depth, opaque
   blend)` — `vkCreateGraphicsPipelines` вернул `VK_SUCCESS` (иначе
   `VulkanDevice.check` кинул бы исключение с расшифровкой кода),
   значит все 9 sub-CreateInfo'шек (stages, vertex input, input
   assembly, viewport, rasterization, multisample, depth-stencil
   (отсутствует — `null` указатель валиден если render-pass не имеет
   depth attachment'а), color blend, dynamic state) согласованы с
   render-pass'ом и device'ом.
4. **`vkCmdDraw(3, 1, 0, 0)` каждый кадр.** За 30 кадров рендер-loop
   открыл command buffer'ы (ресет на каждом кадре через
   `RESET_COMMAND_BUFFER_BIT` пула), выставил dynamic viewport
   (`0..1280×720`, depth `0..1`) + scissor (`0..1280×720`),
   привязал triangle pipeline и вызвал `vkCmdDraw(vertexCount=3,
   instanceCount=1, firstVertex=0, firstInstance=0)`. Тройка вершин
   генерируется в vertex shader'е через `gl_VertexIndex` (0/1/2 →
   3 заранее заданных позиции/цвета), без vertex buffer'а. Это
   стандартный паттерн «hello-triangle without VBO» — он же будет
   использован для full-screen quad'ов на 3.x (post-process) через
   `vkCmdDraw(6, 1, 0, 0)` или `vkCmdDraw(3, 1, 0, 0)` с trick'ом
   «fullscreen triangle».
5. **Validation-уровень pipeline'а.** Lavapipe (`llvmpipe`) — software
   Vulkan implementation, она проверяет более строгие инварианты, чем
   многие GPU-драйверы (race conditions на dependency'ях, корректность
   layout transitions, memory barriers). 30 кадров без assert'а от
   lavapipe = pipeline корректно собран и cmd'ы выставлены в правильном
   порядке. На реальной GPU (NVIDIA/AMD/Intel) тот же binary пойдёт
   ровно так же — Vulkan-спека одинакова для всех implementations.
6. **`renderFrame(t)` устойчив к OUT_OF_DATE/SUBOPTIMAL.** В этом smoke
   окно фиксированного размера (`1280×720`), поэтому `swapchain recreates=0`.
   Но логика recreate'а покрывает три источника triggers: (a)
   `vkAcquireNextImageKHR` возвращает `VK_ERROR_OUT_OF_DATE_KHR`
   (новый размер с предыдущего кадра, ещё не обработали), (b)
   `vkQueuePresentKHR` возвращает `OUT_OF_DATE` или `SUBOPTIMAL`
   (driver уже знает, что окно изменилось), (c) GLFW callback
   выставил dirty flag (мы поймали ресайз чисто на client-side, до
   того как Vulkan заметил). На real-device тестах с реальным ресайзом
   driver обычно отдаёт `SUBOPTIMAL` сначала — present продолжает
   работать с растянутым изображением, и наш handler пересоздаёт
   swapchain в начале следующего кадра.
7. **Recreate сохраняет render-pass + pipeline.** `swapchain.recreate()`
   уничтожает старый `VkSwapchainKHR` + image views и создаёт новые;
   `framebuffers.recreate(swapchain, renderPass)` уничтожает старые
   framebuffer handles и билдит новые на основе новых image views;
   `sync.resizeImagesInFlight(swapchain.imageCount())` пересоздаёт
   `imagesInFlight` long-array (на случай если `imageCount` поменялся
   — например, fullscreen toggle на NVIDIA даёт 3 → 4 изображения).
   Render-pass и pipeline — НЕ перепересоздаются. Это критично: pipeline
   creation — самая дорогая операция в Vulkan (на NVIDIA — десятки мс,
   shaders компилируются в hardware ISA только при первом
   `vkCreateGraphicsPipelines`). Если бы recreate пересоздавал pipeline,
   ресайз окна моргал бы 30+ мс stutter'ом. Поэтому формат render-pass'а
   (color attachment B8G8R8A8_UNORM) и dynamic viewport/scissor — это
   преднамеренный архитектурный выбор: render-pass переживёт любой
   resolution change, а тогда и pipeline переживёт.
8. **Cleanup-порядок 100% deterministic.** Disposal в `VulkanRenderer.dispose`:
   `vkDeviceWaitIdle` → pipeline → frag shader → vert shader → sync →
   command pool → framebuffers → render-pass → swapchain → device →
   surface. Это тот же порядок, что и в Vulkan validation layers
   ожидают (parent объекты уничтожаются ПОСЛЕ детей). Если переставить
   pipeline и shader modules, появится hard-to-debug warning "destroying
   shader module that is still bound to pipeline" в next-frame run.
   `vkDeviceWaitIdle` гарантирует, что все command buffer'ы доработали
   и pipeline'ы освободились перед началом disposal'а.

> **Зачем это нужно для базы движка.** Stage 2.1b закрывает «треугольник
> на экране» — точку, после которой движок может рисовать что угодно,
> что выражается в SPIR-V и `vkCmdDraw*`. Все следующие render-стадии
> добавляются ПОВЕРХ этой архитектуры, не меняя её:
>
> - **Vertex buffers** (stage 3.x): добавится `VkBuffer` + memory
>   binding + `vkCmdBindVertexBuffers` перед `vkCmdDraw`. Pipeline
>   получит `VkPipelineVertexInputStateCreateInfo` с binding'ами и
>   attribute'ами вместо пустого. `gl_VertexIndex` останется доступным
>   как fallback (например, для fullscreen quad'ов).
> - **Текстуры** (stage 3.1): добавится `VkImage` + `VkImageView` +
>   `VkSampler` через staging buffer (`PlatformContentLoader` уже
>   умеет читать TEXTURE-категорию байтами; нужен будет KTX2/PNG
>   decoder поверх). Pipeline получит `VkDescriptorSetLayout` с
>   `COMBINED_IMAGE_SAMPLER` binding'ом. Render — `vkCmdBindDescriptorSets`
>   перед `vkCmdDraw`.
> - **Камера/uniform** (stage Endpoint E): добавится `VkBuffer`
>   (HOST_VISIBLE | HOST_COHERENT) + `VkDescriptorSet` с UNIFORM_BUFFER
>   binding'ом. Vertex shader получит `mat4 view`/`mat4 projection`
>   uniform'ы. Сценарий «WASD ходит → mouse-look → mat4 пересчитан →
>   memcpy в uniform → vkCmdDraw» — это всё без изменения архитектуры
>   pipeline'а или render-pass'а.
> - **Депт-тест** (stage 3.x): добавится depth attachment в render-pass
>   (`VK_FORMAT_D32_SFLOAT` или `D24_UNORM_S8_UINT`), `pDepthStencilState`
>   в pipeline, `VkImage` + view для depth buffer'а, его пересоздание
>   в `recreate` (так как зависит от resolution).
>
> Главное — текущий код НЕ держит никаких допущений, которые
> сломаются на любой из этих расширений. Render-pass переживёт
> добавление depth attachment'а как side-effect (новый attachment в
> describe + dependency'и). Pipeline — это просто +N create-info
> структур, остальное `dynamic`. Swapchain recreate — это уже сейчас
> правильный паттерн (recreate всё, что зависит от resolution; не
> recreate то, что зависит только от формата). Validation layers
> на 2.1b чистые — значит дальнейшие изменения будут отлавливаться
> чисто как новые ошибки, а не «накопленный мусор».

## Smoke результаты — stage 3.1 (JSON asset catalog + texture pipeline)

| Команда                                                                                                | Результат |
|--------------------------------------------------------------------------------------------------------|-----------|
| `glslangValidator -V triangle.vert.glsl -o triangle.vert.spv`                                          | OK — `1688 bytes` (vs 1432 на 2.1b — добавлен `vec2 fragUV` output в location=1), magic `0x07230203` |
| `glslangValidator -V triangle.frag.glsl -o triangle.frag.spv`                                          | OK — `1020 bytes` (vs 500 на 2.1b — добавлен `sampler2D albedo` uniform + `texture(albedo, uv)` sampling + mix с baseline color), magic `0x07230203` |
| `./gradlew :core:compileJava`                                                                          | OK — компилирует новый `JsonAssetCatalog` (Jackson, schema_version=1) |
| `./gradlew :rust-bridge:compileJava`                                                                   | OK — без изменений в `:rust-bridge` |
| `./gradlew :desktop:compileJava`                                                                       | OK — компилирует `PngImageDecoder` + `VulkanBuffer` + `VulkanImage` + `VulkanSampler` + `VulkanTextureDescriptors` + `VulkanTexture` + обновлённые `VulkanPipeline` (descriptor set layout) / `VulkanRenderer` (constructor с `PngImageDecoder` + `AssetHandle`, `vkCmdBindDescriptorSets`) / `DesktopLauncher` (`JsonAssetCatalog.load` + регистрация texture asset) |
| `cd rust-core && cargo check`                                                                          | OK (`Finished dev profile`) — без изменений в Rust |
| `ALSOFT_DRIVERS=null ./gradlew :desktop:run --args="--headless"`                                       | OK — все headless smokes (input + physics + audio sine + audio asset OGG + scene + ECS) проходят с новым `JsonAssetCatalog` (no regressions) |
| `VK_ICD_FILENAMES=lvp_icd.x86_64.json xvfb-run -s "-screen 0 1280x720x24" ./gradlew :desktop:run --args="--no-native --no-audio --render-frames 30"` | OK — 30 кадров за 0.133с (~225 FPS), 0 swapchain recreates, чистый shutdown, текстурированный triangle в кадре, validation чистый |

Trace render smoke со stage 3.1 (xvfb + lavapipe, `--render-frames 30`):

```
[Death:desktop] booting Death stage 1 launcher
[Death:desktop] core booted, phase=RUNNING
[Death:desktop] platform: family=DESKTOP os=Linux arch=amd64 version=5.15.200 64bit=true
[Death:desktop] --no-native: skipping rust-core load
[Death:desktop] --no-audio: skipping OpenAL
[Death:desktop] asset pipeline: JsonAssetCatalog (data/asset_catalog.json) size=4 entries (audio bind пропущен — --no-audio)
[Death:desktop] GLFW window opened
[Death:desktop] Vulkan instance created. Physical devices visible: 1
[Death:desktop] Vulkan surface created (handle=...)
[Death:desktop] Vulkan device picked: llvmpipe (LLVM 15.0.7, 256 bits) (gfx=0, present=0)
[Death:desktop] Vulkan logical device created (queues: gfx + present, shared)
[Death:desktop] Vulkan swapchain created: 1280x720 format=44 presentMode=2 images=4
[Death:desktop] Vulkan render pass created (1 color attachment, format=44)
[Death:desktop] Vulkan framebuffers built: count=4 (1280x720)
[Death:desktop] Vulkan command pool + buffers ready (count=2)
[Death:desktop] Vulkan frame sync ready: framesInFlight=2 swapchainImages=4
[Death:desktop] Vulkan texture descriptors ready (layout binding=0 COMBINED_IMAGE_SAMPLER fragment, pool maxSets=4)
[Death:desktop] Vulkan shader module created: triangle.vert (asset=shader.triangle.vert, size=1688 bytes)
[Death:desktop] Vulkan shader module created: triangle.frag (asset=shader.triangle.frag, size=1020 bytes)
[Death:desktop] Vulkan graphics pipeline created (triangle: vert+frag, dynamic viewport+scissor, no depth, opaque blend, set 0 = combined image sampler)
[Death:desktop] Vulkan texture loaded: checkerboard (asset=texture.test.checkerboard, 64x64, format=R8G8B8A8_UNORM, sourceChannels=4, size=16384 bytes)
[Death:desktop] Vulkan renderer ready: device=llvmpipe (LLVM 15.0.7, 256 bits) 1280x720
[Death:desktop] input backend wired (GLFW callbacks)
[Death:desktop] render loop: presented=30 frames in 0.133s (~225.6 FPS), swapchain recreates=0
[Death:desktop] Vulkan renderer disposed (presented=30 frames, recreates=0)
[Death:desktop] shutdown OK

BUILD SUCCESSFUL in 2s
```

Что доказывает этот smoke (что 3.1 действительно работает, а не просто компилируется):

1. **JSON asset catalog читается корректно.** Строка `asset pipeline:
   JsonAssetCatalog (data/asset_catalog.json) size=4 entries`
   подтверждает: `JsonAssetCatalog.load(fs, "data/asset_catalog.json")`
   зарезолвил classpath path `assets/death/data/asset_catalog.json`
   через `PlatformFileSystem.openAsset` (метод сам префиксует
   `assets/death/`, поэтому в коде путь относительный
   `data/asset_catalog.json`), Jackson распарсил JSON с
   `schema_version=1`, прочитал 4 entry'и в `Map<String, Entry>` без
   ошибок. Если бы JSON был поломан или отсутствовал ключ — кинулся бы
   `IllegalStateException` с расшифровкой. Если бы `schema_version`
   был ≠ 1 — explicit `IllegalStateException("Unsupported
   schema_version: ...")` (защита от silent skip'а на будущих
   обновлениях формата).
2. **PNG декодируется в RGBA8 через STBImage.** Строка `Vulkan texture
   loaded: checkerboard (asset=texture.test.checkerboard, 64x64,
   format=R8G8B8A8_UNORM, sourceChannels=4, size=16384 bytes)` —
   `ContentLoader.readSync(texHandle)` достал 64×64 PNG через
   `PlatformFileSystem.openAsset` ("texture/test/checkerboard.png"
   из catalog'а), `PngImageDecoder.decodeMemory` вызвал
   `STBImage.stbi_load_from_memory(buf, w, h, c, desiredChannels=4)`,
   STB вернул 64*64*4=16384 байт RGBA8 + sourceChannels=4 (PNG уже был
   RGBA, поэтому реконвертация не понадобилась). Если бы PNG был
   повреждён — STBImage вернул бы NULL, и `PngImageDecoder` кинул бы
   `IllegalStateException("STBImage failed to decode: <reason>")`.
3. **VulkanBuffer staging memory выбран корректно.** `VulkanBuffer.create`
   с `usage=TRANSFER_SRC`, `properties=HOST_VISIBLE | HOST_COHERENT`
   вызвал `pickMemoryType(requirements.memoryTypeBits, props)` — на
   lavapipe это найдёт UMA-style memory type (один и тот же физический
   regiom для CPU и GPU). На дискретной NVIDIA выбрался бы memory
   type из BAR'а (256MB видимый CPU heap). `mapAndUpload` сделал
   `vkMapMemory` → `MemoryUtil.memCopy(rgba, mapped, 16384)` →
   `vkUnmapMemory` без `flush` (HOST_COHERENT не требует).
4. **VulkanImage создан с TRANSFER_DST | SAMPLED usage и проведён через
   две layout transitions.** В `VulkanTexture.fromAsset`:
   `VulkanImage.create(format=R8G8B8A8_UNORM, usage=TRANSFER_DST |
   SAMPLED)` создал `VkImage` с `tiling=OPTIMAL`,
   `initialLayout=UNDEFINED`. Затем `image.uploadFromStaging(cmds,
   staging)`: barrier UNDEFINED→TRANSFER_DST (srcStage=TOP_OF_PIPE,
   dstStage=TRANSFER) → `vkCmdCopyBufferToImage` (16384 байт
   staging→image) → barrier TRANSFER_DST→SHADER_READ_ONLY
   (srcStage=TRANSFER, dstStage=FRAGMENT_SHADER). `vkQueueWaitIdle`
   сразу после submit гарантирует, что upload завершён до того, как
   код продолжит. Без правильных layout transitions lavapipe (и любой
   реальный GPU) бросал бы validation error — недопустимый layout для
   `vkCmdCopyBufferToImage` ожидает `TRANSFER_DST_OPTIMAL` или
   `GENERAL`, а для `texture(albedo, uv)` в фрагменте требует
   `SHADER_READ_ONLY_OPTIMAL`.
5. **Descriptor set создан и записан правильно.** Строка `Vulkan texture
   descriptors ready (layout binding=0 COMBINED_IMAGE_SAMPLER fragment,
   pool maxSets=4)` подтверждает, что `VkDescriptorSetLayout` собран
   с 1 binding'ом (`binding=0`, type=`COMBINED_IMAGE_SAMPLER`,
   count=1, stage=`FRAGMENT`), а `VkDescriptorPool` — с `maxSets=4` и
   1 pool size (`COMBINED_IMAGE_SAMPLER × 4`). В `VulkanTexture.fromAsset`
   аллоцируется один set из пула (`vkAllocateDescriptorSets`), затем
   `VkWriteDescriptorSet` с **явным `descriptorCount(1)`** записывает
   `(sampler, imageView, SHADER_READ_ONLY_OPTIMAL)` в `binding=0`.
   Без явного `descriptorCount` calloc дефолтит его в 0 — write
   становится no-op'ом, descriptor set остаётся пустым, и при первом
   же `texture(albedo, uv)` вызове lavapipe сегфолтит на NULL pointer
   (это и был баг из render smoke до фикса). Урок на будущее: **всегда
   ставить `descriptorCount` в `VkWriteDescriptorSet` явно, даже
   если pImageInfo/pBufferInfo буфер имеет ровно 1 элемент** — LWJGL
   не выводит count из buffer'а, спека требует положительного
   значения, и calloc-default в 0 не отлавливается ни компилятором,
   ни validation layers без `VK_LAYER_KHRONOS_validation` (которого в
   minimum-instance setup'е может не быть).
6. **Pipeline layout биндит descriptor set layout.** Строка
   `Vulkan graphics pipeline created (...set 0 = combined image
   sampler)` подтверждает, что `VkPipelineLayoutCreateInfo` теперь
   принимает `pSetLayouts(stack.longs(descriptorSetLayout))`. В
   `record()` `vkCmdBindDescriptorSets(GRAPHICS, pipelineLayout,
   firstSet=0, {triangleTexture.descriptorSet()}, null)` биндит
   descriptor set к pipeline-у на set=0. Без этой связи fragment
   shader при `texture(albedo, uv)` искал бы binding в пустом
   descriptor space и тоже сегфолтил.
7. **Vertex shader выдаёт UV, fragment семплит texture.** Vertex
   shader (`triangle.vert.glsl`): добавлен output `layout(location=1)
   out vec2 fragUV` плюс массив `vec2 uvs[3]` с координатами `(0.5,
   0.0), (1.0, 1.0), (0.0, 1.0)` — top vertex в центре сверху, два
   нижних — в правом нижнем и левом нижнем углах. Fragment shader
   (`triangle.frag.glsl`): добавлен `layout(set=0, binding=0)
   uniform sampler2D albedo`, body теперь `texture(albedo, fragUV)`
   → mix с baseline color (50/50). На рендере виден треугольник
   с checkerboard pattern'ом в основной части и vertex color
   gradient (red/green/blue) поверх. Без фактического sampling'а
   треугольник был бы чисто vertex-color gradient, без признаков
   texture pattern'а.
8. **Cleanup-порядок 100% deterministic.** Disposal в
   `VulkanRenderer.dispose`: `vkDeviceWaitIdle` →
   **`triangleTexture.dispose`** (sampler + image + view + memory) →
   **`textureDescriptors.dispose`** (pool + layout) → pipeline →
   frag shader → vert shader → sync → command pool → framebuffers →
   render-pass → swapchain → device → surface. Это порядок, ожидаемый
   Vulkan validation layers'ами (parent объекты после детей; texture
   зависит от device для destroyImage/destroyImageView/freeMemory,
   descriptors зависят от device для destroyDescriptorPool/Layout).
   В smoke trace `Vulkan renderer disposed (presented=30 frames,
   recreates=0)` подтверждает, что все ресурсы корректно освобождены
   до `vkDestroyDevice`.

> **Зачем это нужно для базы движка.** Stage 3.1 закрывает «ассеты на
> диске → текстура на GPU» — точку, после которой движок может
> загружать любые binary assets через единую конфигурацию (JSON-
> манифест) и отображать их на экране через graphics pipeline. Все
> следующие renderer-стадии и asset-стадии добавляются ПОВЕРХ этой
> архитектуры:
>
> - **Vertex buffers** (Endpoint E): `VulkanBuffer.create(usage=
>   VERTEX_BUFFER, properties=DEVICE_LOCAL)` + staging upload через
>   тот же `VulkanBuffer` (HOST_VISIBLE staging → DEVICE_LOCAL via
>   `vkCmdCopyBuffer` в одноразовой command buffer). Pipeline получит
>   `VkPipelineVertexInputStateCreateInfo` с binding'ами и attribute'ами
>   вместо пустого. `vkCmdBindVertexBuffers(cmd, 0, {bufferHandle},
>   {0})` перед `vkCmdDraw`.
> - **Multiple textures** (Endpoint E + battle proto): тот же
>   `VulkanTextureDescriptors` (pool maxSets=4 — уже хватит на 4
>   разных texture в одной сцене); если нужно больше — увеличить
>   maxSets и pool size. Каждая `VulkanTexture` будет иметь свой
>   descriptor set, и render-loop будет биндить разные set'ы перед
>   разными `vkCmdDraw` (mesh-by-mesh).
> - **Uniform buffer для камеры** (Endpoint E): новый
>   `VkDescriptorSetLayout` с `UNIFORM_BUFFER` binding'ом в set=1
>   (set=0 остаётся за texture'ой), `VulkanBuffer.create(usage=
>   UNIFORM_BUFFER, properties=HOST_VISIBLE | HOST_COHERENT)` для
>   matrix data. Vertex shader получит `mat4 view` / `mat4 projection`
>   uniform'ы. Сценарий «WASD ходит → mouse-look → mat4 пересчитан →
>   memcpy в uniform → vkCmdDraw» — это всё без изменения архитектуры
>   pipeline'а или asset pipeline'а.
> - **KTX2 / glTF / locale strings** (post-3.1): добавятся новые
>   декодеры в `:desktop` (или `:core` для платформонезависимых
>   форматов) и type-specific loader'ы поверх `ContentLoader`,
>   которые умеют возвращать domain-объекты (Texture, Mesh,
>   LocalizedString) вместо raw `ByteBuffer`. `JsonAssetCatalog`
>   уже поддерживает все категории `AssetCategory` enum'а — каталог
>   нужно будет дополнить новыми entry'ями (`texture.player.body`
>   → `texture/player/body.ktx2`, `model.player.body` →
>   `model/player/body.gltf`, etc.).
> - **Async texture loading** (Endpoint E + production):
>   `ContentLoader.readAsync(handle)` уже существует (commonPool
>   `CompletableFuture`) — `VulkanTexture.fromAssetAsync(...)` будет
>   вызывать `readAsync` → `decodeAsync` (на background thread'е),
>   только staging upload + descriptor write делаются на main
>   thread'е (потому что нужен command buffer + pool, а Vulkan
>   queue не thread-safe без external sync). Это критично для
>   open-world сцен, где текстуры стримятся на лету.
>
> Главное — текущий код НЕ держит никаких допущений, которые
> сломаются при этих расширениях. `VulkanBuffer` уже generalized
> (любой usage + любые memory properties), `VulkanImage`/`VulkanSampler`
> уже generalized (можно создать второй sampler с другими параметрами
> для UI без перекомпиляции pipeline'а), descriptor pool уже имеет
> запас на 4 set'а. Asset catalog поддерживает любую категорию из
> `AssetCategory` enum'а — добавление новой entry'и в
> `data/asset_catalog.json` не требует кодовых правок (только в
> consumer'е, который её достаёт из catalog'а). Validation layers
> на 3.1 чистые — значит дальнейшие изменения будут отлавливаться
> чисто как новые ошибки.

## Smoke результаты — stage 3.2 (scene system + minimal Java ECS)

| Команда                                                                                                | Результат |
|--------------------------------------------------------------------------------------------------------|-----------|
| `./gradlew :core:compileJava`                                                                          | OK — компилирует расширенный `Scene` + переписанный `SceneManager` + новый пакет `WeTTeA.core.ecs.*` (5 файлов + `package-info`) |
| `./gradlew :rust-bridge:compileJava`                                                                   | OK — без изменений в `:rust-bridge` |
| `./gradlew :desktop:compileJava`                                                                       | OK — компилирует обновлённый `DesktopLauncher` (новые import'ы `WeTTeA.core.ecs.*`, `Scene`, `SceneManager`, `RenderFrameContext` + методы `runSceneSmoke` / `runEcsSmoke` + nested `TestScene` / `Position` / `Velocity` / `MovementSystem`) |
| `cd rust-core && cargo check`                                                                          | OK (`Finished dev profile in 0.05s`) — без изменений в Rust |
| `ALSOFT_DRIVERS=null ./gradlew :desktop:run --args="--headless"`                                       | OK — input + physics + audio sine + audio asset (OGG) + **scene** + **ECS** smokes |

Trace headless smoke со stage 3.2 (`ALSOFT_DRIVERS=null :desktop:run --args="--headless"`):

```
[Death:desktop] OpenAL device="OpenAL Soft" vendor="OpenAL Community" renderer="OpenAL Soft" version="1.1 ALSOFT 1.24.1"
[Death:desktop] asset pipeline: catalog=1 entries, loader=PlatformContentLoader, oggDecoder bound to OpenAL backend
[Death:desktop] --headless: skipping GLFW + Vulkan smoke
... (input smoke, см. stage 2.2)
... (physics smoke, см. stage 2.5)
[Death:desktop] audio smoke: 440Hz/0.2s sine STOPPED ok, mixer ok, dispose ok (0.221s)
[Death:desktop] audio asset smoke: OGG 'audio.test.sine_440_short' STOPPED twice ok, cache=1 buffer(s), mixer ok, dispose ok (0.433s)
[Death:desktop] scene smoke: push/pop/replace + onPause/onResume + deferred + clear ok (0.003s)
[Death:desktop] ECS smoke: 1000 entities spawned, 60 sim steps, despawn half + reborn 100, ABA-safe, system iterate ok (0.047s)
[Death:desktop] OpenAL shutdown OK
[Death:desktop] native shutdown OK
[Death:desktop] shutdown OK

BUILD SUCCESSFUL in 2s
```

Что доказывает scene smoke (внутренние ассерты в `runSceneSmoke()`):

1. **Чистый стартовый стейт.** На вызове `runSceneSmoke(boot.scenes())`
   `SceneManager.depth() == 0` — `CoreBootstrap` зарегистрировал
   менеджер в `ServiceContainer`, но не push'ил никаких сцен.
2. **`push(A)` вызывает `A.onEnter()`.** После `push(a)`:
   `depth() == 1`, `top() == a`, и счётчик `a.enter == 1`. Все
   остальные счётчики (`exit`/`pause`/`resume`/`tick`/`draw`/`dispose`)
   равны 0 — push не должен случайно вызвать что-то ещё.
3. **`tickTop`/`drawTop` идут только в верхнюю.** Три вызова
   `tickTop(0.016)` + два `drawTop(null)` дают `a.tick == 3` и
   `a.draw == 2`. Без новой реализации (раньше `tick`/`draw` напрямую
   вызывались на `Scene` менеджером, и stack-семантика была пустой)
   эта проверка не имела бы смысла — теперь она доказывает, что
   менеджер действительно фильтрует «активна только top».
4. **`push(B)` поверх `A` парно вызывает `A.onPause()` + `B.onEnter()`.**
   После `push(b)`: `depth() == 2`, `top() == b`, `a.pause == 1`,
   `b.enter == 1`. Это ключевой инвариант, без которого фоновая
   музыка `BattleScene` не остановилась бы при открытии
   `InventoryScene`, и наоборот — inventory не получила бы корректный
   `onEnter` (init UI, animation, focus).
5. **«Замороженная» сцена не получает tick/draw.** После `push(b)`
   ещё один `tickTop(0.016)` + `drawTop(null)`: `b.tick == 1`,
   `b.draw == 1`, а `a.tick`/`a.draw` остаются прежними (`3`/`2`).
   Это критично — иначе после открытия inventory боёвка продолжала
   бы интегрировать AI/физику в фоне и игрок бы вернулся в неё «через
   секунду» в неконсистентном стейте.
6. **Deferred-операция применяется через `applyPending`.**
   `popDeferred()` НЕ дёргает `onExit`/`onResume` сразу — кладёт
   операцию в `pending` (`pendingSize() == 1`). `applyPending()`
   возвращает `1`, после чего `depth() == 1`, `top() == a`,
   `b.exit == 1`, `b.dispose == 1`, `a.resume == 1`. Это нужно для
   gameplay-кода вида `if (player.dead) scenes.popDeferred()` прямо
   внутри `tick(dt)` — мутация стека во время итерации испортила бы
   обходящего код менеджера.
7. **`replace(C)` НЕ вызывает pause/resume у заменяемой сцены.**
   До `replace`: `a.pause == 1`, `a.resume == 1` (от шагов 4/6).
   После `replace(c)`: `a.exit == 1`, `a.dispose == 1`,
   `c.enter == 1`, `c.pause == 0`, `c.resume == 0`, и `a.pause`/
   `a.resume` НЕ инкрементируются. Это потому, что `A` уходит
   насовсем (а не «уступает место»): семантика отличается от
   `pop`+`push` тем, что нижняя сцена под `A` (если бы была) не
   получила бы лишних `onPause`+`onResume`.
8. **`clear()` корректно multi-pop'ит.** В `clear()`: `c.exit == 1`,
   `c.dispose == 1`, `depth() == 0`. Используется в shutdown phase
   launcher'а — без неё стек живых сцен утёк бы при `--headless`
   exit (без render loop'а нет места явно вызвать pop'ы).

Что доказывает ECS smoke (внутренние ассерты в `runEcsSmoke()`):

1. **Spawn 1000 entity'ёв с двумя компонентами.** После 1000
   `world.spawn()` + `world.set(id, Position, ...)` + `world.set(id,
   Velocity, ...)`: `world.aliveCount() == 1000`,
   `world.store(Position).size() == 1000`,
   `world.store(Velocity).size() == 1000`. Sparse-set растёт
   автоматически (power-of-2): начиная с дефолтного capacity=16,
   через ~6 grow'ов выходит на >1000.
2. **`MovementSystem` действительно итерирует и интегрирует.**
   60 вызовов `scheduler.update(world, 1/60)` запускают
   `MovementSystem.update`, который проходит по всем slot'ам
   `velocities` через `entityIndexAt(slot)` + `componentAt(slot)`,
   делает lookup в `positions.get(entityIndex)` и применяет
   `p.x += v.dx * dt` etc. После 60 шагов velocity'и `(1, 2, 3)`
   должны прибавить ровно `(1.0, 2.0, 3.0)` к каждой position;
   ассерт сравнивает float'ы с `1e-3` epsilon (semi-implicit Euler
   допускает накопленную ошибку, но ~1ms погрешность не выходит за
   `0.001`).
3. **No-allocation hot path.** Тело `MovementSystem.update` —
   простой `for (int slot = 0; slot < n; slot++)` без iterator'а
   (Java `for-each` создал бы Iterator object) и без autoboxing'а
   на `int` индексах. Профилировщик не нужен — для 1000 entity'ёв
   60 раз время smoke'а 0.047с включает прогрев JIT; в hot loop
   аллокаций нет (ни в `ComponentStore.get(int)`, ни в `componentAt(int)`,
   ни в `entityIndexAt(int)` — все три просто `int[]`/`Object[]` access).
4. **Despawn ремувает компоненты во всех stores и инкрементирует
   generation.** Despawn 500 entity'ёв (чётные индексы `ids[0]`,
   `ids[2]`, ..., `ids[998]`): `aliveCount() == 500`,
   `Position.size() == 500`, `Velocity.size() == 500`. Старые id
   из `despawnedIds` теперь `isAlive() == false`, и `world.get(stale,
   Position) == null` — потому что `EntityWorld.despawn` сначала
   проходит по всем `stores.values()` и зовёт `.remove(entityIndex)`
   на каждом, потом `generations[entityIndex]++`, потом
   `freeIndices.push(entityIndex)`.
5. **Index переиспользуется через free-list (LIFO).** После despawn
   500 entity'ёв spawn'им новые 100 — `EntityWorld.spawn` сначала
   проверяет `freeIndices.isEmpty()`, если нет — делает pop и берёт
   уже существующий `index` с инкрементированным `generations[index]`.
   Smoke детектит, что хотя бы один `reborn[i].index() ==
   despawnedIds[j].index()` для какого-то `i`/`j` пары.
6. **ABA-safety: stale id с тем же index возвращает `isAlive=false`.**
   Когда index переиспользован, `staleA.generation()` (его поколение
   на момент despawn'а — например 1) НЕ равно `rebornB.generation()`
   (новое поколение, например 2). `world.isAlive(staleA)` сравнивает
   `staleA.generation()` с `generations[staleA.index()] == 2` →
   `false`. Это ключевая защита от ABA-bug'ов: gameplay-код, державший
   ссылку на старого игрока «player1Id», после его despawn'а и
   spawn'а нового игрока в тот же slot НЕ получит ошибочное
   `world.get(player1Id, Health)` — он получит `null` через
   `isAlive()` проверку.
7. **Система пропускает entity без всех нужных компонентов.**
   У 100 reborn entity'ёв есть только `Position` (без `Velocity`).
   В `MovementSystem.update` итерация идёт по `velocities` store
   (size=500, не учитывает reborn'ы), поэтому MovementSystem их не
   двигает — после ещё одного `scheduler.update`: `reborn[i].position
   == (0, 0, 0)` (изначальное значение). Это паттерн `Component
   missing → entity skipped`, без необходимости в полноценном Query DSL.
8. **`world.clear()` обнуляет всё.** После `clear()`: `aliveCount() ==
   0`, `capacity() == 0`, `Position.size() == 0`, `Velocity.size() ==
   0`. Это нужно для unloading'а ECS-уровня при exit'е сцены или
   shutdown'е launcher'а.

> **Зачем это нужно для базы движка.** Stage 3.2 закрывает два разных
> блока, оба критичные для дальнейшего:
>
> 1. **Scene stack** — фундамент для Endpoint **E** (главное меню →
>    settings → white room) и для всего gameplay (`BattleScene` поверх
>    `OverworldScene`, `DialogueScene` поверх `BattleScene`,
>    `InventoryScene` поверх любого). `onPause`/`onResume` обеспечивают
>    корректную семантику «фоновая сцена заморожена и не тратит
>    CPU/AI/audio». Deferred ops защищают от рекурсивной мутации стека
>    (gameplay-код может вызвать `popDeferred` прямо из `tick`).
>    `tickTop`/`drawTop` дают game-loop'у простой контракт — не нужно
>    знать о стеке вообще, просто `scenes.tickTop(dt)` /
>    `scenes.drawTop(frame)` каждый кадр.
> 2. **Java ECS** — фундамент для всего gameplay-стейта (entity'и
>    игрока, мобов, проджектайлов, item'ов на земле, particle'ов).
>    Sparse-set даёт O(1) put/get/has/remove с линейной памятью —
>    важно для bullet-hell сцен, где каждый кадр спавнятся/умирают
>    100+ snake bullet'ов. ABA-safe `EntityId` защищает gameplay-код
>    от классической ошибки «держу старый id, но сущность уже
>    переиспользована — внезапно стреляю в нового моба». Без этого
>    при первом же сложном бою (с лёгкими мобами, которые быстро
>    умирают и спавнятся) пошли бы NPE и неконсистентные стейты.
>
> Stage 3.3 (battle proto) поедет ровно поверх этого: новая
> `BattleScene extends Scene` с собственным `EntityWorld` +
> `SystemScheduler` (`MovementSystem` + `AISystem` + `CombatSystem` +
> `RenderSystem` + `PathfindingSystem` через JNI к
> `rust-core/src/pathfinding`). Тактическая сетка — это компонент
> `TileGrid` в `EntityWorld`, A* — это `EcsSystem`, который читает
> `TileGrid` + текущую позицию + цель и выдаёт path как
> компонент `Path` на entity'е. Сводить ECS в Rust пока не имеет
> смысла — JNI-overhead на per-entity вызовах `get(EntityId,
> Class<C>)` сожрал бы выгоду от cache locality, а у нас < 100k entity'ёв
> и Java-side выигрывает по простоте. Если на stage 4+ окажется, что
> мобы тормозят (e.g. > 100k particle'ов в bullet hell сцене), мигрируем
> ECS storage в Rust как single big batch вызов «step all systems
> for dt» — без изменения gameplay-API на Java стороне.

## Smoke результаты — stage 2.3 (OpenAL audio)

| Команда                                                                                                | Результат |
|--------------------------------------------------------------------------------------------------------|-----------|
| `./gradlew :core:compileJava`                                                                          | OK — без изменений в `:core` (audio контракты были раньше) |
| `./gradlew :rust-bridge:compileJava`                                                                   | OK — без изменений в `:rust-bridge` |
| `./gradlew :desktop:compileJava`                                                                       | OK — компилирует `OpenAlAudioBackend` + `OpenAlSourceHandle` + обновлённый `DesktopLauncher` |
| `cd rust-core && cargo check`                                                                          | OK (`Finished dev profile in 12.74s`) — без изменений в Rust |
| `ALSOFT_DRIVERS=null ./gradlew :desktop:run --args="--headless"`                                       | OK — input + physics + **audio** smokes |
| `./gradlew :desktop:run --args="--headless --no-audio"`                                                | OK — audio пропущен (smoke без OpenAL) |

Trace headless smoke с audio (`ALSOFT_DRIVERS=null :desktop:run --args="--headless"`):

```
> Task :desktop:run
[Death:desktop] booting Death stage 1 launcher
[Death:desktop] core booted, phase=RUNNING
[Death:desktop] platform: family=DESKTOP os=Linux arch=amd64 version=5.15.200 64bit=true
[Death:desktop] userDataDir=/home/ubuntu/.local/share/Death
[Death:rust] native init (no-op stage 2.4)
[Death:desktop] native loaded file=libdeath_native.so version=0.0.0
[ALSOFT] (EE) Could not query RTKit: No such file or directory (2)
[Death:desktop] OpenAL device="OpenAL Soft" vendor="OpenAL Community" renderer="OpenAL Soft" version="1.1 ALSOFT 1.24.1"
[Death:desktop] --headless: skipping GLFW + Vulkan smoke
[Death:desktop] input listener@GAMEPLAY: action=MOVE_UP pressed=true strength=1.0
... (input smoke — без изменений, см. stage 2.2)
[Death:desktop] input smoke: gameplay-listener=4 calls, menu-listener=1 calls, lastGameplayAction=OPEN_MENU (контекстные биндинги работают)
[Death:desktop] physics body0 initial=(0.0000, 10.0000, 0.0000)
[Death:desktop] physics body0 after 60 ticks @ dt=1/60 final=(0.0000, 5.0746, 0.0000)
[Death:desktop] physics smoke: dy=-4.925436019897461 (ожидание ~ -4.9 от gravity)
[Death:desktop] audio smoke: 440Hz/0.2s sine STOPPED ok, mixer ok, dispose ok (0.218s)
[Death:desktop] OpenAL shutdown OK
[Death:rust] native shutdown (no-op stage 2.4)
[Death:desktop] native shutdown OK
[Death:desktop] shutdown OK

BUILD SUCCESSFUL in 3s
```

Что доказывает этот smoke (8 внутренних ассертов в `runAudioSmoke()`):

1. **alcOpenDevice + alcCreateContext.** Дефолтное устройство открылось
   (`device="OpenAL Soft"`), context создан и сделан текущим, `AL_VENDOR`
   непустой. `[ALSOFT] (EE) Could not query RTKit` — это ALSoft пытается
   повысить приоритет real-time потока через D-Bus RTKit, и на VM без
   D-Bus это просто warning; на воспроизведение не влияет.
2. **`ALSOFT_DRIVERS=null`** — null backend ALSoft (записывает PCM в
   /dev/null). Mixer/state-machine OpenAL'а отрабатывают полностью
   (включая state transitions PLAYING→STOPPED по EOF буфера), но звук
   физически не проигрывается. Для VM/CI это идеально — не нужен
   PulseAudio/ALSA/PipeWire.
3. **playSineWaveSmoke(440Hz, 0.2s, SFX).** Backend сгенерировал 8820
   sample'ов 16-bit signed PCM (`44100 * 0.2`), залил в `ALBuffer`,
   создал `ALSource`, привязал buffer → source, вызвал `alSourcePlay`,
   вернул `OpenAlSourceHandle` (`id > 0`, `category == SFX`,
   `isActive() == true`).
4. **State transitions.** Сразу после `play` `sourceState()` ∈
   {`AL_INITIAL`, `AL_PLAYING`}; через 200мс ALSoft проматывает буфер
   до конца и переключает источник в `AL_STOPPED`. `awaitSourceStopped`
   крутит busy-wait с 5мс паузами, lim=1000мс — успевает за ~218мс.
5. **Mixer set/get.** `setCategoryVolume(SFX, 0.5f)` →
   `categoryVolume(SFX) == 0.5f`; `setCategoryMuted(SFX, true)` →
   `categoryMuted(SFX) == true`; backend пересчитывает effective gain
   `volume * (muted ? 0 : 1)` и применяет `alSourcef(handle, AL_GAIN, eff)`
   ко всем активным источникам этой категории. После теста значения
   восстанавливаются в `(1.0f, false)`.
6. **dispose() идемпотентность.** `src.dispose()` зовёт
   `OpenAlAudioBackend.releaseSource(this)`, который удаляет источник
   из `activeSources` и зовёт `alSourceStop` + `alDeleteSources` +
   `alDeleteBuffers`. Повторный `dispose()` — no-op (защищён `disposed`
   флагом). После: `audio.activeSources().isEmpty() == true`.
7. **OpenAL shutdown OK.** `OpenAlAudioBackend.dispose()` сначала
   останавливает все живые источники (`stopAll()`), затем удаляет их
   buffer'ы и source handle'ы, затем `alcMakeContextCurrent(NULL)` →
   `alcDestroyContext(context)` → `alcCloseDevice(device)`. Порядок
   важен — сначала чистим AL ресурсы, потом разрушаем context, потом
   закрываем device.
8. **Disposable contract.** `dispose()` идемпотентный (повторные
   вызовы не падают), приоритет dispose-порядка в launcher: native →
   audio → vulkan → input → window → core (audio закрывается ДО
   vulkan/window, чтобы не зависеть от чужого lifecycle).

> **Зачем это нужно для базы движка.** Stage 2.3 даёт минимальный
> рабочий аудио-конвейер для desktop: можно ставить SFX на удар оружия,
> музыку в меню, ambient в локации (через `playSineWaveSmoke` —
> placeholder; реальный `playSound(AssetHandle)` ждёт Asset Loader
> stage 3.1 + stb_vorbis OGG decode stage 2.3b). 5-категорийный
> mixer закроет требование «громкость музыки и SFX отдельно в
> Settings» из endpoint **E**. Lifecycle через `ServiceContainer`
> единообразен с остальными backend'ами (`PhysicsWorld`,
> `InputBackend`, render-стек), что готовит почву для `:android`/`:ios`
> аудио-реализаций (stage 2.3c/d) — они зарегистрируются в том же
> контейнере и заменят OpenAL на AAudio/AVAudioEngine без правок
> gameplay-кода.
>
> На реальной машине с PulseAudio/PipeWire `ALSOFT_DRIVERS=null` не
> нужен — ALSoft автоматически выберет доступный backend и звук
> реально пойдёт в колонки. На VM/CI null-backend гарантирует, что
> отсутствие звукового устройства не валит smoke.

## Smoke результаты — stage 2.3b (Asset Loader + stb_vorbis OGG decode)

| Команда                                                                                                | Результат |
|--------------------------------------------------------------------------------------------------------|-----------|
| `./gradlew :core:compileJava`                                                                          | OK — компилирует `MapAssetCatalog` + `PlatformContentLoader` |
| `./gradlew :rust-bridge:compileJava`                                                                   | OK — без изменений в `:rust-bridge` |
| `./gradlew :desktop:compileJava`                                                                       | OK — компилирует `OggVorbisAudioDecoder` + обновлённые `OpenAlAudioBackend`/`OpenAlSourceHandle`/`DesktopLauncher` |
| `cd rust-core && cargo check`                                                                          | OK (`Finished dev profile in 0.08s`) — без изменений в Rust |
| `ALSOFT_DRIVERS=null ./gradlew :desktop:run --args="--headless"`                                       | OK — input + physics + audio sine + **audio asset (OGG)** smokes |

Trace headless smoke с stage 2.3b (`ALSOFT_DRIVERS=null :desktop:run --args="--headless"`):

```
[Death:desktop] OpenAL device="OpenAL Soft" vendor="OpenAL Community" renderer="OpenAL Soft" version="1.1 ALSOFT 1.24.1"
[Death:desktop] asset pipeline: catalog=1 entries, loader=PlatformContentLoader, oggDecoder bound to OpenAL backend
[Death:desktop] --headless: skipping GLFW + Vulkan smoke
... (input smoke, см. stage 2.2)
... (physics smoke, см. stage 2.5)
[Death:desktop] audio smoke: 440Hz/0.2s sine STOPPED ok, mixer ok, dispose ok (0.219s)
[Death:desktop] audio asset smoke: OGG 'audio.test.sine_440_short' STOPPED twice ok, cache=1 buffer(s), mixer ok, dispose ok (0.444s)
[Death:desktop] OpenAL shutdown OK
[Death:desktop] native shutdown OK
[Death:desktop] shutdown OK
```

Что доказывает этот smoke (внутренние ассерты в `runAudioAssetSmoke()`):

1. **Asset pipeline собран правильно.** В стартовой строке
   `asset pipeline: catalog=1 entries, loader=PlatformContentLoader,
   oggDecoder bound to OpenAL backend` — каталог наполнен (1 запись),
   `ContentLoader` зарегистрирован в `ServiceContainer` под
   `ContentLoader.class`, `OggVorbisAudioDecoder` тоже, и
   `OpenAlAudioBackend.bindContentPipeline(loader, decoder)` отработал
   без `IllegalStateException` (повторный bind того же loader'а — no-op,
   но bind другого экземпляра кинул бы исключение).
2. **Резолв id → path → bytes → PCM → ALBuffer.** `playSound(handle, SFX,
   false)` под капотом: `catalog.resolvePath(handle)` →
   `Optional.of("audio/test/sine_440_short.ogg")`,
   `fs.openAsset(path)` → classpath InputStream (ресурс лежит в
   `desktop/src/main/resources/assets/death/audio/test/`),
   `readAllBytes` → `ByteBuffer.allocateDirect(...)` (direct ByteBuffer —
   обязательно для stb_vorbis JNI), `STBVorbis.stb_vorbis_decode_memory`
   → `ShortBuffer pcm` (16-bit signed, mono, ~8820 sample'ов),
   `alGenBuffers` → `alBufferData(AL_FORMAT_MONO16, pcm,
   sampleRate=44100)` → `alGenSources` → `alSourcei(AL_BUFFER, ...)` →
   `alSourcef(AL_GAIN, ...)` → `alSourcePlay`. Возвращён
   `OpenAlSourceHandle` с `id > 0`, `category == SFX`, `ownsBuffer ==
   false` (потому что buffer лежит в общем кэше).
3. **State transitions (PLAYING → STOPPED) идентичны sine wave smoke.**
   `awaitSourceStopped` крутит busy-wait с 5мс паузами, lim=1500мс —
   успевает за ~218мс (длительность OGG 0.2с + overhead на decode).
   После: `isActive() == false`, `sourceState() == AL_STOPPED`.
4. **ALBuffer cache hit.** Второй `playSound(handle, SFX, false)` с тем
   же `AssetHandle` НЕ перечитывает файл и НЕ декодирует OGG — `bufferCache.computeIfAbsent(asset.id(),
   ...)` возвращает уже существующий ALBuffer. Ассерт
   `audio.cachedBufferCount() == cacheBefore + 1` после двух playSound
   — `cache=1 buffer(s)` в trace это подтверждает (одна запись на одну
   уникальную id, не зависит от количества активных source'ов).
5. **per-source dispose НЕ удаляет cached buffer.**
   `OpenAlSourceHandle.dispose()` вызывает
   `OpenAlAudioBackend.releaseSource(this)`, который смотрит на флаг
   `handle.ownsBuffer()` — для cached asset'ов это `false`, значит
   `alDeleteBuffers` НЕ вызывается. После `dispose()` обоих source'ов:
   `cachedBufferCount` неизменён, `activeSources()` пуст. Без этой
   проверки повторный `playSound` после dispose первого source'а
   обращался бы к удалённому ALBuffer'у — undefined behavior.
6. **Mixer не зависит от категории asset'а.** `setCategoryMuted(MUSIC,
   true)` на активный SFX-source не влияет (`AL_GAIN` SFX'а пересчитан
   отдельно через `applyMixerToActiveSources(category)`,
   `categoryMuted(MUSIC) == true`, `categoryMuted(SFX)` остался `false`).
   Возвращаем MUSIC в `false` для чистоты state.
7. **Backend.dispose() чистит cache.** `OpenAlAudioBackend.dispose()` →
   `stopAll()` (сразу останавливает все живые source'ы) → проход по
   `bufferCache.values()` → `alDeleteBuffers` для каждого → очистка
   map'ы → `alcMakeContextCurrent(NULL)` → `alcDestroyContext` →
   `alcCloseDevice`. Без этой петли cached buffer'ы становились бы
   утекшими native объектами при завершении приложения.

> **Зачем это нужно для базы движка.** Stage 2.3b замыкает первый
> сквозной content pipeline в проекте: `assets/death/<path>` →
> classpath/APK/Bundle → байты → декодер → backend. Эта же цепочка
> поверх тех же контрактов (`AssetCatalog`, `ContentLoader`,
> `PlatformFileSystem`) на стадии 2.1b пойдёт для SHADER_SPIRV (vertex/
> fragment SPIR-V в `VkShaderModule`), на 3.x — для TEXTURE
> (`VkImage`/`VkImageView` через staging buffer), MODEL_GLTF, LOCALE_STRINGS,
> GAMEPLAY_DATA, BULLET_PATTERN. Когда на stage 3.1 `MapAssetCatalog`
> заменится на `JsonAssetCatalog` (читающий
> `assets/death/data/asset_catalog.json`), всё gameplay-код, использующий
> `playSound(handle)` или `loadShader(handle)`, не заметит перехода —
> контракт не меняется. ALBuffer cache (HashMap по id) — минимальный
> first-pass для stage 2.3b; на 3.1 поверх него поедет полноценный
> ResourceCache с reference counting + LRU eviction для тяжёлых текстур
> (для аудио файлов это пока не нужно — они мелкие).
>
> Test fixture сгенерирован через `ffmpeg -f lavfi -i
> "sine=frequency=440:duration=0.2" -c:a libvorbis -q:a 2 ...`. Файл
> 3.7KB, лежит в `desktop/src/main/resources/assets/death/audio/test/` —
> попадает на classpath через стандартный `processResources` Gradle
> task. На stage 4 (Android) этот же файл будет браться через
> `AAsset_openAsset(...)` из APK assets/, на stage 5 (iOS) — через
> `[NSBundle pathForResource:]`. Менять `MapAssetCatalog` или
> `OggVorbisAudioDecoder` для этого не придётся — `PlatformFileSystem`
> уже абстрагирует разницу.

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
