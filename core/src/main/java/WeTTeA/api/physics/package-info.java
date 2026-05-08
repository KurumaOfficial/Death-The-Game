/**
 * Контракты физической подсистемы Death (stage 2.5+).
 *
 * <p>Контейнер мира — {@link WeTTeA.api.physics.PhysicsWorld}. Тело
 * адресуется непрозрачным {@link WeTTeA.api.physics.PhysicsBodyHandle},
 * позиции/скорости — {@link WeTTeA.api.physics.Vec3}.
 *
 * <p>Stage 2.5 покрывает только gravity-driven dynamic bodies без
 * коллайдеров. Контакт-детектор и raycast-API будут добавлены в stage
 * 2.6 (см. {@code PROGRESS.md} → таблица 3).
 *
 * <p>Реализация по умолчанию: {@code WeTTeA.native_bridge.rust.RustPhysicsWorld}
 * (Rapier3D через JNI).
 *
 * @author Kuruma
 * @since 0.1.0
 */
package WeTTeA.api.physics;
