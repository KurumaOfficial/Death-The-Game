/**
 * Контракты подсистемы звука.
 *
 * <p>Реализации backend'ов:
 * <ul>
 *   <li>{@code WeTTeA.audio.openal.OpenAlAudioBackend} — desktop через LWJGL3 OpenAL;</li>
 *   <li>{@code WeTTeA.audio.aaudio.AAudioBackend} — Android через AAudio NDK;</li>
 *   <li>{@code WeTTeA.audio.coreaudio.CoreAudioBackend} — iOS через AVAudioEngine.</li>
 * </ul>
 *
 * <p>Все эти типы — в платформенных модулях, core их не импортирует.
 *
 * @author Kuruma
 * @since 0.1.0
 */
package WeTTeA.api.audio;
