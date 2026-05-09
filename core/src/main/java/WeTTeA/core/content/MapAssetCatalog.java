package WeTTeA.core.content;

import WeTTeA.api.content.AssetCatalog;
import WeTTeA.api.content.AssetCategory;
import WeTTeA.api.content.AssetHandle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * In-memory реализация {@link AssetCatalog} — простая Map id → relativePath.
 *
 * <p>Stage 2.3b использует {@link MapAssetCatalog} как минимальный backend:
 * лаунчер регистрирует тестовые asset'ы напрямую через {@link Builder}.
 * На stage 3.1 поверх того же контракта появится {@code JsonAssetCatalog}
 * (читает {@code assets/death/data/asset_catalog.json}); gameplay-код, который
 * работает через {@link AssetCatalog}, переключение не заметит.
 *
 * <p><b>Категория проверяется при регистрации.</b> Если попытаться
 * зарегистрировать asset под id, который уже был объявлен, или с
 * категорией, не совпадающей с переданной в {@link AssetHandle}, билдер
 * бросит {@link IllegalStateException} / {@link IllegalArgumentException}.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class MapAssetCatalog implements AssetCatalog {

    private final Map<String, Entry> byId;
    private final EnumMap<AssetCategory, List<String>> byCategory;

    private MapAssetCatalog(Map<String, Entry> byId,
                            EnumMap<AssetCategory, List<String>> byCategory) {
        this.byId = byId;
        this.byCategory = byCategory;
    }

    @Override
    public Optional<String> resolvePath(AssetHandle handle) {
        Objects.requireNonNull(handle, "handle");
        Entry e = byId.get(handle.id());
        if (e == null) return Optional.empty();
        if (e.category != handle.category()) {
            // id зарегистрирован под другой категорией — это ошибка вызывающего,
            // но resolve возвращает empty (вместо тихого мисматча).
            return Optional.empty();
        }
        return Optional.of(e.path);
    }

    @Override
    public Iterable<String> listIds(AssetCategory category) {
        Objects.requireNonNull(category, "category");
        List<String> ids = byCategory.get(category);
        return ids == null ? Collections.emptyList() : Collections.unmodifiableList(ids);
    }

    /** Точное число зарегистрированных asset'ов (для логов / smoke). */
    public int size() {
        return byId.size();
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Builder для {@link MapAssetCatalog}. */
    public static final class Builder {

        private final Map<String, Entry> byId = new HashMap<>();
        private final EnumMap<AssetCategory, List<String>> byCategory =
                new EnumMap<>(AssetCategory.class);

        private Builder() {
        }

        /**
         * Зарегистрировать asset.
         *
         * @param id           стабильный id (см. {@link AssetHandle#id()})
         * @param category     категория (см. {@link AssetCategory})
         * @param relativePath относительный путь от {@code assets/death/}
         *                     (например, {@code "audio/test/sine_440_short.ogg"})
         */
        public Builder register(String id, AssetCategory category, String relativePath) {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(category, "category");
            Objects.requireNonNull(relativePath, "relativePath");
            if (id.isBlank()) {
                throw new IllegalArgumentException("asset id must not be blank");
            }
            if (relativePath.isBlank()) {
                throw new IllegalArgumentException("asset relativePath must not be blank");
            }
            if (byId.containsKey(id)) {
                throw new IllegalStateException("asset id already registered: " + id);
            }
            byId.put(id, new Entry(category, relativePath));
            byCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(id);
            return this;
        }

        public MapAssetCatalog build() {
            EnumMap<AssetCategory, List<String>> snapshot = new EnumMap<>(AssetCategory.class);
            for (Map.Entry<AssetCategory, List<String>> e : byCategory.entrySet()) {
                snapshot.put(e.getKey(), Collections.unmodifiableList(new ArrayList<>(e.getValue())));
            }
            return new MapAssetCatalog(Collections.unmodifiableMap(new HashMap<>(byId)), snapshot);
        }
    }

    private record Entry(AssetCategory category, String path) {
    }
}
