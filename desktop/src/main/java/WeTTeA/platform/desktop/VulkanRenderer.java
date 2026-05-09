package WeTTeA.platform.desktop;

import WeTTeA.api.content.AssetCategory;
import WeTTeA.api.content.AssetHandle;
import WeTTeA.api.content.ContentLoader;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
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
import org.lwjgl.vulkan.VkViewport;

import java.nio.IntBuffer;
import java.nio.LongBuffer;

/**
 * Stage 2.1a/2.1b/3.1 — оркестратор Vulkan render-loop.
 *
 * <p>Владеет всеми Vulkan ресурсами рендера: {@link VulkanSurface},
 * {@link VulkanDevice}, {@link VulkanSwapchain}, {@link VulkanRenderPass},
 * {@link VulkanFramebuffers}, {@link VulkanCommandBuffers},
 * {@link VulkanFrameSync}; на 2.1b — ещё двумя {@link VulkanShaderModule}
 * (vert + frag) и {@link VulkanPipeline} (graphics pipeline для триангла);
 * на 3.1 — {@link VulkanTextureDescriptors} (общий layout + pool) и
 * {@link VulkanTexture} (image + sampler + descriptor set, одной checkerboard-
 * текстурой). Создаются в конструкторе, освобождаются в
 * {@link #dispose()} в обратном порядке.
 *
 * <p>{@link #renderFrame(double)} — единственный «горячий» метод,
 * вызываемый каждый кадр из main loop:
 * <ol>
 *   <li>Проверяем dirty-flag от GLFW framebuffer-size callback'а
 *       ({@link #markFramebufferResized()}); если флаг выставлен — выполняем
 *       {@link #recreateSwapchainAndFramebuffers()} ДО acquire (иначе vkAcquire
 *       вернёт OUT_OF_DATE и потеряем кадр).</li>
 *   <li>Ждём fence слота {@code currentFrame}.</li>
 *   <li>{@code vkAcquireNextImageKHR} с semaphore {@code imageAvailable}.
 *       Если возвращает {@code OUT_OF_DATE_KHR} — recreate и пропуск кадра.</li>
 *   <li>Если этот image уже использует другой frame slot — ждём его fence.</li>
 *   <li>Ресет fence слота, ресет cmd buffer'а, запись:
 *       {@code beginRenderPass} (clear color вычисляется из {@code timeSeconds})
 *       → {@code cmdSetViewport}+{@code cmdSetScissor} (динамический state) →
 *       {@code cmdBindPipeline}+{@code cmdDraw(3,1,0,0)} (триангл из 2.1b) →
 *       {@code endRenderPass}.</li>
 *   <li>{@code vkQueueSubmit} с wait на {@code imageAvailable}, signal
 *       {@code renderFinished} + fence {@code inFlight}.</li>
 *   <li>{@code vkQueuePresentKHR} с wait на {@code renderFinished}.
 *       {@code OUT_OF_DATE_KHR}/{@code SUBOPTIMAL_KHR} триггерят recreate.</li>
 *   <li>{@code currentFrame = (currentFrame + 1) % framesInFlight}.</li>
 * </ol>
 *
 * <p>Clear color циклирует HSV-волной по {@code timeSeconds} — даёт
 * визуально читаемое доказательство, что render-loop реально дошёл до
 * GPU и презентация сработала. Триангл (RGB-градиент top→bottom-right→bottom-left)
 * рисуется поверх clear-color и доказывает работоспособность shader stage'ей.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class VulkanRenderer {

    /** Количество кадров «в полёте» одновременно — типовое значение для double-buffering CPU↔GPU. */
    public static final int MAX_FRAMES_IN_FLIGHT = 2;

    private static final String SHADER_VERT_ID  = "shader.triangle.vert";
    private static final String SHADER_FRAG_ID  = "shader.triangle.frag";
    private static final String TEXTURE_TEST_ID = "texture.test.checkerboard";

    /** Capacity descriptor pool'а под текстуры. На 3.1 хватит одной, но берём запас на 3.x. */
    private static final int TEXTURE_POOL_CAPACITY = 4;

    private final long windowHandle;
    private final VulkanSurface       surface;
    private final VulkanDevice        device;
    private final VulkanSwapchain     swapchain;
    private final VulkanRenderPass    renderPass;
    private final VulkanFramebuffers  framebuffers;
    private final VulkanCommandBuffers commandBuffers;
    private final VulkanFrameSync     sync;
    private final VulkanShaderModule       triangleVert;
    private final VulkanShaderModule       triangleFrag;
    private final VulkanTextureDescriptors textureDescriptors;
    private final VulkanTexture            triangleTexture;
    private final VulkanPipeline           trianglePipeline;

    private int currentFrame = 0;
    private int presentedFrames = 0;
    private int swapchainRecreates = 0;
    private volatile boolean framebufferResized = false;

    public VulkanRenderer(VkInstance instance, long windowHandle,
                          ContentLoader contentLoader, PngImageDecoder pngDecoder) {
        this.windowHandle   = windowHandle;
        this.surface        = new VulkanSurface(instance, windowHandle);
        this.device         = new VulkanDevice(instance, surface.handle());
        this.device.pick();
        this.device.createLogical();
        this.swapchain      = new VulkanSwapchain(device, surface.handle(), windowHandle);
        this.renderPass     = new VulkanRenderPass(device, swapchain.imageFormat());
        this.framebuffers   = new VulkanFramebuffers(device, swapchain, renderPass);
        this.commandBuffers = new VulkanCommandBuffers(device, MAX_FRAMES_IN_FLIGHT);
        this.sync           = new VulkanFrameSync(device, MAX_FRAMES_IN_FLIGHT, swapchain.imageCount());

        // Stage 3.1 — общий layout + pool descriptor set'ов под текстуры.
        this.textureDescriptors = new VulkanTextureDescriptors(device, TEXTURE_POOL_CAPACITY);

        // Stage 2.1b — shader modules + graphics pipeline для триангла.
        this.triangleVert = new VulkanShaderModule(device, contentLoader,
                new AssetHandle(SHADER_VERT_ID, AssetCategory.SHADER_SPIRV), "triangle.vert");
        this.triangleFrag = new VulkanShaderModule(device, contentLoader,
                new AssetHandle(SHADER_FRAG_ID, AssetCategory.SHADER_SPIRV), "triangle.frag");
        this.trianglePipeline = new VulkanPipeline(device, renderPass,
                triangleVert, triangleFrag, textureDescriptors.descriptorSetLayout());

        // Stage 3.1 — загружаем текстуру (PNG → staging → VkImage → descriptor set).
        this.triangleTexture = VulkanTexture.fromAsset(device, commandBuffers,
                textureDescriptors, pngDecoder, contentLoader,
                new AssetHandle(TEXTURE_TEST_ID, AssetCategory.TEXTURE),
                "checkerboard");
    }

    /**
     * Stage 2.1b — flag, выставляемый GLFW framebuffer-size callback'ом
     * (см. {@link DesktopLauncher}). Render loop проверяет его в начале
     * каждого кадра и форсит recreate swapchain'а ДО acquire'а.
     *
     * <p>Volatile, потому что callback теоретически может быть вызван
     * не из main thread'а (на текущем GLFW поведении — main, но flag
     * volatile для будущей мульти-thread'овой модели и для memory barrier'а
     * между callback'ом и render loop'ом).
     */
    public void markFramebufferResized() {
        framebufferResized = true;
    }

    /**
     * Презентует один кадр с цикл-цветом, зависящим от {@code timeSeconds},
     * и нарисованным поверх триангл'ом. На stage 2.1b возвращает {@code true}
     * всегда, кроме случая когда вышестоящий код решил остановить рендер
     * (например, fatal Vulkan error). OUT_OF_DATE/SUBOPTIMAL/resize обрабатываются
     * внутри: каждый из этих сценариев вызывает {@link #recreateSwapchainAndFramebuffers()}
     * и пропускает текущий кадр (caller просто продолжает loop).
     *
     * <p>Если окно минимизировано (extent 0×0), recreate откладывается до
     * восстановления; кадры в это время «съедаются» (return true без present'а).
     */
    public boolean renderFrame(double timeSeconds) {
        // Stage 2.1b — пред-кадровый recreate, если callback пометил окно ресайзнутым.
        if (framebufferResized) {
            framebufferResized = false;
            if (!recreateSwapchainAndFramebuffers()) {
                return true; // окно минимизировано — пропускаем кадр
            }
        }

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
                recreateSwapchainAndFramebuffers();
                return true; // skip frame, на следующем тике re-acquire'нем
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

            record(cmd, imageIndex, timeSeconds, stack);

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
                    || presentResult == KHRSwapchain.VK_SUBOPTIMAL_KHR
                    || framebufferResized) {
                framebufferResized = false;
                recreateSwapchainAndFramebuffers();
            } else if (presentResult != VK10.VK_SUCCESS) {
                throw new IllegalStateException("vkQueuePresentKHR failed, VkResult=" + presentResult);
            }

            currentFrame = (currentFrame + 1) % MAX_FRAMES_IN_FLIGHT;
            presentedFrames++;
            return true;
        }
    }

    /**
     * Stage 2.1b — пересоздаёт swapchain + framebuffers + ресет imagesInFlight.
     *
     * <p>Render pass и pipeline переживают recreate (формат attachment'а и
     * shader stages не меняются; viewport/scissor — динамические). Алгоритм:
     * <ol>
     *   <li>{@link GLFW#glfwGetFramebufferSize} — если 0×0 (минимизировано),
     *       возвращаем {@code false} и пропускаем recreate (try ещё раз
     *       на следующем кадре);</li>
     *   <li>{@code vkDeviceWaitIdle} — ждём, пока GPU закончит все
     *       in-flight операции, чтобы безопасно уничтожить старые ресурсы;</li>
     *   <li>{@link VulkanSwapchain#recreate} (создаёт новые images + views);</li>
     *   <li>{@link VulkanFramebuffers#recreate} поверх новых views;</li>
     *   <li>{@link VulkanFrameSync#resizeImagesInFlight} — на случай если
     *       imageCount изменился.</li>
     * </ol>
     *
     * <p>Возвращает {@code true} если recreate прошёл успешно,
     * {@code false} если окно минимизировано (extent=0×0).
     */
    private boolean recreateSwapchainAndFramebuffers() {
        int[] w = new int[1];
        int[] h = new int[1];
        GLFW.glfwGetFramebufferSize(windowHandle, w, h);
        if (w[0] == 0 || h[0] == 0) {
            return false;
        }

        VK10.vkDeviceWaitIdle(device.logical());

        swapchain.recreate();
        framebuffers.recreate(swapchain, renderPass);
        sync.resizeImagesInFlight(swapchain.imageCount());

        swapchainRecreates++;
        System.out.println("[Death:desktop] Vulkan swapchain recreated #" + swapchainRecreates
                + ": " + swapchain.width() + "x" + swapchain.height()
                + " images=" + swapchain.imageCount());
        return true;
    }

    private void record(VkCommandBuffer cmd, int imageIndex, double t, MemoryStack stack) {
        VkCommandBufferBeginInfo bi = VkCommandBufferBeginInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
        VulkanDevice.check(VK10.vkBeginCommandBuffer(cmd, bi), "vkBeginCommandBuffer");

        // HSV → RGB по t (полный цикл за 4 секунды) — фон.
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

        // Stage 2.1b — динамические viewport+scissor (pipeline переживает swapchain resize).
        VkViewport.Buffer viewport = VkViewport.calloc(1, stack);
        viewport.get(0)
                .x(0.0f).y(0.0f)
                .width((float) swapchain.width())
                .height((float) swapchain.height())
                .minDepth(0.0f).maxDepth(1.0f);
        VK10.vkCmdSetViewport(cmd, 0, viewport);

        VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
        scissor.get(0)
                .offset(VkOffset2D.calloc(stack).set(0, 0))
                .extent(VkExtent2D.calloc(stack).set(swapchain.width(), swapchain.height()));
        VK10.vkCmdSetScissor(cmd, 0, scissor);

        // Stage 2.1b — bind triangle pipeline.
        VK10.vkCmdBindPipeline(cmd, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, trianglePipeline.handle());

        // Stage 3.1 — bind descriptor set 0 (combined image sampler).
        LongBuffer pSet = stack.longs(triangleTexture.descriptorSet());
        VK10.vkCmdBindDescriptorSets(cmd,
                VK10.VK_PIPELINE_BIND_POINT_GRAPHICS,
                trianglePipeline.pipelineLayout(),
                0, pSet, null);

        // Рисуем 3 вершины без vertex buffer'а — вершины/UV в шейдере.
        VK10.vkCmdDraw(cmd, 3, 1, 0, 0);

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

    public int presentedFrames()    { return presentedFrames; }
    public int swapchainRecreates() { return swapchainRecreates; }
    public int width()              { return swapchain.width(); }
    public int height()             { return swapchain.height(); }
    public String deviceName()      { return device.deviceName(); }

    /**
     * Освобождает все Vulkan ресурсы в обратном порядке создания.
     * Перед уничтожением ждёт {@code vkDeviceWaitIdle}, чтобы GPU
     * закончила все в-полёте операции.
     */
    public void dispose() {
        if (device.logical() != null) {
            VK10.vkDeviceWaitIdle(device.logical());
        }
        trianglePipeline.dispose();
        triangleTexture.dispose();
        textureDescriptors.dispose();
        triangleFrag.dispose();
        triangleVert.dispose();
        sync.dispose();
        commandBuffers.dispose();
        framebuffers.dispose();
        renderPass.dispose();
        swapchain.dispose();
        device.dispose();
        surface.dispose();
        System.out.println("[Death:desktop] Vulkan renderer disposed (presented="
                + presentedFrames + " frames, recreates=" + swapchainRecreates + ")");
    }
}
