package WeTTeA.core.ecs;

import java.util.Arrays;
import java.util.Objects;

/**
 * Sparse-set хранилище компонентов одного типа.
 *
 * <p>Структуры:
 * <ul>
 *   <li>{@code int[] sparse} — {@code sparse[entityIndex]} = {@code -1} если у
 *       сущности нет компонента, иначе индекс в {@code dense}.</li>
 *   <li>{@code int[] entityIndices} — {@code entityIndices[slot]} = {@code entityIndex}
 *       (обратное отображение для swap-with-last в {@link #remove(int)}).</li>
 *   <li>{@code Object[] dense} — собственно компоненты в плотной упаковке;
 *       {@code dense[slot]} соответствует {@code entityIndices[slot]}.</li>
 * </ul>
 *
 * <p>Все операции — O(1) амортизированно. Растёт степенью двойки: поэтому
 * добавление 80k компонентов делает ≈17 grow'ов всего.
 *
 * <p>Не потокобезопасен (game-thread).
 *
 * @param <T> тип компонента
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class ComponentStore<T> {

    private static final int INITIAL_SPARSE = 64;
    private static final int INITIAL_DENSE = 16;

    private final Class<T> type;
    private int[] sparse;
    private int[] entityIndices;
    private Object[] dense;
    private int size;

    public ComponentStore(Class<T> type) {
        this.type = Objects.requireNonNull(type, "type");
        this.sparse = new int[INITIAL_SPARSE];
        Arrays.fill(this.sparse, -1);
        this.entityIndices = new int[INITIAL_DENSE];
        this.dense = new Object[INITIAL_DENSE];
        this.size = 0;
    }

    public Class<T> type() {
        return type;
    }

    public int size() {
        return size;
    }

    public boolean has(int entityIndex) {
        if (entityIndex < 0 || entityIndex >= sparse.length) return false;
        return sparse[entityIndex] >= 0;
    }

    /**
     * Записать компонент. Если у сущности уже был компонент этого типа —
     * заменяет (без ре-аллокации, без swap'а).
     */
    public void put(int entityIndex, T component) {
        Objects.requireNonNull(component, "component");
        if (entityIndex < 0) {
            throw new IllegalArgumentException("entityIndex < 0: " + entityIndex);
        }
        if (entityIndex >= sparse.length) {
            growSparse(entityIndex + 1);
        }
        int slot = sparse[entityIndex];
        if (slot >= 0) {
            // overwrite in-place
            dense[slot] = component;
            return;
        }
        slot = size;
        if (slot >= dense.length) {
            growDense(slot + 1);
        }
        sparse[entityIndex] = slot;
        entityIndices[slot] = entityIndex;
        dense[slot] = component;
        size++;
    }

    /**
     * Получить компонент или {@code null}, если у сущности его нет.
     * Не делает {@code Class.cast} — put() гарантирует тип, поэтому
     * unchecked cast безопасен и снимает overhead в hot path.
     */
    @SuppressWarnings("unchecked")
    public T get(int entityIndex) {
        if (!has(entityIndex)) return null;
        return (T) dense[sparse[entityIndex]];
    }

    /**
     * Удалить компонент сущности. Возвращает {@code true} если был и удалён.
     * Реализация — swap-with-last в {@code dense}; это ломает порядок
     * итерации, но даёт O(1) удаление и плотную упаковку для следующего
     * прохода.
     */
    public boolean remove(int entityIndex) {
        if (!has(entityIndex)) return false;
        int slot = sparse[entityIndex];
        int last = size - 1;
        if (slot != last) {
            int swappedEntity = entityIndices[last];
            dense[slot] = dense[last];
            entityIndices[slot] = swappedEntity;
            sparse[swappedEntity] = slot;
        }
        dense[last] = null;
        sparse[entityIndex] = -1;
        size--;
        return true;
    }

    /**
     * entityIndex компонента в плотной позиции {@code slot}. Используется
     * системами для итерации без аллокаций (см. javadoc пакета).
     */
    public int entityIndexAt(int slot) {
        if (slot < 0 || slot >= size) {
            throw new IndexOutOfBoundsException("slot=" + slot + ", size=" + size);
        }
        return entityIndices[slot];
    }

    /**
     * Компонент в плотной позиции {@code slot}. См. {@link #entityIndexAt(int)}.
     */
    @SuppressWarnings("unchecked")
    public T componentAt(int slot) {
        if (slot < 0 || slot >= size) {
            throw new IndexOutOfBoundsException("slot=" + slot + ", size=" + size);
        }
        return (T) dense[slot];
    }

    /** Очистить store целиком (без shrink — буферы сохраняются). */
    public void clear() {
        for (int i = 0; i < size; i++) {
            dense[i] = null;
            sparse[entityIndices[i]] = -1;
            entityIndices[i] = 0;
        }
        size = 0;
    }

    private void growSparse(int needLen) {
        int newLen = Math.max(sparse.length * 2, needLen);
        int oldLen = sparse.length;
        sparse = Arrays.copyOf(sparse, newLen);
        Arrays.fill(sparse, oldLen, newLen, -1);
    }

    private void growDense(int needLen) {
        int newLen = Math.max(dense.length * 2, needLen);
        dense = Arrays.copyOf(dense, newLen);
        entityIndices = Arrays.copyOf(entityIndices, newLen);
    }
}
