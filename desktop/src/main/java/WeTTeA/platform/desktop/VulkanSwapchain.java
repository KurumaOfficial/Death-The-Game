package WeTTeA.platform.desktop;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSurface;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkComponentMapping;
import org.lwjgl.vulkan.VkExtent2D;
import org.lwjgl.vulkan.VkImageSubresourceRange;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;
import org.lwjgl.vulkan.VkSurfaceFormatKHR;
import org.lwjgl.vulkan.VkSwapchainCreateInfoKHR;

import java.nio.IntBuffer;
import java.nio.LongBuffer;

/**
 * Stage 2.1a — {@code VkSwapchainKHR} + image views.
 *
 * <p>Логика выбора параметров:
 * <ul>
 *   <li><b>Surface format.</b> Предпочитаем
 *       {@code VK_FORMAT_B8G8R8A8_UNORM + VK_COLOR_SPACE_SRGB_NONLINEAR_KHR};
 *       если не поддерживается — берём первый из доступных. UNORM (а не SRGB)
 *       выбран сознательно: clear-color на 2.1a задаётся напрямую в
 *       линейном пространстве, а конверсия в sRGB будет добавлена с pipeline'ом
 *       в 2.1b. Lavapipe анонсирует обоих, и UNORM проще для проверки цвета
 *       глазами.</li>
 *   <li><b>Present mode.</b> {@code VK_PRESENT_MODE_FIFO_KHR} — единственный
 *       режим, который Vulkan гарантированно поддерживает. На lavapipe
 *       MAILBOX/IMMEDIATE могут отсутствовать.</li>
 *   <li><b>Extent.</b> Если {@code currentExtent.width != UINT32_MAX} —
 *       используем его. Иначе спрашиваем GLFW размер framebuffer'а
 *       и клампим к {@code minImageExtent}/{@code maxImageExtent}.</li>
 *   <li><b>Image count.</b> {@code minImageCount + 1}, ограничено
 *       {@code maxImageCount} (если он > 0). 2 → triple-buffer-like (2 frames in
 *       flight + 1 acquired).</li>
 *   <li><b>Sharing mode.</b> Если graphics-family совпадает с present-family
 *       (типично на lavapipe / iGPU) — {@code EXCLUSIVE}. Иначе {@code CONCURRENT}
 *       с обоими family-индексами.</li>
 * </ul>
 *
 * <p>Каждому swapchain image создаётся {@link VkImageViewCreateInfo}
 * (тип {@code 2D}, color aspect, без mipmap/array). Эти views используются
 * как color attachment'ы во framebuffer'ах ({@link VulkanFramebuffers}).
 *
 * <p><b>Recreate-on-resize (2.1b).</b> {@link #recreate()} перезапускает
 * {@link #create()} поверх живого {@link VulkanDevice} и {@code VkSurfaceKHR}:
 * сначала уничтожает старые image views и {@code VkSwapchainKHR},
 * затем заново выбирает {@code surfaceFormat / presentMode / extent}
 * (extent читается из {@link GLFW#glfwGetFramebufferSize(long, int[], int[])},
 * поэтому новый размер окна автоматически подхватывается). Caller обязан
 * перед вызовом {@link #recreate()} сделать {@code vkDeviceWaitIdle}
 * и пересоздать {@link VulkanFramebuffers}, привязанные к старым views.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class VulkanSwapchain {

    private final VulkanDevice device;
    private final long surface;
    private final long windowHandle;

    private long swapchain = VK10.VK_NULL_HANDLE;
    private long[] images = new long[0];
    private long[] imageViews = new long[0];
    private int imageFormat;
    private int width;
    private int height;

    public VulkanSwapchain(VulkanDevice device, long surface, long windowHandle) {
        this.device       = device;
        this.surface      = surface;
        this.windowHandle = windowHandle;
        create();
    }

    /**
     * Stage 2.1b — пересоздать swapchain и его image views на месте.
     *
     * <p>Вызывается из {@link VulkanRenderer} после получения
     * {@code VK_ERROR_OUT_OF_DATE_KHR} / {@code VK_SUBOPTIMAL_KHR} или
     * dirty-flag'а от GLFW framebuffer-size callback'а. Caller гарантирует,
     * что перед вызовом был сделан {@code vkDeviceWaitIdle}, поэтому
     * безопасно уничтожать старые ресурсы без ожидания GPU.
     *
     * <p>{@link #imageFormat()} остаётся прежним (формат attachment'а
     * {@link VulkanRenderPass} не меняется), но {@link #width()} /
     * {@link #height()} обновляются. {@link #imageCount()} в общем случае
     * может измениться (драйвер вправе изменить minImageCount под новый
     * extent), поэтому caller должен ресайзнуть {@link VulkanFrameSync}'s
     * imagesInFlight.
     */
    public void recreate() {
        disposeImagesAndChain();
        create();
    }

    private void create() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSurfaceCapabilitiesKHR caps = VkSurfaceCapabilitiesKHR.calloc(stack);
            VulkanDevice.check(KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR(
                    device.physical(), surface, caps), "vkGetPhysicalDeviceSurfaceCapabilitiesKHR");

            VkSurfaceFormatKHR.Buffer formats = querySurfaceFormats(stack);
            VkSurfaceFormatKHR chosenFormat = pickSurfaceFormat(formats);

            int presentMode = pickPresentMode(stack);

            VkExtent2D extent = pickExtent(caps, stack);

            int desiredImageCount = caps.minImageCount() + 1;
            if (caps.maxImageCount() > 0 && desiredImageCount > caps.maxImageCount()) {
                desiredImageCount = caps.maxImageCount();
            }

            VkSwapchainCreateInfoKHR info = VkSwapchainCreateInfoKHR.calloc(stack)
                    .sType(KHRSwapchain.VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR)
                    .surface(surface)
                    .minImageCount(desiredImageCount)
                    .imageFormat(chosenFormat.format())
                    .imageColorSpace(chosenFormat.colorSpace())
                    .imageExtent(extent)
                    .imageArrayLayers(1)
                    .imageUsage(VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
                    .preTransform(caps.currentTransform())
                    .compositeAlpha(KHRSurface.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
                    .presentMode(presentMode)
                    .clipped(true)
                    .oldSwapchain(VK10.VK_NULL_HANDLE);

            int gfx = device.graphicsFamily();
            int prs = device.presentFamily();
            if (gfx == prs) {
                info.imageSharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);
            } else {
                IntBuffer queueIndices = stack.ints(gfx, prs);
                info.imageSharingMode(VK10.VK_SHARING_MODE_CONCURRENT)
                    .pQueueFamilyIndices(queueIndices);
            }

            LongBuffer pSwap = stack.callocLong(1);
            VulkanDevice.check(KHRSwapchain.vkCreateSwapchainKHR(device.logical(), info, null, pSwap),
                    "vkCreateSwapchainKHR");
            swapchain   = pSwap.get(0);
            imageFormat = chosenFormat.format();
            width       = extent.width();
            height      = extent.height();

            // Получаем images
            IntBuffer pCount = stack.callocInt(1);
            KHRSwapchain.vkGetSwapchainImagesKHR(device.logical(), swapchain, pCount, null);
            int n = pCount.get(0);
            LongBuffer pImages = stack.callocLong(n);
            KHRSwapchain.vkGetSwapchainImagesKHR(device.logical(), swapchain, pCount, pImages);
            images = new long[n];
            imageViews = new long[n];
            for (int i = 0; i < n; i++) {
                images[i] = pImages.get(i);
                imageViews[i] = createImageView(images[i], imageFormat, stack);
            }

            System.out.println("[Death:desktop] Vulkan swapchain created: "
                    + width + "x" + height
                    + " format=" + imageFormat
                    + " presentMode=" + presentMode
                    + " images=" + n);
        }
    }

    private VkSurfaceFormatKHR.Buffer querySurfaceFormats(MemoryStack stack) {
        IntBuffer pCount = stack.callocInt(1);
        KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(device.physical(), surface, pCount, null);
        int n = pCount.get(0);
        if (n == 0) throw new IllegalStateException("surface не отдаёт ни одного VkSurfaceFormatKHR");
        VkSurfaceFormatKHR.Buffer buf = VkSurfaceFormatKHR.calloc(n, stack);
        KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(device.physical(), surface, pCount, buf);
        return buf;
    }

    private VkSurfaceFormatKHR pickSurfaceFormat(VkSurfaceFormatKHR.Buffer formats) {
        for (int i = 0; i < formats.capacity(); i++) {
            VkSurfaceFormatKHR f = formats.get(i);
            if (f.format() == VK10.VK_FORMAT_B8G8R8A8_UNORM
                    && f.colorSpace() == KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                return f;
            }
        }
        return formats.get(0);
    }

    private int pickPresentMode(MemoryStack stack) {
        // FIFO гарантирован Vulkan-спекой (KHR_surface §32.10) — используем без
        // enumerate, это проще и стабильнее на lavapipe. На 2.1b добавим выбор
        // MAILBOX/IMMEDIATE при наличии (для VSync-toggle в Settings).
        return KHRSurface.VK_PRESENT_MODE_FIFO_KHR;
    }

    private VkExtent2D pickExtent(VkSurfaceCapabilitiesKHR caps, MemoryStack stack) {
        VkExtent2D current = caps.currentExtent();
        if (current.width() != 0xFFFFFFFF) {
            return VkExtent2D.calloc(stack).set(current);
        }
        int[] w = new int[1];
        int[] h = new int[1];
        GLFW.glfwGetFramebufferSize(windowHandle, w, h);
        int width  = clamp(w[0], caps.minImageExtent().width(),  caps.maxImageExtent().width());
        int height = clamp(h[0], caps.minImageExtent().height(), caps.maxImageExtent().height());
        return VkExtent2D.calloc(stack).width(width).height(height);
    }

    private long createImageView(long image, int format, MemoryStack stack) {
        VkImageViewCreateInfo info = VkImageViewCreateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                .image(image)
                .viewType(VK10.VK_IMAGE_VIEW_TYPE_2D)
                .format(format);

        VkComponentMapping comps = info.components();
        comps.r(VK10.VK_COMPONENT_SWIZZLE_IDENTITY)
             .g(VK10.VK_COMPONENT_SWIZZLE_IDENTITY)
             .b(VK10.VK_COMPONENT_SWIZZLE_IDENTITY)
             .a(VK10.VK_COMPONENT_SWIZZLE_IDENTITY);

        VkImageSubresourceRange sub = info.subresourceRange();
        sub.aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
           .baseMipLevel(0).levelCount(1)
           .baseArrayLayer(0).layerCount(1);

        LongBuffer pView = stack.callocLong(1);
        VulkanDevice.check(VK10.vkCreateImageView(device.logical(), info, null, pView),
                "vkCreateImageView");
        return pView.get(0);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public long   handle()        { return swapchain; }
    public int    imageCount()    { return images.length; }
    public long[] images()        { return images; }
    public long[] imageViews()    { return imageViews; }
    public int    imageFormat()   { return imageFormat; }
    public int    width()         { return width; }
    public int    height()        { return height; }

    public void dispose() {
        disposeImagesAndChain();
    }

    private void disposeImagesAndChain() {
        if (device.logical() == null) return;
        for (long view : imageViews) {
            if (view != VK10.VK_NULL_HANDLE) {
                VK10.vkDestroyImageView(device.logical(), view, null);
            }
        }
        imageViews = new long[0];
        images = new long[0];
        if (swapchain != VK10.VK_NULL_HANDLE) {
            KHRSwapchain.vkDestroySwapchainKHR(device.logical(), swapchain, null);
            swapchain = VK10.VK_NULL_HANDLE;
        }
    }
}
