package WeTTeA.api.input;

/**
 * Числовые коды клавиш и кнопок мыши, используемые в
 * {@link RawInputEvent#keyCode()}.
 *
 * <p>Значения совпадают с GLFW 3.3 (`GLFW_KEY_*` / `GLFW_MOUSE_BUTTON_*`)
 * — это снимает необходимость переводить коды на десктопе. На Android/iOS
 * платформенный adapter транслирует свои нативные коды в эти же значения,
 * чтобы биндинги в {@code WeTTeA.core.input.InputBindings} оставались
 * платформонезависимыми.
 *
 * <p>Класс — namespace; экземпляры не создаются.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class KeyCodes {

    private KeyCodes() {
    }

    // -- Буквы -----------------------------------------------------------------
    public static final int KEY_A = 65;
    public static final int KEY_B = 66;
    public static final int KEY_C = 67;
    public static final int KEY_D = 68;
    public static final int KEY_E = 69;
    public static final int KEY_F = 70;
    public static final int KEY_G = 71;
    public static final int KEY_H = 72;
    public static final int KEY_I = 73;
    public static final int KEY_J = 74;
    public static final int KEY_K = 75;
    public static final int KEY_L = 76;
    public static final int KEY_M = 77;
    public static final int KEY_N = 78;
    public static final int KEY_O = 79;
    public static final int KEY_P = 80;
    public static final int KEY_Q = 81;
    public static final int KEY_R = 82;
    public static final int KEY_S = 83;
    public static final int KEY_T = 84;
    public static final int KEY_U = 85;
    public static final int KEY_V = 86;
    public static final int KEY_W = 87;
    public static final int KEY_X = 88;
    public static final int KEY_Y = 89;
    public static final int KEY_Z = 90;

    // -- Цифры (top row) -------------------------------------------------------
    public static final int KEY_0 = 48;
    public static final int KEY_1 = 49;
    public static final int KEY_2 = 50;
    public static final int KEY_3 = 51;
    public static final int KEY_4 = 52;
    public static final int KEY_5 = 53;
    public static final int KEY_6 = 54;
    public static final int KEY_7 = 55;
    public static final int KEY_8 = 56;
    public static final int KEY_9 = 57;

    // -- Функциональные / навигация --------------------------------------------
    public static final int KEY_SPACE = 32;
    public static final int KEY_ENTER = 257;
    public static final int KEY_ESCAPE = 256;
    public static final int KEY_TAB = 258;
    public static final int KEY_BACKSPACE = 259;
    public static final int KEY_LEFT_SHIFT = 340;
    public static final int KEY_LEFT_CONTROL = 341;
    public static final int KEY_LEFT_ALT = 342;
    public static final int KEY_RIGHT_SHIFT = 344;
    public static final int KEY_RIGHT_CONTROL = 345;
    public static final int KEY_RIGHT_ALT = 346;
    public static final int KEY_F1 = 290;
    public static final int KEY_F2 = 291;
    public static final int KEY_F3 = 292;
    public static final int KEY_F4 = 293;

    // -- Стрелки ---------------------------------------------------------------
    public static final int KEY_RIGHT = 262;
    public static final int KEY_LEFT = 263;
    public static final int KEY_DOWN = 264;
    public static final int KEY_UP = 265;

    // -- Кнопки мыши -----------------------------------------------------------
    public static final int MOUSE_BUTTON_LEFT = 0;
    public static final int MOUSE_BUTTON_RIGHT = 1;
    public static final int MOUSE_BUTTON_MIDDLE = 2;

    /**
     * Человекочитаемое имя кода для логов / debug UI. Возвращает
     * {@code "?<n>"} для неизвестного кода.
     */
    public static String name(InputSource source, int code) {
        if (source == InputSource.MOUSE) {
            return switch (code) {
                case MOUSE_BUTTON_LEFT -> "MOUSE_LEFT";
                case MOUSE_BUTTON_RIGHT -> "MOUSE_RIGHT";
                case MOUSE_BUTTON_MIDDLE -> "MOUSE_MIDDLE";
                default -> "?MOUSE" + code;
            };
        }
        if (source == InputSource.KEYBOARD) {
            if (code >= KEY_A && code <= KEY_Z) {
                return "KEY_" + (char) code;
            }
            if (code >= KEY_0 && code <= KEY_9) {
                return "KEY_" + (char) code;
            }
            return switch (code) {
                case KEY_SPACE -> "KEY_SPACE";
                case KEY_ENTER -> "KEY_ENTER";
                case KEY_ESCAPE -> "KEY_ESCAPE";
                case KEY_TAB -> "KEY_TAB";
                case KEY_BACKSPACE -> "KEY_BACKSPACE";
                case KEY_LEFT_SHIFT -> "KEY_LSHIFT";
                case KEY_LEFT_CONTROL -> "KEY_LCTRL";
                case KEY_LEFT_ALT -> "KEY_LALT";
                case KEY_LEFT -> "KEY_LEFT";
                case KEY_RIGHT -> "KEY_RIGHT";
                case KEY_UP -> "KEY_UP";
                case KEY_DOWN -> "KEY_DOWN";
                default -> "?KEY" + code;
            };
        }
        return "?" + source + code;
    }
}
