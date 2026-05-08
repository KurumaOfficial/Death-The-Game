package WeTTeA.api.content;

/**
 * Категории asset'ов на уровне платформы / каталога.
 *
 * <p>Каждой категории соответствует своя поддиректория в {@code assets/death/}
 * и свой набор форматов (см. соответствующие README в директориях).
 *
 * @author Kuruma
 * @since 0.1.0
 */
public enum AssetCategory {

    /** Скомпилированные SPIR-V шейдеры ({@code .spv}). */
    SHADER_SPIRV,

    /** Текстуры ({@code .png}, {@code .ktx2}). */
    TEXTURE,

    /** Модели в формате glTF 2.0 ({@code .glb}). */
    MODEL_GLTF,

    /** Аудио ({@code .ogg}, {@code .wav}). */
    AUDIO,

    /** Локализационные строки ({@code .json}). */
    LOCALE_STRINGS,

    /** Конфиг-данные геймплея ({@code .json}). */
    GAMEPLAY_DATA,

    /** Bullet hell паттерны ({@code .json}). */
    BULLET_PATTERN,

    /** Narrative диалоги ({@code .json}). */
    DIALOGUE
}
