package WeTTeA.platform.desktop;

import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;

import java.nio.IntBuffer;

/**
 * Bootstrap Vulkan instance.
 *
 * <p>Stage 1 — smoke-тест: создать {@link VkInstance}, запросить количество
 * доступных Vulkan устройств, вывести в stdout, уничтожить инстанс. Никакого
 * device / swapchain / render-pass на этой стадии нет — это INTEGRATION_MISSING.
 *
 * <p>Зависит от {@link GlfwWindow#init()} вызванного перед {@link #create()},
 * потому что {@link GLFWVulkan#glfwGetRequiredInstanceExtensions()} требует
 * инициализированного GLFW.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class VulkanInstanceBootstrap {

    private static final String APP_NAME    = "Death";
    private static final String ENGINE_NAME = "WeTTeA";

    private VkInstance instance;

    public VkInstance create() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkApplicationInfo appInfo = VkApplicationInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_APPLICATION_INFO)
                    .pApplicationName(stack.UTF8(APP_NAME))
                    .applicationVersion(VK10.VK_MAKE_VERSION(0, 1, 0))
                    .pEngineName(stack.UTF8(ENGINE_NAME))
                    .engineVersion(VK10.VK_MAKE_VERSION(0, 1, 0))
                    .apiVersion(VK10.VK_API_VERSION_1_0);

            PointerBuffer requiredExt = GLFWVulkan.glfwGetRequiredInstanceExtensions();
            if (requiredExt == null) {
                throw new IllegalStateException(
                        "GLFW does not expose required Vulkan extensions (Vulkan loader missing?)");
            }

            VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                    .pApplicationInfo(appInfo)
                    .ppEnabledExtensionNames(requiredExt);

            PointerBuffer pInstance = stack.callocPointer(1);
            int err = VK10.vkCreateInstance(createInfo, null, pInstance);
            if (err != VK10.VK_SUCCESS) {
                throw new IllegalStateException("vkCreateInstance failed, code=" + err);
            }
            instance = new VkInstance(pInstance.get(0), createInfo);

            IntBuffer pCount = stack.callocInt(1);
            VK10.vkEnumeratePhysicalDevices(instance, pCount, null);
            System.out.println("[Death:desktop] Vulkan instance created. "
                    + "Physical devices visible: " + pCount.get(0));

            return instance;
        }
    }

    public VkInstance instance() {
        return instance;
    }

    public void dispose() {
        if (instance != null) {
            VK10.vkDestroyInstance(instance, null);
            instance = null;
        }
    }
}
