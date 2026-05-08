package WeTTeA.core.events;

import WeTTeA.api.events.EventBus;
import WeTTeA.api.events.GameEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Реализация {@link EventBus} — синхронная in-process шина.
 *
 * <p>Подписчики вызываются в порядке регистрации, в потоке {@code publish()}.
 * Не потокобезопасен — все операции из game-thread.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class SimpleEventBus implements EventBus {

    private final Map<Class<? extends GameEvent>, List<ActiveSub<? extends GameEvent>>> byType =
            new HashMap<>();

    @Override
    public <T extends GameEvent> Subscription subscribe(Class<T> type, Listener<T> listener) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(listener, "listener");
        ActiveSub<T> sub = new ActiveSub<>(listener);
        byType.computeIfAbsent(type, k -> new ArrayList<>()).add(sub);
        return sub;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends GameEvent> void publish(T event) {
        Objects.requireNonNull(event, "event");
        List<ActiveSub<? extends GameEvent>> subs = byType.get(event.getClass());
        if (subs == null) return;
        for (ActiveSub<? extends GameEvent> raw : subs) {
            if (!raw.active) continue;
            ((ActiveSub<T>) raw).listener.onEvent(event);
        }
    }

    private static final class ActiveSub<T extends GameEvent> implements Subscription {
        final Listener<T> listener;
        boolean active = true;

        ActiveSub(Listener<T> listener) {
            this.listener = listener;
        }

        @Override
        public void unsubscribe() {
            active = false;
        }

        @Override
        public boolean isActive() {
            return active;
        }
    }
}
