package WeTTeA.api.lifecycle;

/**
 * Полный жизненный цикл игровой системы или сцены.
 *
 * <p>Реализации обязаны соблюдать порядок:
 * {@link #onInit()} → 0..N тиков {@link Tickable#tick(double)} и draw'ов →
 * {@link #onShutdown()} → {@link Disposable#dispose()}.
 *
 * <p>Системы, реализующие {@link GameLifecycle}, регистрируются в
 * {@code ServiceContainer} и получают эти колбэки от {@code GameApp}
 * в правильном порядке.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public interface GameLifecycle extends Disposable {

    /**
     * Инициализация системы. Все зависимости из {@code ServiceContainer}
     * к этому моменту уже должны быть доступны.
     */
    void onInit();

    /**
     * Корректное завершение работы. Вызывается до {@link #dispose()}.
     * Должен сохранять persistent state, дренировать очереди, дожидаться
     * завершения in-flight Vulkan кадров там, где применимо.
     */
    void onShutdown();
}
