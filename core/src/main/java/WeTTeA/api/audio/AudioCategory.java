package WeTTeA.api.audio;

/**
 * Категории звука с независимыми громкостями и mute-состоянием.
 *
 * <p>Используется UI настроек звука, событиями паузы (mute MUSIC/SFX/AMBIENT,
 * keep VOICE/UI) и системой narrative диалога.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public enum AudioCategory {

    /** Музыка / soundtrack. */
    MUSIC,

    /** Боевые SFX, оружие, атаки, попадания. */
    SFX,

    /** Окружение — ветер, шаги, шум локации. */
    AMBIENT,

    /** Голосовая озвучка narrative диалогов. */
    VOICE,

    /** Звуки UI — клики, переключения, нотификации. */
    UI
}
