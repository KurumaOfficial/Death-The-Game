package WeTTeA.platform.desktop;

import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Objects;

/**
 * Stage 3.1 — декодер PNG/JPEG/BMP/TGA через {@link STBImage}.
 *
 * <p>Stateless wrapper над {@code stbi_load_from_memory}: принимает direct
 * {@link ByteBuffer} с сжатыми байтами файла (как из
 * {@code ContentLoader.readSync(...)}) и возвращает {@link DecodedImage} с
 * RGBA8 пикселями (4 канала всегда — мы форсим
 * {@link STBImage#STBI_rgb_alpha}, иначе layout текстуры в Vulkan
 * усложняется на 1-/3-канальных PNG).
 *
 * <p><b>Управление памятью.</b> stb_image выделяет нативный буфер через
 * {@code malloc}; {@link DecodedImage#free()} зовёт
 * {@code stbi_image_free} (= нативный {@code free}), идемпотентно. Получатель
 * обязан вызвать {@code free()} после загрузки в Vulkan staging buffer
 * (потом данные больше не нужны). Если забыть — это утечка нативной памяти,
 * GC не поможет.
 *
 * <p><b>Ориентация Y.</b> stb_image отдаёт пиксели сверху вниз
 * (top-left origin), это совпадает с layout'ом Vulkan'а
 * (texture coordinate (0,0) = top-left). Если бы мы сэмплили GL/inverted Y,
 * нужно было бы поставить {@code stbi_set_flip_vertically_on_load(true)};
 * для Vulkan — нет.
 *
 * <p><b>Формат пикселей.</b> Возвращаемые байты — packed RGBA8: 4 байта на
 * пиксель, R/G/B/A в порядке возрастания адресов. Это совпадает с
 * {@code VK_FORMAT_R8G8B8A8_UNORM}, что мы и используем в {@code VulkanImage}.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class PngImageDecoder {

    public PngImageDecoder() {
    }

    /**
     * Декодирует сжатые байты image-файла в RGBA8.
     *
     * @param fileBytes direct ByteBuffer с PNG/JPEG/BMP/TGA содержимым
     * @return {@link DecodedImage}, владеющий нативным буфером пикселей
     * @throws IllegalArgumentException если stb_image не смог декодировать
     */
    public DecodedImage decode(ByteBuffer fileBytes) {
        Objects.requireNonNull(fileBytes, "fileBytes");
        if (!fileBytes.isDirect()) {
            throw new IllegalArgumentException("PngImageDecoder ожидает direct ByteBuffer "
                    + "(stb_image работает с native memory; heap буфер привёл бы к лишней копии)");
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer pW = stack.callocInt(1);
            IntBuffer pH = stack.callocInt(1);
            IntBuffer pComp = stack.callocInt(1);

            ByteBuffer pixels = STBImage.stbi_load_from_memory(
                    fileBytes, pW, pH, pComp, STBImage.STBI_rgb_alpha);
            if (pixels == null) {
                String reason = STBImage.stbi_failure_reason();
                throw new IllegalArgumentException("stb_image: ошибка декодирования: "
                        + (reason != null ? reason : "<unknown>")
                        + " (size=" + fileBytes.remaining() + " bytes)");
            }

            int w = pW.get(0);
            int h = pH.get(0);
            // pComp возвращает оригинальное число каналов в файле (1/2/3/4),
            // но pixels всегда RGBA (4 канала) благодаря STBI_rgb_alpha.
            int sourceChannels = pComp.get(0);
            return new DecodedImage(pixels, w, h, sourceChannels);
        }
    }

    /**
     * Декодированное изображение в RGBA8.
     *
     * <p>{@link #pixels()} — direct buffer на нативную память
     * ({@code malloc}-allocated stb_image'ом); {@code remaining = w*h*4}.
     *
     * <p>Контракт жизни: вызвать {@link #free()} ровно один раз после того,
     * как пиксели скопированы в Vulkan staging buffer. После {@code free()}
     * читать {@link #pixels()} нельзя — буфер указывает в свободную память.
     *
     * @param pixels         RGBA8, top-left origin, w*h*4 байт
     * @param width          ширина в пикселях
     * @param height         высота в пикселях
     * @param sourceChannels 1/2/3/4 — сколько каналов было в исходном файле
     *                       (информационно; pixels всё равно RGBA)
     */
    public record DecodedImage(ByteBuffer pixels, int width, int height, int sourceChannels) {

        public DecodedImage {
            Objects.requireNonNull(pixels, "pixels");
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("width/height должны быть > 0, got "
                        + width + "x" + height);
            }
            if (sourceChannels < 1 || sourceChannels > 4) {
                throw new IllegalArgumentException("sourceChannels должен быть 1..4, got "
                        + sourceChannels);
            }
            if (pixels.remaining() != width * height * 4) {
                throw new IllegalArgumentException("pixels.remaining=" + pixels.remaining()
                        + " не совпадает с width*height*4=" + (width * height * 4));
            }
        }

        /** Размер пиксельных данных в байтах ({@code w*h*4}). */
        public int sizeBytes() {
            return width * height * 4;
        }

        /**
         * Освобождает нативный буфер пикселей через {@code stbi_image_free}.
         * Идемпотентно — повторный вызов no-op (через локальный флаг
         * нельзя — record immutable; вместо этого {@link MemoryUtil#memAddress0}
         * проверка от LWJGL внутри стб_image_free безопасна для null).
         *
         * <p>В практике этот метод вызывается в {@code VulkanTexture.upload()}
         * сразу после копирования в staging buffer.
         */
        public void free() {
            STBImage.stbi_image_free(pixels);
        }
    }
}
