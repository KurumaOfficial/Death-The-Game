package WeTTeA.api.render;

/**
 * Прямоугольная область вывода кадра в пикселях drawable surface.
 *
 * <p>Используется для split-screen, mini-map, debug overlay, picture-in-picture
 * narrative окон и прочих внутрикадровых сцен.
 *
 * @param x      левая граница в пикселях
 * @param y      верхняя граница в пикселях (origin top-left, как в Vulkan
 *               и в большинстве UI систем; backend инвертирует при необходимости)
 * @param width  ширина в пикселях, {@code > 0}
 * @param height высота в пикселях, {@code > 0}
 *
 * @author Kuruma
 * @since 0.1.0
 */
public record Viewport(int x, int y, int width, int height) {

    public Viewport {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "Viewport dimensions must be positive: width=" + width + " height=" + height);
        }
        if (x < 0 || y < 0) {
            throw new IllegalArgumentException(
                    "Viewport origin must be non-negative: x=" + x + " y=" + y);
        }
    }

    /** Вьюпорт во весь surface заданного размера. */
    public static Viewport fullScreen(int width, int height) {
        return new Viewport(0, 0, width, height);
    }

    /** Соотношение сторон. */
    public float aspect() {
        return (float) width / (float) height;
    }
}
