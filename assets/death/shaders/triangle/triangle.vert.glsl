// =============================================================================
// triangle.vert.glsl — Death stage 2.1b + 3.1
//
// Vertex shader для smoke-теста графического pipeline + текстурного семплинга.
//
// Hard-coded triangle через gl_VertexIndex: вершины, цвета и UV лежат в const-
// массивах прямо в шейдере, vertex buffer на 2.1b/3.1 НЕ привязывается. Это
// каноничный приём для bring-up'а — позволяет проверить, что vkCmdDraw(3,1,0,0)
// доходит до GPU и фрагменты растеризуются с правильным interpolation'ом UV,
// без необходимости создавать VkBuffer / выделять VkDeviceMemory.
//
// На 2.1c сюда добавятся:
//   - layout(location=0) in vec3 inPos;       // vertex buffer
//   - layout(location=1) in vec3 inColor;
//   - layout(location=2) in vec2 inUV;
//   - layout(set=1, binding=0) uniform UBO    // MVP matrix
//   - layout(push_constant) uniform PC        // per-draw constants
//
// Координатное пространство: clip-space, Y вниз = +0.5 (как в Vulkan, в
// отличие от OpenGL Y вверх). Triangle wound CCW в clip-space (top, right,
// left), но pipeline создаётся с rasterizationState.cullMode=NONE, поэтому
// face culling неактивен и triangle виден независимо от winding'а.
//
// UV-маппинг: триангл покрывает диапазон [0..1] x [0..1] так, чтобы видна
// была вся checkerboard-текстура, и интерполяция давала чёткое разбиение
// 8×8 на размерах ~512px. Top-vertex имеет UV (0.5, 0), правый = (1, 1),
// левый = (0, 1) — top-left origin совпадает с Vulkan'ом и stb_image'ом.
// =============================================================================

#version 450

layout(location = 0) out vec3 fragColor;
layout(location = 1) out vec2 fragUV;

vec2 positions[3] = vec2[](
    vec2( 0.0, -0.5),  // top — красный
    vec2( 0.5,  0.5),  // bottom-right — зелёный
    vec2(-0.5,  0.5)   // bottom-left — синий
);

vec3 colors[3] = vec3[](
    vec3(1.0, 0.0, 0.0),
    vec3(0.0, 1.0, 0.0),
    vec3(0.0, 0.0, 1.0)
);

vec2 uvs[3] = vec2[](
    vec2(0.5, 0.0),
    vec2(1.0, 1.0),
    vec2(0.0, 1.0)
);

void main() {
    gl_Position = vec4(positions[gl_VertexIndex], 0.0, 1.0);
    fragColor   = colors[gl_VertexIndex];
    fragUV      = uvs[gl_VertexIndex];
}
