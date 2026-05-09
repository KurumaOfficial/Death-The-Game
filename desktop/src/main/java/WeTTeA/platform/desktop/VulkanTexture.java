package WeTTeA.platform.desktop;

import WeTTeA.api.content.AssetHandle;
import WeTTeA.api.content.ContentLoader;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.Objects;

/**
 * Stage 3.1 — orchestrator текстуры: PNG asset → {@link VulkanImage} +
 * {@link VulkanSampler} + descriptor set, готовый к bind'у в pipeline.
 *
 * <p>Pipeline загрузки (один раз в {@link #fromAsset}):
 * <ol>
 *   <li>{@link ContentLoader#readSync} — direct {@link ByteBuffer} с PNG.</li>
 *   <li>{@link PngImageDecoder#decode} — RGBA8 пиксели в нативной памяти.</li>
 *   <li>{@link VulkanBuffer} HOST_VISIBLE | HOST_COHERENT — staging.</li>
 *   <li>{@code staging.write(pixels)} — копия CPU→staging.</li>
 *   <li>{@link DecodedImage#free()} — освобождение нативного буфера stb_image
 *       (после копии в staging пиксели больше не нужны на CPU).</li>
 *   <li>{@link VulkanImage} в DEVICE_LOCAL памяти + image view.</li>
 *   <li>{@link VulkanImage#uploadFromStaging} — barrier + buffer→image
 *       copy + barrier (UNDEFINED → TRANSFER_DST → SHADER_READ_ONLY).</li>
 *   <li>Staging buffer dispose.</li>
 *   <li>{@code vkAllocateDescriptorSets} из pool'а
 *       {@link VulkanTextureDescriptors}.</li>
 *   <li>{@code vkUpdateDescriptorSets} — связывает binding=0 с
 *       (sampler, image view, SHADER_READ_ONLY_OPTIMAL).</li>
 * </ol>
 *
 * <p>После {@code fromAsset} текстура живёт всё время до {@link #dispose()}
 * и пользователь только биндит её descriptor set'ом перед draw'ом:
 * {@code vkCmdBindDescriptorSets(cmd, GRAPHICS, pipelineLayout, 0,
 * texture.descriptorSet(), null)}.
 *
 * <p><b>Идемпотентный dispose</b>: image → sampler. Descriptor set НЕ
 * освобождается явно — pool разрушается централизованно через
 * {@link VulkanTextureDescriptors#dispose()}.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class VulkanTexture {

    private final VulkanDevice device;
    private final String       debugName;
    private VulkanImage   image;
    private VulkanSampler sampler;
    private long descriptorSet = VK10.VK_NULL_HANDLE;

    private VulkanTexture(VulkanDevice device, String debugName,
                          VulkanImage image, VulkanSampler sampler, long descriptorSet) {
        this.device        = device;
        this.debugName     = debugName;
        this.image         = image;
        this.sampler       = sampler;
        this.descriptorSet = descriptorSet;
    }

    /**
     * Загружает текстуру из asset'а через {@link ContentLoader} +
     * {@link PngImageDecoder}, заливает в DEVICE_LOCAL {@link VulkanImage},
     * аллоцирует и заполняет descriptor set из общего пула.
     *
     * @param device          Vulkan device
     * @param cmdBuffers      command pool, из которого временно
     *                        аллоцируется one-shot upload-cmd-buffer
     * @param descriptors     общий layout + pool под combined image samplers
     * @param decoder         stb_image обёртка
     * @param loader          ContentLoader из asset pipeline'а
     * @param handle          asset id (категория должна быть
     *                        {@link WeTTeA.api.content.AssetCategory#TEXTURE})
     * @param debugName       имя для логов
     */
    public static VulkanTexture fromAsset(VulkanDevice device,
                                          VulkanCommandBuffers cmdBuffers,
                                          VulkanTextureDescriptors descriptors,
                                          PngImageDecoder decoder,
                                          ContentLoader loader,
                                          AssetHandle handle,
                                          String debugName) {
        Objects.requireNonNull(device, "device");
        Objects.requireNonNull(cmdBuffers, "cmdBuffers");
        Objects.requireNonNull(descriptors, "descriptors");
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
            // Пиксели уже в staging buffer'е — освобождаем нативный буфер stb_image.
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

        // 6) Аллоцируем descriptor set из shared пула.
        long descriptorSet;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetAllocateInfo allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                    .descriptorPool(descriptors.descriptorPool())
                    .pSetLayouts(stack.longs(descriptors.descriptorSetLayout()));
            LongBuffer pSet = stack.callocLong(1);
            VulkanDevice.check(VK10.vkAllocateDescriptorSets(
                            device.logical(), allocInfo, pSet),
                    "vkAllocateDescriptorSets(" + debugName + ")");
            descriptorSet = pSet.get(0);

            // 7) Заполняем descriptor: binding=0 = (sampler, view, SHADER_READ_ONLY).
            VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.calloc(1, stack);
            imageInfo.get(0)
                    .sampler(sampler.handle())
                    .imageView(image.view())
                    .imageLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

            // descriptorCount ОБЯЗАТЕЛЬНО задавать явно: LWJGL не выводит его из
            // pImageInfo.remaining(); calloc дефолтит в 0, и без явного =1 запись
            // descriptor'а становится no-op'ом — descriptor set остаётся пустым,
            // драйвер при семплинге попадает в NULL pointer (на lavapipe — SIGSEGV
            // в JIT'нутом fragment shader'е, на NV/AMD — undefined behavior).
            VkWriteDescriptorSet.Buffer write = VkWriteDescriptorSet.calloc(1, stack);
            write.get(0)
                    .sType(VK10.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(descriptorSet)
                    .dstBinding(VulkanTextureDescriptors.BINDING_TEXTURE)
                    .dstArrayElement(0)
                    .descriptorCount(1)
                    .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .pImageInfo(imageInfo);
            VK10.vkUpdateDescriptorSets(device.logical(), write, null);
        }

        System.out.println("[Death:desktop] Vulkan texture loaded: " + debugName
                + " (asset=" + handle.id() + ", " + w + "x" + h
                + ", format=R8G8B8A8_UNORM, sourceChannels=" + decoded.sourceChannels()
                + ", size=" + sizeBytes + " bytes)");
        return new VulkanTexture(device, debugName, image, sampler, descriptorSet);
    }

    public long          descriptorSet() { return descriptorSet; }
    public VulkanImage   image()         { return image;         }
    public VulkanSampler sampler()       { return sampler;       }
    public String        debugName()     { return debugName;     }

    public void dispose() {
        // descriptorSet — освобождается вместе с pool'ом в VulkanTextureDescriptors.
        descriptorSet = VK10.VK_NULL_HANDLE;
        if (sampler != null) { sampler.dispose(); sampler = null; }
        if (image   != null) { image.dispose();   image   = null; }
    }
}
