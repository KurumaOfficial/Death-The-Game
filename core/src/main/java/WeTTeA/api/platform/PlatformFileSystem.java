package WeTTeA.api.platform;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Платформенный доступ к assets и user data.
 *
 * <p>Различия по платформам:
 * <ul>
 *   <li><b>Desktop</b>: assets лежат либо рядом с jar (dev), либо внутри jar
 *       (release); user data — в {@code $XDG_DATA_HOME/Death/} (Linux),
 *       {@code %APPDATA%/Death/} (Windows), {@code ~/Library/Application Support/Death/}
 *       (macOS).</li>
 *   <li><b>Android</b>: assets — внутри APK через {@code AssetManager}; user data —
 *       internal storage активити.</li>
 *   <li><b>iOS</b>: assets — внутри bundle; user data — {@code Documents/}.</li>
 * </ul>
 *
 * @author Kuruma
 * @since 0.1.0
 */
public interface PlatformFileSystem {

    /**
     * Открыть asset для чтения. Путь — относительный от корня
     * {@code assets/death/} (например, {@code "data/quality/medium.json"}).
     *
     * @return поток или {@link Optional#empty()}, если файл не найден
     */
    Optional<InputStream> openAsset(String relativePath);

    /**
     * Корневая директория user data — куда писать сохранения, логи, конфиг.
     */
    Path userDataDirectory();

    /** Корневая директория для логов. По умолчанию — {@code userDataDirectory()/logs}. */
    Path logDirectory();
}
