/**
 * Реализация {@link WeTTeA.api.nativebridge.NativeLibraryLoader}.
 *
 * <p>Этот пакет лежит в core, но реальная загрузка .so/.dll/.dylib
 * выполняется в {@code :rust-bridge}. Здесь только interfaces / fallback'и.
 *
 * <p>На этапе 1 — INTEGRATION_MISSING.
 *
 * @author Kuruma
 * @since 0.1.0
 */
package WeTTeA.native_bridge;
