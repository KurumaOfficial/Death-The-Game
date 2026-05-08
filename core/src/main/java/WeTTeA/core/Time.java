package WeTTeA.core;

/**
 * Источник игрового времени.
 *
 * <p>Время в core измеряется в:
 * <ul>
 *   <li>{@code nanoTime()} — монотонные наносекунды от старта приложения,
 *       используются для измерений интервалов;</li>
 *   <li>{@code seconds()} — те же наносекунды, переведённые в секунды,
 *       удобны для физики и анимаций;</li>
 *   <li>{@code frameDeltaSeconds()} — последний посчитанный delta render-кадра
 *       (выставляется {@link WeTTeA.core.loop.GameLoop} перед render-проходом).</li>
 * </ul>
 *
 * <p>Реализация по умолчанию — {@link WeTTeA.core.SystemNanoTime}.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public interface Time {

    long nanoTime();

    default double seconds() {
        return nanoTime() / 1_000_000_000.0;
    }

    float frameDeltaSeconds();
}
