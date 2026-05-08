package WeTTeA.platform.desktop;

import WeTTeA.api.platform.PlatformInfo;

/**
 * Фабрика {@link PlatformInfo} для desktop сборки.
 *
 * <p>Определяет осевые поля через {@link System#getProperty(String)}:
 * {@code os.name}, {@code os.version}, {@code os.arch}, {@code sun.arch.data.model}.
 *
 * <p>{@link PlatformInfo#family()} для desktop всегда {@link PlatformInfo.Family#DESKTOP}.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class DesktopPlatformInfo {

    private DesktopPlatformInfo() {
    }

    public static PlatformInfo detect() {
        String osName    = System.getProperty("os.name", "unknown");
        String osVersion = System.getProperty("os.version", "unknown");
        String archName  = System.getProperty("os.arch", "unknown");
        boolean has64    = "64".equals(System.getProperty("sun.arch.data.model"))
                || archName.contains("64");
        return new PlatformInfo(PlatformInfo.Family.DESKTOP, osName, osVersion, archName, has64);
    }
}
