package WeTTeA.native_bridge.rust;

import WeTTeA.api.nativebridge.NativeBridgeInfo;
import WeTTeA.api.platform.PlatformFileSystem;
import WeTTeA.api.platform.PlatformInfo;

import java.util.Objects;

/**
 * Java-фасад над JNI-экспортами Rust crate'а {@code death-native}.
 *
 * <p>Жизненный цикл:
 * <ol>
 *   <li>{@link #initialize(PlatformInfo, PlatformFileSystem)} — загружает
 *       нативный артефакт через {@link RustNativeLibrary}, вызывает
 *       {@link #nativeInit()};</li>
 *   <li>пользовательские вызовы (физика / ECS / pathfinding / scripting hooks
 *       — будут добавлены на стадии 2);</li>
 *   <li>{@link #shutdown()} — освобождает ресурсы Rust-стороны через
 *       {@link #nativeShutdown()}.</li>
 * </ol>
 *
 * <p>Stage 1: вызов {@link #initialize(PlatformInfo, PlatformFileSystem)}
 * упадёт с {@link UnsatisfiedLinkError}, пока не собран {@code rust-core/}
 * (см. PROGRESS.md строка RustCore — INTEGRATION_MISSING).
 *
 * <p>Symbol mapping для {@code native} методов:
 * {@code Java_WeTTeA_native_1bridge_rust_RustCore_nativeInit} (символ {@code _1}
 * это JNI-mangling для {@code _} в имени пакета).
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class RustCore {

    private final RustNativeLibrary loader;
    private boolean initialized;
    private NativeBridgeInfo info;

    public RustCore() {
        this(new RustNativeLibrary());
    }

    public RustCore(RustNativeLibrary loader) {
        this.loader = Objects.requireNonNull(loader, "loader");
    }

    public NativeBridgeInfo initialize(PlatformInfo platform, PlatformFileSystem fileSystem) {
        if (initialized) return info;
        info = loader.loadIfNeeded(platform, fileSystem);
        nativeInit();
        initialized = true;
        return info;
    }

    public void shutdown() {
        if (!initialized) return;
        nativeShutdown();
        initialized = false;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public NativeBridgeInfo info() {
        if (!initialized) {
            throw new IllegalStateException("RustCore not initialized");
        }
        return info;
    }

    // -------------------------------------------------------------------------
    // JNI native methods. Реализации экспортируются Rust crate'ом death-native
    // (rust-core/src/jni_exports.rs).
    // -------------------------------------------------------------------------

    private static native void nativeInit();
    private static native void nativeShutdown();
}
