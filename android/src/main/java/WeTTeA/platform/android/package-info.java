/**
 * Android платформенный пакет Death.
 *
 * <p><b>Stage 1: placeholder.</b> Реальная Android Activity, GLSurfaceView,
 * Vulkan ANativeWindow, integration с {@code :core} и {@code :rust-bridge}
 * (через cargo-ndk → libdeath_native.so в lib/arm64-v8a/) — INTEGRATION_MISSING,
 * см. PROGRESS.md.
 *
 * <p>Модуль НЕ включается в сборку по умолчанию (см. settings.gradle.kts).
 * Чтобы включить локально:
 * <pre>
 *   ANDROID_HOME=/path/to/sdk ./gradlew :android:assembleDebug
 *   # или
 *   ./gradlew -PenableAndroid=true :android:assembleDebug
 * </pre>
 *
 * @author Kuruma
 * @since 0.1.0
 */
package WeTTeA.platform.android;
