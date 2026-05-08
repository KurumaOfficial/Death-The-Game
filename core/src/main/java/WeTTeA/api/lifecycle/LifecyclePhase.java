package WeTTeA.api.lifecycle;

/**
 * Фазы жизненного цикла приложения и игровых систем.
 *
 * <p>Используется на верхнем уровне ({@code GameApp}) и для систем,
 * чувствительных к фазе (audio mute при PAUSED, save manager flush при
 * SHUTTING_DOWN, lazy bring-up рендера на BOOT → INIT и т.д.).
 *
 * @author Kuruma
 * @since 0.1.0
 */
public enum LifecyclePhase {

    /** Приложение запущено, но платформенный adapter ещё не инициализирован. */
    BOOT,

    /** Adapter инициализирован, идёт инициализация сервисов. */
    INIT,

    /** Игровой цикл активно крутится. */
    RUNNING,

    /**
     * Приостановка. На Android соответствует {@code onPause}; на desktop —
     * пользовательской паузе или потере фокуса (если включено в настройках).
     * Vulkan surface может быть невалиден.
     */
    PAUSED,

    /** Завершение запрошено, идёт корректное освобождение ресурсов. */
    SHUTTING_DOWN,

    /** Все ресурсы освобождены. */
    DISPOSED
}
