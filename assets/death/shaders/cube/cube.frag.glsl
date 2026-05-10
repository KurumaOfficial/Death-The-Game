// =============================================================================
// cube.frag.glsl — Death stage E.1
//
// Fragment shader куба: Lambertian diffuse от одного directional light
// (направление приходит из UBO как ubo.lightDir в world-space) +
// текстурный sample по UV.
//
// Bindings:
//   set=0/binding=0  CameraUbo (см. cube.vert.glsl)
//   set=0/binding=1  combined image sampler (albedo)
//
// Lighting model: lambert = max(dot(N, -L), 0.0); ambient = 0.25.
// На E.1 используется та же checkerboard-текстура что и в triangle —
// после выкатки правильной материальной системы (E.x) шейдер обрастёт
// normal map / specular / PBR.
// =============================================================================

#version 450

layout(set = 0, binding = 0) uniform CameraUbo {
    mat4 model;
    mat4 viewProj;
    vec3 lightDir;
    float padding;
} ubo;

layout(set = 0, binding = 1) uniform sampler2D albedo;

layout(location = 0) in vec3 worldNormal;
layout(location = 1) in vec2 fragUV;
layout(location = 0) out vec4 outColor;

void main() {
    vec3 N       = normalize(worldNormal);
    vec3 L       = normalize(-ubo.lightDir);
    float lambert = max(dot(N, L), 0.0);
    float ambient = 0.25;
    vec4 sampled  = texture(albedo, fragUV);
    vec3 lit      = sampled.rgb * (ambient + (1.0 - ambient) * lambert);
    outColor      = vec4(lit, sampled.a);
}
