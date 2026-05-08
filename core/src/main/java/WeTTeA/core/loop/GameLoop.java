package WeTTeA.core.loop;

/**
 * Игровой цикл core.
 *
 * <p>Контракт:
 * <ul>
 *   <li>{@link #start()} — переводит цикл в состояние running, не блокирует поток;</li>
 *   <li>{@link #stop()} — корректное завершение, дожидается окончания текущего тика;</li>
 *   <li>{@link #tick()} — внешнее проталкивание (для платформ без своего pump'а,
 *       например iOS/Android с CADisplayLink/Choreographer).</li>
 * </ul>
 *
 * <p>Реализация по умолчанию — {@link FixedStepGameLoop}: фиксированный
 * simulation-step и переменный render-rate с интерполяцией.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public interface GameLoop {

    void start();

    void stop();

    void tick();

    boolean isRunning();
}
