package WeTTeA.core.scene;

import WeTTeA.api.lifecycle.Disposable;
import WeTTeA.api.lifecycle.Drawable;
import WeTTeA.api.lifecycle.Tickable;

/**
 * Сцена — единица gameplay state (главное меню, бой, world view, диалог).
 *
 * <p>Тики и render идут через {@link Tickable} и {@link Drawable}.
 * При смене сцены вызывается {@link #onEnter()} / {@link #onExit()};
 * освобождение ресурсов — через {@link Disposable#dispose()}.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public interface Scene extends Tickable, Drawable, Disposable {

    void onEnter();

    void onExit();
}
