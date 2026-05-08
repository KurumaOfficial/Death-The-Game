package WeTTeA.api.lifecycle;

/**
 * Объект, владеющий ресурсами, требующими явного освобождения.
 *
 * <p>Применимо в первую очередь для:
 * <ul>
 *   <li>Vulkan handles (VkBuffer, VkImage, VkPipeline, VkDescriptorSet, ...);</li>
 *   <li>OpenAL источников и буферов;</li>
 *   <li>native memory, выделенной через LWJGL {@code MemoryUtil};</li>
 *   <li>JNI handle'ов на Rust-стороне (структуры, owned Vec'ы, BVH-деревья).</li>
 * </ul>
 *
 * <p><b>Идемпотентность.</b> {@link #dispose()} обязан корректно отрабатывать
 * повторный вызов (no-op после первого освобождения).
 *
 * <p><b>Thread safety.</b> Vulkan command buffer recording привязан к рендер-потоку.
 * Освобождение Vulkan ресурсов должно происходить либо после {@code vkDeviceWaitIdle},
 * либо через delayed-delete очередь.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public interface Disposable extends AutoCloseable {

    /**
     * Освободить связанные ресурсы. Идемпотентен.
     */
    void dispose();

    @Override
    default void close() {
        dispose();
    }
}
