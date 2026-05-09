package WeTTeA.core.content;

import WeTTeA.api.content.AssetCatalog;
import WeTTeA.api.content.AssetCategory;
import WeTTeA.api.content.AssetHandle;
import WeTTeA.api.platform.PlatformFileSystem;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Stage 3.1 — реализация {@link AssetCatalog}, читающая JSON-описание из
 * {@code assets/death/data/asset_catalog.json}.
 *
 * <p>Формат файла:
 * <pre>{@code
 * {
 *   "schema_version": 1,
 *   "assets": [
 *     { "id": "audio.test.sine_440_short", "category": "AUDIO",
 *       "path": "audio/test/sine_440_short.ogg" },
 *     { "id": "shader.triangle.vert", "category": "SHADER_SPIRV",
 *       "path": "shaders/triangle/triangle.vert.spv" },
 *     ...
 *   ]
 * }
 * }</pre>
 *
 * <p>Парсинг — Jackson ({@link ObjectMapper}) на чистых {@link JsonNode}'ах,
 * без annotated DTO: каталог достаточно прост, чтобы не тащить ещё один
 * слой. Поля {@code comment} в JSON разрешены и игнорируются (для
 * человекочитаемости источника правды).
 *
 * <p><b>Семантика и ошибки.</b>
 * <ul>
 *   <li>Файл каталога обязан существовать и читаться через
 *       {@link PlatformFileSystem#openAsset(String)} (по умолчанию путь
 *       {@code data/asset_catalog.json}).</li>
 *   <li>{@code schema_version} обязан быть {@code 1}; иначе fail fast
 *       (форматы старших схем будут добавляться отдельным upgrade path'ом).</li>
 *   <li>Дубликаты {@code id} → {@link IllegalStateException} (как у
 *       {@link MapAssetCatalog}).</li>
 *   <li>Неизвестное значение {@code category} → {@link IllegalStateException}
 *       со ссылкой на актуальный enum {@link AssetCategory} — это
 *       сигнализирует расхождение между каталогом и кодом, что хуже
 *       молчаливого пропуска.</li>
 *   <li>Пустой {@code id}/{@code path} — {@link IllegalStateException}
 *       с указанием индекса записи.</li>
 * </ul>
 *
 * <p><b>Совместимость с {@link MapAssetCatalog}.</b> Игровой/render код,
 * работающий через {@link AssetCatalog}, переключение на JSON не замечает —
 * контракт идентичен. На stage 3.1 launcher переходит с
 * {@code MapAssetCatalog.builder()...build()} на
 * {@code JsonAssetCatalog.load(fs)}; in-memory builder остаётся доступен
 * как лёгкий fixture для unit-тестов и смок-кода без файловой зависимости.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class JsonAssetCatalog implements AssetCatalog {

    /** Путь от корня assets/death/, где лежит каталог. */
    public static final String DEFAULT_RELATIVE_PATH = "data/asset_catalog.json";

    /** Поддерживаемая версия схемы. */
    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Map<String, Entry> byId;
    private final EnumMap<AssetCategory, List<String>> byCategory;
    private final String sourcePath;

    private JsonAssetCatalog(Map<String, Entry> byId,
                             EnumMap<AssetCategory, List<String>> byCategory,
                             String sourcePath) {
        this.byId = byId;
        this.byCategory = byCategory;
        this.sourcePath = sourcePath;
    }

    /**
     * Загружает каталог из {@link #DEFAULT_RELATIVE_PATH}.
     *
     * @throws UncheckedIOException если файл не найден / не парсится
     * @throws IllegalStateException на нарушении схемы / семантики
     */
    public static JsonAssetCatalog load(PlatformFileSystem fs) {
        return load(fs, DEFAULT_RELATIVE_PATH);
    }

    /**
     * Загружает каталог из произвольного relative path внутри
     * {@code assets/death/}. Удобно для тестов с альтернативным fixture'ом.
     */
    public static JsonAssetCatalog load(PlatformFileSystem fs, String relativePath) {
        Objects.requireNonNull(fs, "fs");
        Objects.requireNonNull(relativePath, "relativePath");

        Optional<InputStream> in = fs.openAsset(relativePath);
        if (in.isEmpty()) {
            throw new UncheckedIOException(new IOException(
                    "JsonAssetCatalog: файл не найден на платформе: " + relativePath));
        }
        try (InputStream stream = in.get()) {
            JsonNode root = JSON.readTree(stream);
            return parse(root, relativePath);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JsonAssetCatalog: ошибка JSON-парсинга " + relativePath
                    + ": " + e.getOriginalMessage(), e);
        } catch (IOException e) {
            throw new UncheckedIOException("JsonAssetCatalog: ошибка чтения " + relativePath, e);
        }
    }

    /**
     * Парсинг уже прочитанного JSON-дерева. Удобно для unit-тестов: можно
     * передать {@code ObjectMapper.readTree("{...}")} без файловой системы.
     */
    public static JsonAssetCatalog parse(JsonNode root, String sourcePath) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(sourcePath, "sourcePath");

        if (!root.isObject()) {
            throw new IllegalStateException("JsonAssetCatalog: корень должен быть JSON object, "
                    + "но это " + root.getNodeType() + " (" + sourcePath + ")");
        }
        JsonNode versionNode = root.get("schema_version");
        if (versionNode == null || !versionNode.canConvertToInt()) {
            throw new IllegalStateException("JsonAssetCatalog: missing schema_version "
                    + "(или не int), требуется " + SUPPORTED_SCHEMA_VERSION + " (" + sourcePath + ")");
        }
        int version = versionNode.intValue();
        if (version != SUPPORTED_SCHEMA_VERSION) {
            throw new IllegalStateException("JsonAssetCatalog: schema_version=" + version
                    + ", но поддерживается только " + SUPPORTED_SCHEMA_VERSION
                    + " (" + sourcePath + ")");
        }

        JsonNode assetsNode = root.get("assets");
        if (assetsNode == null || !assetsNode.isArray()) {
            throw new IllegalStateException("JsonAssetCatalog: missing 'assets' array (" + sourcePath + ")");
        }

        Map<String, Entry> byId = new HashMap<>();
        EnumMap<AssetCategory, List<String>> byCategory = new EnumMap<>(AssetCategory.class);

        for (int i = 0; i < assetsNode.size(); i++) {
            JsonNode node = assetsNode.get(i);
            if (!node.isObject()) {
                throw new IllegalStateException("JsonAssetCatalog: запись #" + i
                        + " должна быть object (" + sourcePath + ")");
            }
            String id       = stringOrThrow(node, "id", i, sourcePath);
            String category = stringOrThrow(node, "category", i, sourcePath);
            String path     = stringOrThrow(node, "path", i, sourcePath);

            AssetCategory cat;
            try {
                cat = AssetCategory.valueOf(category);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("JsonAssetCatalog: неизвестная category=\"" + category
                        + "\" в записи #" + i + " (id=" + id + "); ожидаемые значения: "
                        + java.util.Arrays.toString(AssetCategory.values())
                        + " (" + sourcePath + ")", e);
            }

            if (byId.containsKey(id)) {
                throw new IllegalStateException("JsonAssetCatalog: дубликат asset id=\"" + id
                        + "\" в записи #" + i + " (" + sourcePath + ")");
            }
            byId.put(id, new Entry(cat, path));
            byCategory.computeIfAbsent(cat, k -> new ArrayList<>()).add(id);
        }

        EnumMap<AssetCategory, List<String>> snapshot = new EnumMap<>(AssetCategory.class);
        for (Map.Entry<AssetCategory, List<String>> e : byCategory.entrySet()) {
            snapshot.put(e.getKey(), Collections.unmodifiableList(new ArrayList<>(e.getValue())));
        }
        return new JsonAssetCatalog(
                Collections.unmodifiableMap(new HashMap<>(byId)),
                snapshot,
                sourcePath);
    }

    private static String stringOrThrow(JsonNode obj, String field, int idx, String sourcePath) {
        JsonNode v = obj.get(field);
        if (v == null || !v.isTextual()) {
            throw new IllegalStateException("JsonAssetCatalog: запись #" + idx
                    + " missing/non-string '" + field + "' (" + sourcePath + ")");
        }
        String s = v.textValue();
        if (s.isBlank()) {
            throw new IllegalStateException("JsonAssetCatalog: запись #" + idx
                    + " пустое поле '" + field + "' (" + sourcePath + ")");
        }
        return s;
    }

    @Override
    public Optional<String> resolvePath(AssetHandle handle) {
        Objects.requireNonNull(handle, "handle");
        Entry e = byId.get(handle.id());
        if (e == null) return Optional.empty();
        if (e.category != handle.category()) {
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

    /** Точное число зарегистрированных asset'ов. */
    public int size() {
        return byId.size();
    }

    /** Откуда был прочитан каталог (для логов). */
    public String sourcePath() {
        return sourcePath;
    }

    private record Entry(AssetCategory category, String path) {
    }
}
