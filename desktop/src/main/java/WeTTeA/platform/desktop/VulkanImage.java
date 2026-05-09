package WeTTeA.platform.desktop;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkSubmitInfo;

import java.nio.LongBuffer;

/**
 * Stage 3.1 — обёртка над {@code VkImage} + {@code VkImageView} +
 * {@code VkDeviceMemory} для 2D-текстур в DEVICE_LOCAL памяти.
 *
 * <p>Создание идёт по классическому паттерну Vulkan'а:
 * <ol>
 *   <li>{@code vkCreateImage} (tiling=OPTIMAL, usage=TRANSFER_DST | SAMPLED,
 *       layout=UNDEFINED, samples=1, mipLevels=1, arrayLayers=1).</li>
 *   <li>{@code vkGetImageMemoryRequirements} → memory requirements.</li>
 *   <li>{@code vkAllocateMemory} с DEVICE_LOCAL флагом + {@code vkBindImageMemory}.</li>
 *   <li>{@code vkCreateImageView} (тот же формат, aspectMask=COLOR_BIT,
 *       2D, single layer, single mip).</li>
 * </ol>
 *
 * <p>Загрузка пикселей из staging buffer через
 * {@link #uploadFromStaging(VulkanCommandBuffers, VulkanBuffer)}:
 * <ol>
 *   <li>One-shot command buffer (alloc + begin oneTimeSubmit).</li>
 *   <li>Barrier UNDEFINED → TRANSFER_DST_OPTIMAL (без зависимостей с pre-stage).</li>
 *   <li>{@code vkCmdCopyBufferToImage} весь image как один region.</li>
 *   <li>Barrier TRANSFER_DST_OPTIMAL → SHADER_READ_ONLY_OPTIMAL
 *       (для последующего sample'а во fragment shader'е).</li>
 *   <li>End + submit + waitIdle (на 3.1 это допустимо: текстуры грузятся
 *       1 раз при init'е, не в hot path; на 3.x перейдём на async upload
 *       через timeline semaphore).</li>
 *   <li>Free командного буфера.</li>
 * </ol>
 *
 * <p><b>Идемпотентный {@link #dispose()}.</b> Освобождает в обратном
 * порядке: view → image → memory. Безопасен при повторном вызове и при
 * {@code device.logical() == null}.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class VulkanImage {

    private final VulkanDevice device;
    private final int width;
    private final int height;
    private final int format;
    private long image  = VK10.VK_NULL_HANDLE;
    private long memory = VK10.VK_NULL_HANDLE;
    private long view   = VK10.VK_NULL_HANDLE;

    /**
     * Создаёт {@code VkImage} 2D + {@code VkImageView}, layout = UNDEFINED.
     * Пиксели грузятся отдельным вызовом {@link #uploadFromStaging}.
     *
     * @param device Vulkan device
     * @param width  ширина в пикселях
     * @param height высота в пикселях
     * @param format {@code VK_FORMAT_*} (на 3.1: {@code R8G8B8A8_UNORM})
     */
    public VulkanImage(VulkanDevice device, int width, int height, int format) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("VulkanImage: w/h должны быть > 0, got "
                    + width + "x" + height);
        }
        this.device = device;
        this.width  = width;
        this.height = height;
        this.format = format;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageCreateInfo imgInfo = VkImageCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
                    .imageType(VK10.VK_IMAGE_TYPE_2D)
                    .format(format)
                    .mipLevels(1)
                    .arrayLayers(1)
                    .samples(VK10.VK_SAMPLE_COUNT_1_BIT)
                    .tiling(VK10.VK_IMAGE_TILING_OPTIMAL)
                    .usage(VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT
                            | VK10.VK_IMAGE_USAGE_SAMPLED_BIT)
                    .sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE)
                    .initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED);
            imgInfo.extent().width(width).height(height).depth(1);

            LongBuffer pImage = stack.callocLong(1);
            VulkanDevice.check(VK10.vkCreateImage(device.logical(), imgInfo, null, pImage),
                    "vkCreateImage");
            image = pImage.get(0);

            VkMemoryRequirements memReq = VkMemoryRequirements.calloc(stack);
            VK10.vkGetImageMemoryRequirements(device.logical(), image, memReq);
            int memTypeIndex = VulkanBuffer.pickMemoryType(device,
                    memReq.memoryTypeBits(),
                    VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);

            VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                    .allocationSize(memReq.size())
                    .memoryTypeIndex(memTypeIndex);
            LongBuffer pMemory = stack.callocLong(1);
            VulkanDevice.check(VK10.vkAllocateMemory(device.logical(), allocInfo, null, pMemory),
                    "vkAllocateMemory(image)");
            memory = pMemory.get(0);

            VulkanDevice.check(VK10.vkBindImageMemory(device.logical(), image, memory, 0),
                    "vkBindImageMemory");

            VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                    .image(image)
                    .viewType(VK10.VK_IMAGE_VIEW_TYPE_2D)
                    .format(format);
            viewInfo.subresourceRange()
                    .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(1)
                    .baseArrayLayer(0)
                    .layerCount(1);

            LongBuffer pView = stack.callocLong(1);
            VulkanDevice.check(VK10.vkCreateImageView(device.logical(), viewInfo, null, pView),
                    "vkCreateImageView");
            view = pView.get(0);
        }
    }

    /**
     * Копирует содержимое staging buffer'а в этот image и переводит layout
     * в {@code SHADER_READ_ONLY_OPTIMAL}.
     *
     * <p>Командный буфер аллоцируется одноразово из переданного пула,
     * сабмитится и ждётся {@code vkQueueWaitIdle}'ом — на 3.1 текстуры
     * грузятся при init'е и фриз GPU на эти миллисекунды некритичен.
     */
    public void uploadFromStaging(VulkanCommandBuffers cmdBuffers, VulkanBuffer staging) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                    .commandPool(cmdBuffers.pool())
                    .level(VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(1);
            org.lwjgl.PointerBuffer pCmd = stack.callocPointer(1);
            VulkanDevice.check(VK10.vkAllocateCommandBuffers(device.logical(), allocInfo, pCmd),
                    "vkAllocateCommandBuffers(staging upload)");
            VkCommandBuffer cmd = new VkCommandBuffer(pCmd.get(0), device.logical());

            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                    .flags(VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
            VulkanDevice.check(VK10.vkBeginCommandBuffer(cmd, beginInfo),
                    "vkBeginCommandBuffer(staging upload)");

            // Barrier #1: UNDEFINED → TRANSFER_DST_OPTIMAL.
            VkImageMemoryBarrier.Buffer toTransfer = VkImageMemoryBarrier.calloc(1, stack);
            toTransfer.get(0)
                    .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                    .oldLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED)
                    .newLayout(VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                    .srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                    .image(image)
                    .srcAccessMask(0)
                    .dstAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT);
            toTransfer.get(0).subresourceRange()
                    .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0).levelCount(1)
                    .baseArrayLayer(0).layerCount(1);
            VK10.vkCmdPipelineBarrier(cmd,
                    VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                    VK10.VK_PIPELINE_STAGE_TRANSFER_BIT,
                    0, null, null, toTransfer);

            // Copy buffer → image.
            VkBufferImageCopy.Buffer region = VkBufferImageCopy.calloc(1, stack);
            region.get(0)
                    .bufferOffset(0)
                    .bufferRowLength(0)
                    .bufferImageHeight(0);
            region.get(0).imageSubresource()
                    .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                    .mipLevel(0)
                    .baseArrayLayer(0)
                    .layerCount(1);
            region.get(0).imageOffset().set(0, 0, 0);
            region.get(0).imageExtent().width(width).height(height).depth(1);
            VK10.vkCmdCopyBufferToImage(cmd, staging.handle(), image,
                    VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region);

            // Barrier #2: TRANSFER_DST_OPTIMAL → SHADER_READ_ONLY_OPTIMAL.
            VkImageMemoryBarrier.Buffer toShader = VkImageMemoryBarrier.calloc(1, stack);
            toShader.get(0)
                    .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                    .oldLayout(VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                    .newLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                    .image(image)
                    .srcAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK10.VK_ACCESS_SHADER_READ_BIT);
            toShader.get(0).subresourceRange()
                    .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0).levelCount(1)
                    .baseArrayLayer(0).layerCount(1);
            VK10.vkCmdPipelineBarrier(cmd,
                    VK10.VK_PIPELINE_STAGE_TRANSFER_BIT,
                    VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                    0, null, null, toShader);

            VulkanDevice.check(VK10.vkEndCommandBuffer(cmd),
                    "vkEndCommandBuffer(staging upload)");

            VkSubmitInfo submit = VkSubmitInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_SUBMIT_INFO)
                    .pCommandBuffers(stack.pointers(cmd));
            VulkanDevice.check(VK10.vkQueueSubmit(device.graphicsQueue(), submit, VK10.VK_NULL_HANDLE),
                    "vkQueueSubmit(staging upload)");
            VulkanDevice.check(VK10.vkQueueWaitIdle(device.graphicsQueue()),
                    "vkQueueWaitIdle(staging upload)");

            VK10.vkFreeCommandBuffers(device.logical(), cmdBuffers.pool(), pCmd);
        }
    }

    public long handle() { return image; }
    public long view()   { return view; }
    public long memory() { return memory; }
    public int  width()  { return width; }
    public int  height() { return height; }
    public int  format() { return format; }

    public void dispose() {
        if (device.logical() == null) {
            view   = VK10.VK_NULL_HANDLE;
            image  = VK10.VK_NULL_HANDLE;
            memory = VK10.VK_NULL_HANDLE;
            return;
        }
        if (view != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyImageView(device.logical(), view, null);
            view = VK10.VK_NULL_HANDLE;
        }
        if (image != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyImage(device.logical(), image, null);
            image = VK10.VK_NULL_HANDLE;
        }
        if (memory != VK10.VK_NULL_HANDLE) {
            VK10.vkFreeMemory(device.logical(), memory, null);
            memory = VK10.VK_NULL_HANDLE;
        }
    }
}
