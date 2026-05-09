package WeTTeA.platform.desktop;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;

import java.nio.LongBuffer;

/**
 * Stage 3.1 — обёртка над {@code VkBuffer} + {@code VkDeviceMemory}.
 *
 * <p>Универсальный кирпичик для любых буферов: staging (HOST_VISIBLE,
 * один shot upload), vertex / index / uniform / SSBO (DEVICE_LOCAL,
 * долгоживущие). На stage 3.1 используется ТОЛЬКО как staging для текстур;
 * на 2.1c/3.x этот же класс понадобится под vertex buffer'ы (когда
 * gl_VertexIndex заменим на реальную геометрию) и uniform-буферы под MVP.
 *
 * <p><b>Layout памяти.</b>
 * <ol>
 *   <li>{@code vkCreateBuffer} с {@code usage} (например, {@code TRANSFER_SRC}
 *       для staging) и {@code sharingMode = EXCLUSIVE} (один queue family —
 *       graphics на 2.1b).</li>
 *   <li>{@code vkGetBufferMemoryRequirements} → нужный размер + alignment +
 *       memoryTypeBits.</li>
 *   <li>{@link #pickMemoryType(VulkanDevice, int, int)} ищет первый memory
 *       type, который и в bitmask'е, и удовлетворяет {@code requiredFlags}
 *       (например, {@code HOST_VISIBLE | HOST_COHERENT} для staging).</li>
 *   <li>{@code vkAllocateMemory} + {@code vkBindBufferMemory}.</li>
 * </ol>
 *
 * <p><b>HOST_COHERENT</b> важен для staging: после {@link #write(java.nio.ByteBuffer)}
 * не нужен явный {@code vkFlushMappedMemoryRanges} — драйвер видит изменения
 * сразу при следующей GPU-команде. Альтернатива (без COHERENT) требует
 * вручную flush'ить, что добавляет код и шанс ошибки.
 *
 * <p><b>Idempotent dispose</b>: безопасен при повторном вызове и при
 * {@code device.logical() == null} (порядок shutdown'а: ресурсы → device).
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class VulkanBuffer {

    private final VulkanDevice device;
    private final long sizeBytes;
    private long buffer = VK10.VK_NULL_HANDLE;
    private long memory = VK10.VK_NULL_HANDLE;

    /**
     * Создаёт {@code VkBuffer} нужного размера и привязывает свежий
     * {@code VkDeviceMemory}.
     *
     * @param device              Vulkan device
     * @param sizeBytes           размер буфера в байтах
     * @param usage               {@code VK_BUFFER_USAGE_*} bitmask
     *                            (например, {@code TRANSFER_SRC_BIT} для staging)
     * @param requiredMemoryFlags {@code VK_MEMORY_PROPERTY_*} (например,
     *                            {@code HOST_VISIBLE | HOST_COHERENT})
     */
    public VulkanBuffer(VulkanDevice device, long sizeBytes, int usage, int requiredMemoryFlags) {
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("VulkanBuffer.sizeBytes должен быть > 0, got " + sizeBytes);
        }
        this.device    = device;
        this.sizeBytes = sizeBytes;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo bufInfo = VkBufferCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                    .size(sizeBytes)
                    .usage(usage)
                    .sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);
            LongBuffer pBuffer = stack.callocLong(1);
            VulkanDevice.check(VK10.vkCreateBuffer(device.logical(), bufInfo, null, pBuffer),
                    "vkCreateBuffer");
            buffer = pBuffer.get(0);

            VkMemoryRequirements memReq = VkMemoryRequirements.calloc(stack);
            VK10.vkGetBufferMemoryRequirements(device.logical(), buffer, memReq);

            int memTypeIndex = pickMemoryType(device, memReq.memoryTypeBits(), requiredMemoryFlags);
            VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                    .allocationSize(memReq.size())
                    .memoryTypeIndex(memTypeIndex);
            LongBuffer pMemory = stack.callocLong(1);
            VulkanDevice.check(VK10.vkAllocateMemory(device.logical(), allocInfo, null, pMemory),
                    "vkAllocateMemory(buffer)");
            memory = pMemory.get(0);

            VulkanDevice.check(VK10.vkBindBufferMemory(device.logical(), buffer, memory, 0),
                    "vkBindBufferMemory");
        }
    }

    /**
     * Записывает байты {@code src} в начало буфера через
     * {@code vkMapMemory} + {@code memCopy} + {@code vkUnmapMemory}.
     *
     * <p>Применимо ТОЛЬКО к буферам, выделенным с {@code HOST_VISIBLE}.
     * Для DEVICE_LOCAL буферов запись идёт через staging + cmd copy.
     *
     * <p>Если буфер аллоцирован с {@code HOST_COHERENT}, дополнительный
     * flush не требуется. Иначе вызывающий обязан выполнить
     * {@code vkFlushMappedMemoryRanges} (на 3.1 не используется, всегда
     * HOST_COHERENT).
     */
    public void write(java.nio.ByteBuffer src) {
        if (src == null) throw new IllegalArgumentException("src == null");
        if (src.remaining() > sizeBytes) {
            throw new IllegalArgumentException("VulkanBuffer.write: src.remaining=" + src.remaining()
                    + " > sizeBytes=" + sizeBytes);
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pData = stack.callocPointer(1);
            VulkanDevice.check(VK10.vkMapMemory(device.logical(), memory, 0, sizeBytes, 0, pData),
                    "vkMapMemory");
            long dst = pData.get(0);
            org.lwjgl.system.MemoryUtil.memCopy(
                    org.lwjgl.system.MemoryUtil.memAddress(src),
                    dst,
                    src.remaining());
            VK10.vkUnmapMemory(device.logical(), memory);
        }
    }

    public long handle()    { return buffer; }
    public long memory()    { return memory; }
    public long sizeBytes() { return sizeBytes; }

    /**
     * Идемпотентное освобождение buffer + memory.
     */
    public void dispose() {
        if (device.logical() == null) {
            buffer = VK10.VK_NULL_HANDLE;
            memory = VK10.VK_NULL_HANDLE;
            return;
        }
        if (buffer != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyBuffer(device.logical(), buffer, null);
            buffer = VK10.VK_NULL_HANDLE;
        }
        if (memory != VK10.VK_NULL_HANDLE) {
            VK10.vkFreeMemory(device.logical(), memory, null);
            memory = VK10.VK_NULL_HANDLE;
        }
    }

    /**
     * Ищет индекс memory type, который удовлетворяет:
     * <ol>
     *   <li>В bit-маске {@code typeFilter} (от
     *       {@link VkMemoryRequirements#memoryTypeBits()}) выставлен
     *       соответствующий бит;</li>
     *   <li>В {@link VkPhysicalDeviceMemoryProperties#memoryTypes}
     *       у этого индекса {@code propertyFlags} включает все биты
     *       {@code requiredFlags}.</li>
     * </ol>
     *
     * <p>Возвращает первое совпадение; на типичных GPU подходящих типов
     * 1-2, выбор первого корректен.
     *
     * @throws IllegalStateException если ни один memory type не подошёл
     */
    static int pickMemoryType(VulkanDevice device, int typeFilter, int requiredFlags) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceMemoryProperties memProps = VkPhysicalDeviceMemoryProperties.calloc(stack);
            VK10.vkGetPhysicalDeviceMemoryProperties(device.physical(), memProps);
            int n = memProps.memoryTypeCount();
            for (int i = 0; i < n; i++) {
                if ((typeFilter & (1 << i)) == 0) continue;
                int flags = memProps.memoryTypes(i).propertyFlags();
                if ((flags & requiredFlags) == requiredFlags) {
                    return i;
                }
            }
            throw new IllegalStateException("Не найден memory type, удовлетворяющий "
                    + "typeFilter=0x" + Integer.toHexString(typeFilter)
                    + " requiredFlags=0x" + Integer.toHexString(requiredFlags));
        }
    }
}
