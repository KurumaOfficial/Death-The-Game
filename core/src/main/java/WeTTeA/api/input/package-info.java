/**
 * Абстракция input action'ов и платформенных источников ввода.
 *
 * <p>Архитектурный поток:
 * <pre>
 * raw input (GLFW key/MotionEvent/UIKit touch/Gamepad)
 *      │
 *      ▼
 * platform input layer       (модули :desktop / :android / :ios)
 *      │
 *      ▼
 * RawInputEvent              ({@link WeTTeA.api.input.RawInputEvent})
 *      │
 *      ▼
 * InputRouter (core)         связывает события и {@link WeTTeA.api.input.InputAction}
 *      │
 *      ▼
 * gameplay / UI слои         подписаны через {@link WeTTeA.api.input.ActionEventListener}
 * </pre>
 *
 * <p>Запрещено:
 * <ul>
 *   <li>работать с raw input напрямую в gameplay/UI;</li>
 *   <li>смешивать UI input и gameplay input без слоя маршрутизации
 *       по {@link WeTTeA.api.input.InputContext};</li>
 *   <li>хардкодить клавиши/кнопки в логике сущностей.</li>
 * </ul>
 *
 * @author Kuruma
 * @since 0.1.0
 */
package WeTTeA.api.input;
