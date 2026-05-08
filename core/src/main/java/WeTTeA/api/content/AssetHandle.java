package WeTTeA.api.content;

import java.util.Objects;

/**
 * Дескриптор одного asset'а.
 *
 * <p>{@link #id()} — стабильный идентификатор, по которому игровая логика
 * ссылается на asset (например, {@code "music.boss.theme"} или
 * {@code "shader.post.tone_map.frag"}). Реальный путь файла резолвит
 * {@link AssetCatalog}.
 *
 * @param id       стабильный id (не путь!), без расширения
 * @param category категория asset'а
 *
 * @author Kuruma
 * @since 0.1.0
 */
public record AssetHandle(String id, AssetCategory category) {

    public AssetHandle {
        Objects.requireNonNull(id, "asset id");
        Objects.requireNonNull(category, "asset category");
        if (id.isBlank()) {
            throw new IllegalArgumentException("asset id must not be blank");
        }
    }
}
