package WeTTeA.platform.android;

/**
 * Документационный плейсхолдер для Android-интеграции.
 *
 * <p><b>Этот класс намеренно НЕ ссылается на Android SDK и НЕ является
 * Activity.</b> Он существует как описание контракта, который будет
 * реализован на стадии 2 в виде {@code DeathActivity extends NativeActivity}
 * или {@code AppCompatActivity} с Vulkan/GLES surface.
 *
 * <p>Stage 2 чек-лист:
 * <ol>
 *   <li>Подключить {@code com.android.application} в build.gradle.kts;</li>
 *   <li>Создать {@code DeathActivity} в этом пакете;</li>
 *   <li>Подключить {@code :core} и {@code :rust-bridge};</li>
 *   <li>Cargo-ndk hook: сборка {@code libdeath_native.so} для arm64-v8a;</li>
 *   <li>{@code AndroidManifest.xml}: разрешения, intent-filter MAIN/LAUNCHER;</li>
 *   <li>Реализовать {@code WeTTeA.api.platform.PlatformInfo} factory с
 *       {@code Build.MANUFACTURER}, {@code Build.SUPPORTED_ABIS};</li>
 *   <li>Реализовать {@code WeTTeA.api.platform.PlatformFileSystem} с
 *       {@code Context.getFilesDir()}, {@code Context.getExternalFilesDir()};</li>
 *   <li>Реализовать render-контекст (Vulkan через {@code ANativeWindow}).</li>
 * </ol>
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class AndroidIntegrationPlaceholder {

    /**
     * Имя будущей Activity класса. Используется только в документации;
     * сама Activity появится на стадии 2.
     */
    public static final String FUTURE_ACTIVITY_FQN =
            "WeTTeA.platform.android.DeathActivity";

    private AndroidIntegrationPlaceholder() {
    }
}
