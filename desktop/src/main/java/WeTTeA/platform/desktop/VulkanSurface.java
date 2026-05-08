package WeTTeA.platform.desktop;

import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSurface;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkInstance;

import java.nio.LongBuffer;

/**
 * Stage 2.1a — обёртка над {@code VkSurfaceKHR}, созданным GLFW поверх
 * нашего {@link GlfwWindow}.
 *
 * <p>{@code VkSurfaceKHR} принадлежит {@link VkInstance} (а не device),
 * поэтому уничтожается через {@link KHRSurface#vkDestroySurfaceKHR}
 * при шатдауне всего render-стека. Surface должен быть уничтожен ПОСЛЕ
 * swapchain'а — порядок соблюдается в {@link VulkanRenderer#dispose()}.
 *
 * <p>На X11 lavapipe + libvulkan loader выбирают {@code VK_KHR_xlib_surface}
 * автоматически — GLFW сам находит правильное расширение и вызывает
 * соответствующий KHR-API. Нам достаточно
 * {@link GLFWVulkan#glfwCreateWindowSurface}.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class VulkanSurface {

    private final VkInstance instance;
    private long handle = VK10.VK_NULL_HANDLE;

    public VulkanSurface(VkInstance instance, long windowHandle) {
        this.instance = instance;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer pSurface = stack.callocLong(1);
            int err = GLFWVulkan.glfwCreateWindowSurface(instance, windowHandle, null, pSurface);
            if (err != VK10.VK_SUCCESS) {
                throw new IllegalStateException("glfwCreateWindowSurface failed, VkResult=" + err);
            }
            handle = pSurface.get(0);
            System.out.println("[Death:desktop] Vulkan surface created (handle=" + Long.toHexString(handle) + ")");
        }
    }

    public long handle() { return handle; }

    public void dispose() {
        if (handle != VK10.VK_NULL_HANDLE) {
            KHRSurface.vkDestroySurfaceKHR(instance, handle, null);
            handle = VK10.VK_NULL_HANDLE;
        }
    }
}
