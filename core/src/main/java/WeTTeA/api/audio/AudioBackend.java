package WeTTeA.api.audio;

import WeTTeA.api.content.AssetHandle;
import WeTTeA.api.lifecycle.Disposable;

/**
 * Платформенный backend звука — реализуется в платформенных модулях.
 *
 * <p>Контракт:
 * <ul>
 *   <li>{@link #playSound(AssetHandle, AudioCategory, boolean)} — однократно/loop;</li>
 *   <li>{@link #setCategoryVolume(AudioCategory, float)} — глобальный mixer;</li>
 *   <li>{@link #pauseAll()} / {@link #resumeAll()} — на app pause/resume;</li>
 *   <li>{@link #stopAll()} — на смену сцены, снести всё.</li>
 * </ul>
 *
 * <p><b>Threading.</b> Backend сам управляет аудио-потоком; вызовы из
 * gameplay допустимы из game thread, реализации обязаны лочить или
 * перекладывать на свой поток.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public interface AudioBackend extends Disposable {

    /** Инициализация устройства/контекста. Вызывается один раз. */
    void init();

    /**
     * Запустить воспроизведение содержимого asset'а в указанной категории.
     *
     * @param asset    дескриптор аудио-asset'а (см. {@link AssetCategory#AUDIO})
     * @param category категория для микшера
     * @param looping  true — циклически
     * @return дескриптор источника
     */
    AudioSourceHandle playSound(AssetHandle asset, AudioCategory category, boolean looping);

    /** Установить громкость категории, {@code [0..1]}. */
    void setCategoryVolume(AudioCategory category, float volume);

    /** Mute/unmute категории. */
    void setCategoryMuted(AudioCategory category, boolean muted);

    /** Приостановить все источники (на app pause). */
    void pauseAll();

    /** Возобновить все источники. */
    void resumeAll();

    /** Остановить и освободить все источники. */
    void stopAll();
}
