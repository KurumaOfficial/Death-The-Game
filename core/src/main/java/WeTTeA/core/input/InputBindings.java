package WeTTeA.core.input;

import WeTTeA.api.input.InputAction;
import WeTTeA.api.input.InputContext;
import WeTTeA.api.input.InputSource;
import WeTTeA.api.input.KeyCodes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Иммутабельная коллекция {@link ActionBinding} с быстрым lookup'ом по
 * паре {@code (source, keyCode)} в выбранном контексте.
 *
 * <p>Lookup О(N) по числу биндингов, что приемлемо для проектируемого
 * объёма (десятки-сотни биндингов) и не требует {@code HashMap} на
 * боевой path. Если когда-нибудь упрёмся — заменим на массив-индекс по
 * {@code keyCode} per source.
 *
 * <p>Класс — value-object: метод {@link #with(ActionBinding)} возвращает
 * НОВЫЙ {@code InputBindings}, оригинал остаётся неизменным. Это нужно для
 * UI настроек: пользователь меняет клавишу → собирается новый bindings →
 * атомарно подменяется в {@link InputRouter}.
 *
 * <p>Статический {@link #defaults()} — sane defaults для WASD-движения,
 * мыши и системных клавиш, чтобы движок «из коробки» что-то делал
 * без явной настройки.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class InputBindings {

    private final List<ActionBinding> bindings;

    public InputBindings(List<ActionBinding> bindings) {
        Objects.requireNonNull(bindings, "bindings");
        this.bindings = List.copyOf(bindings);
    }

    /** Все привязки (immutable view). */
    public List<ActionBinding> all() {
        return bindings;
    }

    /**
     * Найти {@link InputAction} для пары {@code (source, keyCode)} в
     * указанном контексте. Возвращает {@code null}, если биндинг не
     * найден — caller просто игнорирует raw event.
     */
    public InputAction lookup(InputContext context, InputSource source, int keyCode) {
        for (int i = 0, n = bindings.size(); i < n; i++) {
            ActionBinding b = bindings.get(i);
            if (b.context() == context
                    && b.source() == source
                    && b.keyCode() == keyCode) {
                return b.action();
            }
        }
        return null;
    }

    /** Возвращает новый набор с добавленным биндингом. */
    public InputBindings with(ActionBinding binding) {
        Objects.requireNonNull(binding, "binding");
        ArrayList<ActionBinding> copy = new ArrayList<>(bindings.size() + 1);
        copy.addAll(bindings);
        copy.add(binding);
        return new InputBindings(Collections.unmodifiableList(copy));
    }

    /**
     * Стандартный набор биндингов для desktop:
     * WASD/стрелки → MOVE_*, Space → DODGE_ROLL, Shift → DASH,
     * Q → FOCUS, LMB/RMB → ATTACK_PRIMARY/SECONDARY,
     * Esc/Enter → CANCEL/CONFIRM (зависит от контекста),
     * F → INTERACT, E → ADVANCE_DIALOG.
     */
    public static InputBindings defaults() {
        ArrayList<ActionBinding> list = new ArrayList<>(64);

        // -- Контекст GAMEPLAY -------------------------------------------------
        addKeyboard(list, InputContext.GAMEPLAY, KeyCodes.KEY_W, InputAction.MOVE_UP);
        addKeyboard(list, InputContext.GAMEPLAY, KeyCodes.KEY_UP, InputAction.MOVE_UP);
        addKeyboard(list, InputContext.GAMEPLAY, KeyCodes.KEY_S, InputAction.MOVE_DOWN);
        addKeyboard(list, InputContext.GAMEPLAY, KeyCodes.KEY_DOWN, InputAction.MOVE_DOWN);
        addKeyboard(list, InputContext.GAMEPLAY, KeyCodes.KEY_A, InputAction.MOVE_LEFT);
        addKeyboard(list, InputContext.GAMEPLAY, KeyCodes.KEY_LEFT, InputAction.MOVE_LEFT);
        addKeyboard(list, InputContext.GAMEPLAY, KeyCodes.KEY_D, InputAction.MOVE_RIGHT);
        addKeyboard(list, InputContext.GAMEPLAY, KeyCodes.KEY_RIGHT, InputAction.MOVE_RIGHT);
        addKeyboard(list, InputContext.GAMEPLAY, KeyCodes.KEY_SPACE, InputAction.DODGE_ROLL);
        addKeyboard(list, InputContext.GAMEPLAY, KeyCodes.KEY_LEFT_SHIFT, InputAction.DASH);
        addKeyboard(list, InputContext.GAMEPLAY, KeyCodes.KEY_Q, InputAction.FOCUS);
        addKeyboard(list, InputContext.GAMEPLAY, KeyCodes.KEY_F, InputAction.INTERACT);
        addKeyboard(list, InputContext.GAMEPLAY, KeyCodes.KEY_E, InputAction.ADVANCE_DIALOG);
        addKeyboard(list, InputContext.GAMEPLAY, KeyCodes.KEY_TAB, InputAction.LOCK_TARGET);
        addKeyboard(list, InputContext.GAMEPLAY, KeyCodes.KEY_ESCAPE, InputAction.OPEN_MENU);
        addKeyboard(list, InputContext.GAMEPLAY, KeyCodes.KEY_ENTER, InputAction.CONFIRM);
        addMouse(list, InputContext.GAMEPLAY, KeyCodes.MOUSE_BUTTON_LEFT, InputAction.ATTACK_PRIMARY);
        addMouse(list, InputContext.GAMEPLAY, KeyCodes.MOUSE_BUTTON_RIGHT, InputAction.ATTACK_SECONDARY);
        addMouse(list, InputContext.GAMEPLAY, KeyCodes.MOUSE_BUTTON_MIDDLE, InputAction.LOCK_TARGET);

        // -- Контекст UI_MENU --------------------------------------------------
        addKeyboard(list, InputContext.UI_MENU, KeyCodes.KEY_W, InputAction.MOVE_UP);
        addKeyboard(list, InputContext.UI_MENU, KeyCodes.KEY_UP, InputAction.MOVE_UP);
        addKeyboard(list, InputContext.UI_MENU, KeyCodes.KEY_S, InputAction.MOVE_DOWN);
        addKeyboard(list, InputContext.UI_MENU, KeyCodes.KEY_DOWN, InputAction.MOVE_DOWN);
        addKeyboard(list, InputContext.UI_MENU, KeyCodes.KEY_A, InputAction.MOVE_LEFT);
        addKeyboard(list, InputContext.UI_MENU, KeyCodes.KEY_LEFT, InputAction.MOVE_LEFT);
        addKeyboard(list, InputContext.UI_MENU, KeyCodes.KEY_D, InputAction.MOVE_RIGHT);
        addKeyboard(list, InputContext.UI_MENU, KeyCodes.KEY_RIGHT, InputAction.MOVE_RIGHT);
        addKeyboard(list, InputContext.UI_MENU, KeyCodes.KEY_ENTER, InputAction.CONFIRM);
        addKeyboard(list, InputContext.UI_MENU, KeyCodes.KEY_SPACE, InputAction.CONFIRM);
        addKeyboard(list, InputContext.UI_MENU, KeyCodes.KEY_ESCAPE, InputAction.CANCEL);
        addMouse(list, InputContext.UI_MENU, KeyCodes.MOUSE_BUTTON_LEFT, InputAction.CONFIRM);
        addMouse(list, InputContext.UI_MENU, KeyCodes.MOUSE_BUTTON_RIGHT, InputAction.CANCEL);

        // -- Контекст NARRATIVE_DIALOG -----------------------------------------
        addKeyboard(list, InputContext.NARRATIVE_DIALOG, KeyCodes.KEY_SPACE, InputAction.ADVANCE_DIALOG);
        addKeyboard(list, InputContext.NARRATIVE_DIALOG, KeyCodes.KEY_ENTER, InputAction.ADVANCE_DIALOG);
        addKeyboard(list, InputContext.NARRATIVE_DIALOG, KeyCodes.KEY_E, InputAction.ADVANCE_DIALOG);
        addKeyboard(list, InputContext.NARRATIVE_DIALOG, KeyCodes.KEY_ESCAPE, InputAction.SKIP_DIALOG);
        addKeyboard(list, InputContext.NARRATIVE_DIALOG, KeyCodes.KEY_TAB, InputAction.SKIP_DIALOG);
        addMouse(list, InputContext.NARRATIVE_DIALOG, KeyCodes.MOUSE_BUTTON_LEFT, InputAction.ADVANCE_DIALOG);

        return new InputBindings(Collections.unmodifiableList(list));
    }

    private static void addKeyboard(List<ActionBinding> dst, InputContext ctx, int code, InputAction action) {
        dst.add(new ActionBinding(ctx, InputSource.KEYBOARD, code, action));
    }

    private static void addMouse(List<ActionBinding> dst, InputContext ctx, int code, InputAction action) {
        dst.add(new ActionBinding(ctx, InputSource.MOUSE, code, action));
    }
}
