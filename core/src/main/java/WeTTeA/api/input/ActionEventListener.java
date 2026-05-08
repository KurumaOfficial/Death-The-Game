package WeTTeA.api.input;

/**
 * Подписчик на action-события активного {@link InputContext}'а.
 *
 * <p>Регистрируется в {@code InputRouter} с указанием контекста, в котором
 * слушатель активен. {@code InputRouter} вызывает {@link #onAction} только
 * когда указанный контекст совпадает с активным.
 *
 * <p><b>Thread safety.</b> Колбэки вызываются на потоке игрового цикла.
 * Запрещено блокировать поток или выполнять Vulkan/JNI вызовы прямо из
 * обработчика — складывать в очередь.
 *
 * @author Kuruma
 * @since 0.1.0
 */
@FunctionalInterface
public interface ActionEventListener {

    /**
     * Реакция на action.
     *
     * @param action  логическое действие
     * @param pressed {@code true} — нажатие/начало; {@code false} — отпускание
     * @param strength нормализованная сила/значение в {@code [0..1]}
     *                 (для дискретных action всегда {@code 0} или {@code 1}).
     */
    void onAction(InputAction action, boolean pressed, float strength);
}
