package WeTTeA.core;

/**
 * Базовые константы проекта Death.
 *
 * <p>В этом классе фиксируются единственные источники истины для:
 * <ul>
 *   <li>user-facing названия игры — <b>Death</b>;</li>
 *   <li>внутреннего code-namespace — <b>WeTTeA</b>;</li>
 *   <li>версии проекта — синхронизируется с {@code gradle.properties}.</li>
 * </ul>
 *
 * <p><b>Брендинг.</b> Запрещено использовать строку {@link #INTERNAL_NAMESPACE}
 * в любом player-facing слое: окно, меню, локализация, splash, about.
 * Для всех таких мест использовать {@link #GAME_DISPLAY_NAME}.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class CoreInfo {

    /** User-facing название игры. Используется в окнах, меню, splash и т.д. */
    public static final String GAME_DISPLAY_NAME = "Death";

    /**
     * Внутренний code-namespace.
     * Совпадает с верхним сегментом всех Java пакетов проекта.
     * Запрещено светить в player-facing слое.
     */
    public static final String INTERNAL_NAMESPACE = "WeTTeA";

    /**
     * Версия проекта в формате semver, синхронизируется с {@code gradle.properties → deathVersion}.
     * Загружается из манифеста JAR в runtime через {@link Package#getImplementationVersion()}.
     * Если манифест недоступен (запуск из IDE, тесты), используется значение по умолчанию.
     */
    public static final String VERSION = readVersionOrDefault("0.1.0-SNAPSHOT");

    private CoreInfo() {
        throw new AssertionError("CoreInfo — utility class, инстанцировать запрещено.");
    }

    private static String readVersionOrDefault(String fallback) {
        Package pkg = CoreInfo.class.getPackage();
        if (pkg == null) {
            return fallback;
        }
        String impl = pkg.getImplementationVersion();
        return impl == null || impl.isBlank() ? fallback : impl;
    }
}
