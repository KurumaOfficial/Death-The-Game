package WeTTeA.api.physics;

/**
 * Контракт физического мира Death.
 *
 * <p>Stage 2.5 — минимальная функциональность:
 * <ul>
 *   <li>создание dynamic rigid body в произвольной позиции;</li>
 *   <li>интегрирование шага симуляции (gravity по умолчанию);</li>
 *   <li>чтение мировой позиции тела по handle;</li>
 *   <li>освобождение нативных ресурсов через {@link AutoCloseable#close()}.</li>
 * </ul>
 *
 * <p>Stage 2.6+ расширит интерфейс коллайдерами, силами/импульсами,
 * collision queries (raycast), characters controllers.
 *
 * <p>Реализация по умолчанию — {@code WeTTeA.native_bridge.rust.RustPhysicsWorld}
 * (Rapier3D через JNI). Контракт намеренно не привязан к Rapier'у: можно
 * подменить на CPU-only Java-реализацию для headless юнит-тестов.
 *
 * <h2>Жизненный цикл</h2>
 * <ol>
 *   <li>Конструктор реализации делает native allocate (для RustPhysicsWorld —
 *       выделяется {@code Box<PhysicsWorld>} в Rust);</li>
 *   <li>Все методы можно вызывать сколько угодно раз пока мир не закрыт;</li>
 *   <li>{@link #close()} освобождает нативный буфер. После этого все handle'ы
 *       становятся невалидными, любой следующий вызов — UB.</li>
 * </ol>
 *
 * @author Kuruma
 * @since 0.1.0
 */
public interface PhysicsWorld extends AutoCloseable {

    /**
     * Добавляет dynamic rigid body в позицию {@code pos} без коллайдера.
     *
     * @param pos позиция в мировых координатах
     * @return handle, валидный пока мир открыт
     * @throws IllegalStateException если мир уже закрыт
     */
    PhysicsBodyHandle addDynamicBody(Vec3 pos);

    /**
     * Шаг симуляции на {@code dt} секунд. Применяет гравитацию ко всем
     * dynamic телам и интегрирует скорости/позиции.
     *
     * @param dt шаг в секундах (рекомендуемый 1/60)
     * @throws IllegalStateException если мир уже закрыт
     */
    void step(double dt);

    /**
     * Возвращает текущую мировую позицию тела.
     *
     * @param body handle, ранее выданный {@link #addDynamicBody(Vec3)}
     * @return позиция или {@code Vec3} из NaN если handle уже не валиден
     * @throws IllegalStateException если мир уже закрыт
     */
    Vec3 bodyPosition(PhysicsBodyHandle body);

    /**
     * Освобождает нативные ресурсы. Идемпотентен — повторные вызовы no-op.
     * После закрытия любые методы кроме {@code close()} должны бросать
     * {@link IllegalStateException}.
     */
    @Override
    void close();
}
