/**
 * Минимальный ECS Death (stage 3.2).
 *
 * <p><b>Цель.</b> Storage + iteration для gameplay-логики (бой, AI, движение
 * снарядов, частицы, danmaku-паттерны). Цель — 80k+ entities в кадре без
 * аллокаций в hot path. Архитектура — sparse-set per component, как в
 * <a href="https://github.com/skypjack/entt">EnTT</a>/
 * <a href="https://docs.rs/hecs/latest/hecs/">hecs</a>:
 * O(1) get/set/has/remove, dense-итерация без скачков по памяти.
 *
 * <h3>Контракты</h3>
 * <ul>
 *   <li>{@link WeTTeA.core.ecs.EntityId} — стабильный handle. Пара
 *       {@code (index, generation)} даёт ABA-safety: после despawn'а
 *       старый id больше не валиден, даже если индекс переиспользован.</li>
 *   <li>{@link WeTTeA.core.ecs.ComponentStore} — sparse-set хранилище
 *       компонентов одного типа.</li>
 *   <li>{@link WeTTeA.core.ecs.EntityWorld} — owner всех stores; spawn/
 *       despawn/get/set/has/remove.</li>
 *   <li>{@link WeTTeA.core.ecs.EcsSystem} — функция, обрабатывающая мир за
 *       один tick (FixedStep шаг симуляции).</li>
 *   <li>{@link WeTTeA.core.ecs.SystemScheduler} — упорядоченный список
 *       систем, выполняемый сценой.</li>
 * </ul>
 *
 * <h3>Производительность</h3>
 * <ul>
 *   <li>Storage: {@code int[] sparse} (entityIndex → packed slot),
 *       {@code int[] entityIndices} (slot → entityIndex), {@code Object[] dense}
 *       (slot → component). Итерация — линейный проход по dense.</li>
 *   <li>Despawn — swap-with-last в dense (O(1)).</li>
 *   <li>Spawn — pop из {@code freeIndices} (O(1)) или инкремент high-watermark.</li>
 *   <li>get/has — два массивных доступа без boxing.</li>
 *   <li>Iteration — system берёт {@code ComponentStore} напрямую и идёт
 *       циклом по slot'ам; для join'а нескольких компонентов system должен
 *       выбрать «ведущий» (обычно меньший по размеру) store и в цикле
 *       проверять {@code has(...)} остальных.</li>
 * </ul>
 *
 * <h3>Что НЕ делает stage 3.2</h3>
 * <ul>
 *   <li>Нет query DSL ({@code <With<A, B>>}, {@code <Without<C>>}) —
 *       systems пишутся вручную над двумя-тремя stores.</li>
 *   <li>Нет parallel scheduler'а — все системы запускаются последовательно
 *       в порядке регистрации (важно для детерминизма реплея/replay'я
 *       и пред-сетевого кода).</li>
 *   <li>Нет архитипов (entt-style chunks) — сначала меряем профилем, потом
 *       решаем стоит ли усложнять. Для N=80k sparse-set обычно укладывается
 *       в L2 cache.</li>
 *   <li>Нет миграции в Rust (bevy_ecs/hecs) — это stage 3.x когда
 *       JNI-overhead станет узким местом для ровной частоты 60 Hz.</li>
 * </ul>
 *
 * @author Kuruma
 * @since 0.1.0
 */
package WeTTeA.core.ecs;
