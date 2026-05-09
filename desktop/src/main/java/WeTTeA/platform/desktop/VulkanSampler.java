package WeTTeA.platform.desktop;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkSamplerCreateInfo;

import java.nio.LongBuffer;

/**
 * Stage 3.1 — обёртка над {@code VkSampler}.
 *
 * <p>Конфигурация по умолчанию (на 3.1 одна на всю игру):
 * <ul>
 *   <li>{@code magFilter / minFilter = LINEAR} — гладкая билинейная
 *       интерполяция.</li>
 *   <li>{@code addressMode U/V/W = REPEAT} — wrap-around на UV вне [0..1].</li>
 *   <li>{@code anisotropyEnable = false} — на stage 3.1 не нужен;
 *       включится на 3.x под soft-настройками графики (требует
 *       {@code samplerAnisotropy} feature и проверки максимума).</li>
 *   <li>{@code mipmapMode = NEAREST}, {@code mipLodBias = 0},
 *       {@code minLod = 0}, {@code maxLod = 0} — на 3.1 без мипов.</li>
 *   <li>{@code unnormalizedCoordinates = false} — UV в [0..1].</li>
 * </ul>
 *
 * <p>Sampler НЕ привязан к конкретной {@link VulkanImage}: один и тот же
 * sampler может использоваться с десятками текстур через разные descriptor
 * set'ы. Это специально (Vulkan позиция: «sampler — это hardware unit /
 * политика чтения, image — это data»). На stage 3.x сделаем sampler
 * cache по преcetам (LinearRepeat / LinearClamp / NearestRepeat / ...).
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class VulkanSampler {

    private final VulkanDevice device;
    private long handle = VK10.VK_NULL_HANDLE;

    public VulkanSampler(VulkanDevice device) {
        this.device = device;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSamplerCreateInfo info = VkSamplerCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)
                    .magFilter(VK10.VK_FILTER_LINEAR)
                    .minFilter(VK10.VK_FILTER_LINEAR)
                    .addressModeU(VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT)
                    .addressModeV(VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT)
                    .addressModeW(VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT)
                    .anisotropyEnable(false)
                    .maxAnisotropy(1.0f)
                    .borderColor(VK10.VK_BORDER_COLOR_INT_OPAQUE_BLACK)
                    .unnormalizedCoordinates(false)
                    .compareEnable(false)
                    .compareOp(VK10.VK_COMPARE_OP_ALWAYS)
                    .mipmapMode(VK10.VK_SAMPLER_MIPMAP_MODE_NEAREST)
                    .mipLodBias(0.0f)
                    .minLod(0.0f)
                    .maxLod(0.0f);

            LongBuffer pSampler = stack.callocLong(1);
            VulkanDevice.check(VK10.vkCreateSampler(device.logical(), info, null, pSampler),
                    "vkCreateSampler");
            handle = pSampler.get(0);
        }
    }

    public long handle() { return handle; }

    public void dispose() {
        if (handle == VK10.VK_NULL_HANDLE) return;
        if (device.logical() != null) {
            VK10.vkDestroySampler(device.logical(), handle, null);
        }
        handle = VK10.VK_NULL_HANDLE;
    }
}
