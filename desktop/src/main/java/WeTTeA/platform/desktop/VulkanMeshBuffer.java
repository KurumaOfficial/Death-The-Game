package WeTTeA.platform.desktop;

import WeTTeA.core.render.CubeMeshFactory;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkSubmitInfo;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Stage E.1 — DEVICE_LOCAL vertex + index buffer для одного mesh'а
 * (на 0.1.0 — единичного куба из {@link CubeMeshFactory}).
 *
 * <p>Pipeline загрузки (один раз в {@link #fromCubeMesh}):
 * <ol>
 *   <li>{@link CubeMeshFactory#generate} → CPU-side vertices + indices.</li>
 *   <li>Два HOST_VISIBLE staging буфера ({@link VulkanBuffer}) под
 *       vertex / index — копируем туда данные через
 *       {@code MemoryStack.malloc()} + {@link VulkanBuffer#write}.</li>
 *   <li>Два DEVICE_LOCAL буфера с usage =
 *       {@code TRANSFER_DST | VERTEX_BUFFER} и
 *       {@code TRANSFER_DST | INDEX_BUFFER} соответственно.</li>
 *   <li>One-shot command buffer: {@code vkCmdCopyBuffer} staging → device
 *       для обоих, end + submit + {@code vkQueueWaitIdle} (на 0.1.0
 *       это допустимо: mesh грузится при init'е, не в hot path).</li>
 *   <li>Staging-буферы dispose; DEVICE_LOCAL живут до
 *       {@link #dispose()}.</li>
 * </ol>
 *
 * <p><b>Index format.</b> {@code VK_INDEX_TYPE_UINT16} — куб имеет 24
 * вершины, 16-bit индексов хватает с большим запасом, экономит memory
 * traffic пополам по сравнению с UINT32.
 *
 * <p>Идемпотентный {@link #dispose()} в обратном порядке: index → vertex.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class VulkanMeshBuffer {

    private final VulkanDevice device;
    private final int indexCount;
    private final int indexType;

    private VulkanBuffer vertexBuffer;
    private VulkanBuffer indexBuffer;

    private VulkanMeshBuffer(VulkanDevice device, VulkanBuffer vertexBuffer,
                             VulkanBuffer indexBuffer, int indexCount, int indexType) {
        this.device       = device;
        this.vertexBuffer = vertexBuffer;
        this.indexBuffer  = indexBuffer;
        this.indexCount   = indexCount;
        this.indexType    = indexType;
    }

    /**
     * Загружает куб из {@link CubeMeshFactory#generate} в DEVICE_LOCAL
     * vertex + index buffer'ы.
     */
    public static VulkanMeshBuffer fromCubeMesh(VulkanDevice device, VulkanCommandBuffers cmdBuffers) {
        CubeMeshFactory.CubeMesh mesh = CubeMeshFactory.generate();
        return upload(device, cmdBuffers,
                mesh.vertices(),
                mesh.indices(),
                mesh.indexCount());
    }

    private static VulkanMeshBuffer upload(VulkanDevice device, VulkanCommandBuffers cmdBuffers,
                                           float[] vertices, short[] indices, int indexCount) {
        long vertexSize = (long) vertices.length * Float.BYTES;
        long indexSize  = (long) indices.length  * Short.BYTES;

        VulkanBuffer vertexStaging = new VulkanBuffer(device, vertexSize,
                VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT
                        | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        VulkanBuffer indexStaging = new VulkanBuffer(device, indexSize,
                VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT
                        | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);

        VulkanBuffer vertex = null;
        VulkanBuffer index  = null;
        try {
            // CPU → staging.
            ByteBuffer vBuf = ByteBuffer.allocateDirect((int) vertexSize).order(ByteOrder.nativeOrder());
            for (float f : vertices) vBuf.putFloat(f);
            vBuf.flip();
            vertexStaging.write(vBuf);

            ByteBuffer iBuf = ByteBuffer.allocateDirect((int) indexSize).order(ByteOrder.nativeOrder());
            for (short s : indices) iBuf.putShort(s);
            iBuf.flip();
            indexStaging.write(iBuf);

            // Allocate DEVICE_LOCAL targets.
            vertex = new VulkanBuffer(device, vertexSize,
                    VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT
                            | VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
                    VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
            index = new VulkanBuffer(device, indexSize,
                    VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT
                            | VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT,
                    VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);

            // One-shot copy.
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
                        .sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                        .commandPool(cmdBuffers.pool())
                        .level(VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                        .commandBufferCount(1);
                PointerBuffer pCmd = stack.callocPointer(1);
                VulkanDevice.check(VK10.vkAllocateCommandBuffers(device.logical(), allocInfo, pCmd),
                        "vkAllocateCommandBuffers(mesh upload)");
                VkCommandBuffer cmd = new VkCommandBuffer(pCmd.get(0), device.logical());

                VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                        .sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                        .flags(VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
                VulkanDevice.check(VK10.vkBeginCommandBuffer(cmd, beginInfo),
                        "vkBeginCommandBuffer(mesh upload)");

                VkBufferCopy.Buffer vRegion = VkBufferCopy.calloc(1, stack)
                        .srcOffset(0).dstOffset(0).size(vertexSize);
                VK10.vkCmdCopyBuffer(cmd, vertexStaging.handle(), vertex.handle(), vRegion);

                VkBufferCopy.Buffer iRegion = VkBufferCopy.calloc(1, stack)
                        .srcOffset(0).dstOffset(0).size(indexSize);
                VK10.vkCmdCopyBuffer(cmd, indexStaging.handle(), index.handle(), iRegion);

                VulkanDevice.check(VK10.vkEndCommandBuffer(cmd),
                        "vkEndCommandBuffer(mesh upload)");

                VkSubmitInfo submit = VkSubmitInfo.calloc(stack)
                        .sType(VK10.VK_STRUCTURE_TYPE_SUBMIT_INFO)
                        .pCommandBuffers(stack.pointers(cmd));
                VulkanDevice.check(VK10.vkQueueSubmit(device.graphicsQueue(), submit, VK10.VK_NULL_HANDLE),
                        "vkQueueSubmit(mesh upload)");
                VulkanDevice.check(VK10.vkQueueWaitIdle(device.graphicsQueue()),
                        "vkQueueWaitIdle(mesh upload)");

                VK10.vkFreeCommandBuffers(device.logical(), cmdBuffers.pool(), pCmd);
            }

            System.out.println("[Death:desktop] Vulkan mesh uploaded: vertices="
                    + vertices.length / CubeMeshFactory.FLOATS_PER_VERTEX
                    + " indices=" + indexCount
                    + " (vertexBytes=" + vertexSize + " indexBytes=" + indexSize + ")");

            VulkanMeshBuffer result = new VulkanMeshBuffer(device, vertex, index, indexCount,
                    VK10.VK_INDEX_TYPE_UINT16);
            // ownership transferred — disable dispose из catch block'а.
            vertex = null;
            index  = null;
            return result;
        } finally {
            vertexStaging.dispose();
            indexStaging.dispose();
            // Если throw до transfer ownership — освобождаем target buffers.
            if (vertex != null) vertex.dispose();
            if (index  != null) index.dispose();
        }
    }

    public long vertexBufferHandle() { return vertexBuffer.handle(); }
    public long indexBufferHandle()  { return indexBuffer.handle(); }
    public int  indexCount()         { return indexCount; }
    public int  indexType()          { return indexType; }

    public void dispose() {
        if (indexBuffer != null) {
            indexBuffer.dispose();
            indexBuffer = null;
        }
        if (vertexBuffer != null) {
            vertexBuffer.dispose();
            vertexBuffer = null;
        }
    }
}
