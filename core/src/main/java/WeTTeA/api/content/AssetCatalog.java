package WeTTeA.api.content;

import java.util.Optional;

/**
 * Каталог: id asset'а → реальный путь в {@code assets/death/}.
 *
 * <p>Реализуется в core ({@code WeTTeA.content.JsonAssetCatalog}) и
 * читает дескриптор из {@code assets/death/data/asset_catalog.json}.
 * Платформа решает, как добраться до файла, через
 * {@link WeTTeA.api.platform.PlatformFileSystem}.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public interface AssetCatalog {

    /**
     * Получить относительный путь файла внутри {@code assets/death/}.
     *
     * @param handle дескриптор asset'а
     * @return путь от корня {@code assets/death/}, либо {@link Optional#empty()},
     *         если id не найден.
     */
    Optional<String> resolvePath(AssetHandle handle);

    /** Все известные id в указанной категории. */
    Iterable<String> listIds(AssetCategory category);
}
