package WeTTeA.api.render;

import org.joml.Matrix4fc;

/**
 * Scope одного кадра рендера.
 *
 * <p>Передаётся реализациям {@link WeTTeA.api.lifecycle.Drawable#draw} и UI слою.
 * Через него gameplay/UI получают:
 * <ul>
 *   <li>текущую фазу рендера ({@link #currentPass()});</li>
 *   <li>view/projection матрицы активной камеры;</li>
 *   <li>{@link #viewport()} активной части экрана;</li>
 *   <li>дельту времени с прошлого кадра — для плавных анимаций
 *       и интерполяции при фиксированном симуляционном шаге.</li>
 * </ul>
 *
 * <p><b>Lifetime.</b> Объект валиден только в пределах одного кадра.
 * Сохранять ссылку между вызовами {@code draw} запрещено.
 *
 * <p><b>Thread.</b> Доступ только из render-потока.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public interface RenderFrameContext {

    /** Текущая фаза пайплайна, в которой находится контекст. */
    RenderPassKind currentPass();

    /** Вьюпорт текущей записи кадра. */
    Viewport viewport();

    /** View-матрица активной камеры. Read-only. */
    Matrix4fc viewMatrix();

    /** Projection-матрица активной камеры. Read-only. */
    Matrix4fc projectionMatrix();

    /** Дельта времени между текущим и предыдущим презентом, в секундах. */
    double frameDeltaSeconds();

    /** Возможности backend'а — для shader-feature switching и assert'ов. */
    RenderCapabilities capabilities();
}
