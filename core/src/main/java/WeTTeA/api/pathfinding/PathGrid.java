package WeTTeA.api.pathfinding;

/**
 * Контракт тактической сетки + pathfinding'а Death.
 *
 * <p>Stage 3.3a — минимальная функциональность:
 * <ul>
 *   <li>фиксированная 2D сетка размера {@code width × height} с булевыми
 *       клетками "проходимо / заблокировано";</li>
 *   <li>динамическое выставление {@link #setBlocked(int, int, boolean)};</li>
 *   <li>поиск кратчайшего пути 4-направленного движения (без диагоналей)
 *       через A* с Manhattan-heuristic'ой;</li>
 *   <li>освобождение нативных ресурсов через {@link AutoCloseable#close()}.</li>
 * </ul>
 *
 * <p>Stage 3.3b+ расширит:
 * <ul>
 *   <li>стоимости перехода между клетками (terrain cost) — {@code int costAt(x,y)};</li>
 *   <li>diagonal movement (8-directional) — флаг в конструкторе;</li>
 *   <li>multi-agent reservation system (для одновременного движения
 *       нескольких юнитов без коллизий);</li>
 *   <li>HPA* (hierarchical) для крупных карт &gt; 256×256;</li>
 *   <li>line-of-sight queries поверх той же сетки (для AI awareness).</li>
 * </ul>
 *
 * <p>Реализация по умолчанию — {@code WeTTeA.native_bridge.rust.RustPathGrid}
 * (A* в Rust через JNI). Контракт намеренно не привязан к Rust'у: можно
 * подменить на pure-Java реализацию для headless юнит-тестов или для
 * платформ без native сборки.
 *
 * <h2>Жизненный цикл</h2>
 * <ol>
 *   <li>Конструктор реализации делает native allocate (для RustPathGrid —
 *       выделяется {@code Box<Grid>} в Rust);</li>
 *   <li>Все методы можно вызывать сколько угодно раз пока сетка не закрыта;</li>
 *   <li>{@link #close()} освобождает нативный буфер. После этого все методы
 *       (кроме {@code close()}) бросают {@link IllegalStateException}.</li>
 * </ol>
 *
 * <h2>Координатная система</h2>
 * <p>Origin {@code (0, 0)} — левый нижний угол. Положительный X — вправо,
 * положительный Y — вверх. Допустимые значения: {@code 0 <= x < width()},
 * {@code 0 <= y < height()}. Любой выход за границы → IndexOutOfBoundsException
 * (для {@link #setBlocked} / {@link #isBlocked}); для {@link #findPath} —
 * пустой массив (out-of-bounds трактуется как unreachable).
 *
 * @author Kuruma
 * @since 0.1.0
 */
public interface PathGrid extends AutoCloseable {

    /**
     * Ширина сетки в клетках. Зафиксирована в конструкторе и не меняется.
     */
    int width();

    /**
     * Высота сетки в клетках. Зафиксирована в конструкторе и не меняется.
     */
    int height();

    /**
     * Помечает клетку {@code (x, y)} как проходимую ({@code blocked == false})
     * или заблокированную ({@code blocked == true}).
     *
     * <p>На stage 3.3a это единственный способ редактировать сетку — нет
     * batch update'ов. Вызывается обычно один раз на инициализации сцены
     * (расставить статические препятствия) и опционально при разрушении
     * препятствий по ходу боя.
     *
     * @param x        координата по горизонтали
     * @param y        координата по вертикали
     * @param blocked  {@code true} — клетка непроходима
     * @throws IllegalStateException     если сетка уже закрыта
     * @throws IndexOutOfBoundsException если {@code (x, y)} вне границ
     */
    void setBlocked(int x, int y, boolean blocked);

    /**
     * Возвращает текущее состояние клетки {@code (x, y)}.
     *
     * @return {@code true} — клетка заблокирована
     * @throws IllegalStateException     если сетка уже закрыта
     * @throws IndexOutOfBoundsException если {@code (x, y)} вне границ
     */
    boolean isBlocked(int x, int y);

    /**
     * Ищет кратчайший путь от {@code (sx, sy)} до {@code (gx, gy)}
     * через 4-направленное движение (вверх/вниз/влево/вправо).
     *
     * <p>Возврат — flat массив длины {@code 2 * pathLength}, где
     * {@code [2*i]} = x, {@code [2*i + 1]} = y i-й клетки пути.
     * Путь включает {@code start} (индекс 0) и {@code goal}
     * (последний индекс).
     *
     * <p>Особые случаи:
     * <ul>
     *   <li>{@code start == goal} → возврат {@code int[]{x, y}} (1 клетка);</li>
     *   <li>{@code start} или {@code goal} вне границ → пустой массив;</li>
     *   <li>{@code start} или {@code goal} заблокированы → пустой массив;</li>
     *   <li>пути нет (полностью отрезано стенами) → пустой массив.</li>
     * </ul>
     *
     * <p>Алгоритм — A* с Manhattan-heuristic'ой и binary heap для open-set'а.
     * Сложность: O((W·H) log(W·H)) worst case, обычно гораздо меньше за счёт
     * heuristic'ы. На сетке 8×8 (типичная тактическая карта Death) поиск
     * выполняется за &lt;0.1ms даже на slow target'ах.
     *
     * @param sx start X
     * @param sy start Y
     * @param gx goal X
     * @param gy goal Y
     * @return flat int[] (x0, y0, x1, y1, ...) или пустой массив если пути нет
     * @throws IllegalStateException если сетка уже закрыта
     */
    int[] findPath(int sx, int sy, int gx, int gy);

    /**
     * Освобождает нативные ресурсы. Идемпотентен — повторные вызовы no-op.
     * После закрытия любые методы кроме {@code close()} бросают
     * {@link IllegalStateException}.
     */
    @Override
    void close();
}
