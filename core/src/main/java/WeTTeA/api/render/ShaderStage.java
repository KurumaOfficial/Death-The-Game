package WeTTeA.api.render;

/**
 * Стадии шейдерного пайплайна.
 *
 * <p>Используется при загрузке скомпилированного SPIR-V в backend
 * и при описании material'ов / pipeline-state.
 *
 * <p>Все шейдеры пишутся в GLSL и компилируются в SPIR-V на этапе сборки
 * (через {@code glslc}). Хранение GLSL исходников в runtime запрещено —
 * только {@code .spv} артефакты.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public enum ShaderStage {

    VERTEX,
    FRAGMENT,
    GEOMETRY,
    TESSELLATION_CONTROL,
    TESSELLATION_EVALUATION,
    COMPUTE
}
