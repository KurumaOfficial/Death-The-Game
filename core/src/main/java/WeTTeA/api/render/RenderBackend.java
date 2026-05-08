package WeTTeA.api.render;

import WeTTeA.api.lifecycle.Disposable;

/**
 * Жизненный цикл рендер-backend'а — Vulkan, OpenGL desktop, OpenGL ES.
 *
 * <p>Реализации:
 * <ul>
 *   <li>{@code WeTTeA.render.vulkan.VulkanRenderBackend} (модуль :desktop / :android);</li>
 *   <li>{@code WeTTeA.render.opengl.OpenGLDesktopRenderBackend} (fallback :desktop);</li>
 *   <li>{@code WeTTeA.render.opengl.OpenGLEsRenderBackend} (fallback :android);</li>
 *   <li>{@code WeTTeA.render.vulkan.IosVulkanRenderBackend} (через MoltenVK, :ios).</li>
 * </ul>
 *
 * <p><b>Поток.</b> Все методы вызываются строго на render thread, кроме
 * {@link #onSurfaceLost()} и {@link #onSurfaceRecreated(int, int)}, которые
 * могут прийти из platform thread (Android) — реализации обязаны корректно
 * синхронизироваться.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public interface RenderBackend extends Disposable {

    /**
     * Инициализация backend'а. Создаёт instance/device/swapchain, загружает
     * базовые pipeline'ы. Вызывается ровно один раз после создания surface.
     *
     * @param initialWidth  ширина drawable surface в пикселях
     * @param initialHeight высота drawable surface в пикселях
     */
    void init(int initialWidth, int initialHeight);

    /** Возможности устройства, доступные после {@link #init(int, int)}. */
    RenderCapabilities capabilities();

    /**
     * Начать запись кадра. Возвращает контекст, который надо передавать
     * в {@code Drawable.draw(...)} в течение этого кадра.
     */
    RenderFrameContext beginFrame();

    /**
     * Перейти в указанную фазу. Backend оформит соответствующий render pass /
     * framebuffer / pipeline state. Drawable'ы записывают команды в текущей
     * фазе. До смены фазы — никаких прямых API вызовов из gameplay/UI.
     */
    void enterPass(RenderPassKind pass);

    /** Завершить запись кадра и презентовать его. */
    void endFrameAndPresent();

    /**
     * Surface перестал быть валиден — например, Android {@code onPause}
     * или ресайз окна, требующий пересоздания swapchain'а.
     */
    void onSurfaceLost();

    /**
     * Surface создан заново — нужно реинициализировать swapchain/framebuffer'ы
     * под новый размер.
     */
    void onSurfaceRecreated(int width, int height);
}
