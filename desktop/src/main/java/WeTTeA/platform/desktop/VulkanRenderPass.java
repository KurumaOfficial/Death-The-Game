package WeTTeA.platform.desktop;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkAttachmentDescription;
import org.lwjgl.vulkan.VkAttachmentReference;
import org.lwjgl.vulkan.VkRenderPassCreateInfo;
import org.lwjgl.vulkan.VkSubpassDependency;
import org.lwjgl.vulkan.VkSubpassDescription;

import java.nio.LongBuffer;

/**
 * Stage 2.1a — минимальный {@code VkRenderPass} с одним color attachment'ом.
 *
 * <p>Конфигурация:
 * <ul>
 *   <li>1 attachment: {@code samples=1}, {@code loadOp=CLEAR},
 *       {@code storeOp=STORE}, layout {@code UNDEFINED → PRESENT_SRC_KHR}.</li>
 *   <li>1 subpass типа {@code GRAPHICS} с одной color reference на attachment 0,
 *       layout {@code COLOR_ATTACHMENT_OPTIMAL}.</li>
 *   <li>1 subpass dependency: {@code EXTERNAL → 0}, ждёт
 *       {@code COLOR_ATTACHMENT_OUTPUT}, обеспечивает синхронизацию с
 *       {@code vkAcquireNextImageKHR}'ом.</li>
 * </ul>
 *
 * <p>Этого достаточно для clear-color smoke. С triangle (2.1b) этот же
 * render pass переиспользуется — добавится только pipeline, привязанный
 * к subpass 0.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class VulkanRenderPass {

    private final VulkanDevice device;
    private long handle = VK10.VK_NULL_HANDLE;

    public VulkanRenderPass(VulkanDevice device, int colorFormat) {
        this.device = device;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.calloc(1, stack);
            attachments.get(0)
                    .format(colorFormat)
                    .samples(VK10.VK_SAMPLE_COUNT_1_BIT)
                    .loadOp(VK10.VK_ATTACHMENT_LOAD_OP_CLEAR)
                    .storeOp(VK10.VK_ATTACHMENT_STORE_OP_STORE)
                    .stencilLoadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .stencilStoreOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED)
                    .finalLayout(KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);

            VkAttachmentReference.Buffer colorRef = VkAttachmentReference.calloc(1, stack);
            colorRef.get(0)
                    .attachment(0)
                    .layout(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

            VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack);
            subpass.get(0)
                    .pipelineBindPoint(VK10.VK_PIPELINE_BIND_POINT_GRAPHICS)
                    .colorAttachmentCount(1)
                    .pColorAttachments(colorRef);

            VkSubpassDependency.Buffer dep = VkSubpassDependency.calloc(1, stack);
            dep.get(0)
                    .srcSubpass(VK10.VK_SUBPASS_EXTERNAL)
                    .dstSubpass(0)
                    .srcStageMask(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .srcAccessMask(0)
                    .dstStageMask(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .dstAccessMask(VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);

            VkRenderPassCreateInfo info = VkRenderPassCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
                    .pAttachments(attachments)
                    .pSubpasses(subpass)
                    .pDependencies(dep);

            LongBuffer pPass = stack.callocLong(1);
            VulkanDevice.check(VK10.vkCreateRenderPass(device.logical(), info, null, pPass),
                    "vkCreateRenderPass");
            handle = pPass.get(0);
            System.out.println("[Death:desktop] Vulkan render pass created (1 color attachment, format=" + colorFormat + ")");
        }
    }

    public long handle() { return handle; }

    public void dispose() {
        if (handle != VK10.VK_NULL_HANDLE && device.logical() != null) {
            VK10.vkDestroyRenderPass(device.logical(), handle, null);
            handle = VK10.VK_NULL_HANDLE;
        }
    }
}
