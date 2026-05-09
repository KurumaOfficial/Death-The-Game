package WeTTeA.platform.desktop;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;

import java.nio.LongBuffer;

/**
 * Stage 3.1 — общая инфраструктура descriptor set'ов под текстуры.
 *
 * <p>Хранит:
 * <ul>
 *   <li>{@code VkDescriptorSetLayout} с одним binding'ом на
 *       {@code COMBINED_IMAGE_SAMPLER} в fragment stage'е (binding=0).
 *       Этот же layout используется в {@link VulkanPipeline} как часть
 *       pipeline layout'а — иначе descriptor set нельзя бы было привязать.</li>
 *   <li>{@code VkDescriptorPool} с capacity = {@code maxSets} — сколько
 *       одновременных текстурных слотов мы можем создать. На stage 3.1 = 4
 *       (одна реальная checkerboard-текстура + запас под скорые
 *       3.x текстуры без переаллокации пула).</li>
 * </ul>
 *
 * <p><b>Зачем это вынесено отдельно от {@link VulkanTexture}.</b>
 * Layout + pool — общие: одна layout-структура задаёт «как выглядит
 * descriptor set», и тот же layout должен быть прописан в pipeline'е.
 * Если бы каждый VulkanTexture создавал свой layout, pipeline не мог бы
 * быть совместим со всеми текстурами одновременно. Pool отделён от layout'а
 * по-другой причине: pool — это аллокатор descriptor set'ов, layout —
 * это template; их жизни не обязаны совпадать (pool можно reset'ить, не
 * пересоздавая layout). На 3.1 они умирают вместе при dispose рендерера.
 *
 * <p>На 3.x layout будет расширен под material-системы (binding 0 =
 * albedo sampler, binding 1 = normal sampler, binding 2 = uniform под
 * material params, и т.д.); pool вырастет под N material instances.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class VulkanTextureDescriptors {

    /** Binding единственного combined image sampler'а в layout'е. */
    public static final int BINDING_TEXTURE = 0;

    private final VulkanDevice device;
    private long descriptorSetLayout = VK10.VK_NULL_HANDLE;
    private long descriptorPool      = VK10.VK_NULL_HANDLE;
    private final int maxSets;

    public VulkanTextureDescriptors(VulkanDevice device, int maxSets) {
        if (maxSets <= 0) {
            throw new IllegalArgumentException("maxSets должен быть > 0, got " + maxSets);
        }
        this.device  = device;
        this.maxSets = maxSets;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            // 1) Descriptor set layout: 1× COMBINED_IMAGE_SAMPLER в fragment stage'е.
            VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(1, stack);
            bindings.get(0)
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
                    "vkCreateDescriptorSetLayout(texture)");
            descriptorSetLayout = pLayout.get(0);

            // 2) Descriptor pool: maxSets descriptor set'ов, каждый имеет
            //    1 COMBINED_IMAGE_SAMPLER → итого maxSets sampler'ов в пуле.
            VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(1, stack);
            poolSizes.get(0)
                    .type(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(maxSets);

            VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                    .pPoolSizes(poolSizes)
                    .maxSets(maxSets);
            LongBuffer pPool = stack.callocLong(1);
            VulkanDevice.check(VK10.vkCreateDescriptorPool(
                            device.logical(), poolInfo, null, pPool),
                    "vkCreateDescriptorPool(texture)");
            descriptorPool = pPool.get(0);

            System.out.println("[Death:desktop] Vulkan texture descriptors ready "
                    + "(layout binding=0 COMBINED_IMAGE_SAMPLER fragment, pool maxSets=" + maxSets + ")");
        }
    }

    public long descriptorSetLayout() { return descriptorSetLayout; }
    public long descriptorPool()      { return descriptorPool; }
    public int  maxSets()             { return maxSets; }

    public void dispose() {
        if (device.logical() == null) {
            descriptorPool      = VK10.VK_NULL_HANDLE;
            descriptorSetLayout = VK10.VK_NULL_HANDLE;
            return;
        }
        if (descriptorPool != VK10.VK_NULL_HANDLE) {
            // Уничтожение pool'а автоматически освобождает все аллоцированные
            // из него descriptor sets — поэтому VulkanTexture.descriptorSet
            // не нужно явно free'ить.
            VK10.vkDestroyDescriptorPool(device.logical(), descriptorPool, null);
            descriptorPool = VK10.VK_NULL_HANDLE;
        }
        if (descriptorSetLayout != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyDescriptorSetLayout(device.logical(), descriptorSetLayout, null);
            descriptorSetLayout = VK10.VK_NULL_HANDLE;
        }
    }
}
