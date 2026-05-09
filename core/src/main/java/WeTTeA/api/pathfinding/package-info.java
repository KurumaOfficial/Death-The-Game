/**
 * Тактическая сетка + pathfinding API Death (stage 3.3a).
 *
 * <p>Контракт {@link WeTTeA.api.pathfinding.PathGrid} описывает 2D сетку
 * с проходимыми/заблокированными клетками и поиском кратчайшего пути
 * через A* с Manhattan-heuristic'ой. Реализация по умолчанию —
 * {@code WeTTeA.native_bridge.rust.RustPathGrid} (A* в Rust через JNI),
 * подключается на стадии {@code DesktopLauncher} вместе с
 * {@code RustCore.initialize}.
 *
 * <p>Этот пакет — единственная точка интеграции pathfinding'а в :core
 * (battle scenes, AI, гриды препятствий). Никаких прямых JNI-вызовов
 * наружу не торчит — Java-классы видят только {@code PathGrid} интерфейс.
 *
 * <p>Stage 3.3b и далее расширят пакет:
 * <ul>
 *   <li>{@code TerrainGrid} — клетки с весами (cost) для terrain-heavy карт;</li>
 *   <li>{@code MultiAgentReservation} — резервация клеток на тики
 *       вперёд для синхронного движения нескольких юнитов;</li>
 *   <li>{@code LineOfSight} — visibility queries (Bresenham raycast по сетке);</li>
 *   <li>{@code HierarchicalPathGrid} — HPA* для крупных карт &gt;256×256.</li>
 * </ul>
 *
 * @author Kuruma
 * @since 0.1.0
 */
package WeTTeA.api.pathfinding;
