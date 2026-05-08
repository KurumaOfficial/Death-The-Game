package WeTTeA.api.events;

/**
 * In-process pub/sub шина.
 *
 * <p>Реализация ({@code WeTTeA.core.events.SimpleEventBus}) хранит
 * подписчиков по типу события и выкатывает события синхронно из
 * вызывающего потока. Запрещено публиковать события из render-thread
 * без явного перевода на game-thread.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public interface EventBus {

    /**
     * Подписаться на события типа {@code T}.
     *
     * @return токен отписки — вызвать {@link Subscription#unsubscribe()}
     *         для прекращения получения событий.
     */
    <T extends GameEvent> Subscription subscribe(Class<T> type, Listener<T> listener);

    /** Опубликовать событие. Все подписчики на тип события получат вызов. */
    <T extends GameEvent> void publish(T event);

    /** Подписчик. */
    @FunctionalInterface
    interface Listener<T extends GameEvent> {
        void onEvent(T event);
    }

    /** Токен отписки. */
    interface Subscription {
        void unsubscribe();

        boolean isActive();
    }
}
