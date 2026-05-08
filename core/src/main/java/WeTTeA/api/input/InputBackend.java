package WeTTeA.api.input;

import WeTTeA.api.lifecycle.Disposable;

/**
 * Платформенный backend ввода — реализуется в модулях платформ.
 *
 * <p>Контракт между core и платформенным слоем:
 * <ul>
 *   <li>{@link #pollEvents(RawInputEventConsumer)} — вытолкнуть накопленные
 *       события в core. Вызывается в начале каждого кадра.</li>
 *   <li>{@link #captureMouse(boolean)} — захватить курсор для FPS-стиля.
 *       Mobile реализации игнорируют (capture не имеет смысла для touch).</li>
 *   <li>{@link #showVirtualKeyboard(boolean)} — показ/скрытие виртуальной
 *       клавиатуры на мобайле. Desktop реализации no-op.</li>
 * </ul>
 *
 * <p><b>Thread safety.</b> {@link #pollEvents(RawInputEventConsumer)} вызывается
 * только из main/render потока. Платформенные адаптеры, поднимающие события
 * с других потоков (Android UI thread), обязаны буферизировать события
 * thread-safe способом.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public interface InputBackend extends Disposable {

    /**
     * Потребитель сырых событий, передаваемый из core.
     */
    @FunctionalInterface
    interface RawInputEventConsumer {
        void accept(RawInputEvent event);
    }

    /**
     * Сбросить накопленные события в потребителя.
     *
     * @param consumer обработчик событий, обычно {@code InputRouter}
     */
    void pollEvents(RawInputEventConsumer consumer);

    /** Захватить указатель в режиме FPS. */
    void captureMouse(boolean capture);

    /** Показать/скрыть виртуальную клавиатуру (mobile). */
    void showVirtualKeyboard(boolean show);
}
