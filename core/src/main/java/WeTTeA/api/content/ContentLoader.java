package WeTTeA.api.content;

import WeTTeA.api.lifecycle.Disposable;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

/**
 * Загрузчик контента.
 *
 * <p>Возвращает байты asset'а (все остальные парсеры — поверх
 * {@link ContentLoader}, по категории). Реализация поддерживает
 * фоновую загрузку без блокировки render-thread.
 *
 * <p><b>Direct buffer.</b> Возвращаемый {@link ByteBuffer} — direct,
 * чтобы можно было передавать в Vulkan/OpenAL/Rust JNI без копий.
 * Buffer нельзя освобождать вручную; владение остаётся у loader'а
 * до его {@link #dispose()}.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public interface ContentLoader extends Disposable {

    /**
     * Прочитать asset синхронно. Подходит для маленьких файлов
     * (config, JSON), не для текстур/моделей в bullet hell.
     *
     * @throws java.io.UncheckedIOException если файл не найден / ошибка чтения
     */
    ByteBuffer readSync(AssetHandle handle);

    /** Прочитать asset асинхронно — для крупных ресурсов и фоновой загрузки. */
    CompletableFuture<ByteBuffer> readAsync(AssetHandle handle);

    /** Существует ли asset в каталоге и доступен ли файл. */
    boolean exists(AssetHandle handle);
}
