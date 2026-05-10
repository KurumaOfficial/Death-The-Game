package WeTTeA.platform.desktop;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkFormatProperties;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;

import java.nio.LongBuffer;

/**
 * Stage E.1 — depth attachment ({@code VkImage} + {@code VkImageView} +
 * {@code VkDeviceMemory}) под Vulkan-render-pass.
 *
 * <p>Логика:
 * <ol>
 *   <li>{@link #pickDepthFormat(VulkanDevice)} перебирает кандидаты
 *       {@code D32_SFLOAT → D32_SFLOAT_S8_UINT → D24_UNORM_S8_UINT} и
 *       выбирает первый с поддержкой {@code DEPTH_STENCIL_ATTACHMENT}
 *       в {@code OPTIMAL} tiling. На lavapipe и любом современном GPU
 *       {@code D32_SFLOAT} поддерживается всегда.</li>
 *   <li>{@code vkCreateImage} (TYPE_2D, формат, usage =
 *       DEPTH_STENCIL_ATTACHMENT, layout = UNDEFINED).</li>
 *   <li>{@code vkAllocateMemory} с DEVICE_LOCAL +
 *       {@code vkBindImageMemory}.</li>
 *   <li>{@code vkCreateImageView} с aspectMask =
 *       {@code DEPTH_BIT} (без stencil — для D32_SFLOAT'а).</li>
 * </ol>
 *
 * <p>Layout transition в render pass'е: первый
 * {@code subpass dependency} с {@code dstStageMask =
 * EARLY_FRAGMENT_TESTS} и {@code dstAccessMask =
 * DEPTH_STENCIL_ATTACHMENT_WRITE}; render pass сам переводит
 * UNDEFINED → DEPTH_STENCIL_ATTACHMENT_OPTIMAL при первом проходе.
 *
 * <p><b>Recreate-on-resize.</b> Тот же паттерн, что у
 * {@link VulkanFramebuffers}: caller (VulkanRenderer)
 * делает {@code vkDeviceWaitIdle}, вызывает {@link #recreate(int, int)},
 * и буфер пересобирается под новый extent. Формат остаётся прежним —
 * render pass переживает recreate.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class VulkanDepthBuffer {

    private final VulkanDevice device;
    private final int format;
    private int width;
    private int height;

    private long image  = VK10.VK_NULL_HANDLE;
    private long memory = VK10.VK_NULL_HANDLE;
    private long view   = VK10.VK_NULL_HANDLE;

    public VulkanDepthBuffer(VulkanDevice device, int width, int height) {
        this.device = device;
        this.format = pickDepthFormat(device);
        build(width, height);
    }

    /**
     * Stage E.1 — пересоздать depth-image под новый extent после
     * swapchain recreate. Caller гарантирует {@code vkDeviceWaitIdle}.
     */
    public void recreate(int width, int height) {
        disposeHandles();
        build(width, height);
    }

    private void build(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("VulkanDepthBuffer: w/h должны быть > 0, got "
                    + width + "x" + height);
        }
        this.width  = width;
        this.height = height;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageCreateInfo imgInfo = VkImageCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
                    .imageType(VK10.VK_IMAGE_TYPE_2D)
                    .format(format)
                    .mipLevels(1)
                    .arrayLayers(1)
                    .samples(VK10.VK_SAMPLE_COUNT_1_BIT)
                    .tiling(VK10.VK_IMAGE_TILING_OPTIMAL)
                    .usage(VK10.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT)
                    .sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE)
                    .initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED);
            imgInfo.extent().width(width).height(height).depth(1);

            LongBuffer pImage = stack.callocLong(1);
            VulkanDevice.check(VK10.vkCreateImage(device.logical(), imgInfo, null, pImage),
                    "vkCreateImage(depth)");
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
                    "vkAllocateMemory(depth)");
            memory = pMemory.get(0);

            VulkanDevice.check(VK10.vkBindImageMemory(device.logical(), image, memory, 0),
                    "vkBindImageMemory(depth)");

            VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                    .image(image)
                    .viewType(VK10.VK_IMAGE_VIEW_TYPE_2D)
                    .format(format);
            viewInfo.subresourceRange()
                    .aspectMask(VK10.VK_IMAGE_ASPECT_DEPTH_BIT)
                    .baseMipLevel(0).levelCount(1)
                    .baseArrayLayer(0).layerCount(1);

            LongBuffer pView = stack.callocLong(1);
            VulkanDevice.check(VK10.vkCreateImageView(device.logical(), viewInfo, null, pView),
                    "vkCreateImageView(depth)");
            view = pView.get(0);

            System.out.println("[Death:desktop] Vulkan depth buffer ready: "
                    + width + "x" + height + " format=" + format);
        }
    }

    /**
     * Перебирает кандидатов и возвращает первый, поддерживающий
     * {@code DEPTH_STENCIL_ATTACHMENT} в {@code OPTIMAL} tiling.
     *
     * @throws IllegalStateException если ни один формат не поддержан
     */
    public static int pickDepthFormat(VulkanDevice device) {
        int[] candidates = {
                VK10.VK_FORMAT_D32_SFLOAT,
                VK10.VK_FORMAT_D32_SFLOAT_S8_UINT,
                VK10.VK_FORMAT_D24_UNORM_S8_UINT
        };
        try (MemoryStack stack = MemoryStack.stackPush()) {
            for (int fmt : candidates) {
                VkFormatProperties props = VkFormatProperties.calloc(stack);
                VK10.vkGetPhysicalDeviceFormatProperties(device.physical(), fmt, props);
                if ((props.optimalTilingFeatures()
                        & VK10.VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT) != 0) {
                    return fmt;
                }
            }
        }
        throw new IllegalStateException(
                "Ни один из кандидатов depth-format'а не поддерживается DEPTH_STENCIL_ATTACHMENT в OPTIMAL tiling");
    }

    public long view()    { return view; }
    public long image()   { return image; }
    public int  format()  { return format; }
    public int  width()   { return width; }
    public int  height()  { return height; }

    /**
     * Идемпотентный dispose. Освобождает view → image → memory.
     */
    public void dispose() {
        disposeHandles();
    }

    private void disposeHandles() {
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
