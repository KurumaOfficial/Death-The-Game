/**
 * Контракты к нативным библиотекам (Rust JNI).
 *
 * <p>Реализация JNI bridge'а живёт в модуле {@code :rust-bridge}
 * ({@code WeTTeA.native_bridge.rust.*}). Core видит только контракты этого
 * пакета — {@code RustCore} и подмодули. Прямые JNI-вызовы из gameplay,
 * UI или render слоёв запрещены.
 *
 * <p>Пакеты:
 * <ul>
 *   <li>{@link WeTTeA.api.nativebridge.NativeBridgeInfo} — метаданные о текущей
 *       загруженной нативной библиотеке;</li>
 *   <li>{@link WeTTeA.api.nativebridge.NativeLibraryLoader} — загрузка
 *       платформенно-специфичной библиотеки;</li>
 *   <li>{@link WeTTeA.api.nativebridge.NativeModule} — категория модуля
 *       нативного ядра (AI, Physics, Bullet, LargeScale, Spatial).</li>
 * </ul>
 *
 * @author Kuruma
 * @since 0.1.0
 */
package WeTTeA.api.nativebridge;
