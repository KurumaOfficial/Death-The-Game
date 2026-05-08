package WeTTeA.api.physics;

/**
 * Трёхмерный вектор/точка в мировой системе координат Death.
 *
 * <p>Используется как value-объект для обмена позициями/скоростями между
 * Java-кодом и нативным {@link PhysicsWorld}. Игнорирует сторону "координатной
 * руки" — для stage 2.5 берём правую систему как у Rapier3D
 * (Y вверх, +X вправо, -Z от камеры). Если позже сменим — единая точка
 * пересчёта будет именно здесь.
 *
 * <p>Точность — {@code double} на Java стороне для удобства, нативка работает
 * в {@code f32} (см. {@code rust-core/src/physics/mod.rs}). Cast'ы делаются
 * прозрачно в JNI экспорте.
 *
 * @param x координата X
 * @param y координата Y (вверх)
 * @param z координата Z
 *
 * @author Kuruma
 * @since 0.1.0
 */
public record Vec3(double x, double y, double z) {

    /** Нулевой вектор {@code (0,0,0)} — экземпляр для удобства/аллок-фри сравнений. */
    public static final Vec3 ZERO = new Vec3(0.0, 0.0, 0.0);

    /** Шорт-фабрика для удобочитаемого call-site'а. */
    public static Vec3 of(double x, double y, double z) {
        return new Vec3(x, y, z);
    }
}
