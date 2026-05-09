package WeTTeA.platform.desktop;

import WeTTeA.api.audio.AudioBackend;
import WeTTeA.api.audio.AudioCategory;
import WeTTeA.api.audio.AudioSourceHandle;
import WeTTeA.api.content.AssetCategory;
import WeTTeA.api.content.AssetHandle;
import WeTTeA.api.content.ContentLoader;

import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.openal.ALCapabilities;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Desktop реализация {@link AudioBackend} поверх LWJGL3 OpenAL (OpenAL Soft).
 *
 * <p>Жизненный цикл (stage 2.3 / 2.3b):
 * <ol>
 *   <li>{@link #init()} → {@code alcOpenDevice(null)} (default device)
 *       → {@code alcCreateContext} → {@code alcMakeContextCurrent}
 *       → {@link AL#createCapabilities(ALCCapabilities)}.</li>
 *   <li>{@link #bindContentPipeline(ContentLoader, OggVorbisAudioDecoder)} —
 *       подключает загрузчик контента и OGG-декодер для
 *       {@link #playSound(AssetHandle, AudioCategory, boolean)} (stage 2.3b).
 *       Без bind метод playSound бросит {@link IllegalStateException}.</li>
 *   <li>{@link #playSineWaveSmoke(float, float, AudioCategory)} —
 *       синтетический PCM, не требующий asset loader (stage 2.3 smoke).</li>
 *   <li>{@link #playSound(AssetHandle, AudioCategory, boolean)} —
 *       реальный OGG asset через ContentLoader → stb_vorbis → ALBuffer
 *       cache (stage 2.3b).</li>
 *   <li>{@link #dispose()} → {@link #stopAll()} → освобождение всех
 *       cached ALBuffer'ов → {@code alcMakeContextCurrent(NULL)}
 *       → {@code alcDestroyContext} → {@code alcCloseDevice}.</li>
 * </ol>
 *
 * <p><b>Категории.</b> Для каждой {@link AudioCategory} хранится пара
 * {@code volume[0..1] + muted}. При создании источника громкость берётся
 * из соответствующего mixer'а; {@link #setCategoryVolume(AudioCategory, float)}
 * и {@link #setCategoryMuted(AudioCategory, boolean)} прокатывают новое
 * значение по всем активным источникам этой категории сразу.
 *
 * <p><b>Buffer cache.</b> {@link #playSound(AssetHandle, AudioCategory, boolean)}
 * хранит карту {@code assetId → ALBuffer} в {@link #bufferCache}. Повторное
 * воспроизведение того же asset'а не перечитывает файл и не вызывает
 * stb_vorbis повторно — создаётся только новый ALSource. Buffer удаляется
 * только в {@link #dispose()}; per-source dispose НЕ трогает кешированный
 * buffer (см. {@link OpenAlSourceHandle#ownsBuffer()}).
 *
 * <p><b>Asset-based playback.</b> Stage 2.3b: загрузка только OGG Vorbis
 * mono/stereo через {@link OggVorbisAudioDecoder}. Stage 2.3c+ добавит
 * WAV / FLAC / streaming для долгих треков (musique). Если ContentLoader
 * не привязан, метод бросает {@link IllegalStateException} с пояснением.
 *
 * <p><b>Threading.</b> OpenAL контекст создаётся и используется на main
 * thread (там же, где game-loop и GLFW). Для многопоточной игры на stage
 * 3.x потребуется либо локальный mutex, либо отдельный audio thread с
 * sps queue команд.
 *
 * <p><b>Headless / нет аудио устройства.</b> Если {@code alcOpenDevice}
 * возвращает {@code NULL}, {@link #init()} бросает {@link IllegalStateException}.
 * Лаунчер ловит и предлагает флаг {@code --no-audio}. Альтернатива на CI —
 * env var {@code ALSOFT_DRIVERS=null}: OpenAL Soft в этом случае открывает
 * "null" backend, который проигрывает PCM в /dev/null, но проходит весь
 * pipeline (gen → buffer → source → play → STOPPED).
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class OpenAlAudioBackend implements AudioBackend {

    private long device  = MemoryUtil.NULL;
    private long context = MemoryUtil.NULL;
    private ALCCapabilities alcCaps;
    private ALCapabilities  alCaps;
    private String deviceName  = "<not initialized>";
    private String alVendor    = "";
    private String alRenderer  = "";
    private String alVersion   = "";
    private boolean initialized;
    private boolean disposed;

    private final EnumMap<AudioCategory, CategoryMixer> mixers = new EnumMap<>(AudioCategory.class);
    private final Map<Long, OpenAlSourceHandle> activeSources = new LinkedHashMap<>();
    private final Map<String, Integer> bufferCache = new HashMap<>();
    private final AtomicLong nextId = new AtomicLong(1L);

    private ContentLoader contentLoader;
    private OggVorbisAudioDecoder oggDecoder;

    public OpenAlAudioBackend() {
        for (AudioCategory c : AudioCategory.values()) {
            mixers.put(c, new CategoryMixer());
        }
    }

    // -- lifecycle --------------------------------------------------------

    @Override
    public void init() {
        if (initialized) {
            throw new IllegalStateException("OpenAlAudioBackend.init() уже вызывался");
        }
        if (disposed) {
            throw new IllegalStateException("OpenAlAudioBackend уже dispose'нут — экземпляр одноразовый");
        }

        device = ALC10.alcOpenDevice((ByteBuffer) null);
        if (device == MemoryUtil.NULL) {
            throw new IllegalStateException(
                    "OpenAL alcOpenDevice(null) вернул NULL — нет аудиоустройства; "
                  + "используйте --no-audio или ALSOFT_DRIVERS=null");
        }

        alcCaps = ALC.createCapabilities(device);

        context = ALC10.alcCreateContext(device, (IntBuffer) null);
        if (context == MemoryUtil.NULL) {
            int alcErr = ALC10.alcGetError(device);
            ALC10.alcCloseDevice(device);
            device = MemoryUtil.NULL;
            throw new IllegalStateException(
                    "OpenAL alcCreateContext вернул NULL (alcGetError=0x"
                  + Integer.toHexString(alcErr) + ")");
        }
        if (!ALC10.alcMakeContextCurrent(context)) {
            int alcErr = ALC10.alcGetError(device);
            ALC10.alcDestroyContext(context);
            ALC10.alcCloseDevice(device);
            context = MemoryUtil.NULL;
            device  = MemoryUtil.NULL;
            throw new IllegalStateException(
                    "OpenAL alcMakeContextCurrent failed (alcGetError=0x"
                  + Integer.toHexString(alcErr) + ")");
        }

        alCaps = AL.createCapabilities(alcCaps);

        String name = ALC10.alcGetString(device, ALC10.ALC_DEVICE_SPECIFIER);
        deviceName = name != null && !name.isEmpty() ? name : "<unknown>";
        alVendor   = nullToEmpty(AL10.alGetString(AL10.AL_VENDOR));
        alRenderer = nullToEmpty(AL10.alGetString(AL10.AL_RENDERER));
        alVersion  = nullToEmpty(AL10.alGetString(AL10.AL_VERSION));

        initialized = true;
    }

    @Override
    public void dispose() {
        if (disposed) return;
        disposed = true;
        if (initialized) {
            stopAll();
            // Освобождаем cached ALBuffer'ы — owner-ом по контракту
            // playSound является backend, а не отдельные source'ы.
            for (Integer buf : bufferCache.values()) {
                AL10.alDeleteBuffers(buf);
            }
            bufferCache.clear();
            ALC10.alcMakeContextCurrent(MemoryUtil.NULL);
            if (context != MemoryUtil.NULL) {
                ALC10.alcDestroyContext(context);
                context = MemoryUtil.NULL;
            }
            if (device != MemoryUtil.NULL) {
                ALC10.alcCloseDevice(device);
                device = MemoryUtil.NULL;
            }
            initialized = false;
        }
    }

    /**
     * Привязать загрузчик контента и OGG-декодер. Без этого
     * {@link #playSound(AssetHandle, AudioCategory, boolean)} не работает.
     *
     * <p>Идемпотентно для одинаковых аргументов; повторный bind с другими
     * экземплярами бросает {@link IllegalStateException} (контракт сервис-локатора —
     * один loader/decoder на весь жизненный цикл backend'а).
     */
    public void bindContentPipeline(ContentLoader loader, OggVorbisAudioDecoder decoder) {
        Objects.requireNonNull(loader, "loader");
        Objects.requireNonNull(decoder, "decoder");
        if (this.contentLoader != null && this.contentLoader != loader) {
            throw new IllegalStateException("ContentLoader уже привязан другой; rebind запрещён");
        }
        if (this.oggDecoder != null && this.oggDecoder != decoder) {
            throw new IllegalStateException("OggVorbisAudioDecoder уже привязан другой; rebind запрещён");
        }
        this.contentLoader = loader;
        this.oggDecoder    = decoder;
    }

    /** {@code true} если pipeline для playSound(AssetHandle) активен. */
    public boolean isContentPipelineBound() {
        return contentLoader != null && oggDecoder != null;
    }

    /** Размер buffer cache (для логов / smoke). */
    public int cachedBufferCount() {
        return bufferCache.size();
    }

    // -- info ------------------------------------------------------------

    /** Имя выбранного аудио-устройства (после {@link #init()}). */
    public String deviceName()  { return deviceName; }
    /** AL_VENDOR (после {@link #init()}). */
    public String alVendor()    { return alVendor; }
    /** AL_RENDERER (после {@link #init()}). */
    public String alRenderer()  { return alRenderer; }
    /** AL_VERSION (после {@link #init()}). */
    public String alVersion()   { return alVersion; }
    /** Список активных source'ов (snapshot). */
    public List<AudioSourceHandle> activeSources() {
        return Collections.unmodifiableList(new ArrayList<>(activeSources.values()));
    }

    // -- AudioBackend public API -----------------------------------------

    @Override
    public AudioSourceHandle playSound(AssetHandle asset, AudioCategory category, boolean looping) {
        Objects.requireNonNull(asset, "asset");
        Objects.requireNonNull(category, "category");
        ensureInit();
        if (asset.category() != AssetCategory.AUDIO) {
            throw new IllegalArgumentException(
                    "playSound принимает только AssetCategory.AUDIO; получено "
                  + asset.category() + " (id=\"" + asset.id() + "\")");
        }
        if (!isContentPipelineBound()) {
            throw new IllegalStateException(
                    "OpenAlAudioBackend.playSound(AssetHandle) требует bindContentPipeline("
                  + "ContentLoader, OggVorbisAudioDecoder); вызовите его на boot. "
                  + "asset=\"" + asset.id() + "\"");
        }

        int alBuffer = bufferCache.computeIfAbsent(asset.id(), id -> uploadOggToAlBuffer(asset));

        int source = AL10.alGenSources();
        checkAl("alGenSources");
        AL10.alSourcei(source, AL10.AL_BUFFER, alBuffer);
        AL10.alSourcef(source, AL10.AL_GAIN, effectiveVolume(category));
        AL10.alSourcef(source, AL10.AL_PITCH, 1.0f);
        AL10.alSourcei(source, AL10.AL_LOOPING, looping ? AL10.AL_TRUE : AL10.AL_FALSE);
        AL10.alSourcePlay(source);
        checkAl("alSourcePlay");

        long id = nextId.getAndIncrement();
        // ownsBuffer=false: buffer хранится в bufferCache, удалит его dispose() backend'а
        OpenAlSourceHandle handle = new OpenAlSourceHandle(this, id, source, alBuffer, category, false);
        activeSources.put(id, handle);
        return handle;
    }

    /** Загрузить OGG asset → декодировать → создать и заполнить ALBuffer. */
    private int uploadOggToAlBuffer(AssetHandle asset) {
        ByteBuffer oggBytes = contentLoader.readSync(asset);
        OggVorbisAudioDecoder.DecodedAudio decoded = null;
        int buffer = 0;
        try {
            decoded = oggDecoder.decodeMemory(oggBytes);
            buffer = AL10.alGenBuffers();
            checkAl("alGenBuffers");
            int alFormat = decoded.channels() == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
            AL10.alBufferData(buffer, alFormat, decoded.pcm(), decoded.sampleRate());
            checkAl("alBufferData(" + asset.id() + ")");
            return buffer;
        } catch (RuntimeException e) {
            if (buffer != 0) {
                AL10.alDeleteBuffers(buffer);
            }
            throw e;
        } finally {
            if (decoded != null) {
                decoded.free();
            }
        }
    }

    @Override
    public void setCategoryVolume(AudioCategory category, float volume) {
        Objects.requireNonNull(category, "category");
        float clamped = volume < 0f ? 0f : (volume > 1f ? 1f : volume);
        mixers.get(category).volume = clamped;
        applyMixerToActiveSources(category);
    }

    @Override
    public void setCategoryMuted(AudioCategory category, boolean muted) {
        Objects.requireNonNull(category, "category");
        mixers.get(category).muted = muted;
        applyMixerToActiveSources(category);
    }

    /** Текущее значение громкости категории. */
    public float categoryVolume(AudioCategory category) {
        return mixers.get(Objects.requireNonNull(category)).volume;
    }

    /** Текущее значение mute категории. */
    public boolean categoryMuted(AudioCategory category) {
        return mixers.get(Objects.requireNonNull(category)).muted;
    }

    @Override
    public void pauseAll() {
        if (!initialized) return;
        for (OpenAlSourceHandle h : activeSources.values()) {
            int state = AL10.alGetSourcei(h.alSource(), AL10.AL_SOURCE_STATE);
            if (state == AL10.AL_PLAYING) {
                AL10.alSourcePause(h.alSource());
            }
        }
    }

    @Override
    public void resumeAll() {
        if (!initialized) return;
        for (OpenAlSourceHandle h : activeSources.values()) {
            int state = AL10.alGetSourcei(h.alSource(), AL10.AL_SOURCE_STATE);
            if (state == AL10.AL_PAUSED) {
                AL10.alSourcePlay(h.alSource());
            }
        }
    }

    @Override
    public void stopAll() {
        if (!initialized) return;
        // Снимаем snapshot, потому что dispose() источника убирает запись из map.
        List<OpenAlSourceHandle> snapshot = new ArrayList<>(activeSources.values());
        for (OpenAlSourceHandle h : snapshot) {
            h.dispose();
        }
        activeSources.clear();
    }

    // -- smoke / synthetic playback (stage 2.3) --------------------------

    /**
     * Сгенерировать и проиграть синусоидальный mono PCM (16-bit signed) — это
     * smoke-метод для headless верификации полного OpenAL pipeline без
     * необходимости реального OGG/WAV asset'а.
     *
     * <p>Сэмплрейт фиксирован {@code 44100 Hz}. Громкость берётся из mixer'а
     * указанной категории (учитывается mute).
     *
     * @param frequencyHz     частота тона, Гц (например, {@code 440f} = A4)
     * @param durationSeconds длительность, секунды (должно быть {@code > 0})
     * @param category        категория для mixer'а
     * @return дескриптор активного источника; {@link AudioSourceHandle#isActive()}
     *         вернёт {@code false} после естественного окончания
     */
    public OpenAlSourceHandle playSineWaveSmoke(float frequencyHz,
                                                float durationSeconds,
                                                AudioCategory category) {
        Objects.requireNonNull(category, "category");
        if (frequencyHz <= 0f) {
            throw new IllegalArgumentException("frequencyHz must be > 0, got " + frequencyHz);
        }
        if (durationSeconds <= 0f) {
            throw new IllegalArgumentException("durationSeconds must be > 0, got " + durationSeconds);
        }
        ensureInit();

        final int sampleRate = 44100;
        int sampleCount = (int) Math.max(1, Math.ceil(sampleRate * durationSeconds));

        ShortBuffer pcm = MemoryUtil.memAllocShort(sampleCount);
        int buffer = 0;
        int source = 0;
        try {
            double phase = 0.0;
            double phaseInc = 2.0 * Math.PI * frequencyHz / sampleRate;
            for (int i = 0; i < sampleCount; i++) {
                pcm.put(i, (short) (Math.sin(phase) * 0.5 * Short.MAX_VALUE));
                phase += phaseInc;
            }
            pcm.position(0).limit(sampleCount);

            buffer = AL10.alGenBuffers();
            checkAl("alGenBuffers");
            AL10.alBufferData(buffer, AL10.AL_FORMAT_MONO16, pcm, sampleRate);
            checkAl("alBufferData");

            source = AL10.alGenSources();
            checkAl("alGenSources");
            AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
            AL10.alSourcef(source, AL10.AL_GAIN, effectiveVolume(category));
            AL10.alSourcef(source, AL10.AL_PITCH, 1.0f);
            AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_FALSE);
            AL10.alSourcePlay(source);
            checkAl("alSourcePlay");

            long id = nextId.getAndIncrement();
            // ownsBuffer=true: synthetic PCM не шарится через cache, source чистит сам
            OpenAlSourceHandle handle = new OpenAlSourceHandle(this, id, source, buffer, category, true);
            activeSources.put(id, handle);
            return handle;
        } catch (RuntimeException e) {
            // Чистка частично созданных handle'ов на случай ошибки в середине.
            if (source != 0) {
                AL10.alSourceStop(source);
                AL10.alDeleteSources(source);
            }
            if (buffer != 0) {
                AL10.alDeleteBuffers(buffer);
            }
            throw e;
        } finally {
            MemoryUtil.memFree(pcm);
        }
    }

    /**
     * Удобная утилита для headless smoke: ждёт перехода source в AL_STOPPED
     * не дольше {@code timeoutMillis}. Возвращает {@code true} если дождались
     * STOPPED, {@code false} — если таймаут (источник всё ещё PLAYING/PAUSED).
     *
     * <p>Реализован busy-wait с {@code Thread.sleep(2)} между опросами —
     * Stage 2.3 это headless smoke, реальный mixing thread приедет на stage 3.x.
     */
    public boolean awaitSourceStopped(OpenAlSourceHandle handle, long timeoutMillis) {
        Objects.requireNonNull(handle, "handle");
        ensureInit();
        long deadline = System.currentTimeMillis() + Math.max(0, timeoutMillis);
        while (System.currentTimeMillis() < deadline) {
            int state = AL10.alGetSourcei(handle.alSource(), AL10.AL_SOURCE_STATE);
            if (state == AL10.AL_STOPPED || state == AL10.AL_INITIAL) {
                return true;
            }
            try {
                Thread.sleep(2L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /** Инспекция текущего состояния источника (PLAYING / PAUSED / STOPPED / INITIAL). */
    public int sourceState(OpenAlSourceHandle handle) {
        Objects.requireNonNull(handle, "handle");
        ensureInit();
        return AL10.alGetSourcei(handle.alSource(), AL10.AL_SOURCE_STATE);
    }

    // -- internal --------------------------------------------------------

    /**
     * Вызывается из {@link OpenAlSourceHandle#dispose()} — освобождает source
     * и buffer и убирает запись из {@link #activeSources}.
     */
    void releaseSource(OpenAlSourceHandle handle) {
        if (!initialized) return;
        AL10.alSourceStop(handle.alSource());
        AL10.alSourcei(handle.alSource(), AL10.AL_BUFFER, 0);
        AL10.alDeleteSources(handle.alSource());
        // Buffer удаляем только если source владеет им эксклюзивно;
        // для cached buffer'ов чистка проходит в dispose() backend'а.
        if (handle.ownsBuffer()) {
            AL10.alDeleteBuffers(handle.alBuffer());
        }
        activeSources.remove(handle.id());
    }

    private void applyMixerToActiveSources(AudioCategory category) {
        if (!initialized) return;
        float vol = effectiveVolume(category);
        for (OpenAlSourceHandle h : activeSources.values()) {
            if (h.category() == category) {
                AL10.alSourcef(h.alSource(), AL10.AL_GAIN, vol);
            }
        }
    }

    private float effectiveVolume(AudioCategory category) {
        CategoryMixer m = mixers.get(category);
        return m.muted ? 0f : m.volume;
    }

    private void ensureInit() {
        if (!initialized) {
            throw new IllegalStateException("OpenAlAudioBackend.init() не вызывался");
        }
        if (disposed) {
            throw new IllegalStateException("OpenAlAudioBackend уже dispose'нут");
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static void checkAl(String op) {
        int err = AL10.alGetError();
        if (err != AL10.AL_NO_ERROR) {
            throw new IllegalStateException(
                    "OpenAL " + op + " failed: alGetError=0x" + Integer.toHexString(err));
        }
    }

    private static final class CategoryMixer {
        float volume = 1.0f;
        boolean muted = false;
    }
}
