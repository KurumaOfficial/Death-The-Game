package WeTTeA.api.input;

/**
 * Контекст ввода — определяет, какие подписчики получают action-события.
 *
 * <p>Один и тот же raw input может означать разное:
 * клавиша {@code Escape} в {@link #GAMEPLAY} → {@link InputAction#OPEN_MENU},
 * в {@link #UI_MENU} → {@link InputAction#CANCEL}, в {@link #NARRATIVE_DIALOG}
 * → {@link InputAction#SKIP_DIALOG}.
 *
 * <p>Активный контекст переключается {@code InputRouter} при смене сцены.
 * В каждый момент активен ровно один контекст; стек контекстов поддерживается
 * на уровне core.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public enum InputContext {

    /** Боевая/исследовательская часть игры. */
    GAMEPLAY,

    /** Меню (главное, паузы, настройки, инвентарь). */
    UI_MENU,

    /** Narrative диалог в 3D пространстве. */
    NARRATIVE_DIALOG,

    /** Cinematic/cutscene — большая часть input блокируется. */
    CINEMATIC,

    /** Загрузка/инициализация — input игнорируется. */
    BLOCKED
}
