/**
 * Контракты платформенного adapter'а.
 *
 * <p>Каждая платформа ({@code :desktop}, {@code :android}, {@code :ios})
 * реализует {@link WeTTeA.api.platform.PlatformAdapter}, а core видит её
 * только через этот контракт. {@code PlatformAdapter} раздаёт core'у:
 * {@link WeTTeA.api.input.InputBackend}, {@link WeTTeA.api.audio.AudioBackend},
 * {@link WeTTeA.api.render.RenderBackend}, {@link WeTTeA.api.platform.PlatformFileSystem}
 * и {@link WeTTeA.api.platform.PlatformInfo}.
 *
 * <p>Запрещено:
 * <ul>
 *   <li>в core импортировать LWJGL/GLFW/Vulkan/Android/UIKit классы;</li>
 *   <li>прокидывать {@code Activity} или {@code UIViewController} в core.</li>
 * </ul>
 *
 * @author Kuruma
 * @since 0.1.0
 */
package WeTTeA.api.platform;
