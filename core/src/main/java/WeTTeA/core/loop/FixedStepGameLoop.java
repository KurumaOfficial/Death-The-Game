package WeTTeA.core.loop;

import WeTTeA.core.SystemNanoTime;

/**
 * Реализация {@link GameLoop} с фиксированным simulation-step.
 *
 * <p>Sim-step = {@link #FIXED_STEP_NANOS} (≈16.67 ms = 60 Hz). Render
 * вызывается с интерполяцией alpha = (acc / step). При лагах ограничивается
 * {@link #MAX_STEPS_PER_TICK} чтобы избежать spiral of death.
 *
 * <p>Stage 1: цикл существует в core, но реальный hook update/render
 * подключается на стадии 2 (см. PROGRESS.md строка GameLoop).
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class FixedStepGameLoop implements GameLoop {

    public static final long FIXED_STEP_NANOS = 16_666_667L;
    public static final int MAX_STEPS_PER_TICK = 5;

    private final SystemNanoTime time;
    private long accumulator;
    private long lastNanos;
    private boolean running;

    public FixedStepGameLoop(SystemNanoTime time) {
        this.time = time;
    }

    @Override
    public void start() {
        running = true;
        lastNanos = time.nanoTime();
        accumulator = 0L;
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public void tick() {
        if (!running) return;
        long now = time.nanoTime();
        long delta = now - lastNanos;
        lastNanos = now;
        accumulator += delta;
        int steps = 0;
        while (accumulator >= FIXED_STEP_NANOS && steps < MAX_STEPS_PER_TICK) {
            accumulator -= FIXED_STEP_NANOS;
            steps++;
        }
        time.setFrameDelta((float) (delta / 1_000_000_000.0));
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
