package WeTTeA.core.input;

import WeTTeA.api.events.EventBus;
import WeTTeA.api.input.ActionEventListener;
import WeTTeA.api.input.InputAction;
import WeTTeA.api.input.InputAxis;
import WeTTeA.api.input.InputBackend;
import WeTTeA.api.input.InputContext;
import WeTTeA.api.input.RawInputEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

/**
 * Маршрутизатор ввода.
 *
 * <p>Принимает {@link RawInputEvent} (через {@link #consume(RawInputEvent)}
 * или {@link #pollFrom(InputBackend)}), применяет {@link InputBindings},
 * вызывает {@link ActionEventListener} в активном {@link InputContext} и
 * обновляет {@link InputState}.
 *
 * <p>Контексты управляются стеком: {@link #pushContext}, {@link #popContext},
 * {@link #setContext}. Ввод роутится в верх стека — это удобно для
 * вложенных меню (gameplay → паузу → settings → settings закрылись →
 * вернулись в паузу → закрылась пауза → gameplay).
 *
 * <p>Параллельно роутер публикует {@link ActionEvent} в {@link EventBus} —
 * это нужно UI/наративным слоям, которые подписываются через общий шину
 * событий, а не регистрируют listener'ы напрямую.
 *
 * <p><b>Thread safety.</b> Все методы вызываются из главного потока
 * игрового цикла. Платформенный {@link InputBackend} обязан буферизировать
 * входящие события thread-safe и отдавать их синхронно по
 * {@link InputBackend#pollEvents}.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class InputRouter {

    /**
     * Событие в {@link EventBus}: action случился. Полезно для UI/narrative,
     * подписывающихся через шину событий, а не напрямую через
     * {@link ActionEventListener}.
     *
     * @param context  контекст, в котором был активирован action
     * @param action   логическое действие
     * @param pressed  {@code true} если нажатие, {@code false} если отпускание
     * @param strength нормализованная сила/значение в [0..1]
     */
    public record ActionEvent(InputContext context, InputAction action, boolean pressed, float strength)
            implements WeTTeA.api.events.GameEvent {
    }

    private InputBindings bindings;
    private final EventBus events;
    private final InputState state;
    private final EnumMap<InputContext, List<ActionEventListener>> listeners = new EnumMap<>(InputContext.class);
    private final Deque<InputContext> contextStack = new ArrayDeque<>();

    public InputRouter(InputBindings bindings, EventBus events, InputState state, InputContext initial) {
        this.bindings = Objects.requireNonNull(bindings, "bindings");
        this.events = Objects.requireNonNull(events, "events");
        this.state = Objects.requireNonNull(state, "state");
        Objects.requireNonNull(initial, "initial");
        this.contextStack.push(initial);
    }

    public InputBindings bindings() {
        return bindings;
    }

    public void setBindings(InputBindings bindings) {
        this.bindings = Objects.requireNonNull(bindings, "bindings");
    }

    public InputContext currentContext() {
        return contextStack.peek();
    }

    public void pushContext(InputContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        state.clear();
        contextStack.push(ctx);
    }

    public InputContext popContext() {
        if (contextStack.size() <= 1) {
            throw new IllegalStateException("Нельзя снять последний контекст со стека");
        }
        state.clear();
        return contextStack.pop();
    }

    public void setContext(InputContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        state.clear();
        contextStack.clear();
        contextStack.push(ctx);
    }

    public void registerListener(InputContext ctx, ActionEventListener listener) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(listener, "listener");
        listeners.computeIfAbsent(ctx, k -> new ArrayList<>(4)).add(listener);
    }

    public void unregisterListener(InputContext ctx, ActionEventListener listener) {
        List<ActionEventListener> list = listeners.get(ctx);
        if (list != null) {
            list.remove(listener);
        }
    }

    /** Вытащить накопленные события из backend и прогнать через биндинги. */
    public void pollFrom(InputBackend backend) {
        Objects.requireNonNull(backend, "backend");
        backend.pollEvents(this::consume);
    }

    /** Главный entry point: применить один raw event. */
    public void consume(RawInputEvent event) {
        Objects.requireNonNull(event, "event");
        switch (event.source()) {
            case KEYBOARD, GAMEPAD, TOUCH -> handleButton(event);
            case MOUSE -> handleMouse(event);
            case MOTION -> handleAxisOnly(event);
        }
    }

    private void handleButton(RawInputEvent event) {
        if (event.axis() != null) {
            handleAxisOnly(event);
            return;
        }
        InputContext ctx = currentContext();
        InputAction action = bindings.lookup(ctx, event.source(), event.keyCode());
        if (action == null) {
            return;
        }
        float strength = event.pressed() ? 1f : 0f;
        state.setActionDown(action, event.pressed());
        dispatch(ctx, action, event.pressed(), strength);
    }

    private void handleMouse(RawInputEvent event) {
        if (event.axis() != null) {
            handleAxisOnly(event);
            return;
        }
        if (event.x() != 0f || event.y() != 0f || event.keyCode() < 0) {
            // pointer move без кнопки
            state.setMousePosition(event.x(), event.y());
            if (event.keyCode() < 0) {
                return;
            }
        }
        // mouse button
        InputContext ctx = currentContext();
        InputAction action = bindings.lookup(ctx, event.source(), event.keyCode());
        if (action == null) {
            return;
        }
        float strength = event.pressed() ? 1f : 0f;
        state.setActionDown(action, event.pressed());
        dispatch(ctx, action, event.pressed(), strength);
    }

    private void handleAxisOnly(RawInputEvent event) {
        if (event.axis() == null) {
            return;
        }
        InputAxis axis = event.axis();
        float value = event.value();
        state.setAxis(axis, value);
        // Аксессы пока не привязаны к InputAction; при необходимости
        // расширим InputBindings под axis-биндинги (stage 2.2.1+).
    }

    private void dispatch(InputContext ctx, InputAction action, boolean pressed, float strength) {
        List<ActionEventListener> list = listeners.get(ctx);
        if (list != null) {
            for (int i = 0, n = list.size(); i < n; i++) {
                list.get(i).onAction(action, pressed, strength);
            }
        }
        events.publish(new ActionEvent(ctx, action, pressed, strength));
    }
}
