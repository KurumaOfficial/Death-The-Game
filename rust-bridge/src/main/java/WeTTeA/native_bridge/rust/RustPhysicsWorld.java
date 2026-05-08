package WeTTeA.native_bridge.rust;

import WeTTeA.api.physics.PhysicsBodyHandle;
import WeTTeA.api.physics.PhysicsWorld;
import WeTTeA.api.physics.Vec3;

/**
 * JNI-реализация {@link PhysicsWorld} поверх Rapier3D ({@code rust-core}).
 *
 * <p>Stage 2.5 — gravity-driven симуляция без коллайдеров. Все методы
 * проксируют в нативные функции крейта {@code death-native}, см.
 * {@code rust-core/src/jni_exports.rs}, секция «PhysicsWorld JNI экспорты».
 *
 * <p>Жизненный цикл:
 * <ol>
 *   <li>{@link #RustPhysicsWorld()} — вызывает {@code nativeCreate}, который
 *       аллоцирует {@code Box<PhysicsWorld>} в Rust и возвращает raw pointer.
 *       Указатель хранится в {@link #handle} как {@code long}.</li>
 *   <li>{@code addDynamicBody / step / bodyPosition} — проверяют {@link #closed}
 *       и проксируют в native.</li>
 *   <li>{@link #close()} — вызывает {@code nativeDestroy(handle)}, выставляет
 *       {@code closed=true}. Идемпотентен.</li>
 * </ol>
 *
 * <p><b>Важно:</b> класс предполагает что {@link RustCore#initialize}
 * уже отработал — то есть {@code death-native} библиотека загружена и
 * символы видны. Если это не так — конструктор упадёт с
 * {@link UnsatisfiedLinkError}.
 *
 * <p>Класс не thread-safe: все вызовы должны идти из одного потока (того
 * же где был вызван конструктор) пока в Rust симуляция не завёрнута в Mutex.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class RustPhysicsWorld implements PhysicsWorld {

    /** Raw pointer на нативный {@code Box<PhysicsWorld>}, упакованный в long. */
    private long handle;

    /** Установлен в {@code true} после первого {@link #close()}; делает методы no-op/throwing. */
    private boolean closed;

    /** Re-used buffer для {@code nativeBodyPosition} чтобы не аллоцировать на каждом запросе. */
    private final double[] posScratch = new double[3];

    /**
     * Создаёт новый физический мир. Вызывает native init под капотом —
     * library должна быть загружена через {@link RustCore#initialize}.
     */
    public RustPhysicsWorld() {
        this.handle = nativeCreate();
        if (this.handle == 0L) {
            throw new IllegalStateException("nativeCreate вернул 0 — Rust не смог аллоцировать PhysicsWorld");
        }
    }

    @Override
    public PhysicsBodyHandle addDynamicBody(Vec3 pos) {
        ensureOpen();
        long raw = nativeAddDynamicBody(handle, pos.x(), pos.y(), pos.z());
        return new PhysicsBodyHandle(raw);
    }

    @Override
    public void step(double dt) {
        ensureOpen();
        nativeStep(handle, dt);
    }

    @Override
    public Vec3 bodyPosition(PhysicsBodyHandle body) {
        ensureOpen();
        if (!body.isValid()) {
            return new Vec3(Double.NaN, Double.NaN, Double.NaN);
        }
        nativeBodyPosition(handle, body.raw(), posScratch);
        return new Vec3(posScratch[0], posScratch[1], posScratch[2]);
    }

    @Override
    public void close() {
        if (closed) return;
        long h = handle;
        handle = 0L;
        closed = true;
        nativeDestroy(h);
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("RustPhysicsWorld уже закрыт");
        }
    }

    // -------------------------------------------------------------------------
    // Native экспорты — реализация в rust-core/src/jni_exports.rs.
    // Симвoл-mangling: WeTTeA.native_bridge.rust.RustPhysicsWorld →
    //                   Java_WeTTeA_native_1bridge_rust_RustPhysicsWorld_<method>
    // -------------------------------------------------------------------------

    private static native long nativeCreate();
    private static native void nativeDestroy(long handle);
    private static native long nativeAddDynamicBody(long handle, double x, double y, double z);
    private static native void nativeStep(long handle, double dt);
    private static native void nativeBodyPosition(long handle, long body, double[] out);
}
