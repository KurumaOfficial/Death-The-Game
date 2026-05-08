package WeTTeA.platform.desktop;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;

import java.nio.LongBuffer;

/**
 * Stage 2.1a — массив {@code VkFramebuffer} (по одному на каждый swapchain
 * image view). Привязан к {@link VulkanRenderPass} и переиспользуется
 * в command buffer'ах через {@code vkCmdBeginRenderPass}.
 *
 * <p>Размер framebuffer'ов = размеру swapchain'а; layers=1, attachments=1
 * (только color из {@link VulkanSwapchain#imageViews()}).
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class VulkanFramebuffers {

    private final VulkanDevice device;
    private long[] handles = new long[0];

    public VulkanFramebuffers(VulkanDevice device, VulkanSwapchain swap, VulkanRenderPass renderPass) {
        this.device = device;
        long[] views = swap.imageViews();
        handles = new long[views.length];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            for (int i = 0; i < views.length; i++) {
                LongBuffer pAttachments = stack.longs(views[i]);
                VkFramebufferCreateInfo info = VkFramebufferCreateInfo.calloc(stack)
                        .sType(VK10.VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
                        .renderPass(renderPass.handle())
                        .pAttachments(pAttachments)
                        .width(swap.width())
                        .height(swap.height())
                        .layers(1);
                LongBuffer pFb = stack.callocLong(1);
                VulkanDevice.check(VK10.vkCreateFramebuffer(device.logical(), info, null, pFb),
                        "vkCreateFramebuffer[" + i + "]");
                handles[i] = pFb.get(0);
            }
            System.out.println("[Death:desktop] Vulkan framebuffers created: count=" + handles.length);
        }
    }

    public long[] handles()         { return handles; }
    public long   handle(int index) { return handles[index]; }

    public void dispose() {
        if (device.logical() == null) return;
        for (long h : handles) {
            if (h != VK10.VK_NULL_HANDLE) {
                VK10.vkDestroyFramebuffer(device.logical(), h, null);
            }
        }
        handles = new long[0];
    }
}
