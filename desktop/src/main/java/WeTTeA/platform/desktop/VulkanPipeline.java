package WeTTeA.platform.desktop;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState;
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo;

import java.nio.IntBuffer;
import java.nio.LongBuffer;

/**
 * Stage 2.1b — Vulkan graphics pipeline для триангла.
 *
 * <p>Собирает один {@link VK10#VK_PIPELINE_BIND_POINT_GRAPHICS} pipeline
 * с двумя стадиями (vertex + fragment) поверх существующего
 * {@link VulkanRenderPass}. Все стадии настроены под минимальный smoke:
 *
 * <ul>
 *   <li><b>Pipeline layout</b> — содержит один {@code VkDescriptorSetLayout}
 *       (set=0) c одним binding'ом {@code COMBINED_IMAGE_SAMPLER} в fragment
 *       stage'е (см. {@link VulkanTextureDescriptors}). Push constants нет.
 *       Триангл рисуется через {@code gl_VertexIndex} в шейдере, MVP/uniform'ы
 *       появятся на 2.1c/2.2-render через дополнительный set=1 с UBO.</li>
 *   <li><b>Vertex input</b> — пустой ({@code vertexBindingDescriptionCount=0},
 *       {@code vertexAttributeDescriptionCount=0}). Vertex buffer на 2.1b
 *       не привязывается; 3 вершины генерируются шейдером.</li>
 *   <li><b>Input assembly</b> — {@code TRIANGLE_LIST}, primitive restart
 *       выкл. Подходит для не-индексированного {@code vkCmdDraw(3,1,0,0)}.</li>
 *   <li><b>Viewport state</b> — viewportCount=1, scissorCount=1; сами
 *       значения помечены как {@code DYNAMIC_STATE} и устанавливаются
 *       per-frame через {@code vkCmdSetViewport}/{@code vkCmdSetScissor}.
 *       Это требование для swapchain recreate-on-resize: pipeline переживает
 *       ресайз окна без пересборки.</li>
 *   <li><b>Rasterization</b> — polygonMode={@code FILL}, cullMode={@code NONE}
 *       (без face culling — триангл должен быть виден независимо от winding'а
 *       в clip-space; на 2.2-render включим {@code BACK} для меш'ей),
 *       frontFace={@code COUNTER_CLOCKWISE}, lineWidth=1.0, без depth bias.</li>
 *   <li><b>Multisample</b> — {@code SAMPLE_COUNT_1_BIT}, sampleShadingEnable=false.
 *       MSAA попадёт в 2.2-render вместе с post-process цепочкой.</li>
 *   <li><b>Depth/stencil</b> — НЕ привязан ({@code pDepthStencilState = null}).
 *       На 2.1b в render pass'е нет depth attachment'а; depth testing
 *       включится с 2.2-render когда добавим VK_FORMAT_D32_SFLOAT.</li>
 *   <li><b>Color blend</b> — opaque (blendEnable=false), writeMask=RGBA. Один
 *       attachment по числу color attachment'ов в render pass'е.</li>
 *   <li><b>Dynamic state</b> — {@code [VIEWPORT, SCISSOR]}. Остальные
 *       значения зашиты в pipeline и не меняются per-frame.</li>
 * </ul>
 *
 * <p><b>Жизненный цикл.</b> Pipeline переживает swapchain recreate (потому
 * что viewport/scissor — динамические, а формат color attachment'а в render
 * pass'е тот же). Pipeline пересоздаётся ТОЛЬКО при изменении render pass'а
 * (depth attachment, MSAA samples) или шейдеров (hot reload на 3.x).
 *
 * <p><b>Идемпотентность {@link #dispose()}.</b> Пересборка не нужна —
 * shutdown order: pipeline → shader modules → device, дабл-вызов dispose
 * безопасен.
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
            // 1. Pipeline layout — set=0 = layout под combined image sampler
            //    (см. VulkanTextureDescriptors). Push constants нет.
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

            // 3. Vertex input — пустой; gl_VertexIndex генерирует данные внутри vert shader'а.
            VkPipelineVertexInputStateCreateInfo vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO);

            // 4. Input assembly — список треугольников.
            VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                    .topology(VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
                    .primitiveRestartEnable(false);

            // 5. Viewport state — count=1, сами значения устанавливаются per-frame через cmdSetViewport/Scissor.
            VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
                    .viewportCount(1)
                    .scissorCount(1);

            // 6. Rasterization — fill polygons, без culling (триангл с произвольным winding'ом),
            //    front face CCW (как в Vulkan default).
            VkPipelineRasterizationStateCreateInfo rasterizer = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
                    .depthClampEnable(false)
                    .rasterizerDiscardEnable(false)
                    .polygonMode(VK10.VK_POLYGON_MODE_FILL)
                    .lineWidth(1.0f)
                    .cullMode(VK10.VK_CULL_MODE_NONE)
                    .frontFace(VK10.VK_FRONT_FACE_COUNTER_CLOCKWISE)
                    .depthBiasEnable(false);

            // 7. Multisample — без MSAA, 1 sample/pixel.
            VkPipelineMultisampleStateCreateInfo multisample = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
                    .sampleShadingEnable(false)
                    .rasterizationSamples(VK10.VK_SAMPLE_COUNT_1_BIT);

            // 8. Color blend — opaque, write mask RGBA, один attachment по числу color attachment'ов в RP.
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

            // 9. Dynamic state — viewport+scissor задаём per-frame, чтобы pipeline пережил swapchain resize.
            IntBuffer dynStates = stack.ints(VK10.VK_DYNAMIC_STATE_VIEWPORT, VK10.VK_DYNAMIC_STATE_SCISSOR);
            VkPipelineDynamicStateCreateInfo dynamic = VkPipelineDynamicStateCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO)
                    .pDynamicStates(dynStates);

            // 10. Сборка pipeline.
            VkGraphicsPipelineCreateInfo.Buffer pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack);
            pipelineInfo.get(0)
                    .sType(VK10.VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                    .pStages(stages)
                    .pVertexInputState(vertexInput)
                    .pInputAssemblyState(inputAssembly)
                    .pViewportState(viewportState)
                    .pRasterizationState(rasterizer)
                    .pMultisampleState(multisample)
                    .pDepthStencilState(null)
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
                    "vkCreateGraphicsPipelines(triangle)");
            pipeline = pPipeline.get(0);
        }

        System.out.println("[Death:desktop] Vulkan graphics pipeline created (triangle: vert+frag, "
                + "dynamic viewport+scissor, no depth, opaque blend, "
                + "set 0 = combined image sampler)");
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
