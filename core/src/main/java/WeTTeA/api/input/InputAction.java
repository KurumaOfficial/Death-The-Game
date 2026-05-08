package WeTTeA.api.input;

/**
 * Абстрактные действия ввода — единый словарь для gameplay/UI/narrative слоёв.
 *
 * <p>Все игровые системы реагируют на эти действия, а не на конкретные клавиши,
 * касания или кнопки геймпада. Привязка raw input → action делается в
 * {@code InputRouter} на уровне core (см. {@code WeTTeA.core.input}).
 *
 * <p>Список покрывает:
 * <ul>
 *   <li>движение (Dark Souls-like + bullet hell focus);</li>
 *   <li>боевую систему (атаки, додж, lock target);</li>
 *   <li>narrative диалоги (advance, skip);</li>
 *   <li>камеру;</li>
 *   <li>системные действия (pause, menu, confirm, cancel).</li>
 * </ul>
 *
 * <p>Расширяется явно — добавление новых action'ов сюда.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public enum InputAction {

    // -- Движение --------------------------------------------------------------
    MOVE_LEFT,
    MOVE_RIGHT,
    MOVE_UP,
    MOVE_DOWN,

    // -- Bullet hell / focus ---------------------------------------------------
    FOCUS,
    DODGE_ROLL,
    DASH,
    SHOOT,

    // -- Боевая система (Dark Souls-like) --------------------------------------
    ATTACK_PRIMARY,
    ATTACK_SECONDARY,
    LOCK_TARGET,
    INTERACT,

    // -- Камера ----------------------------------------------------------------
    CAMERA_ROTATE,
    CAMERA_ZOOM,

    // -- Narrative -------------------------------------------------------------
    ADVANCE_DIALOG,
    SKIP_DIALOG,

    // -- Системные / UI --------------------------------------------------------
    CONFIRM,
    CANCEL,
    OPEN_MENU,
    PAUSE
}
