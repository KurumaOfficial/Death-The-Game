package WeTTeA.core.ecs;

/**
 * Функциональный интерфейс ECS-системы.
 *
 * <p>Система — это чистая функция «прочитать stores → записать stores»,
 * вызываемая на каждом sim-step (фиксированный шаг {@code FixedStepGameLoop}).
 * Контракт:
 * <ul>
 *   <li>Системы запускаются последовательно в порядке регистрации в
 *       {@link SystemScheduler}; параллельный scheduler — не stage 3.2.</li>
 *   <li>Реализация может читать и мутировать любые {@link ComponentStore} мира.</li>
 *   <li>Реализация НЕ должна спавнить/деспавнить сущности изнутри одного
 *       прохода стейджа: {@link EntityWorld#despawn(EntityId)} безопасен
 *       (компоненты уберутся из всех stores), но НЕ безопасен относительно
 *       уже-захваченных слотов в текущем цикле итерации того же store.
 *       Если нужно — буферизовать spawn/despawn в локальный список и
 *       применить после прохода.</li>
 *   <li>Системы НЕ обращаются к рендеру: render-фаза идёт через
 *       {@link WeTTeA.api.lifecycle.Drawable}, а сцена сама решает как
 *       визуализировать состояние ECS.</li>
 *   <li>Никаких аллокаций в hot path (создание систем — единоразово
 *       при сетапе сцены, а не покадрово).</li>
 * </ul>
 *
 * <p>Stage 3.2: scheduler — последовательный {@link SystemScheduler}. Для
 * детерминизма (replay, сетевой код) важно сохранить именно
 * sequential-порядок регистрации.
 *
 * @author Kuruma
 * @since 0.1.0
 */
@FunctionalInterface
public interface EcsSystem {

    /**
     * Обработать один шаг симуляции.
     *
     * @param world         корневой world ECS
     * @param deltaSeconds  шаг симуляции в секундах. При фиксированном шаге
     *                      ({@link WeTTeA.core.loop.FixedStepGameLoop}) равен
     *                      {@code FIXED_STEP_NANOS / 1e9}.
     */
    void update(EntityWorld world, double deltaSeconds);
}
