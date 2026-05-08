package WeTTeA.core;

import WeTTeA.api.events.EventBus;
import WeTTeA.api.input.InputContext;
import WeTTeA.api.lifecycle.LifecyclePhase;
import WeTTeA.core.events.SimpleEventBus;
import WeTTeA.core.input.InputBindings;
import WeTTeA.core.input.InputRouter;
import WeTTeA.core.input.InputState;
import WeTTeA.core.loop.FixedStepGameLoop;
import WeTTeA.core.loop.GameLoop;
import WeTTeA.core.scene.SceneManager;
import WeTTeA.core.service.ServiceContainer;

/**
 * Точка входа в core.
 *
 * <p>Платформенный launcher ({@code WeTTeA.platform.desktop.DesktopLauncher},
 * {@code WeTTeA.platform.android.MainActivity}, {@code WeTTeA.platform.ios.IosLauncher})
 * вызывает {@link #boot()}, передавая платформенно-специфичные реализации
 * {@link WeTTeA.api.platform.PlatformAdapter},
 * {@link WeTTeA.api.platform.PlatformFileSystem},
 * {@link WeTTeA.api.render.RenderBackend},
 * {@link WeTTeA.api.audio.AudioBackend},
 * {@link WeTTeA.api.input.InputBackend} и
 * {@link WeTTeA.api.save.SaveStorage}.
 *
 * <p>Stage 1: метод {@link #boot()} собирает только базовые core-сервисы
 * (Time, EventBus, ServiceContainer, GameLoop, SceneManager). Регистрация
 * платформенных backend'ов — на стадии 2.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class CoreBootstrap {

    private CoreBootstrap() {
    }

    public static BootResult boot() {
        SystemNanoTime time = new SystemNanoTime();
        EventBus events = new SimpleEventBus();
        ServiceContainer services = new ServiceContainer();
        SceneManager scenes = new SceneManager();
        GameLoop loop = new FixedStepGameLoop(time);

        InputBindings bindings = InputBindings.defaults();
        InputState inputState = new InputState();
        InputRouter inputRouter = new InputRouter(bindings, events, inputState, InputContext.GAMEPLAY);

        services.register(Time.class, time);
        services.register(SystemNanoTime.class, time);
        services.register(EventBus.class, events);
        services.register(SceneManager.class, scenes);
        services.register(GameLoop.class, loop);
        services.register(InputBindings.class, bindings);
        services.register(InputState.class, inputState);
        services.register(InputRouter.class, inputRouter);

        return new BootResult(services, loop, scenes, time, events, inputRouter, inputState, LifecyclePhase.RUNNING);
    }

    public record BootResult(
            ServiceContainer services,
            GameLoop loop,
            SceneManager scenes,
            Time time,
            EventBus events,
            InputRouter inputRouter,
            InputState inputState,
            LifecyclePhase phase
    ) {
    }
}
