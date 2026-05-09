// =============================================================================
// triangle.frag.glsl — Death stage 2.1b + 3.1
//
// Fragment shader для smoke-теста графического pipeline + текстурного
// семплинга.
//
// Принимает интерполированный цвет от vertex shader'а (location=0) и UV
// (location=1), сэмплит COMBINED_IMAGE_SAMPLER из set=0 binding=0 по UV,
// и mix'ит результат с per-vertex цветом (50/50). Это даёт визуальное
// доказательство, что:
//   1. SPIR-V vkCreateShaderModule'нулся;
//   2. VkPipeline собрался c set=0 layout'ом, binding=0 = sampler2D;
//   3. vkCmdDraw + vkCmdBindDescriptorSets запустили растеризацию с
//      реальным sampler'ом и текстура попала в фрагмент;
//   4. UV interpolation работает (на checkerboard 8×8 видны квадраты).
//
// Mix с vertex color сделан specifically чтобы на скриншоте было видно
// и текстуру (cyan/magenta шахматка), и градиент per-vertex (R/G/B углы):
// если бы был только sampleColor — нельзя было бы отличить «текстура
// загрузилась» от «весь триангл одного цвета». Если бы был только
// vertexColor — никакой visibility у текстуры.
//
// На 2.1c/2.2-render сюда добавятся:
//   - depth/stencil testing через render pass dependency;
//   - normal mapping (доп binding под нормали);
//   - lighting когда добавится freelook + UBO с lightDir.
// =============================================================================

#version 450

layout(set = 0, binding = 0) uniform sampler2D albedo;

layout(location = 0) in vec3 fragColor;
layout(location = 1) in vec2 fragUV;
layout(location = 0) out vec4 outColor;

void main() {
    vec4 sampled = texture(albedo, fragUV);
    vec3 mixed = mix(sampled.rgb, fragColor, 0.5);
    outColor = vec4(mixed, 1.0);
}
