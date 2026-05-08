package WeTTeA.api.platform;

/**
 * Сводная информация о текущей платформе.
 *
 * @param family    семейство ({@code DESKTOP}, {@code ANDROID}, {@code IOS})
 * @param osName    человекочитаемое имя ОС
 * @param osVersion версия ОС
 * @param archName  имя архитектуры процессора
 * @param has64Bit  процесс — 64-bit
 *
 * @author Kuruma
 * @since 0.1.0
 */
public record PlatformInfo(
        Family family,
        String osName,
        String osVersion,
        String archName,
        boolean has64Bit
) {
    /** Семейство платформы. */
    public enum Family {
        DESKTOP,
        ANDROID,
        IOS
    }
}
