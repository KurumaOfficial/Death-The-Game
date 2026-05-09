package WeTTeA.platform.desktop;

import WeTTeA.api.content.AssetHandle;
import WeTTeA.api.content.ContentLoader;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.Objects;

/**
 * Stage 2.1b — обёртка над {@code VkShaderModule}.
 *
 * <p>Загружает SPIR-V байты из asset pipeline'а ({@link ContentLoader}
 * → {@link AssetHandle}) и создаёт {@code VkShaderModule} через
 * {@code vkCreateShaderModule}. Сам по себе модуль НЕ привязан к стадии
 * pipeline'а (vertex/fragment/compute) — стадия указывается уже при сборке
 * {@link VulkanPipeline} в {@code VkPipelineShaderStageCreateInfo.stage}.
 *
 * <p><b>Layout SPIR-V в памяти.</b> Vulkan ждёт указатель на 32-битные
 * слова ({@code uint32_t*}); LWJGL биндинг {@link VkShaderModuleCreateInfo}
 * принимает {@link ByteBuffer} с автоматическим делением длины на 4 для
 * {@code codeSize} (codeSize в байтах). Buffer должен быть direct и иметь
 * native byte order — {@link ContentLoader#readSync} как раз возвращает
 * direct {@link ByteBuffer}.
 *
 * <p><b>Жизненный цикл.</b> Шейдеры на 2.1b создаются один раз при
 * инициализации рендерера и переиспользуются всеми pipeline'ами. После
 * сборки pipeline'ов модули могут быть уничтожены (Vulkan копирует SPIR-V
 * во внутреннюю структуру pipeline'а), но 2.1b держит их до dispose
 * рендерера — это упрощает hot reload на 3.x и стоит ~копейки памяти.
 *
 * <p><b>Идемпотентность.</b> {@link #dispose()} безопасен при повторном
 * вызове и при {@code device == null} (порядок shutdown'а: pipeline →
 * shader modules → device).
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class VulkanShaderModule {

    private final VulkanDevice device;
    private final String debugName;
    private long handle = VK10.VK_NULL_HANDLE;

    public VulkanShaderModule(VulkanDevice device, ContentLoader loader,
                              AssetHandle handle, String debugName) {
        this.device    = Objects.requireNonNull(device, "device");
        this.debugName = Objects.requireNonNull(debugName, "debugName");
        Objects.requireNonNull(loader, "loader");
        Objects.requireNonNull(handle, "handle");

        ByteBuffer spirv = loader.readSync(handle);
        // SPIR-V магическое число: 0x07230203 (little-endian = 03 02 23 07).
        // Если буфер не SPIR-V — fail fast с понятным сообщением.
        if (spirv.remaining() < 4) {
            throw new IllegalArgumentException("SPIR-V buffer слишком короткий: "
                    + spirv.remaining() + " байт (id=" + handle.id() + ")");
        }
        if ((spirv.remaining() & 3) != 0) {
            throw new IllegalArgumentException("SPIR-V buffer длина не кратна 4: "
                    + spirv.remaining() + " байт (id=" + handle.id() + ")");
        }
        // SPIR-V binary всегда little-endian (Vulkan spec §A. SPIR-V Limits / SPIR-V spec §2.3).
        // Читаем magic явно по байтам, не зависим от ByteBuffer.order() (default = BIG_ENDIAN
        // у allocateDirect — был бы баг).
        int p = spirv.position();
        int magic = (spirv.get(p) & 0xff)
                | ((spirv.get(p + 1) & 0xff) << 8)
                | ((spirv.get(p + 2) & 0xff) << 16)
                | ((spirv.get(p + 3) & 0xff) << 24);
        if (magic != 0x07230203) {
            throw new IllegalArgumentException("SPIR-V buffer имеет неверное magic: 0x"
                    + Integer.toHexString(magic) + " (ожидалось 0x07230203, id=" + handle.id() + ")");
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkShaderModuleCreateInfo info = VkShaderModuleCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
                    .pCode(spirv);
            LongBuffer pModule = stack.callocLong(1);
            VulkanDevice.check(VK10.vkCreateShaderModule(device.logical(), info, null, pModule),
                    "vkCreateShaderModule(" + debugName + ")");
            this.handle = pModule.get(0);
        }
        System.out.println("[Death:desktop] Vulkan shader module created: " + debugName
                + " (asset=" + handle.id() + ", size=" + spirv.capacity() + " bytes)");
    }

    public long   handle()    { return handle; }
    public String debugName() { return debugName; }

    public void dispose() {
        if (handle == VK10.VK_NULL_HANDLE) return;
        if (device.logical() == null) {
            handle = VK10.VK_NULL_HANDLE;
            return;
        }
        VK10.vkDestroyShaderModule(device.logical(), handle, null);
        handle = VK10.VK_NULL_HANDLE;
    }
}
