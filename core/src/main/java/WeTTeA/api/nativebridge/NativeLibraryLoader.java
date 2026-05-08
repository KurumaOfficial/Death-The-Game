package WeTTeA.api.nativebridge;

import WeTTeA.api.platform.PlatformFileSystem;
import WeTTeA.api.platform.PlatformInfo;

/**
 * Загрузчик нативной Rust библиотеки.
 *
 * <p>Реализация ({@code WeTTeA.native_bridge.rust.RustNativeLibrary})
 * выбирает правильную бинарь по {@link PlatformInfo}:
 * <ul>
 *   <li>Linux x86_64 → {@code libdeath_native.so};</li>
 *   <li>Windows x86_64 → {@code death_native.dll};</li>
 *   <li>macOS x86_64 / arm64 → {@code libdeath_native.dylib};</li>
 *   <li>Android arm64-v8a → {@code libdeath_native.so} в {@code lib/arm64-v8a/};</li>
 *   <li>iOS arm64 → статически слинкована.</li>
 * </ul>
 *
 * @author Kuruma
 * @since 0.1.0
 */
public interface NativeLibraryLoader {

    /**
     * Загрузить нативную библиотеку, если ещё не загружена. Идемпотентен.
     *
     * @param platform     информация о платформе для выбора бинарной артефакты
     * @param fileSystem   доступ к user data dir для распаковки (Android извлекает .so)
     * @return метаданные загруженной библиотеки
     * @throws IllegalStateException если бинарник для текущей платформы не найден
     */
    NativeBridgeInfo loadIfNeeded(PlatformInfo platform, PlatformFileSystem fileSystem);

    /** Уже загружена ли библиотека. */
    boolean isLoaded();
}
