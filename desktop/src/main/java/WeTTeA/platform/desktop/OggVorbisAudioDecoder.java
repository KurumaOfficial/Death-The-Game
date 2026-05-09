package WeTTeA.platform.desktop;

import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.Objects;

/**
 * Декодер OGG Vorbis → 16-bit signed PCM (interleaved для stereo).
 *
 * <p>Реализация — поверх {@code stb_vorbis} (LWJGL bind в
 * {@link org.lwjgl.stb.STBVorbis}). Поддерживает два API:
 * <ul>
 *   <li>{@link #decodeMemory(ByteBuffer)} — fast path,
 *       {@code stb_vorbis_decode_memory}: читает весь файл целиком,
 *       возвращает PCM в {@link DecodedAudio};</li>
 *   <li>{@link #probeMemory(ByteBuffer)} — открытие потока через
 *       {@code stb_vorbis_open_memory} только ради метаданных
 *       (channels, sample_rate, length_in_samples) — без декода. Полезно
 *       для streaming на stage 3.x.</li>
 * </ul>
 *
 * <p><b>Direct buffer.</b> Метод требует вход в виде direct
 * {@link ByteBuffer} (как и контракт {@code stb_vorbis_decode_memory}).
 * Возвращаемый {@link DecodedAudio#pcm()} — direct {@link ShortBuffer},
 * выделенный stb через native malloc; владелец обязан освободить его
 * через {@link DecodedAudio#free()} (см. {@link DecodedAudio}).
 *
 * <p><b>Stateless.</b> Декодер не хранит состояние и safe для shared
 * use из любого потока — внутреннее состояние держит только stb_vorbis
 * во время вызова.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class OggVorbisAudioDecoder {

    /**
     * Декодировать целиком OGG Vorbis из direct {@link ByteBuffer} в PCM.
     *
     * @param oggBytes сырые байты OGG; должен быть direct buffer и position
     *                 указывать на начало данных, limit — на конец
     * @return результат декода — обернёт {@link ShortBuffer} с PCM
     * @throws IllegalArgumentException если buffer не direct
     * @throws RuntimeException         если stb вернул ошибку (битый OGG)
     */
    public DecodedAudio decodeMemory(ByteBuffer oggBytes) {
        Objects.requireNonNull(oggBytes, "oggBytes");
        if (!oggBytes.isDirect()) {
            throw new IllegalArgumentException(
                    "stb_vorbis_decode_memory требует direct ByteBuffer; "
                  + "получено heap buffer (исправить ContentLoader)");
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer channelsOut   = stack.mallocInt(1);
            IntBuffer sampleRateOut = stack.mallocInt(1);

            ShortBuffer pcm = STBVorbis.stb_vorbis_decode_memory(
                    oggBytes, channelsOut, sampleRateOut);
            if (pcm == null) {
                throw new RuntimeException(
                        "stb_vorbis_decode_memory вернул NULL — битый или не-OGG поток");
            }

            int channels   = channelsOut.get(0);
            int sampleRate = sampleRateOut.get(0);
            if (channels != 1 && channels != 2) {
                MemoryUtil.memFree(pcm);
                throw new RuntimeException(
                        "Поддерживаются только mono/stereo OGG, получили channels=" + channels);
            }
            if (sampleRate <= 0) {
                MemoryUtil.memFree(pcm);
                throw new RuntimeException("Невалидный sampleRate=" + sampleRate + " из stb_vorbis");
            }
            // pcm.remaining() == samplesPerChannel * channels (interleaved).
            int totalSamples = pcm.remaining();
            int samplesPerChannel = totalSamples / channels;
            return new DecodedAudio(pcm, channels, sampleRate, samplesPerChannel);
        }
    }

    /**
     * Только метаданные — без декода. Полезно для streaming/UI ("длительность
     * трека"). Возвращает {@code null} если файл не открылся.
     */
    public ProbeInfo probeMemory(ByteBuffer oggBytes) {
        Objects.requireNonNull(oggBytes, "oggBytes");
        if (!oggBytes.isDirect()) {
            throw new IllegalArgumentException(
                    "stb_vorbis_open_memory требует direct ByteBuffer");
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer errorOut = stack.mallocInt(1);
            long handle = STBVorbis.stb_vorbis_open_memory(oggBytes, errorOut, null);
            if (handle == MemoryUtil.NULL) {
                return null;
            }
            try (STBVorbisInfo info = STBVorbisInfo.malloc(stack)) {
                STBVorbis.stb_vorbis_get_info(handle, info);
                int samples = STBVorbis.stb_vorbis_stream_length_in_samples(handle);
                return new ProbeInfo(info.channels(), info.sample_rate(), samples);
            } finally {
                STBVorbis.stb_vorbis_close(handle);
            }
        }
    }

    /**
     * Результат полного декода OGG → PCM.
     *
     * <p>Владение: {@link #pcm} выделен через native malloc внутри stb;
     * вызывающий обязан вызвать {@link #free()} после использования
     * (например, после {@code alBufferData}). Повторный {@link #free()}
     * — no-op (защита от двойного освобождения).
     */
    public static final class DecodedAudio {
        private ShortBuffer pcm;
        private final int channels;
        private final int sampleRate;
        private final int samplesPerChannel;

        DecodedAudio(ShortBuffer pcm, int channels, int sampleRate, int samplesPerChannel) {
            this.pcm = pcm;
            this.channels = channels;
            this.sampleRate = sampleRate;
            this.samplesPerChannel = samplesPerChannel;
        }

        /** Interleaved 16-bit signed PCM. */
        public ShortBuffer pcm() {
            if (pcm == null) {
                throw new IllegalStateException("DecodedAudio уже освобождён через free()");
            }
            return pcm;
        }

        public int channels()           { return channels; }
        public int sampleRate()         { return sampleRate; }
        public int samplesPerChannel()  { return samplesPerChannel; }
        /** Длительность в секундах (samplesPerChannel / sampleRate). */
        public double durationSeconds() { return (double) samplesPerChannel / sampleRate; }

        /** Освободить native PCM-буфер. Идемпотентен. */
        public void free() {
            if (pcm != null) {
                MemoryUtil.memFree(pcm);
                pcm = null;
            }
        }
    }

    /** Только метаданные OGG (для streaming/UI, без декода). */
    public record ProbeInfo(int channels, int sampleRate, int samplesPerChannel) {

        public double durationSeconds() {
            return sampleRate > 0 ? (double) samplesPerChannel / sampleRate : 0.0;
        }
    }
}
