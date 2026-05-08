/**
 * Desktop платформенная реализация (Windows / Linux / macOS).
 *
 * <p>Содержит:
 * <ul>
 *   <li>{@link WeTTeA.platform.desktop.DesktopLauncher} — точка входа JVM
 *       приложения для desktop-сборки;</li>
 *   <li>{@link WeTTeA.platform.desktop.DesktopPlatformInfo} — реализация
 *       {@link WeTTeA.api.platform.PlatformInfo} с детектом ОС и архитектуры;</li>
 *   <li>{@link WeTTeA.platform.desktop.DesktopPlatformAdapter} — реализация
 *       {@link WeTTeA.api.platform.PlatformAdapter} (orientation, display info,
 *       заглушки для лайфцикла, который desktop трактует через GLFW callbacks);</li>
 *   <li>{@link WeTTeA.platform.desktop.DesktopPlatformFileSystem} — реализация
 *       {@link WeTTeA.api.platform.PlatformFileSystem} поверх стандартных
 *       XDG / AppData директорий;</li>
 *   <li>{@link WeTTeA.platform.desktop.GlfwWindow} — обёртка над GLFW окном;</li>
 *   <li>{@link WeTTeA.platform.desktop.VulkanInstanceBootstrap} — создание
 *       VkInstance через LWJGL и запрос capabilities (smoke-стадия 1).</li>
 * </ul>
 *
 * <p>Stage 1: launcher выполняет boot core, открывает GLFW окно, создаёт
 * VkInstance и сразу завершает работу (smoke-режим). Полный render-pass /
 * swapchain / event-pump поверх этого добавляется на стадии 2.
 *
 * @author Kuruma
 * @since 0.1.0
 */
package WeTTeA.platform.desktop;
