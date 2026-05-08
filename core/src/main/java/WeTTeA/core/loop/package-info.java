/**
 * Игровой цикл core: фиксированный шаг + интерполяция render.
 *
 * <p>{@link WeTTeA.core.loop.GameLoop} — контракт цикла,
 * {@link WeTTeA.core.loop.FixedStepGameLoop} — реализация по умолчанию
 * с фиксированным simulation-step (60 Hz) и переменным render-rate.
 *
 * @author Kuruma
 * @since 0.1.0
 */
package WeTTeA.core.loop;
