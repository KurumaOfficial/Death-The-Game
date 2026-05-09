package WeTTeA.platform.desktop;

import WeTTeA.api.audio.AudioCategory;
import WeTTeA.api.audio.AudioSourceHandle;

import org.lwjgl.openal.AL10;

import java.util.Objects;

/**
 * Реализация {@link AudioSourceHandle} поверх OpenAL source/buffer пары.
 *
 * <p>Контракт владения:
 * <ul>
 *   <li>{@link #alSource()} и {@link #alBuffer()} — handles, выданные
 *       OpenAL'ом ({@link AL10#alGenSources()} / {@link AL10#alGenBuffers()});</li>
 *   <li>{@link #dispose()} вызывает back-pointer на {@link OpenAlAudioBackend},
 *       который останавливает source, отвязывает buffer и удаляет оба handle'а;</li>
 *   <li>после {@link #dispose()} все методы — no-op (идемпотентность);</li>
 *   <li>цикл воспроизведения может прийти к концу естественным путём — в этом
 *       случае source перейдёт в {@code AL_STOPPED}, но handle ОСТАЁТСЯ
 *       валидным до явного {@link #dispose()} (см. {@link AudioSourceHandle}).</li>
 * </ul>
 *
 * <p><b>Threading.</b> Все методы должны вызываться на одном потоке (тот же,
 * который сделал контекст OpenAL текущим). Stage 2.3 — это main thread.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class OpenAlSourceHandle implements AudioSourceHandle {

    private final OpenAlAudioBackend backend;
    private final long id;
    private final int alSource;
    private final int alBuffer;
    private final AudioCategory category;
    private final boolean ownsBuffer;
    private boolean disposed;

    OpenAlSourceHandle(OpenAlAudioBackend backend,
                       long id,
                       int alSource,
                       int alBuffer,
                       AudioCategory category,
                       boolean ownsBuffer) {
        this.backend    = Objects.requireNonNull(backend, "backend");
        this.category   = Objects.requireNonNull(category, "category");
        this.id         = id;
        this.alSource   = alSource;
        this.alBuffer   = alBuffer;
        this.ownsBuffer = ownsBuffer;
    }

    /** Внутренний OpenAL handle source'а — нужен backend'у для микшера/паузы. */
    int alSource() {
        return alSource;
    }

    /** Внутренний OpenAL handle buffer'а — нужен backend'у при освобождении. */
    int alBuffer() {
        return alBuffer;
    }

    /**
     * {@code true} если source владеет ALBuffer'ом эксклюзивно (например,
     * синтетический PCM из {@code playSineWaveSmoke}). {@code false} —
     * buffer пришёл из {@code bufferCache} {@link OpenAlAudioBackend} и
     * шарится между источниками; {@link OpenAlAudioBackend#releaseSource}
     * не должен его удалять.
     */
    boolean ownsBuffer() {
        return ownsBuffer;
    }

    @Override
    public long id() {
        return id;
    }

    @Override
    public AudioCategory category() {
        return category;
    }

    @Override
    public boolean isActive() {
        if (disposed) return false;
        int state = AL10.alGetSourcei(alSource, AL10.AL_SOURCE_STATE);
        return state == AL10.AL_PLAYING || state == AL10.AL_PAUSED;
    }

    @Override
    public void setVolume(float volume) {
        if (disposed) return;
        float clamped = volume < 0f ? 0f : (volume > 1f ? 1f : volume);
        AL10.alSourcef(alSource, AL10.AL_GAIN, clamped);
    }

    @Override
    public void setPitch(float pitch) {
        if (disposed) return;
        float safe = pitch < 0.01f ? 0.01f : pitch;
        AL10.alSourcef(alSource, AL10.AL_PITCH, safe);
    }

    @Override
    public void setLooping(boolean looping) {
        if (disposed) return;
        AL10.alSourcei(alSource, AL10.AL_LOOPING, looping ? AL10.AL_TRUE : AL10.AL_FALSE);
    }

    @Override
    public void pause() {
        if (disposed) return;
        AL10.alSourcePause(alSource);
    }

    @Override
    public void resume() {
        if (disposed) return;
        AL10.alSourcePlay(alSource);
    }

    @Override
    public void stop() {
        if (disposed) return;
        AL10.alSourceStop(alSource);
    }

    @Override
    public void dispose() {
        if (disposed) return;
        disposed = true;
        backend.releaseSource(this);
    }

    /** Тест-only: проверка отметки disposed без побочных эффектов. */
    boolean isDisposed() {
        return disposed;
    }
}
