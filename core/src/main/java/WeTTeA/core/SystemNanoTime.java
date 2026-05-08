package WeTTeA.core;

/**
 * Реализация {@link Time} на {@link System#nanoTime()}.
 *
 * <p>Хранит {@link #frameDelta} последнего render-кадра.
 * Не потокобезопасен — вызывается только из game-thread.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class SystemNanoTime implements Time {

    private final long startNanos = System.nanoTime();
    private float frameDelta;

    @Override
    public long nanoTime() {
        return System.nanoTime() - startNanos;
    }

    @Override
    public float frameDeltaSeconds() {
        return frameDelta;
    }

    public void setFrameDelta(float frameDelta) {
        this.frameDelta = frameDelta;
    }
}
