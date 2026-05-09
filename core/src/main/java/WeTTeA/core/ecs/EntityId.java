package WeTTeA.core.ecs;

/**
 * Стабильный handle ECS-сущности.
 *
 * <p>Пара {@code (index, generation)}. {@code index} — позиция в плоском
 * массиве entity-records внутри {@link EntityWorld}; {@code generation} —
 * монотонно растущий счётчик переиспользования слота. После
 * {@link EntityWorld#despawn(EntityId)} {@code generation} в массиве
 * {@code generations} инкрементируется, поэтому старый {@link EntityId}
 * перестанет совпадать с актуальным состоянием — это даёт ABA-safety:
 * gameplay-код, державший «протухший» id, корректно получит
 * {@code isAlive() == false} даже если индекс уже переиспользован
 * для новой сущности.
 *
 * <p>{@code generation} всегда > 0; {@code index} ≥ 0. Конструктор
 * валидирует — это защищает от случайной конструкции «случайного» id
 * с {@code generation == 0}, который иначе совпал бы с дефолтным
 * содержимым {@code int[] generations}.
 *
 * @param index      позиция в массиве entity-records {@link EntityWorld};
 *                   стабильна между spawn'ом и despawn'ом, после
 *                   despawn'а слот может быть переиспользован
 * @param generation поколение слота; разное для каждой инкарнации индекса
 *
 * @author Kuruma
 * @since 0.1.0
 */
public record EntityId(int index, int generation) {

    public EntityId {
        if (index < 0) {
            throw new IllegalArgumentException("EntityId.index должен быть >= 0, получено: " + index);
        }
        if (generation <= 0) {
            throw new IllegalArgumentException(
                    "EntityId.generation должен быть > 0, получено: " + generation);
        }
    }

    @Override
    public String toString() {
        return "Entity#" + index + ":g" + generation;
    }
}
