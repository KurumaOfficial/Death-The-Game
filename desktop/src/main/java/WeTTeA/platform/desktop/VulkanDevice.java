package WeTTeA.platform.desktop;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSurface;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkQueueFamilyProperties;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Stage 2.1a — выбор физического устройства Vulkan и создание логического
 * устройства с двумя очередями: graphics + presentation.
 *
 * <p>Алгоритм выбора {@link VkPhysicalDevice}:
 * <ol>
 *   <li>Перебираются все устройства, видимые инстансу;</li>
 *   <li>Для каждого ищется queue family с {@code VK_QUEUE_GRAPHICS_BIT};</li>
 *   <li>Для каждого ищется queue family с
 *       {@link KHRSurface#vkGetPhysicalDeviceSurfaceSupportKHR} == true для
 *       нашего surface;</li>
 *   <li>Если оба индекса найдены — устройство принимается; первое подходящее
 *       устройство выбирается. Дискретные/интегрированные предпочтения
 *       будут добавлены в 2.1b/2.1c (пока берём первый совпавший — на
 *       одной типичной системе таких 1).</li>
 * </ol>
 *
 * <p>Логическое устройство создаётся с включённым расширением
 * {@code VK_KHR_swapchain} (без него swapchain не построить) и с пустым
 * {@link VkPhysicalDeviceFeatures} — никаких optional features 2.1a не
 * требует. Также не активируются validation layers — для CI smoke они
 * лишние; в dev-режиме они включаются через {@code VK_INSTANCE_LAYERS}
 * env-var на стороне loader'а.
 *
 * <p>Жизненный цикл: {@link #pick(VkInstance, long)} → {@link #createLogical()}
 * → использование {@link #logical()} / {@link #graphicsQueue()} /
 * {@link #presentQueue()} → {@link #dispose()}.
 *
 * <p>Не потокобезопасен; все вызовы на render thread.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class VulkanDevice {

    private final VkInstance instance;
    private final long surface;

    private VkPhysicalDevice physical;
    private VkDevice logical;
    private int graphicsFamily = -1;
    private int presentFamily  = -1;
    private VkQueue graphicsQueue;
    private VkQueue presentQueue;
    private String deviceName = "?";

    public VulkanDevice(VkInstance instance, long surface) {
        if (instance == null) throw new IllegalArgumentException("instance == null");
        if (surface == VK10.VK_NULL_HANDLE) {
            throw new IllegalArgumentException("surface handle is VK_NULL_HANDLE");
        }
        this.instance = instance;
        this.surface  = surface;
    }

    /**
     * Перебирает физические устройства и находит первое, у которого есть
     * graphics queue family и present queue family для нашего {@code surface}.
     *
     * @throws IllegalStateException если ни одно устройство не подошло
     */
    public void pick() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer pCount = stack.callocInt(1);
            check(VK10.vkEnumeratePhysicalDevices(instance, pCount, null), "vkEnumeratePhysicalDevices(count)");
            int count = pCount.get(0);
            if (count == 0) {
                throw new IllegalStateException("Vulkan loader не видит ни одного физического устройства");
            }
            PointerBuffer pDevices = stack.callocPointer(count);
            check(VK10.vkEnumeratePhysicalDevices(instance, pCount, pDevices), "vkEnumeratePhysicalDevices(devices)");

            for (int i = 0; i < count; i++) {
                VkPhysicalDevice candidate = new VkPhysicalDevice(pDevices.get(i), instance);
                int[] families = findQueueFamilies(candidate);
                if (families == null) continue;

                physical       = candidate;
                graphicsFamily = families[0];
                presentFamily  = families[1];

                VkPhysicalDeviceProperties props = VkPhysicalDeviceProperties.calloc(stack);
                VK10.vkGetPhysicalDeviceProperties(physical, props);
                deviceName = props.deviceNameString();
                System.out.println("[Death:desktop] Vulkan device picked: " + deviceName
                        + " (gfx=" + graphicsFamily + ", present=" + presentFamily + ")");
                return;
            }
            throw new IllegalStateException(
                    "не нашли физическое устройство с graphics + present queue для surface=" + surface);
        }
    }

    /**
     * Создаёт логическое {@link VkDevice} и достаёт два {@link VkQueue}:
     * graphics и present. Если queue family совпадает — оба handle указывают
     * на одну и ту же очередь (это нормально и часто встречается на
     * lavapipe/iGPU).
     */
    public void createLogical() {
        if (physical == null) throw new IllegalStateException("call pick() first");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer priorities = stack.floats(1.0f);

            int families = (graphicsFamily == presentFamily) ? 1 : 2;
            VkDeviceQueueCreateInfo.Buffer qInfo = VkDeviceQueueCreateInfo.calloc(families, stack);
            qInfo.get(0)
                    .sType(VK10.VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                    .queueFamilyIndex(graphicsFamily)
                    .pQueuePriorities(priorities);
            if (families == 2) {
                qInfo.get(1)
                        .sType(VK10.VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                        .queueFamilyIndex(presentFamily)
                        .pQueuePriorities(priorities);
            }

            VkPhysicalDeviceFeatures features = VkPhysicalDeviceFeatures.calloc(stack);
            // оставляем все features в false — 2.1a fence/semaphore/swapchain не требуют ничего особого

            PointerBuffer enabledExt = stack.callocPointer(1);
            ByteBuffer extSwapchain = stack.UTF8(KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME);
            enabledExt.put(0, extSwapchain);

            VkDeviceCreateInfo info = VkDeviceCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
                    .pQueueCreateInfos(qInfo)
                    .ppEnabledExtensionNames(enabledExt)
                    .pEnabledFeatures(features);

            PointerBuffer pDevice = stack.callocPointer(1);
            check(VK10.vkCreateDevice(physical, info, null, pDevice), "vkCreateDevice");
            logical = new VkDevice(pDevice.get(0), physical, info);

            PointerBuffer pQueue = stack.callocPointer(1);
            VK10.vkGetDeviceQueue(logical, graphicsFamily, 0, pQueue);
            graphicsQueue = new VkQueue(pQueue.get(0), logical);

            VK10.vkGetDeviceQueue(logical, presentFamily, 0, pQueue);
            presentQueue = new VkQueue(pQueue.get(0), logical);

            System.out.println("[Death:desktop] Vulkan logical device created (queues: gfx + present"
                    + (graphicsFamily == presentFamily ? ", shared" : ", distinct") + ")");
        }
    }

    /** @return {@code [graphicsFamily, presentFamily]} или {@code null}, если не нашли. */
    private int[] findQueueFamilies(VkPhysicalDevice device) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer pCount = stack.callocInt(1);
            VK10.vkGetPhysicalDeviceQueueFamilyProperties(device, pCount, null);
            int n = pCount.get(0);
            if (n == 0) return null;
            VkQueueFamilyProperties.Buffer props = VkQueueFamilyProperties.calloc(n, stack);
            VK10.vkGetPhysicalDeviceQueueFamilyProperties(device, pCount, props);

            int gfx = -1;
            int present = -1;
            IntBuffer supportFlag = stack.callocInt(1);
            for (int i = 0; i < n; i++) {
                if (gfx == -1 && (props.get(i).queueFlags() & VK10.VK_QUEUE_GRAPHICS_BIT) != 0) {
                    gfx = i;
                }
                if (present == -1) {
                    KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR(device, i, surface, supportFlag);
                    if (supportFlag.get(0) == VK10.VK_TRUE) present = i;
                }
                if (gfx != -1 && present != -1) break;
            }
            if (gfx == -1 || present == -1) return null;
            return new int[]{gfx, present};
        }
    }

    public VkPhysicalDevice physical()    { return physical; }
    public VkDevice         logical()     { return logical; }
    public int              graphicsFamily() { return graphicsFamily; }
    public int              presentFamily()  { return presentFamily; }
    public VkQueue          graphicsQueue()  { return graphicsQueue; }
    public VkQueue          presentQueue()   { return presentQueue; }
    public String           deviceName()     { return deviceName; }

    public void dispose() {
        if (logical != null) {
            VK10.vkDestroyDevice(logical, null);
            logical = null;
            graphicsQueue = null;
            presentQueue  = null;
        }
        physical = null;
    }

    static void check(int err, String op) {
        if (err != VK10.VK_SUCCESS) {
            throw new IllegalStateException(op + " failed, VkResult=" + err);
        }
    }
}
