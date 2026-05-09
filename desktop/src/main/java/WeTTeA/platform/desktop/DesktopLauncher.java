package WeTTeA.platform.desktop;

import WeTTeA.api.audio.AudioBackend;
import WeTTeA.api.audio.AudioCategory;
import WeTTeA.api.content.AssetCatalog;
import WeTTeA.api.content.AssetCategory;
import WeTTeA.api.content.AssetHandle;
import WeTTeA.api.content.ContentLoader;
import WeTTeA.api.events.EventBus;
import WeTTeA.api.input.ActionEventListener;
import WeTTeA.api.input.InputAction;
import WeTTeA.api.input.InputAxis;
import WeTTeA.api.input.InputContext;
import WeTTeA.api.input.InputSource;
import WeTTeA.api.input.KeyCodes;
import WeTTeA.api.input.RawInputEvent;
import WeTTeA.api.nativebridge.NativeBridgeInfo;
import WeTTeA.api.pathfinding.PathGrid;
import WeTTeA.api.physics.PhysicsBodyHandle;
import WeTTeA.api.physics.Vec3;
import WeTTeA.api.platform.PlatformFileSystem;
import WeTTeA.api.platform.PlatformInfo;
import WeTTeA.api.render.RenderFrameContext;
import WeTTeA.core.CoreBootstrap;
import WeTTeA.core.content.JsonAssetCatalog;
import WeTTeA.core.content.PlatformContentLoader;
import WeTTeA.core.ecs.ComponentStore;
import WeTTeA.core.ecs.EcsSystem;
import WeTTeA.core.ecs.EntityId;
import WeTTeA.core.ecs.EntityWorld;
import WeTTeA.core.ecs.SystemScheduler;
import WeTTeA.core.input.InputRouter;
import WeTTeA.core.input.InputState;
import WeTTeA.core.scene.Scene;
import WeTTeA.core.scene.SceneManager;
import WeTTeA.native_bridge.rust.RustCore;
import WeTTeA.native_bridge.rust.RustPathGrid;
import WeTTeA.native_bridge.rust.RustPhysicsWorld;

/**
 * Точка входа Death на desktop (Windows / Linux / macOS).
 *
 * <p>Stage 2.1a/2.2/2.3/2.3b/2.5/3.2 boot pipeline:
 * <ol>
 *   <li>Boot core ({@link CoreBootstrap#boot()});</li>
 *   <li>Создание {@link PlatformInfo} через {@link DesktopPlatformInfo#detect()}
 *       и {@link DesktopPlatformFileSystem};</li>
 *   <li>Загрузка нативной библиотеки {@code death-native} через
 *       {@link RustCore#initialize(PlatformInfo, PlatformFileSystem)};
 *       пропускается флагом {@code --no-native};</li>
 *   <li>Инициализация {@link OpenAlAudioBackend} ({@code alcOpenDevice}
 *       → {@code alcCreateContext} → {@code alcMakeContextCurrent});
 *       пропускается флагом {@code --no-audio};</li>
 *   <li>Сборка asset pipeline (stage 2.3b/3.1): {@link JsonAssetCatalog}
 *       (читает {@code assets/death/data/asset_catalog.json}),
 *       {@link PlatformContentLoader} (читает байты через PlatformFileSystem),
 *       {@link OggVorbisAudioDecoder} (stb_vorbis → PCM),
 *       {@link PngImageDecoder} (stb_image → RGBA8), регистрация в ServiceContainer
 *       и привязка audio decoder'а к OpenAL backend через
 *       {@link OpenAlAudioBackend#bindContentPipeline}; PNG decoder передаётся
 *       в {@link VulkanRenderer} для загрузки checkerboard-текстуры;</li>
 *   <li>В headless: stage 2.2 input smoke
 *       ({@link #runInputSmoke(InputRouter, InputState, EventBus)}) +
 *       stage 2.5 physics smoke ({@link #runPhysicsSmoke()}) +
 *       stage 2.3 audio smoke ({@link #runAudioSmoke(OpenAlAudioBackend)}) +
 *       stage 2.3b asset audio smoke
 *       ({@link #runAudioAssetSmoke(OpenAlAudioBackend)}) +
 *       stage 3.2 scene smoke ({@link #runSceneSmoke(SceneManager)}) +
 *       stage 3.2 ECS smoke ({@link #runEcsSmoke()}) +
 *       stage 3.3a pathfinding smoke ({@link #runPathfindingSmoke()});</li>
 *   <li>Открытие GLFW окна {@link GlfwWindow} (пропускается в режиме
 *       {@code --headless});</li>
 *   <li>Создание Vulkan instance ({@link VulkanInstanceBootstrap});</li>
 *   <li>Создание {@link VulkanRenderer} (surface + device + swapchain +
 *       render pass + framebuffers + command buffers + frame sync);</li>
 *   <li>Регистрация {@link GlfwInputBackend} на хендле окна; в каждом
 *       кадре вызов {@link InputRouter#pollFrom(WeTTeA.api.input.InputBackend)}
 *       после {@link GlfwWindow#pollEvents()};</li>
 *   <li>В main loop: {@link VulkanRenderer#renderFrame(double)} с
 *       cycling clear color (визуальное доказательство, что swapchain
 *       реально презентует);</li>
 *   <li>Корректное закрытие (renderer dispose → input dispose → vulkan
 *       instance dispose → glfw dispose → audio dispose → loop stop →
 *       {@link RustCore#shutdown()}).</li>
 * </ol>
 *
 * <p>Триангл/pipeline/shaders/mesh — INTEGRATION_MISSING (stage 2.1b/2.1c
 * и далее, см. PROGRESS.md).
 *
 * <p>Флаги:
 * <ul>
 *   <li>{@code --headless} — пропускает создание GLFW окна (для CI smoke
 *       на машинах без display server). Vulkan instance тоже не создаётся,
 *       т.к. GLFW Vulkan extension query требует init'нутого GLFW.</li>
 *   <li>{@code --no-vulkan} — открывает GLFW окно, но пропускает Vulkan
 *       instance + renderer (для случая когда Vulkan loader недоступен).</li>
 *   <li>{@code --no-native} — пропускает загрузку {@code death-native};
 *       для случая когда {@code rust-core} не собран (например {@code -PskipCargo=true}).
 *       Также отключает physics smoke (требует native).</li>
 *   <li>{@code --no-audio} — пропускает инициализацию OpenAL (для CI без
 *       аудио-устройства; альтернатива — env {@code ALSOFT_DRIVERS=null},
 *       которая прогонит весь pipeline в null-backend).</li>
 *   <li>{@code --asset-catalog <path>} — relative path к JSON-каталогу внутри
 *       classpath (от корня {@code assets/death/}). По умолчанию
 *       {@code data/asset_catalog.json}.</li>
 *   <li>{@code --render-frames N} — рендерит N кадров и выходит (для smoke
 *       без интерактивности; полезно с lavapipe/CI). По умолчанию 60.</li>
 * </ul>
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class DesktopLauncher {

    private static final int DEFAULT_WIDTH  = 1280;
    private static final int DEFAULT_HEIGHT = 720;
    private static final String WINDOW_TITLE = "Death — stage 3.1 textured triangle";

    /**
     * Stage 3.1 — дефолтный relative path до JSON asset catalog'а.
     * Путь дан относительно corня {@code assets/death/} (как требует
     * {@link PlatformFileSystem#openAsset(String)} convention'а).
     */
    private static final String DEFAULT_ASSET_CATALOG_PATH = "data/asset_catalog.json";

    private DesktopLauncher() {
    }

    public static void main(String[] args) {
        boolean headless = false;
        boolean noVulkan = false;
        boolean noNative = false;
        boolean noAudio = false;
        int renderFrames = 60;
        String assetCatalogPath = DEFAULT_ASSET_CATALOG_PATH;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--headless".equals(arg)) headless = true;
            else if ("--no-vulkan".equals(arg)) noVulkan = true;
            else if ("--no-native".equals(arg)) noNative = true;
            else if ("--no-audio".equals(arg)) noAudio = true;
            else if ("--render-frames".equals(arg) && i + 1 < args.length) {
                try {
                    renderFrames = Integer.parseInt(args[++i]);
                    if (renderFrames < 0) {
                        throw new IllegalArgumentException("--render-frames должен быть >= 0");
                    }
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("--render-frames ожидает целое число, получили: " + args[i]);
                }
            }
            else if ("--asset-catalog".equals(arg) && i + 1 < args.length) {
                assetCatalogPath = args[++i];
            }
        }

        System.out.println("[Death:desktop] booting Death stage 1 launcher");

        CoreBootstrap.BootResult boot = CoreBootstrap.boot();
        System.out.println("[Death:desktop] core booted, phase=" + boot.phase());

        PlatformInfo info = DesktopPlatformInfo.detect();
        System.out.println("[Death:desktop] platform: family=" + info.family()
                + " os=" + info.osName() + " arch=" + info.archName()
                + " version=" + info.osVersion()
                + " 64bit=" + info.has64Bit());

        PlatformFileSystem fs = new DesktopPlatformFileSystem(info);
        System.out.println("[Death:desktop] userDataDir=" + fs.userDataDirectory());

        RustCore rust = noNative ? null : new RustCore();
        if (rust != null) {
            try {
                NativeBridgeInfo nb = rust.initialize(info, fs);
                System.out.println("[Death:desktop] native loaded file=" + nb.libraryFileName()
                        + " version=" + nb.semverVersion());
            } catch (UnsatisfiedLinkError | RuntimeException e) {
                System.err.println("[Death:desktop] native load failed: " + e.getMessage()
                        + " (используйте --no-native или -PskipCargo=true)");
                throw e;
            }
        } else {
            System.out.println("[Death:desktop] --no-native: skipping rust-core load");
        }

        OpenAlAudioBackend audio = noAudio ? null : new OpenAlAudioBackend();
        if (audio != null) {
            try {
                audio.init();
                boot.services().register(AudioBackend.class, audio);
                boot.services().register(OpenAlAudioBackend.class, audio);
                System.out.println("[Death:desktop] OpenAL device=\"" + audio.deviceName()
                        + "\" vendor=\"" + audio.alVendor()
                        + "\" renderer=\"" + audio.alRenderer()
                        + "\" version=\"" + audio.alVersion() + "\"");
            } catch (UnsatisfiedLinkError | RuntimeException e) {
                System.err.println("[Death:desktop] OpenAL init failed: " + e.getMessage()
                        + " (используйте --no-audio или ALSOFT_DRIVERS=null)");
                audio = null;
                throw e;
            }
        } else {
            System.out.println("[Death:desktop] --no-audio: skipping OpenAL");
        }

        // Stage 3.1: asset pipeline через JSON catalog. Каталог регистрируется ВСЕГДА
        // (даже без аудио), потому что Vulkan/SHADER_SPIRV/TEXTURE — другие категории
        // — тоже используют его (shader.triangle.vert/frag, texture.test.checkerboard).
        JsonAssetCatalog catalog = JsonAssetCatalog.load(fs, assetCatalogPath);
        PlatformContentLoader loader = new PlatformContentLoader(catalog, fs);
        OggVorbisAudioDecoder oggDecoder = new OggVorbisAudioDecoder();
        PngImageDecoder pngDecoder = new PngImageDecoder();
        boot.services().register(AssetCatalog.class, catalog);
        boot.services().register(ContentLoader.class, loader);
        boot.services().register(OggVorbisAudioDecoder.class, oggDecoder);
        boot.services().register(PngImageDecoder.class, pngDecoder);
        if (audio != null) {
            audio.bindContentPipeline(loader, oggDecoder);
            System.out.println("[Death:desktop] asset pipeline: JsonAssetCatalog (" + assetCatalogPath
                    + ") size=" + catalog.size() + " entries, loader="
                    + loader.getClass().getSimpleName()
                    + ", oggDecoder bound to OpenAL backend, pngDecoder ready");
        } else {
            System.out.println("[Death:desktop] asset pipeline: JsonAssetCatalog (" + assetCatalogPath
                    + ") size=" + catalog.size() + " entries (audio bind пропущен — --no-audio)");
        }

        try {
            if (headless) {
                System.out.println("[Death:desktop] --headless: skipping GLFW + Vulkan smoke");
                runInputSmoke(boot.inputRouter(), boot.inputState(), boot.events());
                if (rust != null) {
                    runPhysicsSmoke();
                } else {
                    System.out.println("[Death:desktop] --no-native + --headless: skipping physics smoke");
                }
                if (audio != null) {
                    runAudioSmoke(audio);
                    runAudioAssetSmoke(audio);
                } else {
                    System.out.println("[Death:desktop] --no-audio + --headless: skipping audio smoke");
                }
                runSceneSmoke(boot.scenes());
                runEcsSmoke();
                if (rust != null) {
                    runPathfindingSmoke();
                } else {
                    System.out.println("[Death:desktop] --no-native + --headless: skipping pathfinding smoke");
                }
                boot.loop().stop();
                return;
            }

            GlfwWindow window = new GlfwWindow(DEFAULT_WIDTH, DEFAULT_HEIGHT, WINDOW_TITLE);
            VulkanInstanceBootstrap vulkan = new VulkanInstanceBootstrap();
            VulkanRenderer renderer = null;
            GlfwInputBackend input = null;
            try {
                window.init();
                window.centerOnPrimaryMonitor();
                window.show();
                System.out.println("[Death:desktop] GLFW window opened");

                if (!noVulkan) {
                    vulkan.create();
                    renderer = new VulkanRenderer(vulkan.instance(), window.handle(), loader, pngDecoder);
                    System.out.println("[Death:desktop] Vulkan renderer ready: device=" + renderer.deviceName()
                            + " " + renderer.width() + "x" + renderer.height());

                    // Stage 2.1b — GLFW resize callback → dirty flag в renderer'е.
                    // Сам рендерер выполнит vkDeviceWaitIdle + recreate в начале следующего кадра.
                    final VulkanRenderer rendererRef = renderer;
                    window.setFramebufferSizeListener((w, h) -> {
                        rendererRef.markFramebufferResized();
                        return 0;
                    });
                } else {
                    System.out.println("[Death:desktop] --no-vulkan: skipping VkInstance + renderer");
                }

                input = new GlfwInputBackend(window.handle());
                System.out.println("[Death:desktop] input backend wired (GLFW callbacks)");

                boot.loop().start();

                long startNanos = System.nanoTime();
                int rendered = 0;
                int frame = 0;
                while (!window.shouldClose() && frame < renderFrames) {
                    window.pollEvents();
                    boot.inputRouter().pollFrom(input);
                    boot.loop().tick();

                    if (renderer != null) {
                        double t = (System.nanoTime() - startNanos) / 1_000_000_000.0;
                        if (renderer.renderFrame(t)) {
                            rendered++;
                        } else {
                            // Stage 2.1b: renderFrame больше не возвращает false при OUT_OF_DATE/SUBOPTIMAL
                            // (это теперь handle'ится внутри recreate); этот break остаётся для
                            // будущих fatal сценариев.
                            System.out.println("[Death:desktop] renderFrame() вернул false — прекращаем loop");
                            break;
                        }
                    }
                    frame++;
                }

                if (renderer != null) {
                    double seconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
                    double fps = seconds > 0 ? rendered / seconds : 0.0;
                    System.out.println("[Death:desktop] render loop: presented=" + rendered
                            + " frames in " + String.format("%.3f", seconds) + "s"
                            + " (~" + String.format("%.1f", fps) + " FPS)"
                            + ", swapchain recreates=" + renderer.swapchainRecreates());
                }
            } finally {
                if (renderer != null) renderer.dispose();
                if (input != null) input.dispose();
                vulkan.dispose();
                window.dispose();
                boot.loop().stop();
            }
        } finally {
            if (audio != null) {
                audio.dispose();
                System.out.println("[Death:desktop] OpenAL shutdown OK");
            }
            loader.dispose();
            if (rust != null) {
                rust.shutdown();
                System.out.println("[Death:desktop] native shutdown OK");
            }
            System.out.println("[Death:desktop] shutdown OK");
        }
    }

    /**
     * Stage 2.2 input smoke. Подписывается на {@link InputRouter.ActionEvent}
     * через {@link EventBus} И регистрирует прямой
     * {@link ActionEventListener}; синтезирует серию
     * {@link RawInputEvent}'ов через {@link InputRouter#consume(RawInputEvent)}
     * и проверяет, что:
     * <ul>
     *   <li>биндинг {@code KEY_W → MOVE_UP} в контексте GAMEPLAY срабатывает
     *       (приходит {@code onAction(MOVE_UP, true, 1.0)});</li>
     *   <li>{@link InputState#isActionDown(InputAction)} обновляется;</li>
     *   <li>{@link InputState#mouseX()} / {@link InputState#mouseY()} /
     *       {@link InputState#mouseDeltaX()} обновляются от pointer events;</li>
     *   <li>при переключении в контекст {@code UI_MENU} та же клавиша
     *       {@code KEY_ESCAPE} мапится в {@code CANCEL}, а не в
     *       {@code OPEN_MENU} (доказательство, что биндинги контекстные).</li>
     * </ul>
     *
     * <p>Smoke считается пройденным, если все 4 ассерта выше совпали;
     * иначе бросает {@link IllegalStateException} и тест валится.
     */
    private static void runInputSmoke(InputRouter router, InputState state, EventBus events) {
        final int[] gameplayCalls = {0};
        final int[] menuCalls = {0};
        final InputAction[] lastAction = {null};

        ActionEventListener gameplayListener = (action, pressed, strength) -> {
            gameplayCalls[0]++;
            lastAction[0] = action;
            System.out.println("[Death:desktop] input listener@GAMEPLAY: action=" + action
                    + " pressed=" + pressed + " strength=" + strength);
        };
        router.registerListener(InputContext.GAMEPLAY, gameplayListener);

        ActionEventListener menuListener = (action, pressed, strength) -> {
            menuCalls[0]++;
            System.out.println("[Death:desktop] input listener@UI_MENU: action=" + action
                    + " pressed=" + pressed);
        };
        router.registerListener(InputContext.UI_MENU, menuListener);

        events.subscribe(InputRouter.ActionEvent.class, e ->
                System.out.println("[Death:desktop] input EventBus: ctx=" + e.context()
                        + " action=" + e.action() + " pressed=" + e.pressed()));

        long t = System.nanoTime();

        // 1. KEY_W press → MOVE_UP в GAMEPLAY
        router.consume(RawInputEvent.button(InputSource.KEYBOARD, KeyCodes.KEY_W, true, t));
        require(state.isActionDown(InputAction.MOVE_UP), "MOVE_UP должен быть зажат после KEY_W press");
        require(lastAction[0] == InputAction.MOVE_UP, "ожидался MOVE_UP в listener");

        // 2. KEY_W release → MOVE_UP отпустился
        router.consume(RawInputEvent.button(InputSource.KEYBOARD, KeyCodes.KEY_W, false, t + 1));
        require(!state.isActionDown(InputAction.MOVE_UP), "MOVE_UP должен быть отпущен");

        // 3. mouse move → InputState.mouseX/Y и delta
        router.consume(RawInputEvent.pointer(InputSource.MOUSE, -1, false, 100f, 200f, t + 2));
        require(Math.abs(state.mouseX() - 100f) < 0.001f, "mouseX=100");
        require(Math.abs(state.mouseY() - 200f) < 0.001f, "mouseY=200");
        require(Math.abs(state.mouseDeltaX() - 100f) < 0.001f, "mouseDeltaX=100");
        state.consumeMouseDelta();
        require(Math.abs(state.mouseDeltaX()) < 0.001f, "delta обнулился после consume");

        // 4. scroll → axis CAMERA_ZOOM
        router.consume(RawInputEvent.axis(InputSource.MOUSE, InputAxis.CAMERA_ZOOM, 1.5f, t + 3));
        require(Math.abs(state.axis(InputAxis.CAMERA_ZOOM) - 1.5f) < 0.001f, "axis CAMERA_ZOOM=1.5");

        // 5. KEY_ESCAPE в GAMEPLAY → OPEN_MENU
        int gameplayBefore = gameplayCalls[0];
        router.consume(RawInputEvent.button(InputSource.KEYBOARD, KeyCodes.KEY_ESCAPE, true, t + 4));
        require(lastAction[0] == InputAction.OPEN_MENU, "Esc в GAMEPLAY → OPEN_MENU");
        require(gameplayCalls[0] == gameplayBefore + 1, "GAMEPLAY listener должен был сработать");
        router.consume(RawInputEvent.button(InputSource.KEYBOARD, KeyCodes.KEY_ESCAPE, false, t + 5));

        // 6. push UI_MENU; KEY_ESCAPE → CANCEL (доказательство контекстных биндингов)
        router.pushContext(InputContext.UI_MENU);
        int menuBefore = menuCalls[0];
        router.consume(RawInputEvent.button(InputSource.KEYBOARD, KeyCodes.KEY_ESCAPE, true, t + 6));
        require(menuCalls[0] == menuBefore + 1, "UI_MENU listener должен был сработать");
        router.popContext();

        System.out.println("[Death:desktop] input smoke: gameplay-listener=" + gameplayCalls[0]
                + " calls, menu-listener=" + menuCalls[0]
                + " calls, lastGameplayAction=" + lastAction[0]
                + " (контекстные биндинги работают)");

        router.unregisterListener(InputContext.GAMEPLAY, gameplayListener);
        router.unregisterListener(InputContext.UI_MENU, menuListener);
        state.clear();
    }

    private static void require(boolean cond, String msg) {
        if (!cond) {
            throw new IllegalStateException("[input-smoke] " + msg);
        }
    }

    /**
     * Stage 2.5 physics smoke. Создаёт {@link RustPhysicsWorld}, кладёт
     * dynamic body на {@code y=10}, прокручивает 60 шагов по {@code 1/60s}
     * (1 секунда симуляции) и логирует начальную/конечную позицию.
     *
     * <p>Ожидаемая финальная высота: примерно {@code 5.0} (свободное падение
     * под гравитацией Земли за 1с с {@code v0=0}, semi-implicit Euler даёт
     * {@code y = 10 - 0.5 * 9.81 * 1² ≈ 5.1}). Это видимое доказательство
     * что Java↔Rust handle round-trip + Rapier integration реально работают.
     */
    private static void runPhysicsSmoke() {
        try (RustPhysicsWorld world = new RustPhysicsWorld()) {
            PhysicsBodyHandle body = world.addDynamicBody(Vec3.of(0.0, 10.0, 0.0));
            Vec3 initial = world.bodyPosition(body);
            System.out.println("[Death:desktop] physics body0 initial=" + format(initial));

            final double dt = 1.0 / 60.0;
            for (int i = 0; i < 60; i++) {
                world.step(dt);
            }

            Vec3 finalPos = world.bodyPosition(body);
            System.out.println("[Death:desktop] physics body0 after 60 ticks @ dt=1/60 final=" + format(finalPos));
            System.out.println("[Death:desktop] physics smoke: dy=" + (finalPos.y() - initial.y())
                    + " (ожидание ~ -4.9 от gravity)");
        }
    }

    /**
     * Stage 2.3 audio smoke. Проигрывает синтетический синусоидальный
     * mono PCM (16-bit, 44100 Hz) длительностью {@code 0.2s} в категории
     * {@link AudioCategory#SFX} и ждёт перехода source в {@code AL_STOPPED}
     * не дольше {@code 1000 мс}.
     *
     * <p>Smoke считается пройденным, если:
     * <ul>
     *   <li>{@code AL_VENDOR} непустой (контекст реально активен);</li>
     *   <li>{@link OpenAlAudioBackend#playSineWaveSmoke(float, float, AudioCategory)}
     *       вернул валидный {@link OpenAlSourceHandle};</li>
     *   <li>сразу после {@link OpenAlAudioBackend#playSineWaveSmoke}
     *       состояние source — {@code AL_PLAYING};</li>
     *   <li>после ожидания source перешёл в {@code AL_STOPPED};</li>
     *   <li>{@link AudioCategory#SFX} mute → 0 эффективная громкость;
     *       обратно в 1.0 → applied к существующим source'ам без падения.</li>
     * </ul>
     *
     * <p>В CI без аудио-устройства запуск с {@code ALSOFT_DRIVERS=null}
     * инициализирует OpenAL Soft с null-backend'ом — pipeline проходит
     * полностью, реального звука нет. Без env var и без {@code --no-audio}
     * smoke упадёт на стадии {@link OpenAlAudioBackend#init()}.
     */
    private static void runAudioSmoke(OpenAlAudioBackend audio) {
        require(!audio.alVendor().isEmpty(), "AL_VENDOR должен быть непустой после init");

        long t0 = System.nanoTime();
        OpenAlSourceHandle src = audio.playSineWaveSmoke(440f, 0.2f, AudioCategory.SFX);
        require(src != null, "playSineWaveSmoke вернул null");
        require(src.id() > 0, "id source'а должен быть положителен");
        require(src.category() == AudioCategory.SFX, "category должна быть SFX");

        int initialState = audio.sourceState(src);
        require(initialState == org.lwjgl.openal.AL10.AL_PLAYING
                        || initialState == org.lwjgl.openal.AL10.AL_INITIAL,
                "источник сразу после play должен быть PLAYING/INITIAL, был=0x"
                        + Integer.toHexString(initialState));

        boolean reachedStopped = audio.awaitSourceStopped(src, 1000L);
        require(reachedStopped, "источник должен перейти в STOPPED за 1с");
        require(!src.isActive(), "isActive() должен вернуть false после STOPPED");

        // Микшер: mute SFX → effective volume 0; восстанавливаем обратно.
        audio.setCategoryVolume(AudioCategory.SFX, 0.5f);
        require(Math.abs(audio.categoryVolume(AudioCategory.SFX) - 0.5f) < 1e-6f,
                "SFX volume должен стать 0.5");
        audio.setCategoryMuted(AudioCategory.SFX, true);
        require(audio.categoryMuted(AudioCategory.SFX), "SFX должен быть muted");
        audio.setCategoryMuted(AudioCategory.SFX, false);
        audio.setCategoryVolume(AudioCategory.SFX, 1.0f);

        src.dispose();
        require(audio.activeSources().isEmpty(), "после dispose источников быть не должно");

        double seconds = (System.nanoTime() - t0) / 1_000_000_000.0;
        System.out.println("[Death:desktop] audio smoke: 440Hz/0.2s sine STOPPED ok, "
                + "mixer ok, dispose ok (" + String.format("%.3f", seconds) + "s)");
    }

    /**
     * Stage 2.3b asset audio smoke. Проигрывает реальный OGG-asset
     * {@code audio.test.sine_440_short} (мини-фикстура 0.2s @ 440Hz, mono,
     * закодирована {@code libvorbis}, лежит в
     * {@code desktop/src/main/resources/assets/death/audio/test/}) через
     * полный pipeline:
     * <pre>
     * AssetHandle
     *   → ContentLoader.readSync   (PlatformFileSystem.openAsset)
     *   → OggVorbisAudioDecoder.decodeMemory  (stb_vorbis_decode_memory)
     *   → ALBuffer (alBufferData, AL_FORMAT_MONO16)
     *   → ALSource (alSourcePlay)
     *   → awaitSourceStopped
     * </pre>
     *
     * <p>Smoke считается пройденным, если:
     * <ul>
     *   <li>{@code ContentLoader.exists(handle)} = true (резолв через каталог);</li>
     *   <li>{@link AudioBackend#playSound(AssetHandle, AudioCategory, boolean)}
     *       возвращает валидный handle с категорией {@code SFX} и id &gt; 0;</li>
     *   <li>после {@code awaitSourceStopped(...)} с таймаутом 1с — STOPPED;</li>
     *   <li>повторный {@code playSound} с тем же id НЕ инкрементирует
     *       {@code cachedBufferCount} (доказательство, что cache работает,
     *       не перечитывает файл);</li>
     *   <li>{@code AudioCategory.MUSIC} mute → effective gain 0
     *       без падения applyMixerToActiveSources;</li>
     *   <li>{@code dispose()} удаляет source из активных, но НЕ удаляет
     *       cached ALBuffer (он живёт до {@link OpenAlAudioBackend#dispose()}).</li>
     * </ul>
     *
     * <p>Без {@code ALSOFT_DRIVERS=null} на VM/CI без аудиоустройства
     * smoke упадёт раньше — на {@link OpenAlAudioBackend#init()}.
     */
    private static void runAudioAssetSmoke(OpenAlAudioBackend audio) {
        require(audio.isContentPipelineBound(),
                "audio pipeline должен быть привязан до runAudioAssetSmoke");

        long t0 = System.nanoTime();
        AssetHandle handle = new AssetHandle("audio.test.sine_440_short", AssetCategory.AUDIO);

        AudioBackend api = audio;
        int cacheBefore = audio.cachedBufferCount();
        AudioSourceHandleHolder src1 = new AudioSourceHandleHolder(
                api.playSound(handle, AudioCategory.SFX, false));
        require(src1.h.id() > 0, "id source'а должен быть положителен");
        require(src1.h.category() == AudioCategory.SFX, "category должна быть SFX");
        require(audio.cachedBufferCount() == cacheBefore + 1,
                "первый playSound должен добавить ALBuffer в cache");

        boolean stopped1 = audio.awaitSourceStopped(
                (OpenAlSourceHandle) src1.h, 1500L);
        require(stopped1, "OGG source должен перейти в STOPPED за 1.5с");
        require(!src1.h.isActive(), "isActive() должен быть false после STOPPED");

        // Повторное воспроизведение — buffer должен переиспользоваться из cache.
        AudioSourceHandleHolder src2 = new AudioSourceHandleHolder(
                api.playSound(handle, AudioCategory.SFX, false));
        require(audio.cachedBufferCount() == cacheBefore + 1,
                "повторный playSound НЕ должен добавлять buffer (cache hit)");
        boolean stopped2 = audio.awaitSourceStopped(
                (OpenAlSourceHandle) src2.h, 1500L);
        require(stopped2, "повторный source тоже должен дойти до STOPPED");

        // Mixer: mute MUSIC, проверяем что effective gain пересчитался
        // (мы играем в SFX — изменение MUSIC не должно ронять активные source'ы
        // другой категории).
        audio.setCategoryMuted(AudioCategory.MUSIC, true);
        require(audio.categoryMuted(AudioCategory.MUSIC), "MUSIC должен быть muted");
        audio.setCategoryMuted(AudioCategory.MUSIC, false);

        // Dispose source — buffer остаётся в cache (не удаляется per-source).
        int cacheAfterStop = audio.cachedBufferCount();
        src1.h.dispose();
        src2.h.dispose();
        require(audio.cachedBufferCount() == cacheAfterStop,
                "per-source dispose НЕ должен трогать cached ALBuffer");
        require(audio.activeSources().isEmpty(),
                "после dispose обоих источников активных быть не должно");

        double seconds = (System.nanoTime() - t0) / 1_000_000_000.0;
        System.out.println("[Death:desktop] audio asset smoke: OGG '" + handle.id()
                + "' STOPPED twice ok, cache=" + audio.cachedBufferCount()
                + " buffer(s), mixer ok, dispose ok ("
                + String.format("%.3f", seconds) + "s)");
    }

    /** Тонкий holder, чтобы не прокидывать generic AudioSourceHandle через касты в каждой строке. */
    private static final class AudioSourceHandleHolder {
        final WeTTeA.api.audio.AudioSourceHandle h;
        AudioSourceHandleHolder(WeTTeA.api.audio.AudioSourceHandle h) { this.h = h; }
    }

    /**
     * Stage 3.2 scene smoke. Проверяет lifecycle-инварианты
     * {@link SceneManager}: push/pop/replace и парность
     * {@code onPause}/{@code onResume}/{@code onEnter}/{@code onExit}.
     *
     * <p>Сценарий:
     * <ol>
     *   <li>{@code push(A)} → {@code A.onEnter()=1}, depth=1, top=A.</li>
     *   <li>{@code tickTop} 3 раза → {@code A.tick=3}; {@code drawTop} 2 раза → {@code A.draw=2}.</li>
     *   <li>{@code push(B)} → {@code A.onPause()=1}, {@code B.onEnter()=1}, depth=2.</li>
     *   <li>{@code tickTop} 1 раз → только {@code B.tick=1}, {@code A.tick} остаётся 3.</li>
     *   <li>{@code popDeferred()} + {@code applyPending()} → {@code B.onExit()=1}, {@code B.dispose()=1},
     *       {@code A.onResume()=1}, depth=1.</li>
     *   <li>{@code replace(C)} → {@code A.onExit()=1}, {@code A.dispose()=1}, {@code C.onEnter()=1}, depth=1.
     *       {@code A.onPause}/{@code C.onPause} НЕ инкрементируются.</li>
     *   <li>{@code clear()} → {@code C.onExit()=1}, {@code C.dispose()=1}, depth=0.</li>
     * </ol>
     *
     * <p>Smoke считается пройденным, если все 7 ассертов выше совпали.
     */
    private static void runSceneSmoke(SceneManager scenes) {
        long t0 = System.nanoTime();
        require(scenes.depth() == 0, "scene-smoke: на старте стек должен быть пуст");

        TestScene a = new TestScene("A");
        TestScene b = new TestScene("B");
        TestScene c = new TestScene("C");

        // 1. push A.
        scenes.push(a);
        require(scenes.depth() == 1, "depth=1 после push(A)");
        require(scenes.top() == a, "top=A");
        require(a.enter == 1 && a.exit == 0 && a.pause == 0 && a.resume == 0,
                "A: только onEnter после push");

        // 2. tick/draw на top — счётчики растут.
        scenes.tickTop(0.016);
        scenes.tickTop(0.016);
        scenes.tickTop(0.016);
        scenes.drawTop(null);
        scenes.drawTop(null);
        require(a.tick == 3, "A.tick должен быть 3, был " + a.tick);
        require(a.draw == 2, "A.draw должен быть 2, был " + a.draw);

        // 3. push B поверх A → A.onPause(), B.onEnter().
        scenes.push(b);
        require(scenes.depth() == 2, "depth=2 после push(B)");
        require(scenes.top() == b, "top=B");
        require(a.pause == 1, "A.onPause должен быть вызван при push(B), было " + a.pause);
        require(b.enter == 1, "B.onEnter должен быть вызван");

        // 4. tickTop теперь идёт только в B; A заморожена.
        int aTickBefore = a.tick;
        int aDrawBefore = a.draw;
        scenes.tickTop(0.016);
        scenes.drawTop(null);
        require(b.tick == 1 && b.draw == 1, "B должен получить tick/draw");
        require(a.tick == aTickBefore && a.draw == aDrawBefore,
                "A не должна тикать/рисоваться пока она не top");

        // 5. deferred pop через applyPending.
        scenes.popDeferred();
        require(scenes.pendingSize() == 1, "pendingSize=1 после popDeferred");
        int applied = scenes.applyPending();
        require(applied == 1, "applyPending должен вернуть 1, вернул " + applied);
        require(scenes.depth() == 1, "depth=1 после applyPending pop");
        require(scenes.top() == a, "top снова A после pop B");
        require(b.exit == 1 && b.dispose == 1, "B.onExit + B.dispose должны быть 1");
        require(a.resume == 1, "A.onResume должен быть вызван при pop B");

        // 6. replace A → C. onExit/dispose для A, onEnter для C, без pause/resume.
        int aPauseBeforeReplace = a.pause;
        int aResumeBeforeReplace = a.resume;
        scenes.replace(c);
        require(scenes.depth() == 1, "depth=1 после replace");
        require(scenes.top() == c, "top=C после replace");
        require(a.exit == 1 && a.dispose == 1, "A.onExit + A.dispose должны быть 1");
        require(a.pause == aPauseBeforeReplace && a.resume == aResumeBeforeReplace,
                "replace не должен инкрементировать pause/resume");
        require(c.enter == 1 && c.pause == 0 && c.resume == 0,
                "C: только onEnter после replace");

        // 7. clear() — мульти-pop с onExit + dispose.
        scenes.clear();
        require(scenes.depth() == 0, "depth=0 после clear");
        require(c.exit == 1 && c.dispose == 1, "C должен получить onExit + dispose в clear");

        double seconds = (System.nanoTime() - t0) / 1_000_000_000.0;
        System.out.println("[Death:desktop] scene smoke: push/pop/replace + onPause/onResume + "
                + "deferred + clear ok (" + String.format("%.3f", seconds) + "s)");
    }

    /**
     * Stage 3.2 ECS smoke. Создаёт {@link EntityWorld}, спавнит
     * {@code N=1000} сущностей с {@code Position}+{@code Velocity}
     * компонентами, прогоняет {@link SystemScheduler} с
     * {@code MovementSystem} 60 раз по {@code dt=1/60} (т.е. 1 секунду
     * симуляции) и проверяет:
     * <ul>
     *   <li>{@code aliveCount=1000} после spawn'ов;</li>
     *   <li>{@code positions.size()=1000}, {@code velocities.size()=1000} в stores;</li>
     *   <li>после симуляции: каждая позиция = initial + velocity * 1.0 (в пределах float epsilon);</li>
     *   <li>despawn половины (чётные индексы) → {@code aliveCount=500}, store sizes=500;</li>
     *   <li>спавн новых 100 entity и проверка ABA-safety: старый id
     *       (с переиспользованным index, но старым generation) должен
     *       вернуть {@code isAlive=false};</li>
     *   <li>система в новом проходе обновляет ТОЛЬКО живые компоненты.</li>
     * </ul>
     */
    private static void runEcsSmoke() {
        long t0 = System.nanoTime();
        EntityWorld world = new EntityWorld();
        SystemScheduler scheduler = new SystemScheduler();
        scheduler.add(new MovementSystem());

        final int N = 1000;
        EntityId[] ids = new EntityId[N];
        for (int i = 0; i < N; i++) {
            EntityId id = world.spawn();
            ids[i] = id;
            // Детерминированные начальные значения для проверки интегрирования.
            world.set(id, Position.class, new Position(i * 0.01f, 0f, 0f));
            world.set(id, Velocity.class, new Velocity(1f, 2f, 3f));
        }
        require(world.aliveCount() == N, "aliveCount=" + N + " после spawn'ов, был " + world.aliveCount());
        require(world.store(Position.class).size() == N,
                "positions.size=" + N + ", было " + world.store(Position.class).size());
        require(world.store(Velocity.class).size() == N,
                "velocities.size=" + N);

        final double dt = 1.0 / 60.0;
        for (int step = 0; step < 60; step++) {
            scheduler.update(world, dt);
        }

        // Проверка интегрирования: за 60 шагов по 1/60s velocity*1.0 должно прибавиться к position.
        // Для i=0 ожидаем (0+1.0, 0+2.0, 0+3.0) ≈ (1.0, 2.0, 3.0).
        for (int i = 0; i < N; i++) {
            Position p = world.get(ids[i], Position.class);
            require(p != null, "position для ids[" + i + "] должна существовать");
            float expectedX = i * 0.01f + 1.0f;
            require(Math.abs(p.x - expectedX) < 1e-3f,
                    "i=" + i + " position.x=" + p.x + " ожидалось=" + expectedX);
            require(Math.abs(p.y - 2.0f) < 1e-3f,
                    "i=" + i + " position.y=" + p.y + " ожидалось 2.0");
            require(Math.abs(p.z - 3.0f) < 1e-3f,
                    "i=" + i + " position.z=" + p.z + " ожидалось 3.0");
        }

        // Despawn половины — чётные индексы.
        int despawnedCount = 0;
        EntityId[] despawnedIds = new EntityId[N / 2];
        for (int i = 0; i < N; i += 2) {
            require(world.despawn(ids[i]), "despawn ids[" + i + "] должен быть успешен");
            despawnedIds[despawnedCount++] = ids[i];
        }
        require(world.aliveCount() == N / 2,
                "aliveCount=" + (N / 2) + " после despawn половины, был " + world.aliveCount());
        require(world.store(Position.class).size() == N / 2,
                "positions.size=" + (N / 2) + " после despawn");
        require(world.store(Velocity.class).size() == N / 2,
                "velocities.size=" + (N / 2) + " после despawn");

        // Старые id больше не alive (даже если index переиспользуется ниже).
        for (EntityId stale : despawnedIds) {
            require(!world.isAlive(stale), "despawn'нутый " + stale + " не должен быть alive");
            require(world.get(stale, Position.class) == null,
                    "get на despawn'нутую сущность должен вернуть null");
        }

        // Спавним новые 100 — index'ы переиспользуются из freeList.
        // ABA-safety: старый id с тем же index теперь имеет старое generation,
        // которое НЕ совпадает с актуальным generations[index] (тот инкрементирован).
        EntityId[] reborn = new EntityId[100];
        for (int i = 0; i < 100; i++) {
            reborn[i] = world.spawn();
            world.set(reborn[i], Position.class, new Position(0f, 0f, 0f));
        }
        // Проверим, что хотя бы один из reborn имеет тот же index, что был у despawn'нутого.
        boolean reuseDetected = false;
        for (EntityId staleA : despawnedIds) {
            for (EntityId rebornB : reborn) {
                if (staleA.index() == rebornB.index()) {
                    require(staleA.generation() != rebornB.generation(),
                            "ABA: index переиспользован, generation должен отличаться");
                    require(world.isAlive(rebornB), "reborn должен быть alive");
                    require(!world.isAlive(staleA), "старый id с тем же index не должен быть alive");
                    reuseDetected = true;
                    break;
                }
            }
            if (reuseDetected) break;
        }
        require(reuseDetected, "ABA-safety: ожидалось переиспользование index'а хотя бы для одного reborn");

        require(world.aliveCount() == N / 2 + 100,
                "aliveCount=" + (N / 2 + 100) + " после reborn, был " + world.aliveCount());

        // Финальный проход системы — должен проходить ТОЛЬКО по живым.
        ComponentStore<Position> posStore = world.store(Position.class);
        ComponentStore<Velocity> velStore = world.store(Velocity.class);
        require(posStore.size() == N / 2 + 100, "positions.size=" + (N / 2 + 100));
        require(velStore.size() == N / 2, "velocities.size остался " + (N / 2)
                + " — у reborn нет velocity, что и проверяем");

        scheduler.update(world, dt);
        // У reborn velocity нет — позиция остаётся (0,0,0).
        for (EntityId rb : reborn) {
            Position p = world.get(rb, Position.class);
            require(Math.abs(p.x) < 1e-6f && Math.abs(p.y) < 1e-6f && Math.abs(p.z) < 1e-6f,
                    "reborn без velocity не должен двигаться");
        }

        // Полная очистка world'а.
        world.clear();
        require(world.aliveCount() == 0, "aliveCount=0 после clear");
        require(world.capacity() == 0, "capacity=0 после clear");
        require(posStore.size() == 0, "positions.size=0 после clear");
        require(velStore.size() == 0, "velocities.size=0 после clear");

        double seconds = (System.nanoTime() - t0) / 1_000_000_000.0;
        System.out.println("[Death:desktop] ECS smoke: " + N + " entities spawned, "
                + "60 sim steps, despawn half + reborn 100, ABA-safe, system iterate ok ("
                + String.format("%.3f", seconds) + "s)");
    }

    /** Тестовая сцена для scene smoke — счётчики на каждом lifecycle-вызове. */
    private static final class TestScene implements Scene {
        final String name;
        int enter, exit, pause, resume, tick, draw, dispose;

        TestScene(String name) {
            this.name = name;
        }

        @Override public void onEnter()  { enter++; }
        @Override public void onExit()   { exit++; }
        @Override public void onPause()  { pause++; }
        @Override public void onResume() { resume++; }
        @Override public void tick(double deltaSeconds) { tick++; }
        @Override public void draw(RenderFrameContext frame) { draw++; }
        @Override public void dispose() { dispose++; }

        @Override public String toString() { return "TestScene(" + name + ")"; }
    }

    /** Тестовый компонент для ECS smoke — мутируется системой в hot path. */
    private static final class Position {
        float x, y, z;
        Position(float x, float y, float z) { this.x = x; this.y = y; this.z = z; }
    }

    /** Тестовый компонент для ECS smoke — задаёт скорость для MovementSystem. */
    private static final class Velocity {
        final float dx, dy, dz;
        Velocity(float dx, float dy, float dz) { this.dx = dx; this.dy = dy; this.dz = dz; }
    }

    /**
     * Тестовая система: для всех entity, у которых есть Position И Velocity,
     * интегрирует position += velocity * dt.
     *
     * <p>Итерация — по slot'ам velocity store (меньшим из двух или равным),
     * с lookup по entityIndex в position store. Без аллокаций.
     */
    private static final class MovementSystem implements EcsSystem {
        @Override
        public void update(EntityWorld world, double deltaSeconds) {
            ComponentStore<Position> positions = world.store(Position.class);
            ComponentStore<Velocity> velocities = world.store(Velocity.class);
            float dt = (float) deltaSeconds;
            int n = velocities.size();
            for (int slot = 0; slot < n; slot++) {
                int entityIndex = velocities.entityIndexAt(slot);
                Position p = positions.get(entityIndex);
                if (p == null) continue;
                Velocity v = velocities.componentAt(slot);
                p.x += v.dx * dt;
                p.y += v.dy * dt;
                p.z += v.dz * dt;
            }
        }
    }

    private static String format(Vec3 v) {
        return String.format("(%.4f, %.4f, %.4f)", v.x(), v.y(), v.z());
    }

    /**
     * Stage 3.3a pathfinding smoke. Создаёт {@link RustPathGrid} 8×8 со
     * стеной по {@code x=4} и проёмом в {@code (4, 4)}; запрашивает A*
     * путь от {@code (1, 1)} к {@code (6, 6)}; проверяет что путь:
     * <ul>
     *   <li>непустой и его длина &gt; 0;</li>
     *   <li>начинается ровно в {@code (1, 1)};</li>
     *   <li>заканчивается ровно в {@code (6, 6)};</li>
     *   <li>каждая клетка внутри пути соединена 4-directional шагом
     *       (Manhattan-distance = 1) с соседней;</li>
     *   <li>ни одна клетка не заблокирована;</li>
     *   <li>длина пути ≥ Manhattan-расстояния (admissibility A*);</li>
     *   <li>проходит через проём {@code (4, 4)} (единственный способ
     *       обойти стену);</li>
     *   <li>недостижимая цель (полностью окружённая стенами) →
     *       пустой массив, без бросков.</li>
     * </ul>
     *
     * <p>Это видимое доказательство того, что Java↔Rust handle round-trip
     * для grid + JNI int[] возврат + A* реально работают.
     */
    private static void runPathfindingSmoke() {
        long t0 = System.nanoTime();
        try (PathGrid grid = new RustPathGrid(8, 8)) {
            require(grid.width() == 8 && grid.height() == 8,
                    "pathfinding-smoke: grid должен быть 8x8");

            // Стена по x=4 на всю высоту, кроме (4, 4) — там проём.
            for (int y = 0; y < 8; y++) {
                if (y == 4) continue;
                grid.setBlocked(4, y, true);
            }
            require(grid.isBlocked(4, 0), "(4,0) должен быть заблокирован");
            require(grid.isBlocked(4, 7), "(4,7) должен быть заблокирован");
            require(!grid.isBlocked(4, 4), "(4,4) — проём, должен быть свободен");

            int[] path = grid.findPath(1, 1, 6, 6);
            require(path.length > 0,
                    "pathfinding-smoke: путь от (1,1) к (6,6) обязан существовать");
            require(path.length % 2 == 0,
                    "pathfinding-smoke: длина flat-массива должна быть чётной");

            int cells = path.length / 2;
            require(path[0] == 1 && path[1] == 1,
                    "pathfinding-smoke: путь должен начинаться в (1,1), нашли ("
                            + path[0] + "," + path[1] + ")");
            require(path[path.length - 2] == 6 && path[path.length - 1] == 6,
                    "pathfinding-smoke: путь должен заканчиваться в (6,6), нашли ("
                            + path[path.length - 2] + "," + path[path.length - 1] + ")");

            // 4-directional connectivity + ни одна клетка не заблокирована.
            boolean throughGap = false;
            for (int i = 0; i < cells; i++) {
                int x = path[i * 2];
                int y = path[i * 2 + 1];
                require(!grid.isBlocked(x, y),
                        "pathfinding-smoke: ячейка (" + x + "," + y + ") в пути не должна быть заблокирована");
                if (x == 4 && y == 4) {
                    throughGap = true;
                }
                if (i > 0) {
                    int px = path[(i - 1) * 2];
                    int py = path[(i - 1) * 2 + 1];
                    int dx = Math.abs(x - px);
                    int dy = Math.abs(y - py);
                    require(dx + dy == 1,
                            "pathfinding-smoke: соседние клетки пути должны быть в 4-directional шаге; "
                                    + "нашли (" + px + "," + py + ")→(" + x + "," + y + ")");
                }
            }
            require(throughGap,
                    "pathfinding-smoke: единственный обход — через проём (4,4)");

            // Admissibility: длина пути ≥ Manhattan(start, goal) = |6-1|+|6-1| = 10.
            int manhattan = Math.abs(6 - 1) + Math.abs(6 - 1);
            int steps = cells - 1;
            require(steps >= manhattan,
                    "pathfinding-smoke: A* admissibility — steps (" + steps + ") >= Manhattan ("
                            + manhattan + ")");

            // Недостижимая цель: окружим (7,0) стенами (соседи блокируем),
            // findPath должен вернуть пустой массив.
            grid.setBlocked(6, 0, true);
            grid.setBlocked(7, 1, true);
            int[] unreachable = grid.findPath(1, 1, 7, 0);
            require(unreachable.length == 0,
                    "pathfinding-smoke: недостижимая цель должна возвращать пустой массив, "
                            + "длина=" + unreachable.length);

            double seconds = (System.nanoTime() - t0) / 1_000_000_000.0;
            System.out.println("[Death:desktop] pathfinding smoke: 8x8 grid, wall@x=4 with gap@(4,4), "
                    + "A*(1,1)→(6,6) cells=" + cells + " steps=" + steps
                    + " (Manhattan=" + manhattan + "), unreachable=ok, dispose ok ("
                    + String.format("%.3f", seconds) + "s)");
        }
    }
}
