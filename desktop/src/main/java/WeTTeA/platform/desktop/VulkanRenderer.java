package WeTTeA.platform.desktop;

import WeTTeA.api.content.AssetCategory;
import WeTTeA.api.content.AssetHandle;
import WeTTeA.api.content.ContentLoader;
import WeTTeA.api.render.Camera;
import WeTTeA.core.render.PerspectiveCamera;
import org.joml.Matrix4f;
import org.joml.Vector3f;
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
 * Stage 2.1a/2.1b/3.1 + E.1 — оркестратор Vulkan render-loop.
 *
 * <p>На stage E.1 ренедерер расширен под полноценный 3D pipeline:
 *
 * <ul>
 *   <li><b>Camera</b> — внешний контракт {@link Camera}; рендерер только
 *       читает {@link Camera#viewProjectionMatrix()} и зовёт
 *       {@link Camera#updateAspect(int, int)} на recreate. Контроль
 *       камеры (orbit / mouse-look) в gameplay-слое.</li>
 *   <li><b>Depth buffer</b> — {@link VulkanDepthBuffer} (D32_SFLOAT по
 *       умолчанию), recreate-on-resize вместе со swapchain'ом.</li>
 *   <li><b>Mesh</b> — {@link VulkanMeshBuffer} с единичным кубом из
 *       {@link WeTTeA.core.render.CubeMeshFactory} (24 vertex + 36 index
 *       в DEVICE_LOCAL).</li>
 *   <li><b>UBO[N]</b> — {@link VulkanUniformBuffer} один на каждый
 *       frame slot ({@value #MAX_FRAMES_IN_FLIGHT} штук). Записывается
 *       host-side {@code (model, viewProj, lightDir)} перед каждым
 *       submit'ом.</li>
 *   <li><b>Descriptors</b> — {@link VulkanSceneDescriptors} (UBO@0 +
 *       sampler@1). На init'е аллоцируется N descriptor set'ов; в каждый
 *       пишется свой UBO + общая текстура.</li>
 *   <li><b>Pipeline</b> — vertex bindings (pos+normal+uv), depth test
 *       LESS, BACK culling, dynamic viewport+scissor.</li>
 * </ul>
 *
 * <p><b>renderFrame()</b> алгоритм (E.1):
 * <ol>
 *   <li>Если выставлен resize-flag — recreate swapchain+depth+framebuffers
 *       + camera.updateAspect.</li>
 *   <li>Wait fence currentFrame.</li>
 *   <li>vkAcquireNextImageKHR.</li>
 *   <li>Wait imageInFlight (если нужно), reset fence, reset cmd.</li>
 *   <li>Считаем model матрицу (вращение куба от t).</li>
 *   <li>{@code ubo[currentFrame].update(model, camera, lightDir)}.</li>
 *   <li>record(): clear color (HSV-волна) + clear depth (1.0) →
 *       cmdSetViewport+Scissor → bindPipeline →
 *       bindVertexBuffer+bindIndexBuffer → bindDescriptorSet[currentFrame] →
 *       cmdDrawIndexed(36).</li>
 *   <li>Submit + present.</li>
 *   <li>currentFrame = (currentFrame+1) % N.</li>
 * </ol>
 *
 * <p>Идемпотентный {@link #dispose()} в обратном порядке создания.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class VulkanRenderer {

    /** Количество кадров «в полёте» одновременно — типовое значение для double-buffering CPU↔GPU. */
    public static final int MAX_FRAMES_IN_FLIGHT = 2;

    private static final String SHADER_VERT_ID  = "shader.cube.vert";
    private static final String SHADER_FRAG_ID  = "shader.cube.frag";
    private static final String TEXTURE_TEST_ID = "texture.test.checkerboard";

    private final long windowHandle;
    private final Camera camera;
    private final VulkanSurface       surface;
    private final VulkanDevice        device;
    private final VulkanSwapchain     swapchain;
    private final VulkanDepthBuffer   depthBuffer;
    private final VulkanRenderPass    renderPass;
    private final VulkanFramebuffers  framebuffers;
    private final VulkanCommandBuffers commandBuffers;
    private final VulkanFrameSync     sync;
    private final VulkanSceneDescriptors sceneDescriptors;
    private final VulkanShaderModule cubeVert;
    private final VulkanShaderModule cubeFrag;
    private final VulkanTexture      cubeTexture;
    private final VulkanMeshBuffer   cubeMesh;
    private final VulkanUniformBuffer[] uniformBuffers;
    private final long[]             frameDescriptorSets;
    private final VulkanPipeline     cubePipeline;

    private final Matrix4f scratchModel = new Matrix4f();
    private final Vector3f lightDir     = new Vector3f(-0.4f, -1.0f, -0.6f).normalize();

    private int currentFrame = 0;
    private int presentedFrames = 0;
    private int swapchainRecreates = 0;
    private volatile boolean framebufferResized = false;

    public VulkanRenderer(VkInstance instance, long windowHandle, Camera camera,
                          ContentLoader contentLoader, PngImageDecoder pngDecoder) {
        this.windowHandle   = windowHandle;
        this.camera         = camera;
        this.surface        = new VulkanSurface(instance, windowHandle);
        this.device         = new VulkanDevice(instance, surface.handle());
        this.device.pick();
        this.device.createLogical();
        this.swapchain      = new VulkanSwapchain(device, surface.handle(), windowHandle);
        this.depthBuffer    = new VulkanDepthBuffer(device, swapchain.width(), swapchain.height());
        this.renderPass     = new VulkanRenderPass(device, swapchain.imageFormat(), depthBuffer.format());
        this.framebuffers   = new VulkanFramebuffers(device, swapchain, renderPass, depthBuffer);
        this.commandBuffers = new VulkanCommandBuffers(device, MAX_FRAMES_IN_FLIGHT);
        this.sync           = new VulkanFrameSync(device, MAX_FRAMES_IN_FLIGHT, swapchain.imageCount());

        // Stage E.1 — descriptor layout/pool под scene.
        this.sceneDescriptors = new VulkanSceneDescriptors(device, MAX_FRAMES_IN_FLIGHT);

        // Stage E.1 — shader modules + graphics pipeline для куба.
        this.cubeVert = new VulkanShaderModule(device, contentLoader,
                new AssetHandle(SHADER_VERT_ID, AssetCategory.SHADER_SPIRV), "cube.vert");
        this.cubeFrag = new VulkanShaderModule(device, contentLoader,
                new AssetHandle(SHADER_FRAG_ID, AssetCategory.SHADER_SPIRV), "cube.frag");
        this.cubePipeline = new VulkanPipeline(device, renderPass,
                cubeVert, cubeFrag, sceneDescriptors.descriptorSetLayout());

        // Stage E.1 — загружаем checkerboard как albedo для куба.
        this.cubeTexture = VulkanTexture.fromAsset(device, commandBuffers,
                pngDecoder, contentLoader,
                new AssetHandle(TEXTURE_TEST_ID, AssetCategory.TEXTURE),
                "checkerboard");

        // Stage E.1 — mesh + UBOs + descriptor sets.
        this.cubeMesh = VulkanMeshBuffer.fromCubeMesh(device, commandBuffers);

        this.uniformBuffers = new VulkanUniformBuffer[MAX_FRAMES_IN_FLIGHT];
        for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
            uniformBuffers[i] = new VulkanUniformBuffer(device);
        }

        this.frameDescriptorSets = sceneDescriptors.allocateSets(MAX_FRAMES_IN_FLIGHT);
        for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
            sceneDescriptors.writeFrameSet(
                    frameDescriptorSets[i],
                    uniformBuffers[i].handle(),
                    uniformBuffers[i].sizeBytes(),
                    cubeTexture.imageView(),
                    cubeTexture.samplerHandle());
        }

        // Camera учится знать своё разрешение. На E.2 мы перенесём это в
        // gameplay-слой; на E.1 рендерер прокидывает aspect ratio сам.
        camera.updateAspect(swapchain.width(), swapchain.height());

        System.out.println("[Death:desktop] Vulkan renderer ready (stage E.1: cube + camera + depth, "
                + "framesInFlight=" + MAX_FRAMES_IN_FLIGHT + ")");
    }

    /**
     * Stage 2.1b — flag, выставляемый GLFW framebuffer-size callback'ом.
     */
    public void markFramebufferResized() {
        framebufferResized = true;
    }

    /**
     * Презентует один кадр с вращающимся кубом.
     */
    public boolean renderFrame(double timeSeconds) {
        if (framebufferResized) {
            framebufferResized = false;
            if (!recreateSwapchainAndFramebuffers()) {
                return true;
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
                return true;
            }
            if (acquireResult != VK10.VK_SUCCESS && acquireResult != KHRSwapchain.VK_SUBOPTIMAL_KHR) {
                throw new IllegalStateException("vkAcquireNextImageKHR failed, VkResult=" + acquireResult);
            }
            int imageIndex = pImageIndex.get(0);

            long imageFence = sync.imageInFlight(imageIndex);
            if (imageFence != VK10.VK_NULL_HANDLE) {
                VK10.vkWaitForFences(device.logical(), imageFence, true, Long.MAX_VALUE);
            }
            sync.setImageInFlight(imageIndex, inFlightFence);

            VK10.vkResetFences(device.logical(), inFlightFence);

            // E.1 — обновление camera + UBO для текущего frame slot'а.
            updateScene(timeSeconds);
            uniformBuffers[currentFrame].update(scratchModel, camera, lightDir);

            VkCommandBuffer cmd = commandBuffers.buffer(currentFrame);
            VK10.vkResetCommandBuffer(cmd, 0);

            record(cmd, imageIndex, timeSeconds, stack);

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
     * E.1 — расчёт model матрицы куба и orbit-камеры в зависимости от
     * прошедшего времени. На E.2 этот метод исчезнет: камера двигается
     * через ActionMap в gameplay-слое, а model матрица каждого объекта
     * приходит из Transform-component'а ECS.
     */
    private void updateScene(double timeSeconds) {
        float t = (float) timeSeconds;
        // Куб медленно вращается вокруг Y, плюс лёгкое покачивание по X.
        scratchModel.identity()
                .rotateY(t * 0.6f)
                .rotateX((float) Math.sin(t * 0.4) * 0.25f);

        // Орбита камеры вокруг куба: yaw 0.25 rad/sec, pitch 0.15 rad amplitude, distance 3.5
        if (camera instanceof PerspectiveCamera pc) {
            float yaw   = t * 0.25f;
            float pitch = (float) Math.sin(t * 0.3) * 0.15f;
            pc.setOrbit(yaw, pitch, 3.5f);
        }
    }

    /**
     * Stage 2.1b + E.1 — пересоздаёт swapchain + depth + framebuffers,
     * обновляет aspect ratio камеры.
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
        depthBuffer.recreate(swapchain.width(), swapchain.height());
        framebuffers.recreate(swapchain, renderPass, depthBuffer);
        sync.resizeImagesInFlight(swapchain.imageCount());
        camera.updateAspect(swapchain.width(), swapchain.height());

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

        // E.1 — два clear value: color (HSV-волна по t) + depth (1.0).
        float hue = (float) ((t / 4.0) % 1.0);
        float[] rgb = hsvToRgb(hue, 0.4f, 0.55f);
        VkClearValue.Buffer clear = VkClearValue.calloc(2, stack);
        clear.get(0).color()
                .float32(0, rgb[0])
                .float32(1, rgb[1])
                .float32(2, rgb[2])
                .float32(3, 1.0f);
        clear.get(1).depthStencil().set(1.0f, 0);

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

        VK10.vkCmdBindPipeline(cmd, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, cubePipeline.handle());

        // E.1 — vertex + index buffer'ы куба.
        LongBuffer pVertexBuffers = stack.longs(cubeMesh.vertexBufferHandle());
        LongBuffer pVertexOffsets = stack.longs(0L);
        VK10.vkCmdBindVertexBuffers(cmd, 0, pVertexBuffers, pVertexOffsets);
        VK10.vkCmdBindIndexBuffer(cmd, cubeMesh.indexBufferHandle(), 0L, cubeMesh.indexType());

        // E.1 — frame-specific descriptor set (UBO + sampler).
        LongBuffer pSet = stack.longs(frameDescriptorSets[currentFrame]);
        VK10.vkCmdBindDescriptorSets(cmd,
                VK10.VK_PIPELINE_BIND_POINT_GRAPHICS,
                cubePipeline.pipelineLayout(),
                0, pSet, null);

        VK10.vkCmdDrawIndexed(cmd, cubeMesh.indexCount(), 1, 0, 0, 0);

        VK10.vkCmdEndRenderPass(cmd);

        VulkanDevice.check(VK10.vkEndCommandBuffer(cmd), "vkEndCommandBuffer");
    }

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
     */
    public void dispose() {
        if (device.logical() != null) {
            VK10.vkDeviceWaitIdle(device.logical());
        }
        if (uniformBuffers != null) {
            for (VulkanUniformBuffer ubo : uniformBuffers) {
                if (ubo != null) ubo.dispose();
            }
        }
        if (cubeMesh != null)         cubeMesh.dispose();
        if (cubePipeline != null)     cubePipeline.dispose();
        if (cubeTexture != null)      cubeTexture.dispose();
        if (sceneDescriptors != null) sceneDescriptors.dispose();
        if (cubeFrag != null)         cubeFrag.dispose();
        if (cubeVert != null)         cubeVert.dispose();
        if (sync != null)             sync.dispose();
        if (commandBuffers != null)   commandBuffers.dispose();
        if (framebuffers != null)     framebuffers.dispose();
        if (renderPass != null)       renderPass.dispose();
        if (depthBuffer != null)      depthBuffer.dispose();
        if (swapchain != null)        swapchain.dispose();
        if (device != null)           device.dispose();
        if (surface != null)          surface.dispose();
        System.out.println("[Death:desktop] Vulkan renderer disposed (presented="
                + presentedFrames + " frames, recreates=" + swapchainRecreates + ")");
    }
}
