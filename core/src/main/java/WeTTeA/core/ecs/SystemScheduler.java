package WeTTeA.core.ecs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Последовательный запуск {@link EcsSystem} в порядке регистрации.
 *
 * <p>Stage 3.2: один поток, sequential execution. Параллельный scheduler
 * (как Bevy с барьерами по доступу к компонентам) — отдельный stage
 * после того, как профайлер покажет, что системы упёрлись в одно ядро.
 *
 * <p>Сцены владеют scheduler'ом 1:1; при {@code Scene.dispose()}
 * scheduler'у можно дать выйти из области видимости — он не держит
 * native-ресурсов. Системы, в свою очередь, могут переиспользоваться
 * между сценами, но в большинстве случаев логика боя/интро/меню разная,
 * и собственный scheduler у каждой сцены — нормальная практика.
 *
 * <p>Не потокобезопасен (game-thread).
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class SystemScheduler {

    private final List<EcsSystem> systems = new ArrayList<>();

    /** Зарегистрировать систему в конец очереди. */
    public SystemScheduler add(EcsSystem system) {
        Objects.requireNonNull(system, "system");
        systems.add(system);
        return this;
    }

    /** Удалить систему. Возвращает {@code true}, если была. */
    public boolean remove(EcsSystem system) {
        return systems.remove(system);
    }

    /** Прогнать все системы по миру в порядке регистрации. */
    public void update(EntityWorld world, double deltaSeconds) {
        Objects.requireNonNull(world, "world");
        // Индексация по int — чтобы исключить аллокацию итератора (в hot path
        // на 60 Hz это значимо в долгой игровой сессии).
        int n = systems.size();
        for (int i = 0; i < n; i++) {
            systems.get(i).update(world, deltaSeconds);
        }
    }

    public int size() {
        return systems.size();
    }

    /** Снимок зарегистрированных систем (read-only). */
    public List<EcsSystem> systems() {
        return Collections.unmodifiableList(systems);
    }

    /** Очистить scheduler без вызова чего-либо у систем. */
    public void clear() {
        systems.clear();
    }
}
