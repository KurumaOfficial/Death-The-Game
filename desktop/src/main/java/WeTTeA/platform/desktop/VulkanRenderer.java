package WeTTeA.platform.desktop;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkExtent2D;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkOffset2D;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;
import org.lwjgl.vulkan.VkSubmitInfo;

import java.nio.IntBuffer;
import java.nio.LongBuffer;

/**
 * Stage 2.1a — оркестратор Vulkan render-loop.
 *
 * <p>Владеет всеми Vulkan ресурсами рендера: {@link VulkanSurface},
 * {@link VulkanDevice}, {@link VulkanSwapchain}, {@link VulkanRenderPass},
 * {@link VulkanFramebuffers}, {@link VulkanCommandBuffers},
 * {@link VulkanFrameSync}. Создаются в конструкторе, освобождаются в
 * {@link #dispose()} в обратном порядке.
 *
 * <p>{@link #renderFrame(double)} — единственный «горячий» метод,
 * вызываемый каждый кадр из main loop:
 * <ol>
 *   <li>Ждём fence слота {@code currentFrame} (CPU не убегает вперёд GPU
 *       больше чем на {@code framesInFlight} кадров).</li>
 *   <li>{@code vkAcquireNextImageKHR} с semaphore {@code imageAvailable}.</li>
 *   <li>Если этот image уже использует другой frame slot — ждём его fence.</li>
 *   <li>Ресет fence слота, ресет cmd buffer'а, запись:
 *       {@code beginRenderPass} (clear color вычисляется из {@code timeSeconds}) →
 *       (на 2.1a больше ничего, на 2.1b будет {@code vkCmdDraw} триангла) →
 *       {@code endRenderPass}.</li>
 *   <li>{@code vkQueueSubmit} с wait на {@code imageAvailable}, signal
 *       {@code renderFinished} + fence {@code inFlight}.</li>
 *   <li>{@code vkQueuePresentKHR} с wait на {@code renderFinished}.</li>
 *   <li>{@code currentFrame = (currentFrame + 1) % framesInFlight}.</li>
 * </ol>
 *
 * <p>Clear color циклирует HSV-волной по {@code timeSeconds} — даёт
 * визуально читаемое доказательство, что render-loop реально дошёл до
 * GPU и презентация сработала.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class VulkanRenderer {

    /** Количество кадров «в полёте» одновременно — типовое значение для double-buffering CPU↔GPU. */
    public static final int MAX_FRAMES_IN_FLIGHT = 2;

    private final VulkanSurface       surface;
    private final VulkanDevice        device;
    private final VulkanSwapchain     swapchain;
    private final VulkanRenderPass    renderPass;
    private final VulkanFramebuffers  framebuffers;
    private final VulkanCommandBuffers commandBuffers;
    private final VulkanFrameSync     sync;

    private int currentFrame = 0;
    private int presentedFrames = 0;

    public VulkanRenderer(VkInstance instance, long windowHandle) {
        this.surface        = new VulkanSurface(instance, windowHandle);
        this.device         = new VulkanDevice(instance, surface.handle());
        this.device.pick();
        this.device.createLogical();
        this.swapchain      = new VulkanSwapchain(device, surface.handle(), windowHandle);
        this.renderPass     = new VulkanRenderPass(device, swapchain.imageFormat());
        this.framebuffers   = new VulkanFramebuffers(device, swapchain, renderPass);
        this.commandBuffers = new VulkanCommandBuffers(device, MAX_FRAMES_IN_FLIGHT);
        this.sync           = new VulkanFrameSync(device, MAX_FRAMES_IN_FLIGHT, swapchain.imageCount());
    }

    /**
     * Презентует один кадр с цикл-цветом, зависящим от {@code timeSeconds}.
     * Возвращает {@code false}, если презентация сообщила
     * {@code OUT_OF_DATE_KHR}/{@code SUBOPTIMAL_KHR} — на 2.1a это
     * означает «окно ресайзнули или закрыли», caller должен прекратить цикл.
     */
    public boolean renderFrame(double timeSeconds) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long inFlightFence = sync.inFlight(currentFrame);
            long imageAvail    = sync.imageAvailable(currentFrame);
            long renderDone    = sync.renderFinished(currentFrame);

            VK10.vkWaitForFences(device.logical(), inFlightFence, true, Long.MAX_VALUE);

            IntBuffer pImageIndex = stack.callocInt(1);
            int acquireResult = KHRSwapchain.vkAcquireNextImageKHR(
                    device.logical(),
                    swapchain.handle(),
                    Long.MAX_VALUE,
                    imageAvail,
                    VK10.VK_NULL_HANDLE,
                    pImageIndex);
            if (acquireResult == KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR) {
                System.out.println("[Death:desktop] swapchain OUT_OF_DATE — нужен recreate (2.1b)");
                return false;
            }
            if (acquireResult != VK10.VK_SUCCESS && acquireResult != KHRSwapchain.VK_SUBOPTIMAL_KHR) {
                throw new IllegalStateException("vkAcquireNextImageKHR failed, VkResult=" + acquireResult);
            }
            int imageIndex = pImageIndex.get(0);

            // Если этот image уже использует другой frame slot — ждём его fence.
            long imageFence = sync.imageInFlight(imageIndex);
            if (imageFence != VK10.VK_NULL_HANDLE) {
                VK10.vkWaitForFences(device.logical(), imageFence, true, Long.MAX_VALUE);
            }
            sync.setImageInFlight(imageIndex, inFlightFence);

            VK10.vkResetFences(device.logical(), inFlightFence);

            VkCommandBuffer cmd = commandBuffers.buffer(currentFrame);
            VK10.vkResetCommandBuffer(cmd, 0);

            recordClear(cmd, imageIndex, timeSeconds, stack);

            // Submit
            LongBuffer pWaitSem    = stack.longs(imageAvail);
            IntBuffer  pWaitStages = stack.ints(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
            LongBuffer pSignalSem  = stack.longs(renderDone);
            PointerBuffer pCmd     = stack.pointers(cmd);
            VkSubmitInfo submit = VkSubmitInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_SUBMIT_INFO)
                    .waitSemaphoreCount(1)
                    .pWaitSemaphores(pWaitSem)
                    .pWaitDstStageMask(pWaitStages)
                    .pCommandBuffers(pCmd)
                    .pSignalSemaphores(pSignalSem);
            VulkanDevice.check(VK10.vkQueueSubmit(device.graphicsQueue(), submit, inFlightFence),
                    "vkQueueSubmit");

            // Present
            LongBuffer pSwap = stack.longs(swapchain.handle());
            IntBuffer  pIdx  = stack.ints(imageIndex);
            VkPresentInfoKHR present = VkPresentInfoKHR.calloc(stack)
                    .sType(KHRSwapchain.VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
                    .pWaitSemaphores(pSignalSem)
                    .swapchainCount(1)
                    .pSwapchains(pSwap)
                    .pImageIndices(pIdx);
            int presentResult = KHRSwapchain.vkQueuePresentKHR(device.presentQueue(), present);
            if (presentResult == KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR
                    || presentResult == KHRSwapchain.VK_SUBOPTIMAL_KHR) {
                System.out.println("[Death:desktop] present sub-optimal/out-of-date — recreate (2.1b)");
                return false;
            }
            if (presentResult != VK10.VK_SUCCESS) {
                throw new IllegalStateException("vkQueuePresentKHR failed, VkResult=" + presentResult);
            }

            currentFrame = (currentFrame + 1) % MAX_FRAMES_IN_FLIGHT;
            presentedFrames++;
            return true;
        }
    }

    private void recordClear(VkCommandBuffer cmd, int imageIndex, double t, MemoryStack stack) {
        VkCommandBufferBeginInfo bi = VkCommandBufferBeginInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
        VulkanDevice.check(VK10.vkBeginCommandBuffer(cmd, bi), "vkBeginCommandBuffer");

        // HSV → RGB по t (полный цикл за 4 секунды)
        float hue = (float) ((t / 4.0) % 1.0);
        float[] rgb = hsvToRgb(hue, 0.6f, 0.85f);
        VkClearValue.Buffer clear = VkClearValue.calloc(1, stack);
        clear.get(0).color()
                .float32(0, rgb[0])
                .float32(1, rgb[1])
                .float32(2, rgb[2])
                .float32(3, 1.0f);

        VkRect2D area = VkRect2D.calloc(stack);
        area.offset(VkOffset2D.calloc(stack).set(0, 0));
        area.extent(VkExtent2D.calloc(stack).set(swapchain.width(), swapchain.height()));

        VkRenderPassBeginInfo rpBegin = VkRenderPassBeginInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                .renderPass(renderPass.handle())
                .framebuffer(framebuffers.handle(imageIndex))
                .renderArea(area)
                .pClearValues(clear);

        VK10.vkCmdBeginRenderPass(cmd, rpBegin, VK10.VK_SUBPASS_CONTENTS_INLINE);
        // 2.1a: ничего больше не рисуем — render pass с CLEAR loadOp заполнит цвет автоматически.
        // На 2.1b сюда добавится bindPipeline + cmdDraw для триангла.
        VK10.vkCmdEndRenderPass(cmd);

        VulkanDevice.check(VK10.vkEndCommandBuffer(cmd), "vkEndCommandBuffer");
    }

    /** Простой HSV→RGB для clear-color циклирования. */
    private static float[] hsvToRgb(float h, float s, float v) {
        float c = v * s;
        float hh = h * 6.0f;
        float x = c * (1 - Math.abs((hh % 2) - 1));
        float r, g, b;
        if      (hh < 1) { r = c; g = x; b = 0; }
        else if (hh < 2) { r = x; g = c; b = 0; }
        else if (hh < 3) { r = 0; g = c; b = x; }
        else if (hh < 4) { r = 0; g = x; b = c; }
        else if (hh < 5) { r = x; g = 0; b = c; }
        else             { r = c; g = 0; b = x; }
        float m = v - c;
        return new float[]{r + m, g + m, b + m};
    }

    public int presentedFrames() { return presentedFrames; }
    public int width()           { return swapchain.width(); }
    public int height()          { return swapchain.height(); }
    public String deviceName()   { return device.deviceName(); }

    /**
     * Освобождает все Vulkan ресурсы в обратном порядке создания.
     * Перед уничтожением ждёт {@code vkDeviceWaitIdle}, чтобы GPU
     * закончила все в-полёте операции.
     */
    public void dispose() {
        if (device.logical() != null) {
            VK10.vkDeviceWaitIdle(device.logical());
        }
        sync.dispose();
        commandBuffers.dispose();
        framebuffers.dispose();
        renderPass.dispose();
        swapchain.dispose();
        device.dispose();
        surface.dispose();
        System.out.println("[Death:desktop] Vulkan renderer disposed");
    }
}
