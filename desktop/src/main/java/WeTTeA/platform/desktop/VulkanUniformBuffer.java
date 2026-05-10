package WeTTeA.platform.desktop;

import WeTTeA.api.render.Camera;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Stage E.1 — HOST_VISIBLE | HOST_COHERENT uniform buffer для одного
 * frame slot'а.
 *
 * <p>Содержит {@link Layout} std140-структуру:
 * <pre>
 *   offset 0    mat4 model       (64 bytes)
 *   offset 64   mat4 viewProj    (64 bytes)
 *   offset 128  vec3 lightDir    (12 bytes, padded до 16)
 *   offset 140  float _padding   (4 bytes)
 *   total = 144 bytes
 * </pre>
 *
 * <p>HOST_VISIBLE | HOST_COHERENT — пишем напрямую через {@code vkMapMemory},
 * coherent flag убирает необходимость explicit'ного flush'а (драйвер
 * сам гарантирует видимость GPU при следующей submit-команде).
 *
 * <p>Жизненный цикл: один экземпляр на frame-slot
 * (MAX_FRAMES_IN_FLIGHT штук всего), создаётся один раз при init'е
 * рендерера, переживает swapchain recreate.
 *
 * <p>{@link #update(Matrix4fc, Camera, Vector3fc)} переписывает все
 * 144 байта in-place. ByteBuffer переиспользуется (lazy-init'ится).
 *
 * <p>Идемпотентный {@link #dispose()}. На 0.1.0 не использует named
 * mapping (через {@code vkMapMemory} каждое обновление). Для сотен
 * обновлений в секунду это медленно; на E.x перейдём на persistent
 * mapping (mapMemory один раз при init'е).
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class VulkanUniformBuffer {

    /**
     * Размер UBO в байтах: 64 + 64 + 16 (vec3 + pad) = 144.
     */
    public static final int UBO_SIZE_BYTES = 144;

    private static final int OFFSET_MODEL     = 0;
    private static final int OFFSET_VIEWPROJ  = 64;
    private static final int OFFSET_LIGHT_DIR = 128;

    private final VulkanDevice device;
    private final VulkanBuffer buffer;
    private final ByteBuffer scratch;

    public VulkanUniformBuffer(VulkanDevice device) {
        this.device  = device;
        this.buffer  = new VulkanBuffer(device, UBO_SIZE_BYTES,
                VK10.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT
                        | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        this.scratch = ByteBuffer.allocateDirect(UBO_SIZE_BYTES).order(ByteOrder.nativeOrder());
    }

    /**
     * Перезаписать содержимое UBO актуальными матрицами и направлением света.
     *
     * @param model    model matrix (per-frame, на E.1 — вращающийся куб)
     * @param camera   камера, у которой запрошен viewProj
     * @param lightDir мировое направление источника света (unit vector)
     */
    public void update(Matrix4fc model, Camera camera, Vector3fc lightDir) {
        scratch.clear();
        // mat4 в JOML лежит column-major, что совпадает со std140'ом для mat4.
        model.get(OFFSET_MODEL, scratch);
        camera.viewProjectionMatrix().get(OFFSET_VIEWPROJ, scratch);

        scratch.position(OFFSET_LIGHT_DIR);
        scratch.putFloat(lightDir.x());
        scratch.putFloat(lightDir.y());
        scratch.putFloat(lightDir.z());
        scratch.putFloat(0.0f); // padding до 16-байтового слота

        scratch.position(0);
        scratch.limit(UBO_SIZE_BYTES);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pData = stack.callocPointer(1);
            VulkanDevice.check(VK10.vkMapMemory(device.logical(), buffer.memory(), 0, UBO_SIZE_BYTES, 0, pData),
                    "vkMapMemory(ubo)");
            long dst = pData.get(0);
            org.lwjgl.system.MemoryUtil.memCopy(
                    org.lwjgl.system.MemoryUtil.memAddress(scratch),
                    dst,
                    UBO_SIZE_BYTES);
            VK10.vkUnmapMemory(device.logical(), buffer.memory());
        }
    }

    public long handle()    { return buffer.handle(); }
    public long sizeBytes() { return UBO_SIZE_BYTES; }

    public void dispose() {
        buffer.dispose();
    }

    /** Marker-класс под std140-документацию (не используется в коде). */
    public static final class Layout {
        private Layout() {}
    }
}
