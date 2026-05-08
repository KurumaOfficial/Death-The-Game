package WeTTeA.platform.desktop;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;

import java.nio.LongBuffer;

/**
 * Stage 2.1a — синхронизация кадров.
 *
 * <p>На каждый из {@code framesInFlight} слотов создаются:
 * <ul>
 *   <li>{@code imageAvailable} — semaphore, сигналится
 *       {@code vkAcquireNextImageKHR} когда image готов к рендеру;</li>
 *   <li>{@code renderFinished} — semaphore, сигналится submit'ом, ожидается
 *       {@code vkQueuePresentKHR}'ом;</li>
 *   <li>{@code inFlight} — fence, сигналится submit'ом; CPU ждёт его перед
 *       началом следующего кадра в этом слоте, чтобы не переписать
 *       command buffer пока GPU ещё им пользуется. Создаётся со state
 *       {@code SIGNALED}, чтобы первый wait не блокировал бесконечно.</li>
 * </ul>
 *
 * <p>Также есть массив {@code imagesInFlight} — для случая, когда swapchain
 * выдаёт image, который уже используется другим frame slot'ом (re-acquire);
 * мы привязываем его к ожидаемому fence, чтобы не пересечься.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class VulkanFrameSync {

    private final VulkanDevice device;
    private final long[] imageAvailable;
    private final long[] renderFinished;
    private final long[] inFlight;
    private final long[] imagesInFlight;

    public VulkanFrameSync(VulkanDevice device, int framesInFlight, int swapchainImages) {
        this.device         = device;
        this.imageAvailable = new long[framesInFlight];
        this.renderFinished = new long[framesInFlight];
        this.inFlight       = new long[framesInFlight];
        this.imagesInFlight = new long[swapchainImages];

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSemaphoreCreateInfo semInfo = VkSemaphoreCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);
            VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)
                    .flags(VK10.VK_FENCE_CREATE_SIGNALED_BIT);

            LongBuffer p = stack.callocLong(1);
            for (int i = 0; i < framesInFlight; i++) {
                VulkanDevice.check(VK10.vkCreateSemaphore(device.logical(), semInfo, null, p),
                        "vkCreateSemaphore[imgAvail][" + i + "]");
                imageAvailable[i] = p.get(0);

                VulkanDevice.check(VK10.vkCreateSemaphore(device.logical(), semInfo, null, p),
                        "vkCreateSemaphore[renderFinished][" + i + "]");
                renderFinished[i] = p.get(0);

                VulkanDevice.check(VK10.vkCreateFence(device.logical(), fenceInfo, null, p),
                        "vkCreateFence[inFlight][" + i + "]");
                inFlight[i] = p.get(0);
            }
        }
        System.out.println("[Death:desktop] Vulkan frame sync ready: framesInFlight=" + framesInFlight
                + " swapchainImages=" + swapchainImages);
    }

    public long imageAvailable(int frame) { return imageAvailable[frame]; }
    public long renderFinished(int frame) { return renderFinished[frame]; }
    public long inFlight(int frame)       { return inFlight[frame]; }

    public long imageInFlight(int imageIndex)                 { return imagesInFlight[imageIndex]; }
    public void setImageInFlight(int imageIndex, long fence)  { imagesInFlight[imageIndex] = fence; }

    public int framesInFlight() { return inFlight.length; }

    public void dispose() {
        if (device.logical() == null) return;
        for (int i = 0; i < inFlight.length; i++) {
            if (imageAvailable[i] != VK10.VK_NULL_HANDLE) {
                VK10.vkDestroySemaphore(device.logical(), imageAvailable[i], null);
                imageAvailable[i] = VK10.VK_NULL_HANDLE;
            }
            if (renderFinished[i] != VK10.VK_NULL_HANDLE) {
                VK10.vkDestroySemaphore(device.logical(), renderFinished[i], null);
                renderFinished[i] = VK10.VK_NULL_HANDLE;
            }
            if (inFlight[i] != VK10.VK_NULL_HANDLE) {
                VK10.vkDestroyFence(device.logical(), inFlight[i], null);
                inFlight[i] = VK10.VK_NULL_HANDLE;
            }
        }
    }
}
