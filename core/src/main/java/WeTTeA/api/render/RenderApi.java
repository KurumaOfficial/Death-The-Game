package WeTTeA.api.render;

/**
 * Графическое API, на котором сейчас работает {@link RenderBackend}.
 *
 * <p>Vulkan — первичная цель проекта на всех платформах:
 * Windows/Linux/Android — нативно, macOS/iOS — через MoltenVK.
 * OpenGL варианты оставлены как fallback для устройств без актуального
 * Vulkan драйвера.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public enum RenderApi {

    /** Vulkan 1.2+. Основной путь. */
    VULKAN,

    /** OpenGL 4.1 Core. Fallback для desktop без Vulkan. */
    OPENGL_DESKTOP,

    /** OpenGL ES 3.0+. Fallback для Android без Vulkan. */
    OPENGL_ES
}
