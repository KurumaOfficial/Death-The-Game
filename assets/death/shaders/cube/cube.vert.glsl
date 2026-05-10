// =============================================================================
// cube.vert.glsl — Death stage E.1
//
// Vertex shader первого реального 3D-mesh'а (куб). Принимает per-vertex
// атрибуты position / normal / uv (vertex buffer создан в Java через
// VulkanMeshBuffer + CubeMeshFactory), читает MVP и lightDir из UBO
// (set=0/binding=0), пишет clip-space позицию + worldNormal + UV в out'ы
// для интерполяции до fragment'а.
//
// Layout вершины — 32 байта, ровно тот же что в CubeMeshFactory:
//   location=0  vec3 inPosition   (offset 0)
//   location=1  vec3 inNormal     (offset 12)
//   location=2  vec2 inUV         (offset 24)
//
// Layout UBO — std140, 144 байта:
//   offset 0    mat4 model        (per-frame model matrix; на E.1 крутит куб)
//   offset 64   mat4 viewProj     (от PerspectiveCamera, pre-multiplied)
//   offset 128  vec3 lightDir     (мировое направление света, нормализован)
//   offset 140  float _pad        (выравнивание до 16 байт)
//
// Coordinate convention: world-space Y вверх; PerspectiveCamera уже
// делает Y-flip в proj-матрице (m11 *= -1) под Vulkan clip-space.
//
// Normal transform: используется linear-only path (assumes uniform-scale
// model matrix). Если на E.x появятся non-uniform scales —
// перейдём на mat3(transpose(inverse(model))).
// =============================================================================

#version 450

layout(set = 0, binding = 0) uniform CameraUbo {
    mat4 model;
    mat4 viewProj;
    vec3 lightDir;
    float padding;
} ubo;

layout(location = 0) in vec3 inPosition;
layout(location = 1) in vec3 inNormal;
layout(location = 2) in vec2 inUV;

layout(location = 0) out vec3 worldNormal;
layout(location = 1) out vec2 fragUV;

void main() {
    vec4 worldPos = ubo.model * vec4(inPosition, 1.0);
    gl_Position   = ubo.viewProj * worldPos;
    worldNormal   = mat3(ubo.model) * inNormal;
    fragUV        = inUV;
}
