package WeTTeA.platform.desktop;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;

import java.nio.LongBuffer;

/**
 * Stage 2.1a — пул и набор command buffer'ов для frames-in-flight.
 *
 * <p>Создаёт один {@code VkCommandPool} (флаг
 * {@code RESET_COMMAND_BUFFER_BIT}) на graphics queue family и аллоцирует
 * {@code count} primary command buffer'ов из него (где {@code count} =
 * {@code MAX_FRAMES_IN_FLIGHT}). На каждом кадре буфер ресетается через
 * {@code vkResetCommandBuffer} и записывается заново.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class VulkanCommandBuffers {

    private final VulkanDevice device;
    private long pool = VK10.VK_NULL_HANDLE;
    private VkCommandBuffer[] buffers = new VkCommandBuffer[0];

    public VulkanCommandBuffers(VulkanDevice device, int count) {
        this.device = device;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandPoolCreateInfo poolInfo = VkCommandPoolCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
                    .flags(VK10.VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
                    .queueFamilyIndex(device.graphicsFamily());
            LongBuffer pPool = stack.callocLong(1);
            VulkanDevice.check(VK10.vkCreateCommandPool(device.logical(), poolInfo, null, pPool),
                    "vkCreateCommandPool");
            pool = pPool.get(0);

            VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                    .commandPool(pool)
                    .level(VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(count);
            PointerBuffer pBuffers = stack.callocPointer(count);
            VulkanDevice.check(VK10.vkAllocateCommandBuffers(device.logical(), allocInfo, pBuffers),
                    "vkAllocateCommandBuffers");
            buffers = new VkCommandBuffer[count];
            for (int i = 0; i < count; i++) {
                buffers[i] = new VkCommandBuffer(pBuffers.get(i), device.logical());
            }
            System.out.println("[Death:desktop] Vulkan command pool + buffers ready (count=" + count + ")");
        }
    }

    public VkCommandBuffer[] buffers()      { return buffers; }
    public VkCommandBuffer   buffer(int i)  { return buffers[i]; }
    public long              pool()         { return pool; }

    public void dispose() {
        if (device.logical() == null) return;
        if (pool != VK10.VK_NULL_HANDLE) {
            // Уничтожение pool'а автоматически освобождает все command buffers.
            VK10.vkDestroyCommandPool(device.logical(), pool, null);
            pool = VK10.VK_NULL_HANDLE;
            buffers = new VkCommandBuffer[0];
        }
    }
}
