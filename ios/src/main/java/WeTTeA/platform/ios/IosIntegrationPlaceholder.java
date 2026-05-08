package WeTTeA.platform.ios;

/**
 * Документационный плейсхолдер для iOS-интеграции.
 *
 * <p>Stage 2 чек-лист:
 * <ol>
 *   <li>Подключить RoboVM/MobiVM Gradle plugin;</li>
 *   <li>Создать {@code DeathIosLauncher} с {@code UIApplicationMain};</li>
 *   <li>Подключить {@code :core} и {@code :rust-bridge};</li>
 *   <li>Собрать {@code libdeath_native.a} (staticlib) для arm64;</li>
 *   <li>Настроить {@code robovm.xml} (frameworks: Metal, MetalKit, UIKit, Foundation);</li>
 *   <li>Настроить {@code Info.plist.xml} (CFBundleIdentifier, CFBundleVersion);</li>
 *   <li>Реализовать {@code WeTTeA.api.platform.PlatformInfo} factory с
 *       {@code UIDevice.currentDevice}, {@code [NSProcessInfo processInfo]};</li>
 *   <li>Реализовать {@code WeTTeA.api.platform.PlatformFileSystem} с
 *       NSDocumentDirectory / NSCachesDirectory;</li>
 *   <li>Реализовать render-контекст (Vulkan через MoltenVK, или нативный Metal).</li>
 * </ol>
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class IosIntegrationPlaceholder {

    public static final String FUTURE_LAUNCHER_FQN =
            "WeTTeA.platform.ios.DeathIosLauncher";

    private IosIntegrationPlaceholder() {
    }
}
