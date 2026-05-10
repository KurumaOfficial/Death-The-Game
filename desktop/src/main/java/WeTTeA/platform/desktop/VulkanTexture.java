package WeTTeA.platform.desktop;

import WeTTeA.api.content.AssetHandle;
import WeTTeA.api.content.ContentLoader;
import org.lwjgl.vulkan.VK10;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Stage 3.1 + E.1 — orchestrator текстуры: PNG asset →
 * {@link VulkanImage} + {@link VulkanSampler}.
 *
 * <p>На stage 3.1 этот класс ещё аллоцировал собственный descriptor set
 * (один binding под combined image sampler). На stage E.1 layout
 * расширился до 2-binding'ового scene-set'а
 * (см. {@link VulkanSceneDescriptors}); теперь VulkanTexture владеет
 * только image + sampler, а descriptor set'ы создаются и заполняются
 * рендерером per-frame через {@link VulkanSceneDescriptors#writeFrameSet}.
 *
 * <p>Pipeline загрузки (один раз в {@link #fromAsset}):
 * <ol>
 *   <li>{@link ContentLoader#readSync} — direct {@link ByteBuffer} с PNG.</li>
 *   <li>{@link PngImageDecoder#decode} — RGBA8 пиксели в нативной памяти.</li>
 *   <li>{@link VulkanBuffer} HOST_VISIBLE | HOST_COHERENT — staging.</li>
 *   <li>{@code staging.write(pixels)} — копия CPU→staging.</li>
 *   <li>{@link PngImageDecoder.DecodedImage#free()} — освобождение
 *       нативного буфера stb_image.</li>
 *   <li>{@link VulkanImage} в DEVICE_LOCAL памяти + image view.</li>
 *   <li>{@link VulkanImage#uploadFromStaging} — barrier + buffer→image
 *       copy + barrier (UNDEFINED → TRANSFER_DST → SHADER_READ_ONLY).</li>
 *   <li>Staging buffer dispose.</li>
 *   <li>{@link VulkanSampler} с linear-filter / repeat-address.</li>
 * </ol>
 *
 * <p>После {@code fromAsset} рендерер запрашивает {@link #imageView()} и
 * {@link #samplerHandle()}, передаёт их в
 * {@link VulkanSceneDescriptors#writeFrameSet} вместе со своим UBO,
 * и при draw'е биндит итоговый scene set.
 *
 * <p>Идемпотентный {@link #dispose()}: sampler → image.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class VulkanTexture {

    private final String       debugName;
    private VulkanImage   image;
    private VulkanSampler sampler;

    private VulkanTexture(String debugName, VulkanImage image, VulkanSampler sampler) {
        this.debugName = debugName;
        this.image     = image;
        this.sampler   = sampler;
    }

    /**
     * Загружает текстуру из asset'а через {@link ContentLoader} +
     * {@link PngImageDecoder}, заливает в DEVICE_LOCAL {@link VulkanImage},
     * создаёт {@link VulkanSampler}.
     *
     * @param device          Vulkan device
     * @param cmdBuffers      command pool, из которого временно
     *                        аллоцируется one-shot upload-cmd-buffer
     * @param decoder         stb_image обёртка
     * @param loader          ContentLoader из asset pipeline'а
     * @param handle          asset id (категория должна быть
     *                        {@link WeTTeA.api.content.AssetCategory#TEXTURE})
     * @param debugName       имя для логов
     */
    public static VulkanTexture fromAsset(VulkanDevice device,
                                          VulkanCommandBuffers cmdBuffers,
                                          PngImageDecoder decoder,
                                          ContentLoader loader,
                                          AssetHandle handle,
                                          String debugName) {
        Objects.requireNonNull(device, "device");
        Objects.requireNonNull(cmdBuffers, "cmdBuffers");
        Objects.requireNonNull(decoder, "decoder");
        Objects.requireNonNull(loader, "loader");
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(debugName, "debugName");

        // 1) Декодируем PNG → RGBA8.
        ByteBuffer fileBytes = loader.readSync(handle);
        PngImageDecoder.DecodedImage decoded = decoder.decode(fileBytes);
        int w = decoded.width();
        int h = decoded.height();
        int sizeBytes = decoded.sizeBytes();

        // 2) Staging buffer (HOST_VISIBLE | HOST_COHERENT, TRANSFER_SRC).
        VulkanBuffer staging = new VulkanBuffer(device,
                sizeBytes,
                VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT
                        | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        try {
            staging.write(decoded.pixels());
        } finally {
            decoded.free();
        }

        // 3) DEVICE_LOCAL VkImage + image view.
        VulkanImage image = new VulkanImage(device, w, h, VK10.VK_FORMAT_R8G8B8A8_UNORM);

        // 4) staging → image (с layout transitions).
        try {
            image.uploadFromStaging(cmdBuffers, staging);
        } finally {
            staging.dispose();
        }

        // 5) Sampler.
        VulkanSampler sampler = new VulkanSampler(device);

        System.out.println("[Death:desktop] Vulkan texture loaded: " + debugName
                + " (asset=" + handle.id() + ", " + w + "x" + h
                + ", format=R8G8B8A8_UNORM, sourceChannels=" + decoded.sourceChannels()
                + ", size=" + sizeBytes + " bytes)");
        return new VulkanTexture(debugName, image, sampler);
    }

    public long          imageView()     { return image.view();    }
    public long          samplerHandle() { return sampler.handle(); }
    public VulkanImage   image()         { return image;            }
    public VulkanSampler sampler()       { return sampler;          }
    public String        debugName()     { return debugName;        }

    public void dispose() {
        if (sampler != null) { sampler.dispose(); sampler = null; }
        if (image   != null) { image.dispose();   image   = null; }
    }
}
