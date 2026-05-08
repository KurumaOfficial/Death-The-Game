package WeTTeA.api.lifecycle;

import WeTTeA.api.render.RenderFrameContext;

/**
 * Объект, записывающий draw-команды в текущий render scope.
 *
 * <p><b>Render / update разделение.</b> Реализации обязаны:
 * <ul>
 *   <li>читать актуальное состояние, вычисленное в {@link Tickable#tick(double)};</li>
 *   <li>записывать команды через переданный {@link RenderFrameContext};</li>
 *   <li>не модифицировать игровое состояние;</li>
 *   <li>не выполнять JNI-вызовы в Rust ядро покадрово (батчить).</li>
 * </ul>
 *
 * <p><b>Производительность.</b> Минимизировать draw calls; использовать
 * GPU instancing для масс-рендера ({@code 80k+} объектов); не аллоцировать
 * в горячем пути.
 *
 * @author Kuruma
 * @since 0.1.0
 */
@FunctionalInterface
public interface Drawable {

    /**
     * Записать draw-команды текущего объекта в кадр.
     *
     * @param frame контекст текущего кадра. Реализация не должна сохранять
     *              ссылку на frame между вызовами — он валиден только в пределах
     *              одного draw-проёма.
     */
    void draw(RenderFrameContext frame);
}
