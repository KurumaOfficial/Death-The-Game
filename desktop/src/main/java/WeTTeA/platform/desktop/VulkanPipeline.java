package WeTTeA.platform.desktop;

import WeTTeA.core.render.CubeMeshFactory;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState;
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDepthStencilStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;

import java.nio.IntBuffer;
import java.nio.LongBuffer;

/**
 * Stage 2.1b + E.1 — Vulkan graphics pipeline под cube-mesh с
 * перспективной камерой и depth test'ом.
 *
 * <p>На stage 2.1b pipeline собирался под триангл с пустым vertex
 * input'ом и без depth state'а. На stage E.1 он расширен под полноценный
 * 3D-mesh:
 *
 * <ul>
 *   <li><b>Pipeline layout</b> — содержит один descriptor set layout
 *       (set=0) c двумя binding'ами:
 *       binding=0 = UBO в vertex+fragment stage'ах,
 *       binding=1 = combined image sampler во fragment stage'е
 *       (см. {@link VulkanSceneDescriptors}). Push constants нет.</li>
 *   <li><b>Vertex input</b> — один binding (vertex stride
 *       {@value CubeMeshFactory#VERTEX_STRIDE_BYTES} байт), три
 *       attribute'а: location=0 vec3 position (offset 0),
 *       location=1 vec3 normal (offset 12), location=2 vec2 uv
 *       (offset 24). Совпадает с layout'ом из {@link CubeMeshFactory}
 *       и in-параметров cube.vert.glsl.</li>
 *   <li><b>Input assembly</b> — {@code TRIANGLE_LIST}, primitive restart
 *       выкл. Подходит для {@code vkCmdDrawIndexed} с UINT16 индексами.</li>
 *   <li><b>Viewport state</b> — viewportCount=1, scissorCount=1; сами
 *       значения помечены как {@code DYNAMIC_STATE} и устанавливаются
 *       per-frame через {@code vkCmdSetViewport}/{@code vkCmdSetScissor}.
 *       Это позволяет pipeline'у переживать swapchain recreate.</li>
 *   <li><b>Rasterization</b> — polygonMode={@code FILL},
 *       cullMode={@code BACK} (back face culling включён, потому что
 *       все 24 cube vertex'а заданы CCW из {@link CubeMeshFactory}),
 *       frontFace={@code COUNTER_CLOCKWISE}, lineWidth=1.0,
 *       depthBiasEnable=false.</li>
 *   <li><b>Multisample</b> — {@code SAMPLE_COUNT_1_BIT},
 *       sampleShadingEnable=false. MSAA попадёт в E.x вместе с
 *       post-process цепочкой.</li>
 *   <li><b>Depth/stencil</b> — depthTestEnable=true, depthWriteEnable=true,
 *       depthCompareOp={@code LESS}, depthBoundsTestEnable=false,
 *       stencilTestEnable=false. Используется в паре с depth
 *       attachment'ом render pass'а ({@link VulkanRenderPass} stage E.1).</li>
 *   <li><b>Color blend</b> — opaque (blendEnable=false), writeMask=RGBA.</li>
 *   <li><b>Dynamic state</b> — {@code [VIEWPORT, SCISSOR]}. Остальное
 *       зашито в pipeline.</li>
 * </ul>
 *
 * <p><b>Жизненный цикл.</b> Pipeline переживает swapchain recreate (потому
 * что viewport/scissor — динамические, а форматы color/depth attachment'ов
 * в render pass'е те же). Пересоздаётся ТОЛЬКО при изменении render pass'а
 * или шейдеров.
 *
 * <p>Идемпотентный {@link #dispose()}.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class VulkanPipeline {

    private final VulkanDevice device;
    private long pipelineLayout = VK10.VK_NULL_HANDLE;
    private long pipeline       = VK10.VK_NULL_HANDLE;

    public VulkanPipeline(VulkanDevice device, VulkanRenderPass renderPass,
                          VulkanShaderModule vertex, VulkanShaderModule fragment,
                          long descriptorSetLayout) {
        this.device = device;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            // 1. Pipeline layout — set=0 = scene layout (UBO + sampler).
            VkPipelineLayoutCreateInfo layoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                    .pSetLayouts(stack.longs(descriptorSetLayout));
            LongBuffer pLayout = stack.callocLong(1);
            VulkanDevice.check(VK10.vkCreatePipelineLayout(device.logical(), layoutInfo, null, pLayout),
                    "vkCreatePipelineLayout");
            pipelineLayout = pLayout.get(0);

            // 2. Shader stages.
            VkPipelineShaderStageCreateInfo.Buffer stages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
            stages.get(0)
                    .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK10.VK_SHADER_STAGE_VERTEX_BIT)
                    .module(vertex.handle())
                    .pName(stack.UTF8("main"));
            stages.get(1)
                    .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK10.VK_SHADER_STAGE_FRAGMENT_BIT)
                    .module(fragment.handle())
                    .pName(stack.UTF8("main"));

            // 3. Vertex input — один binding (cube vertex), 3 attribute'а.
            VkVertexInputBindingDescription.Buffer bindingDesc = VkVertexInputBindingDescription.calloc(1, stack);
            bindingDesc.get(0)
                    .binding(0)
                    .stride(CubeMeshFactory.VERTEX_STRIDE_BYTES)
                    .inputRate(VK10.VK_VERTEX_INPUT_RATE_VERTEX);

            VkVertexInputAttributeDescription.Buffer attrDesc = VkVertexInputAttributeDescription.calloc(3, stack);
            attrDesc.get(0)
                    .location(0).binding(0)
                    .format(VK10.VK_FORMAT_R32G32B32_SFLOAT)
                    .offset(0);
            attrDesc.get(1)
                    .location(1).binding(0)
                    .format(VK10.VK_FORMAT_R32G32B32_SFLOAT)
                    .offset(3 * Float.BYTES);
            attrDesc.get(2)
                    .location(2).binding(0)
                    .format(VK10.VK_FORMAT_R32G32_SFLOAT)
                    .offset(6 * Float.BYTES);

            VkPipelineVertexInputStateCreateInfo vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
                    .pVertexBindingDescriptions(bindingDesc)
                    .pVertexAttributeDescriptions(attrDesc);

            // 4. Input assembly — список треугольников.
            VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                    .topology(VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
                    .primitiveRestartEnable(false);

            // 5. Viewport state — count=1, сами значения устанавливаются per-frame.
            VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
                    .viewportCount(1)
                    .scissorCount(1);

            // 6. Rasterization — fill, BACK culling, CCW winding.
            VkPipelineRasterizationStateCreateInfo rasterizer = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
                    .depthClampEnable(false)
                    .rasterizerDiscardEnable(false)
                    .polygonMode(VK10.VK_POLYGON_MODE_FILL)
                    .lineWidth(1.0f)
                    .cullMode(VK10.VK_CULL_MODE_BACK_BIT)
                    .frontFace(VK10.VK_FRONT_FACE_COUNTER_CLOCKWISE)
                    .depthBiasEnable(false);

            // 7. Multisample — без MSAA, 1 sample/pixel.
            VkPipelineMultisampleStateCreateInfo multisample = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
                    .sampleShadingEnable(false)
                    .rasterizationSamples(VK10.VK_SAMPLE_COUNT_1_BIT);

            // 8. Depth/stencil — на E.1 включаем depth test + write, LESS, без stencil/bounds.
            VkPipelineDepthStencilStateCreateInfo depthStencil = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO)
                    .depthTestEnable(true)
                    .depthWriteEnable(true)
                    .depthCompareOp(VK10.VK_COMPARE_OP_LESS)
                    .depthBoundsTestEnable(false)
                    .stencilTestEnable(false)
                    .minDepthBounds(0.0f)
                    .maxDepthBounds(1.0f);

            // 9. Color blend — opaque, write mask RGBA.
            VkPipelineColorBlendAttachmentState.Buffer cba = VkPipelineColorBlendAttachmentState.calloc(1, stack);
            cba.get(0)
                    .colorWriteMask(VK10.VK_COLOR_COMPONENT_R_BIT
                            | VK10.VK_COLOR_COMPONENT_G_BIT
                            | VK10.VK_COLOR_COMPONENT_B_BIT
                            | VK10.VK_COLOR_COMPONENT_A_BIT)
                    .blendEnable(false);
            VkPipelineColorBlendStateCreateInfo colorBlend = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
                    .logicOpEnable(false)
                    .pAttachments(cba);

            // 10. Dynamic state — viewport+scissor.
            IntBuffer dynStates = stack.ints(VK10.VK_DYNAMIC_STATE_VIEWPORT, VK10.VK_DYNAMIC_STATE_SCISSOR);
            VkPipelineDynamicStateCreateInfo dynamic = VkPipelineDynamicStateCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO)
                    .pDynamicStates(dynStates);

            // 11. Сборка pipeline.
            VkGraphicsPipelineCreateInfo.Buffer pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack);
            pipelineInfo.get(0)
                    .sType(VK10.VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                    .pStages(stages)
                    .pVertexInputState(vertexInput)
                    .pInputAssemblyState(inputAssembly)
                    .pViewportState(viewportState)
                    .pRasterizationState(rasterizer)
                    .pMultisampleState(multisample)
                    .pDepthStencilState(depthStencil)
                    .pColorBlendState(colorBlend)
                    .pDynamicState(dynamic)
                    .layout(pipelineLayout)
                    .renderPass(renderPass.handle())
                    .subpass(0)
                    .basePipelineHandle(VK10.VK_NULL_HANDLE)
                    .basePipelineIndex(-1);

            LongBuffer pPipeline = stack.callocLong(1);
            VulkanDevice.check(VK10.vkCreateGraphicsPipelines(device.logical(),
                            VK10.VK_NULL_HANDLE, pipelineInfo, null, pPipeline),
                    "vkCreateGraphicsPipelines(cube)");
            pipeline = pPipeline.get(0);
        }

        System.out.println("[Death:desktop] Vulkan graphics pipeline created (cube: vert+frag, "
                + "vertex stride=" + CubeMeshFactory.VERTEX_STRIDE_BYTES + " bytes, "
                + "BACK culling, depth LESS, "
                + "set 0 = scene UBO + sampler)");
    }

    public long handle()         { return pipeline; }
    public long pipelineLayout() { return pipelineLayout; }

    public void dispose() {
        if (device.logical() == null) {
            pipeline       = VK10.VK_NULL_HANDLE;
            pipelineLayout = VK10.VK_NULL_HANDLE;
            return;
        }
        if (pipeline != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyPipeline(device.logical(), pipeline, null);
            pipeline = VK10.VK_NULL_HANDLE;
        }
        if (pipelineLayout != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyPipelineLayout(device.logical(), pipelineLayout, null);
            pipelineLayout = VK10.VK_NULL_HANDLE;
        }
    }
}
