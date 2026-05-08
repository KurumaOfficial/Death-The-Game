package WeTTeA.core.input;

import WeTTeA.api.input.InputAction;
import WeTTeA.api.input.InputAxis;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;

/**
 * Снапшот текущего состояния ввода — какие action'ы зажаты, какие
 * аналоговые оси сколько показывают, где курсор.
 *
 * <p>Заполняется {@link InputRouter} по мере поступления
 * {@link WeTTeA.api.input.RawInputEvent}. Читается gameplay/UI слоями для
 * запросов вида «зажат ли сейчас MOVE_UP» (поллинговый стиль) — параллельно
 * с push-стилем через {@link WeTTeA.api.input.ActionEventListener}.
 *
 * <p><b>Thread safety.</b> Состояние модифицируется в потоке игрового цикла
 * (там же где {@link InputRouter#dispatch}). Чтение из других потоков
 * требует внешней синхронизации; типичный паттерн — читать на том же
 * потоке, куда роутится input.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class InputState {

    private final Set<InputAction> downActions = EnumSet.noneOf(InputAction.class);
    private final EnumMap<InputAxis, Float> axisValues = new EnumMap<>(InputAxis.class);

    private float mouseX;
    private float mouseY;
    private float mouseDeltaX;
    private float mouseDeltaY;

    /** Зажато ли в данный момент action. Для аналоговых action — true если |strength| > 0. */
    public boolean isActionDown(InputAction action) {
        return downActions.contains(action);
    }

    /** Текущее значение аналоговой оси, либо {@code 0} если не получали событий. */
    public float axis(InputAxis axis) {
        Float v = axisValues.get(axis);
        return v == null ? 0f : v;
    }

    /** Абсолютная X координата курсора в пикселях окна. */
    public float mouseX() { return mouseX; }

    /** Абсолютная Y координата курсора в пикселях окна. */
    public float mouseY() { return mouseY; }

    /**
     * Дельта мыши с прошлого {@link #consumeMouseDelta()}. Удобно для
     * mouse-look камеры: каждый кадр спросил delta, применил, обнулил.
     */
    public float mouseDeltaX() { return mouseDeltaX; }

    /** Дельта мыши Y с прошлого {@link #consumeMouseDelta()}. */
    public float mouseDeltaY() { return mouseDeltaY; }

    /**
     * Сбросить накопленную дельту в ноль. Вызывается в конце кадра после
     * того, как камера/character-controller её прочитал.
     */
    public void consumeMouseDelta() {
        mouseDeltaX = 0f;
        mouseDeltaY = 0f;
    }

    // -- API для InputRouter (package-private) ---------------------------------

    void setActionDown(InputAction action, boolean down) {
        if (down) {
            downActions.add(action);
        } else {
            downActions.remove(action);
        }
    }

    void setAxis(InputAxis axis, float value) {
        axisValues.put(axis, value);
    }

    void setMousePosition(float x, float y) {
        mouseDeltaX += x - mouseX;
        mouseDeltaY += y - mouseY;
        mouseX = x;
        mouseY = y;
    }

    /** Полный сброс — для смены контекста или приостановки игры. */
    public void clear() {
        downActions.clear();
        axisValues.clear();
        mouseDeltaX = 0f;
        mouseDeltaY = 0f;
    }
}
