/**
 * Cross-platform render core: камеры, фабрики геометрии, frame state,
 * не зависящие от Vulkan/Metal/OpenGL backend'а.
 *
 * <p>На stage E.1 здесь живут:
 * <ul>
 *   <li>{@link WeTTeA.core.render.PerspectiveCamera} —
 *       JOML-based perspective camera, реализующая {@link WeTTeA.api.render.Camera};</li>
 *   <li>{@link WeTTeA.core.render.CubeMeshFactory} —
 *       процедурный генератор единичного куба (24 vertex + 36 index) для
 *       загрузки в vertex/index buffer'ы backend'а.</li>
 * </ul>
 *
 * <p>Запрещено в этом пакете импортировать:
 * <ul>
 *   <li>{@code org.lwjgl.*} — это конкретный Vulkan/GLFW backend, живёт в :desktop;</li>
 *   <li>Android SDK — :android модуль;</li>
 *   <li>RoboVM/UIKit — :ios модуль.</li>
 * </ul>
 *
 * @author Kuruma
 * @since 0.1.0
 */
package WeTTeA.core.render;
