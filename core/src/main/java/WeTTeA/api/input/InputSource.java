package WeTTeA.api.input;

/**
 * Платформенный источник ввода.
 *
 * <p>Используется для:
 * <ul>
 *   <li>выбора подходящего set'а биндингов (KEYBOARD vs GAMEPAD vs TOUCH);</li>
 *   <li>отображения подсказок UI (icons by source);</li>
 *   <li>статистики/телеметрии (например, "как игроки реально играют").</li>
 * </ul>
 *
 * @author Kuruma
 * @since 0.1.0
 */
public enum InputSource {

    KEYBOARD,
    MOUSE,
    GAMEPAD,
    TOUCH,

    /** Сенсоры — акселерометр, гироскоп. */
    MOTION
}
