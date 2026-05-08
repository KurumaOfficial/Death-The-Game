package WeTTeA.api.nativebridge;

/**
 * Метаданные о загруженной нативной библиотеке Rust ядра.
 *
 * @param libraryFileName  имя файла, который реально был загружен (с расширением)
 * @param semverVersion    semver версия Rust crate (читается через JNI после init)
 * @param featuresEnabled  включённые feature-flags Rust crate'а (compiled in)
 *
 * @author Kuruma
 * @since 0.1.0
 */
public record NativeBridgeInfo(
        String libraryFileName,
        String semverVersion,
        String[] featuresEnabled
) {
    public NativeBridgeInfo {
        if (libraryFileName == null || libraryFileName.isBlank()) {
            throw new IllegalArgumentException("libraryFileName must not be blank");
        }
        if (semverVersion == null || semverVersion.isBlank()) {
            throw new IllegalArgumentException("semverVersion must not be blank");
        }
        if (featuresEnabled == null) {
            throw new IllegalArgumentException("featuresEnabled must not be null");
        }
    }
}
