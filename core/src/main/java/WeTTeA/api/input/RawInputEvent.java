package WeTTeA.api.input;

/**
 * Сырое событие от платформенного input layer.
 *
 * <p>Источники: GLFW callbacks, Android {@code MotionEvent/KeyEvent},
 * UIKit touch handlers, gamepad poll. Каждое событие содержит достаточно
 * информации, чтобы {@code InputRouter} мог замапить его в action.
 *
 * <p><b>Аллокации.</b> События должны переиспользоваться pooled-структурой,
 * либо это immutable record, аллоцируемый редко. Bullet hell в финальной
 * сборке не должен генерировать GC pressure из-за input pipeline.
 *
 * @param source     платформенный источник
 * @param keyCode    код клавиши/кнопки в формате {@link InputSource}.
 *                   {@code -1}, если событие — оси.
 * @param pressed    {@code true} если нажатие, {@code false} если отпускание.
 *                   Для осей не используется (всегда {@code false}).
 * @param axis       аналоговая ось, если событие аналоговое; иначе {@code null}.
 * @param value      значение для оси в нормализованном диапазоне.
 *                   Для дискретных событий равно {@code 0.0}.
 * @param x          координата X (для touch/mouse, в пикселях экрана).
 * @param y          координата Y (для touch/mouse, в пикселях экрана).
 * @param timestamp  ns relative от старта приложения.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public record RawInputEvent(
        InputSource source,
        int keyCode,
        boolean pressed,
        InputAxis axis,
        float value,
        float x,
        float y,
        long timestamp
) {

    /** Дискретное нажатие/отпускание. */
    public static RawInputEvent button(InputSource source, int keyCode, boolean pressed, long timestamp) {
        return new RawInputEvent(source, keyCode, pressed, null, 0f, 0f, 0f, timestamp);
    }

    /** Аналоговая ось. */
    public static RawInputEvent axis(InputSource source, InputAxis axis, float value, long timestamp) {
        return new RawInputEvent(source, -1, false, axis, value, 0f, 0f, timestamp);
    }

    /** Touch/mouse позиция. */
    public static RawInputEvent pointer(InputSource source, int keyCode, boolean pressed, float x, float y, long timestamp) {
        return new RawInputEvent(source, keyCode, pressed, null, 0f, x, y, timestamp);
    }
}
