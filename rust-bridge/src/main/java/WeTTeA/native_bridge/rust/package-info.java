/**
 * Java-сторона JNI bridge'а к Rust ядру.
 *
 * <p>Содержит реализацию контрактов
 * {@link WeTTeA.api.nativebridge.NativeLibraryLoader} и фасад
 * {@link WeTTeA.native_bridge.rust.RustCore} над JNI-экспортами Rust crate'а.
 *
 * <p>Stage 1: модуль содержит только Java-сторону. Фактическая загрузка
 * нативной библиотеки требует собранной {@code rust-core/} (см.
 * docs/NATIVE_BRIDGE.md). Без бинарника {@link WeTTeA.native_bridge.rust.RustNativeLibrary#loadIfNeeded}
 * упадёт с {@link UnsatisfiedLinkError} — это намеренно и трекается в PROGRESS.md.
 *
 * @author Kuruma
 * @since 0.1.0
 */
package WeTTeA.native_bridge.rust;
