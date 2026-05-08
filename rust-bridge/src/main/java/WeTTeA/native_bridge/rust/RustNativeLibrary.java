package WeTTeA.native_bridge.rust;

import WeTTeA.api.nativebridge.NativeBridgeInfo;
import WeTTeA.api.nativebridge.NativeLibraryLoader;
import WeTTeA.api.platform.PlatformFileSystem;
import WeTTeA.api.platform.PlatformInfo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * Реализация {@link NativeLibraryLoader} — загрузчик нативной Rust библиотеки
 * {@code death-native}.
 *
 * <p>Стратегия загрузки (по убыванию приоритета):
 * <ol>
 *   <li>Если установлено системное свойство {@code -Ddeath.native.path=/abs/path}
 *       — используется как готовый путь к {@code libdeath_native.{so,dll,dylib}};
 *       загрузчик вызывает {@link System#load(String)} напрямую. Полезно для
 *       локальной отладки нативки без перепаковки jar.</li>
 *   <li>Иначе — извлечение из classpath по пути
 *       {@code /native/<osTag>/<archTag>/<libname>}. Артефакт распаковывается во
 *       временный файл в {@link PlatformFileSystem#userDataDirectory()} и
 *       загружается через {@link System#load(String)}. Этот режим используется
 *       при обычном {@code :desktop:run} (нативка кладётся туда таском
 *       {@code copyNativeArtifact} из {@code :rust-bridge/build.gradle.kts}).</li>
 *   <li>Fallback — {@link System#loadLibrary(String)} (требует наличия
 *       {@code java.library.path}). Полезно если пользователь сам положил
 *       библиотеку рядом с jar.</li>
 * </ol>
 *
 * <p>Соответствие имён:
 * <ul>
 *   <li>Linux:   {@code linux/<arch>/libdeath_native.so}</li>
 *   <li>Windows: {@code windows/<arch>/death_native.dll}</li>
 *   <li>macOS:   {@code macos/<arch>/libdeath_native.dylib}</li>
 *   <li>Android: {@code System.loadLibrary} (apk packaging кладёт {@code .so}
 *       в {@code lib/<abi>/}; classpath-extract не нужен)</li>
 *   <li>iOS:     статически слинкована — extract не выполняется,
 *       {@link #loadIfNeeded} предполагает что символы уже в процессе.</li>
 * </ul>
 *
 * <p>{@link NativeBridgeInfo} на стадии 2.4 заполняется host-метаданными:
 * {@code artifactName} = реальное имя файла, {@code semverVersion} = "0.0.0"
 * (плейсхолдер до подключения JNI экспорта {@code nativeBridgeVersion}),
 * {@code featuresEnabled} = пустой массив.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class RustNativeLibrary implements NativeLibraryLoader {

    /** Базовое имя крейта: {@code death_native} (без префикса {@code lib} и расширения). */
    public static final String LIBRARY_NAME = "death_native";

    /** Системное свойство для override-пути. */
    public static final String OVERRIDE_PROPERTY = "death.native.path";

    private boolean loaded;
    private NativeBridgeInfo info;

    @Override
    public NativeBridgeInfo loadIfNeeded(PlatformInfo platform, PlatformFileSystem fileSystem) {
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(fileSystem, "fileSystem");
        if (loaded) return info;

        String fileName = resolveExpectedFileName(platform);

        String override = System.getProperty(OVERRIDE_PROPERTY);
        if (override != null && !override.isEmpty()) {
            System.load(override);
            info = new NativeBridgeInfo(fileName, "0.0.0", new String[0]);
            loaded = true;
            return info;
        }

        PlatformInfo.Family family = platform.family();

        // iOS: статическая линковка — символы уже в процессе.
        if (family == PlatformInfo.Family.IOS) {
            info = new NativeBridgeInfo(fileName, "0.0.0", new String[0]);
            loaded = true;
            return info;
        }

        // Android: native lib пакуется в apk (lib/<abi>/) и грузится по короткому имени.
        if (family == PlatformInfo.Family.ANDROID) {
            System.loadLibrary(LIBRARY_NAME);
            info = new NativeBridgeInfo(fileName, "0.0.0", new String[0]);
            loaded = true;
            return info;
        }

        // Desktop: classpath extract → System.load.
        String resourcePath = "/native/" + platformTag(platform) + "/" + archTag(platform) + "/" + fileName;
        Path extracted = extractToTemp(resourcePath, fileName, fileSystem);
        if (extracted != null) {
            System.load(extracted.toAbsolutePath().toString());
        } else {
            // Fallback: java.library.path (например пользователь положил вручную).
            System.loadLibrary(LIBRARY_NAME);
        }

        info = new NativeBridgeInfo(fileName, "0.0.0", new String[0]);
        loaded = true;
        return info;
    }

    @Override
    public boolean isLoaded() {
        return loaded;
    }

    /**
     * Разрешает имя файла нативки для текущей платформы.
     *
     * <p>В отличие от {@code System.mapLibraryName}, мы фиксируем имя явно — JNI
     * symbol mangling в Rust crate'е завязан именно на {@code death_native}.
     * Внутри семейства DESKTOP различаем windows/macos/linux по {@code osName}.
     */
    static String resolveExpectedFileName(PlatformInfo platform) {
        String os = platform.osName() == null ? "" : platform.osName().toLowerCase();
        if (os.contains("win")) return LIBRARY_NAME + ".dll";
        if (os.contains("mac") || os.contains("darwin")) return "lib" + LIBRARY_NAME + ".dylib";
        return "lib" + LIBRARY_NAME + ".so";
    }

    /** Тэг ОС в путях classpath ({@code linux} / {@code windows} / {@code macos}). */
    static String platformTag(PlatformInfo platform) {
        String os = platform.osName() == null ? "" : platform.osName().toLowerCase();
        if (os.contains("win")) return "windows";
        if (os.contains("mac") || os.contains("darwin")) return "macos";
        return "linux";
    }

    /** Тэг архитектуры в путях classpath ({@code x86_64} / {@code aarch64} / fallback). */
    static String archTag(PlatformInfo platform) {
        String arch = platform.archName() == null ? "" : platform.archName().toLowerCase();
        if (arch.contains("aarch64") || arch.contains("arm64")) return "aarch64";
        if (arch.contains("64")) return "x86_64";
        return arch.isEmpty() ? "unknown" : arch;
    }

    /**
     * Извлекает ресурс classpath во временный файл в
     * {@link PlatformFileSystem#userDataDirectory()}{@code /native/}.
     * Возвращает {@code null} если ресурс отсутствует.
     */
    private static Path extractToTemp(String resourcePath, String fileName, PlatformFileSystem fileSystem) {
        try (InputStream in = RustNativeLibrary.class.getResourceAsStream(resourcePath)) {
            if (in == null) return null;
            Path dir = fileSystem.userDataDirectory().resolve("native");
            Files.createDirectories(dir);
            Path target = dir.resolve(fileName);
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось извлечь нативную библиотеку " + resourcePath, e);
        }
    }
}
