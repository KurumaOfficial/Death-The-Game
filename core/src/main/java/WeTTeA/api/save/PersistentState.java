package WeTTeA.api.save;

/**
 * Persistent состояние одного слота — сериализуется и десериализуется
 * через {@link SaveStorage}.
 *
 * <p>Реализации:
 * <ul>
 *   <li>{@code WeTTeA.core.save.DefaultPersistentState} — для старта,
 *       хранит JSON-сериализуемые данные;</li>
 *   <li>в будущем — типизированные структуры по разделам (player, world,
 *       quests).</li>
 * </ul>
 *
 * @author Kuruma
 * @since 0.1.0
 */
public interface PersistentState {

    /** Версия формата — для миграций между билдами. */
    int formatVersion();

    /**
     * Время игры в секундах с момента старта новой партии. Обновляется
     * при сохранении.
     */
    double playTimeSeconds();

    /**
     * Имя локации, где игрок последний раз сохранился. Допустимо
     * {@code null}/пустая строка на чистом старте.
     */
    String locationId();
}
