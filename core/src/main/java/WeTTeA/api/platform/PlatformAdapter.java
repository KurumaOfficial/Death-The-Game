package WeTTeA.api.platform;

import WeTTeA.api.audio.AudioBackend;
import WeTTeA.api.input.InputBackend;
import WeTTeA.api.lifecycle.Disposable;
import WeTTeA.api.render.RenderBackend;

/**
 * Контракт между core и платформой.
 *
 * <p>Adapter создаётся в платформенном модуле ({@code :desktop} —
 * {@code DesktopPlatformAdapter}; {@code :android} — {@code AndroidPlatformAdapter};
 * {@code :ios} — {@code IosPlatformAdapter}) и передаётся в {@code GameApp}
 * как единственный мост к платформе.
 *
 * <p>Adapter <b>владеет</b> и {@link RenderBackend}, и {@link InputBackend},
 * и {@link AudioBackend}, и {@link PlatformFileSystem} — все эти ресурсы
 * освобождаются при {@link #dispose()} adapter'а.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public interface PlatformAdapter extends Disposable {

    /** Сводная информация о платформе. */
    PlatformInfo info();

    /** Backend рендера, выбранный платформой по предпочтению Vulkan-first. */
    RenderBackend renderBackend();

    /** Backend ввода. */
    InputBackend inputBackend();

    /** Backend звука. */
    AudioBackend audioBackend();

    /** Доступ к файловой системе и assets. */
    PlatformFileSystem fileSystem();

    /** Колбэки жизненного цикла приложения от платформы. */
    AppLifecycle lifecycleListener();
}
