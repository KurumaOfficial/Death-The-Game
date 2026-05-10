package WeTTeA.core.render;

import WeTTeA.api.render.Camera;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Stage E.1 — perspective-камера для Vulkan-backend'а.
 *
 * <p>Реализует {@link Camera} с фиксированным FOV по вертикали и
 * настраиваемыми clip-plane'ами. Внутри хранит eye / target / up как
 * {@link Vector3f}, а матрицы — как {@link Matrix4f}, перестраивает их
 * лениво при каждом изменении входных параметров.
 *
 * <h3>Vulkan-совместимость</h3>
 * <ul>
 *   <li>Для projection используется
 *       {@link Matrix4f#setPerspective(float, float, float, float, boolean)}
 *       с {@code zZeroToOne=true} — Z диапазон сразу [0..1] (в отличие
 *       от OpenGL [-1..+1]).</li>
 *   <li>Y-flip делается через {@code m11 = -m11} после
 *       {@code setPerspective}, чтобы Y вниз в clip-space соответствовал
 *       Y вверх в world-space. Альтернатива — устанавливать
 *       {@code viewport.height < 0} в {@code vkCmdSetViewport};
 *       выбран первый путь, чтобы не привязывать gameplay к
 *       Vulkan-флагам viewport'а.</li>
 *   <li>{@link #viewProjectionMatrix()} pre-multiplied'ит matrices
 *       при каждом обновлении, чтобы vertex shader делал один matmul.</li>
 * </ul>
 *
 * <h3>Camera control</h3>
 * <p>На stage E.1 камера вращается вокруг {@code target} по Y-orbit'у:
 * вызывающий код передаёт через {@link #setOrbit(float, float, float)}
 * текущий yaw/pitch/distance, и камера сама обновляет {@code eye} и
 * {@code viewMatrix}. На stage E.2 этот класс получит {@link #setPosition}
 * и {@link #setLookDirection} для свободного first-person mouse-look'а.
 *
 * <p>Не потокобезопасен; только game thread.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class PerspectiveCamera implements Camera {

    private static final float DEFAULT_FOV_Y_DEG  = 60.0f;
    private static final float DEFAULT_NEAR       = 0.05f;
    private static final float DEFAULT_FAR        = 100.0f;
    private static final float MIN_ASPECT         = 1.0f / 16.0f;
    private static final float MAX_ASPECT         = 16.0f;

    private final Vector3f eye    = new Vector3f(0.0f, 0.0f, 4.0f);
    private final Vector3f target = new Vector3f(0.0f, 0.0f, 0.0f);
    private final Vector3f up     = new Vector3f(0.0f, 1.0f, 0.0f);

    private final Matrix4f view     = new Matrix4f();
    private final Matrix4f proj     = new Matrix4f();
    private final Matrix4f viewProj = new Matrix4f();

    private float fovYRadians;
    private float nearPlane;
    private float farPlane;
    private float aspectRatio = 16.0f / 9.0f;

    /**
     * Создаёт камеру с дефолтным FOV {@value #DEFAULT_FOV_Y_DEG}°,
     * near {@value #DEFAULT_NEAR}, far {@value #DEFAULT_FAR}, aspect
     * 16:9. Aspect перетрётся при первом {@link #updateAspect(int, int)}.
     */
    public PerspectiveCamera() {
        this.fovYRadians = (float) Math.toRadians(DEFAULT_FOV_Y_DEG);
        this.nearPlane   = DEFAULT_NEAR;
        this.farPlane    = DEFAULT_FAR;
        rebuildView();
        rebuildProjection();
        rebuildViewProj();
    }

    /**
     * Установить orbit-параметры (Y-axis orbit вокруг {@link #target}).
     *
     * @param yawRadians   азимут вокруг Y, 0 = по +Z в сторону камеры
     * @param pitchRadians elevation, 0 = горизонталь, +π/2 = сверху
     * @param distance     расстояние от target, должно быть {@code > 0}
     */
    public void setOrbit(float yawRadians, float pitchRadians, float distance) {
        if (distance <= 0.0f) {
            throw new IllegalArgumentException("distance должно быть > 0, got " + distance);
        }
        // Безопасный clamp pitch'а, чтобы up-вектор не вырождался при ±π/2.
        float pitchClamped = Math.max(-1.5533f, Math.min(1.5533f, pitchRadians));
        float cosP = (float) Math.cos(pitchClamped);
        float sinP = (float) Math.sin(pitchClamped);
        float cosY = (float) Math.cos(yawRadians);
        float sinY = (float) Math.sin(yawRadians);
        eye.set(
                target.x + distance * cosP * sinY,
                target.y + distance * sinP,
                target.z + distance * cosP * cosY);
        rebuildView();
        rebuildViewProj();
    }

    /**
     * Установить мировую позицию камеры напрямую (для E.2 first-person).
     */
    public void setPosition(float x, float y, float z) {
        eye.set(x, y, z);
        rebuildView();
        rebuildViewProj();
    }

    /**
     * Куда смотрит камера (worldSpace target). На stage E.1 — центр сцены.
     */
    public void setTarget(float x, float y, float z) {
        target.set(x, y, z);
        rebuildView();
        rebuildViewProj();
    }

    @Override
    public Vector3fc position() {
        return eye;
    }

    @Override
    public Matrix4fc viewMatrix() {
        return view;
    }

    @Override
    public Matrix4fc projectionMatrix() {
        return proj;
    }

    @Override
    public Matrix4fc viewProjectionMatrix() {
        return viewProj;
    }

    @Override
    public void updateAspect(int widthPx, int heightPx) {
        if (widthPx <= 0 || heightPx <= 0) {
            throw new IllegalArgumentException("PerspectiveCamera.updateAspect: w/h должны быть > 0, got "
                    + widthPx + "x" + heightPx);
        }
        float a = (float) widthPx / (float) heightPx;
        if (a < MIN_ASPECT) a = MIN_ASPECT;
        if (a > MAX_ASPECT) a = MAX_ASPECT;
        if (Math.abs(a - aspectRatio) < 1e-6f) {
            return; // ничего не изменилось — экономим matrix rebuild
        }
        aspectRatio = a;
        rebuildProjection();
        rebuildViewProj();
    }

    private void rebuildView() {
        view.identity().lookAt(eye, target, up);
    }

    private void rebuildProjection() {
        proj.identity()
                .setPerspective(fovYRadians, aspectRatio, nearPlane, farPlane, true);
        // Vulkan clip-space: Y вниз. Flip element m11 (y-scale).
        proj.m11(-proj.m11());
    }

    private void rebuildViewProj() {
        viewProj.set(proj).mul(view);
    }
}
