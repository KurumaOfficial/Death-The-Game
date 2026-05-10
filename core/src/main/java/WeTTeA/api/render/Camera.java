package WeTTeA.api.render;

import org.joml.Matrix4fc;
import org.joml.Vector3fc;

/**
 * Stage E.1 — контракт активной камеры рендера.
 *
 * <p>Описывает источник {@code view} и {@code projection} матриц,
 * которые render-backend (Vulkan / Metal-через-MoltenVK / OpenGL ES в
 * будущем) кладёт в UBO кадра. Камера не знает ничего про конкретный
 * backend: {@link Matrix4fc} из JOML — нейтральная мат-структура, уже
 * включена в зависимости {@code :core} (см. {@code core/build.gradle.kts}).
 *
 * <h3>Координатная система</h3>
 * <ul>
 *   <li><b>World space:</b> Y вверх, X вправо, Z к зрителю
 *       (правая тройка). Это ровно тот же базис, что использует JOML
 *       по умолчанию в {@link org.joml.Matrix4f#lookAt}.</li>
 *   <li><b>Clip space (Vulkan):</b> Y вниз = +1 (в отличие от OpenGL),
 *       Z диапазон [0..1]. Реализация {@link #projectionMatrix()} обязана
 *       компенсировать это (в JOML — флипом Y компонента projection-матрицы
 *       или {@link org.joml.Matrix4f#setPerspective(float, float, float, float, boolean)}
 *       с {@code zZeroToOne=true}).</li>
 * </ul>
 *
 * <h3>Lifetime</h3>
 *
 * <p>Камера живёт всё время сессии (Service-уровень в
 * {@link WeTTeA.core.service.ServiceContainer}). Реализация владеет
 * собственным {@link org.joml.Matrix4f} и переиспользует его при
 * каждом {@link #updateAspect(int, int)} / {@link #setPosition} —
 * memory churn нулевой.
 *
 * <h3>Thread</h3>
 *
 * <p>Только game thread / render thread (на 0.1.0 это один и тот же).
 *
 * @author Kuruma
 * @since 0.1.0
 */
public interface Camera {

    /**
     * Текущая мировая позиция (eye) камеры.
     *
     * @return read-only {@link Vector3fc}; владелец — реализация
     */
    Vector3fc position();

    /**
     * View-матрица: {@code worldSpace → cameraSpace}.
     *
     * @return read-only {@link Matrix4fc}; владелец — реализация,
     *         не сохранять между кадрами
     */
    Matrix4fc viewMatrix();

    /**
     * Projection-матрица: {@code cameraSpace → clipSpace}.
     *
     * <p>В реализациях для Vulkan уже учтён Y-flip и Z-диапазон
     * {@code [0..1]} (см. javadoc интерфейса).
     *
     * @return read-only {@link Matrix4fc}; владелец — реализация
     */
    Matrix4fc projectionMatrix();

    /**
     * Pre-multiplied {@code projection * view} — оптимизация, чтобы
     * vertex shader делал один matrix-multiply, а не два.
     *
     * @return read-only {@link Matrix4fc}; владелец — реализация
     */
    Matrix4fc viewProjectionMatrix();

    /**
     * Уведомить камеру об изменении размеров framebuffer'а
     * (свапчейн-recreate). Реализация пересчитывает aspect ratio
     * и перестраивает {@link #projectionMatrix()}.
     *
     * @param widthPx  ширина framebuffer'а в пикселях, должна быть {@code > 0}
     * @param heightPx высота framebuffer'а в пикселях, должна быть {@code > 0}
     */
    void updateAspect(int widthPx, int heightPx);
}
