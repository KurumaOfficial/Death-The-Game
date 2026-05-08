/**
 * Жизненный цикл игровых объектов и систем.
 *
 * <p>Контракты этого пакета:
 * <ul>
 *   <li>{@link WeTTeA.api.lifecycle.Tickable} — обновляется каждый тик игрового цикла;</li>
 *   <li>{@link WeTTeA.api.lifecycle.Drawable} — записывает draw-команды в текущий render scope;</li>
 *   <li>{@link WeTTeA.api.lifecycle.Disposable} — освобождает ресурсы (нативные handles, native memory);</li>
 *   <li>{@link WeTTeA.api.lifecycle.LifecyclePhase} — фазы жизненного цикла приложения;</li>
 *   <li>{@link WeTTeA.api.lifecycle.GameLifecycle} — общий контракт инициализации/обновления/завершения системы.</li>
 * </ul>
 *
 * @author Kuruma
 * @since 0.1.0
 */
package WeTTeA.api.lifecycle;
