package WeTTeA.core.input;

import WeTTeA.api.input.InputAction;
import WeTTeA.api.input.InputContext;
import WeTTeA.api.input.InputSource;

/**
 * Привязка платформенного кода клавиши/кнопки к логическому
 * {@link InputAction} в рамках конкретного {@link InputContext}.
 *
 * <p>Пример: {@code new ActionBinding(GAMEPLAY, KEYBOARD, KEY_W, MOVE_UP)}
 * означает «в контексте GAMEPLAY клавиша W мапится в action MOVE_UP».
 *
 * <p>Биндинги — immutable. Перепривязка идёт через создание нового
 * {@link InputBindings} c обновлённым списком. Это упрощает UI настроек:
 * пользователь меняет клавишу → строится новый набор биндингов и атомарно
 * выставляется в {@link InputRouter}.
 *
 * @param context  контекст, в котором действует биндинг
 * @param source   платформенный источник
 * @param keyCode  код клавиши или кнопки (см. {@link WeTTeA.api.input.KeyCodes})
 * @param action   логическое действие, которое будет вызвано
 *
 * @author Kuruma
 * @since 0.1.0
 */
public record ActionBinding(
        InputContext context,
        InputSource source,
        int keyCode,
        InputAction action
) {
}
