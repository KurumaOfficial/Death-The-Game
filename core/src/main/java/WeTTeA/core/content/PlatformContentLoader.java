package WeTTeA.core.content;

import WeTTeA.api.content.AssetCatalog;
import WeTTeA.api.content.AssetHandle;
import WeTTeA.api.content.ContentLoader;
import WeTTeA.api.platform.PlatformFileSystem;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

/**
 * Реализация {@link ContentLoader} поверх {@link AssetCatalog} +
 * {@link PlatformFileSystem}.
 *
 * <p>Алгоритм {@link #readSync(AssetHandle)}:
 * <ol>
 *   <li>{@link AssetCatalog#resolvePath(AssetHandle)} → относительный путь
 *       внутри {@code assets/death/};</li>
 *   <li>{@link PlatformFileSystem#openAsset(String)} → {@link InputStream};</li>
 *   <li>read all bytes → выделяем direct {@link ByteBuffer} через
 *       {@code ByteBuffer.allocateDirect(...)}, копируем содержимое.</li>
 * </ol>
 *
 * <p><b>Direct buffer.</b> Возвращаемый {@link ByteBuffer} — direct (как
 * требует {@link ContentLoader} contract): можно скармливать в
 * {@code stb_vorbis_decode_memory}, в Vulkan staging buffer, в Rust JNI без
 * лишней копии heap→native. Buffer владеет данными до тех пор, пока
 * вызывающий не позволит ему быть собранным GC.
 *
 * <p><b>Async.</b> {@link #readAsync(AssetHandle)} = {@link CompletableFuture#supplyAsync(java.util.function.Supplier)}
 * на {@link ForkJoinPool#commonPool()} — простой запуск ИО в фоновом
 * потоке. На stage 3.x пул пользовательский с приоритетами; для smoke
 * stage 2.3b common pool достаточен.
 *
 * <p><b>{@link #dispose()}.</b> Loader не владеет durable resources (каждый
 * прочитанный буфер уходит вызывающему); метод — no-op для совместимости
 * с {@link Disposable}.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class PlatformContentLoader implements ContentLoader {

    private final AssetCatalog catalog;
    private final PlatformFileSystem fs;
    private boolean disposed;

    public PlatformContentLoader(AssetCatalog catalog, PlatformFileSystem fs) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.fs      = Objects.requireNonNull(fs, "fs");
    }

    @Override
    public ByteBuffer readSync(AssetHandle handle) {
        Objects.requireNonNull(handle, "handle");
        ensureOpen();

        Optional<String> pathOpt = catalog.resolvePath(handle);
        if (pathOpt.isEmpty()) {
            throw new UncheckedIOException(new IOException(
                    "Asset id не найден в каталоге: " + handle.id()
                  + " (category=" + handle.category() + ")"));
        }
        String path = pathOpt.get();

        Optional<InputStream> in = fs.openAsset(path);
        if (in.isEmpty()) {
            throw new UncheckedIOException(new IOException(
                    "Asset файл не найден на платформе: " + path
                  + " (id=" + handle.id() + ", category=" + handle.category() + ")"));
        }

        try (InputStream stream = in.get()) {
            byte[] bytes = stream.readAllBytes();
            ByteBuffer buf = ByteBuffer.allocateDirect(bytes.length);
            buf.put(bytes);
            buf.flip();
            return buf;
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Ошибка чтения asset'а: " + path + " (id=" + handle.id() + ")", e);
        }
    }

    @Override
    public CompletableFuture<ByteBuffer> readAsync(AssetHandle handle) {
        Objects.requireNonNull(handle, "handle");
        return CompletableFuture.supplyAsync(() -> readSync(handle));
    }

    @Override
    public boolean exists(AssetHandle handle) {
        Objects.requireNonNull(handle, "handle");
        if (disposed) return false;
        Optional<String> pathOpt = catalog.resolvePath(handle);
        if (pathOpt.isEmpty()) return false;
        Optional<InputStream> in = fs.openAsset(pathOpt.get());
        if (in.isEmpty()) return false;
        try {
            in.get().close();
        } catch (IOException ignored) {
            // не критично — мы только проверяли существование
        }
        return true;
    }

    @Override
    public void dispose() {
        disposed = true;
    }

    private void ensureOpen() {
        if (disposed) {
            throw new IllegalStateException("PlatformContentLoader уже dispose'нут");
        }
    }
}
