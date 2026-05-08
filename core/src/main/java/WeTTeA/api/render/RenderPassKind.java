package WeTTeA.api.render;

/**
 * Логические фазы рендер-пайплайна.
 *
 * <p>Конкретный backend (Vulkan/OpenGL) маппит эти фазы на свои render passes,
 * framebuffers и attachment layouts. Gameplay/UI слой подписывается через
 * {@link RenderFrameContext} на нужную фазу — он не знает, что именно за
 * VkRenderPass под ней.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public enum RenderPassKind {

    /** Геометрия мира (G-buffer для deferred / forward+ pre-pass). */
    GEOMETRY_OPAQUE,

    /** Прозрачная геометрия мира — после освещения. */
    GEOMETRY_TRANSPARENT,

    /** Освещение и тени. */
    LIGHTING,

    /** Bullet hell / VFX слой — масс-инстансинг частиц и снарядов. */
    BULLET_AND_FX,

    /** Пост-обработка (bloom, tone-mapping, color grading). */
    POST_PROCESS,

    /** UI / HUD — поверх всей сцены. */
    UI_HUD,

    /** Narrative диалог-оверлей (3D-портреты, текст). */
    NARRATIVE_OVERLAY,

    /** Debug overlay (выводится только в dev сборке). */
    DEBUG_OVERLAY
}
