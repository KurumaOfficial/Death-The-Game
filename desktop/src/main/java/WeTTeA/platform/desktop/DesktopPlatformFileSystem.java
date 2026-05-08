package WeTTeA.platform.desktop;

import WeTTeA.api.platform.PlatformFileSystem;
import WeTTeA.api.platform.PlatformInfo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;

/**
 * Реализация {@link PlatformFileSystem} для desktop.
 *
 * <p>Стратегия определения {@code userDataDirectory()}:
 * <ul>
 *   <li>Linux: {@code $XDG_DATA_HOME/Death} или {@code ~/.local/share/Death};</li>
 *   <li>Windows: {@code %APPDATA%/Death};</li>
 *   <li>macOS: {@code ~/Library/Application Support/Death}.</li>
 * </ul>
 *
 * <p>{@link #logDirectory()} = {@code userDataDirectory()/logs}.
 *
 * <p>{@link #openAsset(String)} ищет файл в classpath по корневому
 * префиксу {@code /assets/death/<relativePath>}. Это позволяет на стадии
 * разработки класть assets в {@code core/src/main/resources/assets/death/},
 * а на release — упаковывать в jar.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class DesktopPlatformFileSystem implements PlatformFileSystem {

    private static final String ASSETS_CLASSPATH_ROOT = "/assets/death/";

    private final Path userDataDirectory;
    private final Path logDirectory;

    public DesktopPlatformFileSystem(PlatformInfo info) {
        Objects.requireNonNull(info, "info");
        this.userDataDirectory = resolveUserDataDir(info.osName());
        this.logDirectory      = userDataDirectory.resolve("logs");
    }

    private static Path resolveUserDataDir(String osName) {
        String home = System.getProperty("user.home", ".");
        String os = osName == null ? "" : osName.toLowerCase();
        if (os.contains("win")) {
            String appdata = System.getenv("APPDATA");
            return Paths.get(appdata != null ? appdata : home, "Death");
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return Paths.get(home, "Library", "Application Support", "Death");
        }
        if (os.contains("linux")) {
            String xdg = System.getenv("XDG_DATA_HOME");
            return Paths.get(xdg != null ? xdg : home + "/.local/share", "Death");
        }
        return Paths.get(home, ".death");
    }

    @Override
    public Optional<InputStream> openAsset(String relativePath) {
        Objects.requireNonNull(relativePath, "relativePath");
        String normalized = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
        InputStream in = DesktopPlatformFileSystem.class.getResourceAsStream(
                ASSETS_CLASSPATH_ROOT + normalized);
        return Optional.ofNullable(in);
    }

    @Override
    public Path userDataDirectory() {
        ensure(userDataDirectory);
        return userDataDirectory;
    }

    @Override
    public Path logDirectory() {
        ensure(logDirectory);
        return logDirectory;
    }

    private static void ensure(Path p) {
        try {
            Files.createDirectories(p);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create directory: " + p, e);
        }
    }
}
