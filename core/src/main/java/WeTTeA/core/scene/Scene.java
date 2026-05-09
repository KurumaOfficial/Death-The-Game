package WeTTeA.core.scene;

import WeTTeA.api.lifecycle.Disposable;
import WeTTeA.api.lifecycle.Drawable;
import WeTTeA.api.lifecycle.Tickable;

/**
 * Сцена — единица gameplay state (главное меню, бой, world view, диалог).
 *
 * <p>Тики и render идут через {@link Tickable} и {@link Drawable}.
 * Только верхняя сцена в {@link SceneManager} получает {@code tick}/{@code draw}
 * каждый кадр; нижние «заморожены» до возврата на верх стека.
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>{@link #onEnter()} — вызывается один раз при push на верх стека (новая сцена).</li>
 *   <li>{@link #onPause()} — вызывается, когда поверх неё push'ится другая сцена
 *       (текущая «уходит вниз», временно перестаёт получать tick/draw). По умолчанию no-op.</li>
 *   <li>{@link #onResume()} — вызывается, когда сверху pop'ается сцена и текущая
 *       снова становится top. По умолчанию no-op.</li>
 *   <li>{@link #onExit()} — вызывается при pop самой сцены, перед {@link Disposable#dispose()}.</li>
 * </ol>
 *
 * <p>Stage 3.2: pause/resume — default no-op, чтобы существующие сцены (Stage 1 stub'ы)
 * собирались без правок. Реальные сцены (главное меню, боёвка) переопределят эти методы
 * для остановки своих таймеров, музыки, ECS scheduler'а и т.п.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public interface Scene extends Tickable, Drawable, Disposable {

    /**
     * Вызывается ОДИН раз при push сцены на стек.
     * Здесь — выделение ресурсов, регистрация input listener'ов, запуск музыки.
     */
    void onEnter();

    /**
     * Вызывается ОДИН раз при pop сцены со стека (перед {@link Disposable#dispose()}).
     * Здесь — отмена подписок, остановка таймеров, сохранение прогресса.
     */
    void onExit();

    /**
     * Вызывается, когда поверх сцены push'ится другая сцена и текущая
     * становится «не-top». Сцена больше не получает tick/draw до
     * следующего {@link #onResume()}. По умолчанию no-op.
     *
     * <p>Реальные применения: остановить background music у battle-сцены при
     * открытии inventory, закешировать состояние симуляции, поставить ECS на паузу.
     */
    default void onPause() {
    }

    /**
     * Вызывается, когда сцена возвращается на top (выше неё что-то pop'нули).
     * После этого вызова она снова получает tick/draw. По умолчанию no-op.
     *
     * <p>Реальные применения: возобновить music, разморозить ECS scheduler,
     * сбросить стейт input router'а если он был мутирован inventory-сценой.
     */
    default void onResume() {
    }
}
