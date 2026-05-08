package WeTTeA.api.platform;

/**
 * Колбэки жизненного цикла приложения от платформы.
 *
 * <p>Платформа сообщает core, когда:
 * <ul>
 *   <li>{@link #onPause()} — окно потеряло фокус (desktop) или
 *       {@code Activity.onPause()} (Android), {@code applicationWillResignActive} (iOS).
 *       Render должен быть остановлен; audio замьючен.</li>
 *   <li>{@link #onResume()} — фокус восстановлен; render и audio возобновляются.</li>
 *   <li>{@link #onLowMemory()} — система просит освободить кэш (Android/iOS).</li>
 *   <li>{@link #onShutdownRequested()} — пользователь закрыл окно или система
 *       требует завершения (Android {@code onDestroy}, iOS termination).</li>
 * </ul>
 *
 * @author Kuruma
 * @since 0.1.0
 */
public interface AppLifecycle {

    void onPause();

    void onResume();

    void onLowMemory();

    void onShutdownRequested();
}
