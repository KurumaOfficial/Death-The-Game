package WeTTeA.core.ecs;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Корневой контейнер ECS-сущностей и компонентов.
 *
 * <p>Управляет:
 * <ul>
 *   <li>аллокацией {@link EntityId} (free-list с переиспользованием
 *       индексов и инкрементом {@code generation});</li>
 *   <li>хранилищами компонентов по типу — {@link ComponentStore} per
 *       {@link Class};</li>
 *   <li>удалением всех компонентов сущности при despawn'е.</li>
 * </ul>
 *
 * <h3>Алгоритм spawn/despawn</h3>
 * <ol>
 *   <li>{@link #spawn()}: если есть свободный слот в {@code freeIndices} —
 *       pop его, иначе — занимаем следующий слот ({@code highWatermark++}).
 *       Инкрементируем {@code generations[index]} и возвращаем
 *       {@link EntityId}{@code (index, generations[index])}.</li>
 *   <li>{@link #despawn(EntityId)}: проверяем {@link #isAlive(EntityId)},
 *       удаляем все компоненты сущности из всех stores, инкрементируем
 *       {@code generations[index]} (это сразу же делает старый id
 *       невалидным), пушим index в {@code freeIndices}.</li>
 * </ol>
 *
 * <p>Нюанс: {@code generations} инкрементируется на каждом spawn'е <b>и</b>
 * на каждом despawn'е. Поэтому для уже-аллоцированного слота:
 * <pre>
 *   spawn  → generation = 1 (alive)
 *   despawn → generation = 2 (dead, старый id #1 не совпадёт)
 *   spawn  → generation = 3 (alive, новый id #3)
 * </pre>
 * Старый сохранённый id {@code (index, 1)} никогда не пройдёт {@code isAlive}.
 *
 * <p>Не потокобезопасен (game-thread).
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class EntityWorld {

    private static final int INITIAL_CAPACITY = 64;

    /** generations[index] — текущее поколение слота; >0 alive, чётное == исходное+1. */
    private int[] generations;
    /** Свободные индексы (LIFO стек), готовые к переиспользованию. */
    private int[] freeIndices;
    private int freeCount;
    /** Сколько индексов занято всего (high-watermark, не уменьшается). */
    private int highWatermark;
    /** Активных сущностей сейчас. */
    private int aliveCount;

    private final Map<Class<?>, ComponentStore<?>> stores = new HashMap<>();

    public EntityWorld() {
        this.generations = new int[INITIAL_CAPACITY];
        this.freeIndices = new int[INITIAL_CAPACITY];
    }

    public EntityId spawn() {
        int index;
        if (freeCount > 0) {
            index = freeIndices[--freeCount];
        } else {
            if (highWatermark >= generations.length) {
                growGenerations(highWatermark + 1);
            }
            index = highWatermark++;
        }
        // Bump generation. После последовательности spawn→despawn→spawn это
        // даёт generation 1, 2, 3 и т.д. — старые id никогда не совпадают.
        int gen = ++generations[index];
        if (gen <= 0) {
            // Сверхредкий overflow — пропускаем 0 чтобы не нарушить инвариант
            // EntityId.generation > 0. На практике 2^31 spawn'ов одного слота
            // нереально, но защититься стоит.
            generations[index] = 1;
            gen = 1;
        }
        aliveCount++;
        return new EntityId(index, gen);
    }

    public boolean despawn(EntityId id) {
        Objects.requireNonNull(id, "id");
        if (!isAlive(id)) return false;
        int idx = id.index();
        // Удаляем компоненты сущности из всех stores. Не аллоцируем —
        // итерируем values() того же HashMap.
        for (ComponentStore<?> store : stores.values()) {
            store.remove(idx);
        }
        // Bump generation чтобы все висящие id с этим index перестали быть
        // alive (даже до следующего spawn'а на этот же слот).
        generations[idx]++;
        if (freeCount >= freeIndices.length) {
            freeIndices = Arrays.copyOf(freeIndices, freeIndices.length * 2);
        }
        freeIndices[freeCount++] = idx;
        aliveCount--;
        return true;
    }

    public boolean isAlive(EntityId id) {
        if (id == null) return false;
        int idx = id.index();
        if (idx < 0 || idx >= highWatermark) return false;
        return generations[idx] == id.generation();
    }

    public int aliveCount() {
        return aliveCount;
    }

    public int capacity() {
        return highWatermark;
    }

    public int freeSlots() {
        return freeCount;
    }

    /**
     * Получить store данного типа, создав если нужно.
     */
    public <T> ComponentStore<T> store(Class<T> type) {
        Objects.requireNonNull(type, "type");
        @SuppressWarnings("unchecked")
        ComponentStore<T> existing = (ComponentStore<T>) stores.get(type);
        if (existing != null) return existing;
        ComponentStore<T> created = new ComponentStore<>(type);
        stores.put(type, created);
        return created;
    }

    /** {@code true} если store этого типа уже существует. */
    public boolean hasStore(Class<?> type) {
        return stores.containsKey(type);
    }

    /** Снимок всех зарегистрированных stores (read-only). */
    public Collection<ComponentStore<?>> stores() {
        return Collections.unmodifiableCollection(stores.values());
    }

    public <T> void set(EntityId id, Class<T> type, T component) {
        Objects.requireNonNull(component, "component");
        if (!isAlive(id)) {
            throw new IllegalStateException("set: сущность " + id + " не жива");
        }
        store(type).put(id.index(), component);
    }

    public <T> T get(EntityId id, Class<T> type) {
        if (!isAlive(id)) return null;
        @SuppressWarnings("unchecked")
        ComponentStore<T> store = (ComponentStore<T>) stores.get(type);
        if (store == null) return null;
        return store.get(id.index());
    }

    public <T> boolean has(EntityId id, Class<T> type) {
        if (!isAlive(id)) return false;
        ComponentStore<?> store = stores.get(type);
        if (store == null) return false;
        return store.has(id.index());
    }

    public <T> boolean remove(EntityId id, Class<T> type) {
        if (!isAlive(id)) return false;
        ComponentStore<?> store = stores.get(type);
        if (store == null) return false;
        return store.remove(id.index());
    }

    /**
     * Полная очистка world'а (все entity dead + все stores cleared).
     * Используется в {@code Scene.onExit()} перед dispose.
     */
    public void clear() {
        for (ComponentStore<?> store : stores.values()) {
            store.clear();
        }
        Arrays.fill(generations, 0, highWatermark, 0);
        freeCount = 0;
        highWatermark = 0;
        aliveCount = 0;
    }

    private void growGenerations(int needLen) {
        int newLen = Math.max(generations.length * 2, needLen);
        generations = Arrays.copyOf(generations, newLen);
    }
}
