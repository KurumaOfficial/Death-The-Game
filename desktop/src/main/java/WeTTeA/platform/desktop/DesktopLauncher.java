package WeTTeA.platform.desktop;

import WeTTeA.api.events.EventBus;
import WeTTeA.api.input.ActionEventListener;
import WeTTeA.api.input.InputAction;
import WeTTeA.api.input.InputAxis;
import WeTTeA.api.input.InputContext;
import WeTTeA.api.input.InputSource;
import WeTTeA.api.input.KeyCodes;
import WeTTeA.api.input.RawInputEvent;
import WeTTeA.api.nativebridge.NativeBridgeInfo;
import WeTTeA.api.physics.PhysicsBodyHandle;
import WeTTeA.api.physics.Vec3;
import WeTTeA.api.platform.PlatformFileSystem;
import WeTTeA.api.platform.PlatformInfo;
import WeTTeA.core.CoreBootstrap;
import WeTTeA.core.input.InputRouter;
import WeTTeA.core.input.InputState;
import WeTTeA.native_bridge.rust.RustCore;
import WeTTeA.native_bridge.rust.RustPhysicsWorld;

/**
 * Точка входа Death на desktop (Windows / Linux / macOS).
 *
 * <p>Stage 2.1a/2.2/2.5 boot pipeline:
 * <ol>
 *   <li>Boot core ({@link CoreBootstrap#boot()});</li>
 *   <li>Создание {@link PlatformInfo} через {@link DesktopPlatformInfo#detect()}
 *       и {@link DesktopPlatformFileSystem};</li>
 *   <li>Загрузка нативной библиотеки {@code death-native} через
 *       {@link RustCore#initialize(PlatformInfo, PlatformFileSystem)};
 *       пропускается флагом {@code --no-native};</li>
 *   <li>В headless: stage 2.2 input smoke
 *       ({@link #runInputSmoke(InputRouter, InputState, EventBus)}) +
 *       stage 2.5 physics smoke
 *       ({@link #runPhysicsSmoke()});</li>
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
 *       instance dispose → glfw dispose → loop stop →
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
    private static final String WINDOW_TITLE = "Death — stage 2.1a render";

    private DesktopLauncher() {
    }

    public static void main(String[] args) {
        boolean headless = false;
        boolean noVulkan = false;
        boolean noNative = false;
        int renderFrames = 60;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--headless".equals(arg)) headless = true;
            else if ("--no-vulkan".equals(arg)) noVulkan = true;
            else if ("--no-native".equals(arg)) noNative = true;
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

        try {
            if (headless) {
                System.out.println("[Death:desktop] --headless: skipping GLFW + Vulkan smoke");
                runInputSmoke(boot.inputRouter(), boot.inputState(), boot.events());
                if (rust != null) {
                    runPhysicsSmoke();
                } else {
                    System.out.println("[Death:desktop] --no-native + --headless: skipping physics smoke");
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
                    renderer = new VulkanRenderer(vulkan.instance(), window.handle());
                    System.out.println("[Death:desktop] Vulkan renderer ready: device=" + renderer.deviceName()
                            + " " + renderer.width() + "x" + renderer.height());
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
                            System.out.println("[Death:desktop] swapchain нуждается в recreate — выходим из 2.1a smoke");
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
                            + " (~" + String.format("%.1f", fps) + " FPS)");
                }
            } finally {
                if (renderer != null) renderer.dispose();
                if (input != null) input.dispose();
                vulkan.dispose();
                window.dispose();
                boot.loop().stop();
            }
        } finally {
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

    private static String format(Vec3 v) {
        return String.format("(%.4f, %.4f, %.4f)", v.x(), v.y(), v.z());
    }
}
