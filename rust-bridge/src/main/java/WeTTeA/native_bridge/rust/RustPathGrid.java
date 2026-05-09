package WeTTeA.native_bridge.rust;

import WeTTeA.api.pathfinding.PathGrid;

/**
 * JNI-реализация {@link PathGrid} поверх pure-Rust A* (модуль
 * {@code death-native::pathfinding}). Все методы проксируют в нативные
 * функции крейта {@code death-native}, см.
 * {@code rust-core/src/jni_exports.rs}, секция «PathGrid JNI экспорты».
 *
 * <p>Stage 3.3a — минимальный функционал: 4-directional A* с
 * Manhattan-heuristic'ой, без terrain costs и без diagonal movement
 * (см. roadmap в {@link PathGrid}).
 *
 * <p>Жизненный цикл:
 * <ol>
 *   <li>{@link #RustPathGrid(int, int)} — вызывает {@code nativeCreate(w, h)},
 *       аллоцирующий {@code Box<Grid>} в Rust и возвращающий raw pointer.
 *       Указатель хранится в {@link #handle} как {@code long}.</li>
 *   <li>{@code setBlocked / isBlocked / findPath} — проверяют {@link #closed}
 *       и проксируют в native (с предварительной валидацией bounds на
 *       Java-стороне).</li>
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
 * же где был вызван конструктор) пока в Rust сетка не завёрнута в Mutex.
 * Для тактического боя это естественно — pathfinding вызывается из game
 * loop'а, не из render-потока.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class RustPathGrid implements PathGrid {

    /** Raw pointer на нативный {@code Box<Grid>}, упакованный в long. */
    private long handle;

    /** Установлен в {@code true} после первого {@link #close()}; делает методы no-op/throwing. */
    private boolean closed;

    private final int width;
    private final int height;

    /**
     * Создаёт сетку {@code width × height} со всеми клетками проходимыми.
     * Вызывает native init под капотом — library должна быть загружена
     * через {@link RustCore#initialize}.
     *
     * @throws IllegalArgumentException если {@code width <= 0} или {@code height <= 0}
     */
    public RustPathGrid(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                "RustPathGrid: width/height must be > 0, got " + width + "x" + height);
        }
        this.width = width;
        this.height = height;
        this.handle = nativeCreate(width, height);
        if (this.handle == 0L) {
            throw new IllegalStateException(
                "nativeCreate вернул 0 — Rust не смог аллоцировать Grid");
        }
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public void setBlocked(int x, int y, boolean blocked) {
        ensureOpen();
        ensureBounds(x, y);
        nativeSetBlocked(handle, x, y, blocked);
    }

    @Override
    public boolean isBlocked(int x, int y) {
        ensureOpen();
        ensureBounds(x, y);
        return nativeIsBlocked(handle, x, y);
    }

    @Override
    public int[] findPath(int sx, int sy, int gx, int gy) {
        ensureOpen();
        // findPath намеренно НЕ бросает на out-of-bounds — возвращает
        // пустой массив (см. javadoc PathGrid.findPath). Native слой
        // тоже это поддерживает.
        int[] path = nativeFindPath(handle, sx, sy, gx, gy);
        return path != null ? path : new int[0];
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
            throw new IllegalStateException("RustPathGrid уже закрыт");
        }
    }

    private void ensureBounds(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            throw new IndexOutOfBoundsException(
                "RustPathGrid: cell (" + x + ", " + y + ") out of bounds for "
                    + width + "x" + height + " grid");
        }
    }

    // -------------------------------------------------------------------------
    // Native экспорты — реализация в rust-core/src/jni_exports.rs.
    // Симвoл-mangling: WeTTeA.native_bridge.rust.RustPathGrid →
    //                   Java_WeTTeA_native_1bridge_rust_RustPathGrid_<method>
    // -------------------------------------------------------------------------

    private static native long nativeCreate(int width, int height);
    private static native void nativeDestroy(long handle);
    private static native void nativeSetBlocked(long handle, int x, int y, boolean blocked);
    private static native boolean nativeIsBlocked(long handle, int x, int y);
    private static native int[] nativeFindPath(long handle, int sx, int sy, int gx, int gy);
}
