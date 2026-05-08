/**
 * Контракты подсистемы рендера.
 *
 * <p>Этот пакет описывает, <b>что</b> рендер делает,
 * не зная <b>как</b>. Конкретный backend (Vulkan-first, OpenGL fallback)
 * живёт в модуле {@code WeTTeA.render.*} в platform-зависимых модулях
 * и в {@code WeTTeA.render.vulkan} / {@code WeTTeA.render.opengl} —
 * core <b>никогда</b> не импортирует backend напрямую.
 *
 * <p>Ключевые сущности:
 * <ul>
 *   <li>{@link WeTTeA.api.render.RenderApi} — API: VULKAN / OPENGL_DESKTOP / OPENGL_ES;</li>
 *   <li>{@link WeTTeA.api.render.RenderBackend} — life-cycle backend'а;</li>
 *   <li>{@link WeTTeA.api.render.RenderFrameContext} — scope одного кадра;</li>
 *   <li>{@link WeTTeA.api.render.RenderPassKind} — фазы пайплайна;</li>
 *   <li>{@link WeTTeA.api.render.RenderQualityTier} — пресеты качества;</li>
 *   <li>{@link WeTTeA.api.render.RenderCapabilities} — что поддерживает железо.</li>
 * </ul>
 *
 * <p>Запрещено в этом пакете:
 * <ul>
 *   <li>Vulkan/OpenGL/MoltenVK типы;</li>
 *   <li>LWJGL / GLFW классы;</li>
 *   <li>конкретные shader handle'ы / pipeline state objects.</li>
 * </ul>
 *
 * @author Kuruma
 * @since 0.1.0
 */
package WeTTeA.api.render;
