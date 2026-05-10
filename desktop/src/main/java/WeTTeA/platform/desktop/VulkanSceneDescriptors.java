package WeTTeA.platform.desktop;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import java.nio.LongBuffer;

/**
 * Stage E.1 — descriptor set layout + pool под рендер сцены.
 *
 * <p>Заменяет {@code VulkanTextureDescriptors} (stage 3.1) на расширенный
 * layout, поддерживающий два binding'а в одном descriptor set'е:
 *
 * <ul>
 *   <li><b>binding=0</b>: {@code UNIFORM_BUFFER} в {@code VK_SHADER_STAGE_VERTEX_BIT}.
 *       Содержит per-frame данные камеры (model + viewProj + lightDir;
 *       см. {@link VulkanUniformBuffer}).</li>
 *   <li><b>binding=1</b>: {@code COMBINED_IMAGE_SAMPLER} в
 *       {@code VK_SHADER_STAGE_FRAGMENT_BIT}. Содержит albedo текстуру
 *       (см. {@link VulkanTexture}).</li>
 * </ul>
 *
 * <p>Layout shared между всеми frame slot'ами; pool вмещает
 * {@code maxSets} descriptor set'ов (на 0.1.0 = MAX_FRAMES_IN_FLIGHT).
 * Каждый descriptor set «принадлежит» одному frame slot'у и хранит
 * пару (UBO этого slot'а, общая текстура).
 *
 * <p><b>Зачем 1 set с 2 binding'ами, а не 2 set'а.</b> На stage E.1
 * текстура одна и переиспользуется всеми frame slot'ами, так что
 * множить на отдельный set нет смысла. Когда на E.x появятся material
 * variations и instancing — frame data вынесется в {@code set=0}, а
 * material data в {@code set=1}; шаблон расширим, не ломая вызывающие.
 *
 * <p>Идемпотентный {@link #dispose()}: pool → layout. Уничтожение
 * pool'а автоматически освобождает все аллоцированные из него descriptor
 * set'ы — поэтому отдельных {@code vkFreeDescriptorSets} не делается.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class VulkanSceneDescriptors {

    /** Binding UBO в layout'е (vertex stage). */
    public static final int BINDING_UBO     = 0;

    /** Binding combined image sampler'а в layout'е (fragment stage). */
    public static final int BINDING_TEXTURE = 1;

    private final VulkanDevice device;
    private final int maxSets;
    private long descriptorSetLayout = VK10.VK_NULL_HANDLE;
    private long descriptorPool      = VK10.VK_NULL_HANDLE;

    public VulkanSceneDescriptors(VulkanDevice device, int maxSets) {
        if (maxSets <= 0) {
            throw new IllegalArgumentException("maxSets должен быть > 0, got " + maxSets);
        }
        this.device  = device;
        this.maxSets = maxSets;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(2, stack);
            bindings.get(0)
                    .binding(BINDING_UBO)
                    .descriptorType(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(VK10.VK_SHADER_STAGE_VERTEX_BIT
                            | VK10.VK_SHADER_STAGE_FRAGMENT_BIT)
                    .pImmutableSamplers(null);
            bindings.get(1)
                    .binding(BINDING_TEXTURE)
                    .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .stageFlags(VK10.VK_SHADER_STAGE_FRAGMENT_BIT)
                    .pImmutableSamplers(null);

            VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                    .pBindings(bindings);
            LongBuffer pLayout = stack.callocLong(1);
            VulkanDevice.check(VK10.vkCreateDescriptorSetLayout(
                            device.logical(), layoutInfo, null, pLayout),
                    "vkCreateDescriptorSetLayout(scene)");
            descriptorSetLayout = pLayout.get(0);

            VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(2, stack);
            poolSizes.get(0)
                    .type(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .descriptorCount(maxSets);
            poolSizes.get(1)
                    .type(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(maxSets);

            VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                    .pPoolSizes(poolSizes)
                    .maxSets(maxSets);
            LongBuffer pPool = stack.callocLong(1);
            VulkanDevice.check(VK10.vkCreateDescriptorPool(
                            device.logical(), poolInfo, null, pPool),
                    "vkCreateDescriptorPool(scene)");
            descriptorPool = pPool.get(0);

            System.out.println("[Death:desktop] Vulkan scene descriptors ready "
                    + "(layout: binding=0 UBO vertex+fragment, binding=1 sampler fragment; pool maxSets=" + maxSets + ")");
        }
    }

    /**
     * Аллоцирует {@code count} descriptor set'ов из pool'а. На stage E.1
     * вызывается один раз при init'е VulkanRenderer'а с
     * {@code count = MAX_FRAMES_IN_FLIGHT}.
     *
     * @return массив длиной {@code count} с handle'ами descriptor set'ов
     */
    public long[] allocateSets(int count) {
        if (count <= 0 || count > maxSets) {
            throw new IllegalArgumentException("count должен быть в [1.." + maxSets + "], got " + count);
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer pLayouts = stack.callocLong(count);
            for (int i = 0; i < count; i++) pLayouts.put(i, descriptorSetLayout);

            VkDescriptorSetAllocateInfo allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                    .descriptorPool(descriptorPool)
                    .pSetLayouts(pLayouts);

            LongBuffer pSets = stack.callocLong(count);
            VulkanDevice.check(VK10.vkAllocateDescriptorSets(device.logical(), allocInfo, pSets),
                    "vkAllocateDescriptorSets(scene, " + count + ")");

            long[] sets = new long[count];
            for (int i = 0; i < count; i++) sets[i] = pSets.get(i);
            return sets;
        }
    }

    /**
     * Записывает в descriptor set оба binding'а: UBO по {@code binding=0}
     * и combined image sampler по {@code binding=1}.
     *
     * @param set        descriptor set, в который пишем
     * @param uboHandle  {@code VkBuffer} UBO для binding=0
     * @param uboSize    размер в байтах (точно столько, сколько лежит в UBO)
     * @param imageView  {@code VkImageView} для binding=1
     * @param sampler    {@code VkSampler} для binding=1
     */
    public void writeFrameSet(long set, long uboHandle, long uboSize, long imageView, long sampler) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorBufferInfo.Buffer bufferInfo = VkDescriptorBufferInfo.calloc(1, stack);
            bufferInfo.get(0)
                    .buffer(uboHandle)
                    .offset(0)
                    .range(uboSize);

            VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.calloc(1, stack);
            imageInfo.get(0)
                    .sampler(sampler)
                    .imageView(imageView)
                    .imageLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(2, stack);
            writes.get(0)
                    .sType(VK10.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(set)
                    .dstBinding(BINDING_UBO)
                    .dstArrayElement(0)
                    .descriptorCount(1)
                    .descriptorType(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .pBufferInfo(bufferInfo);
            writes.get(1)
                    .sType(VK10.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(set)
                    .dstBinding(BINDING_TEXTURE)
                    .dstArrayElement(0)
                    .descriptorCount(1)
                    .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .pImageInfo(imageInfo);
            VK10.vkUpdateDescriptorSets(device.logical(), writes, null);
        }
    }

    public long descriptorSetLayout() { return descriptorSetLayout; }
    public int  maxSets()             { return maxSets; }

    public void dispose() {
        if (device.logical() == null) {
            descriptorPool      = VK10.VK_NULL_HANDLE;
            descriptorSetLayout = VK10.VK_NULL_HANDLE;
            return;
        }
        if (descriptorPool != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyDescriptorPool(device.logical(), descriptorPool, null);
            descriptorPool = VK10.VK_NULL_HANDLE;
        }
        if (descriptorSetLayout != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyDescriptorSetLayout(device.logical(), descriptorSetLayout, null);
            descriptorSetLayout = VK10.VK_NULL_HANDLE;
        }
    }
}
