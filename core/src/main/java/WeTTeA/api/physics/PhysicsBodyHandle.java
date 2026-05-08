package WeTTeA.api.physics;

/**
 * Непрозрачный handle на rigid body внутри {@link PhysicsWorld}.
 *
 * <p>Обёртка вокруг {@code long}, который Rust возвращает из
 * {@code nativeAddDynamicBody}. Внутри long закодировано:
 * <ul>
 *   <li>младшие 32 бита — index в {@code RigidBodySet};</li>
 *   <li>старшие 32 бита — generation (anti-ABA).</li>
 * </ul>
 *
 * <p>Java коду не нужно (и нельзя) интерпретировать содержимое — handle
 * передаётся обратно в {@link PhysicsWorld#bodyPosition(PhysicsBodyHandle)}
 * как непрозрачный токен. Время жизни — пока соответствующий мир не
 * закрыт; после этого handle становится невалидным.
 *
 * @param raw упакованный {@code long}, выданный нативкой
 *
 * @author Kuruma
 * @since 0.1.0
 */
public record PhysicsBodyHandle(long raw) {

    /** Sentinel для "нет тела" — например когда мир ещё не открыт. */
    public static final PhysicsBodyHandle INVALID = new PhysicsBodyHandle(-1L);

    /** {@code true} если handle не равен {@link #INVALID}. */
    public boolean isValid() {
        return raw != -1L;
    }
}
