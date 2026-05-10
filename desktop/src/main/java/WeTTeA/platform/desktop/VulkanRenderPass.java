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
 * Stage 2.1a + E.1 — {@code VkRenderPass} с color + depth attachment'ами.
 *
 * <p>На stage 2.1a render pass состоял из одного color attachment'а
 * (CLEAR → STORE → PRESENT_SRC_KHR). На stage E.1 добавился второй
 * attachment — depth.
 *
 * <h3>Attachments</h3>
 * <ul>
 *   <li><b>0 (color)</b>: format = swapchain image format,
 *       samples=1, loadOp=CLEAR, storeOp=STORE, layout
 *       UNDEFINED → PRESENT_SRC_KHR.</li>
 *   <li><b>1 (depth)</b>: format = D32_SFLOAT (или fallback,
 *       см. {@link VulkanDepthBuffer#pickDepthFormat}), samples=1,
 *       loadOp=CLEAR, storeOp=DONT_CARE (depth не презентится),
 *       layout UNDEFINED → DEPTH_STENCIL_ATTACHMENT_OPTIMAL.</li>
 * </ul>
 *
 * <h3>Subpass</h3>
 * <p>Один subpass GRAPHICS с обоими attachment-reference'ами:
 * color@0 (COLOR_ATTACHMENT_OPTIMAL) и
 * depth-stencil@1 (DEPTH_STENCIL_ATTACHMENT_OPTIMAL).
 *
 * <h3>Dependencies</h3>
 * <p>Один dependency EXTERNAL → 0, ждущий и
 * {@code COLOR_ATTACHMENT_OUTPUT}, и
 * {@code EARLY_FRAGMENT_TESTS | LATE_FRAGMENT_TESTS} — это страхует от
 * race condition'а между {@code vkAcquireNextImageKHR} (color) и
 * предыдущим использованием depth-image'а в кадре N-1.
 * {@code dstAccessMask} включает оба write'а
 * (COLOR_ATTACHMENT_WRITE + DEPTH_STENCIL_ATTACHMENT_WRITE).
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class VulkanRenderPass {

    private final VulkanDevice device;
    private long handle = VK10.VK_NULL_HANDLE;

    public VulkanRenderPass(VulkanDevice device, int colorFormat, int depthFormat) {
        this.device = device;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.calloc(2, stack);
            attachments.get(0)
                    .format(colorFormat)
                    .samples(VK10.VK_SAMPLE_COUNT_1_BIT)
                    .loadOp(VK10.VK_ATTACHMENT_LOAD_OP_CLEAR)
                    .storeOp(VK10.VK_ATTACHMENT_STORE_OP_STORE)
                    .stencilLoadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .stencilStoreOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED)
                    .finalLayout(KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
            attachments.get(1)
                    .format(depthFormat)
                    .samples(VK10.VK_SAMPLE_COUNT_1_BIT)
                    .loadOp(VK10.VK_ATTACHMENT_LOAD_OP_CLEAR)
                    .storeOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .stencilLoadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .stencilStoreOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED)
                    .finalLayout(VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);

            VkAttachmentReference.Buffer colorRef = VkAttachmentReference.calloc(1, stack);
            colorRef.get(0)
                    .attachment(0)
                    .layout(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

            VkAttachmentReference depthRef = VkAttachmentReference.calloc(stack)
                    .attachment(1)
                    .layout(VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);

            VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack);
            subpass.get(0)
                    .pipelineBindPoint(VK10.VK_PIPELINE_BIND_POINT_GRAPHICS)
                    .colorAttachmentCount(1)
                    .pColorAttachments(colorRef)
                    .pDepthStencilAttachment(depthRef);

            VkSubpassDependency.Buffer dep = VkSubpassDependency.calloc(1, stack);
            dep.get(0)
                    .srcSubpass(VK10.VK_SUBPASS_EXTERNAL)
                    .dstSubpass(0)
                    .srcStageMask(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
                            | VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT
                            | VK10.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT)
                    .srcAccessMask(0)
                    .dstStageMask(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
                            | VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT
                            | VK10.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT)
                    .dstAccessMask(VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
                            | VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);

            VkRenderPassCreateInfo info = VkRenderPassCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
                    .pAttachments(attachments)
                    .pSubpasses(subpass)
                    .pDependencies(dep);

            LongBuffer pPass = stack.callocLong(1);
            VulkanDevice.check(VK10.vkCreateRenderPass(device.logical(), info, null, pPass),
                    "vkCreateRenderPass");
            handle = pPass.get(0);
            System.out.println("[Death:desktop] Vulkan render pass created "
                    + "(color format=" + colorFormat + " + depth format=" + depthFormat + ")");
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
