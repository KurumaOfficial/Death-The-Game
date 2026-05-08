package WeTTeA.platform.desktop;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.system.MemoryUtil;

/**
 * Тонкая обёртка над GLFW окном.
 *
 * <p>Создаёт окно без OpenGL контекста ({@code GLFW_NO_API}) — Vulkan создаст
 * {@code VkSurfaceKHR} поверх этого окна на стадии 2. На стадии 1 окно
 * открывается, отображается и закрывается без рендера — это smoke-тест
 * GLFW + LWJGL native loading.
 *
 * <p>Жизненный цикл:
 * <pre>
 *     init() → handle() → centerOnPrimaryMonitor()
 *     pollEvents() в loop
 *     dispose() в конце
 * </pre>
 *
 * <p>Не потокобезопасен; все вызовы — из main thread (требование GLFW).
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class GlfwWindow {

    private final int width;
    private final int height;
    private final String title;
    private long handle = MemoryUtil.NULL;
    private GLFWErrorCallback errorCallback;

    public GlfwWindow(int width, int height, String title) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("width/height must be > 0");
        }
        this.width  = width;
        this.height = height;
        this.title  = title;
    }

    public void init() {
        errorCallback = GLFWErrorCallback.createPrint(System.err);
        GLFW.glfwSetErrorCallback(errorCallback);

        if (!GLFW.glfwInit()) {
            throw new IllegalStateException("GLFW init failed");
        }

        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_NO_API);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE,  GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,    GLFW.GLFW_FALSE);

        handle = GLFW.glfwCreateWindow(width, height, title, MemoryUtil.NULL, MemoryUtil.NULL);
        if (handle == MemoryUtil.NULL) {
            throw new IllegalStateException("GLFW window create failed");
        }
    }

    public void centerOnPrimaryMonitor() {
        long monitor = GLFW.glfwGetPrimaryMonitor();
        if (monitor == MemoryUtil.NULL) return;
        GLFWVidMode vid = GLFW.glfwGetVideoMode(monitor);
        if (vid == null) return;
        GLFW.glfwSetWindowPos(handle, (vid.width() - width) / 2, (vid.height() - height) / 2);
    }

    public void show() {
        GLFW.glfwShowWindow(handle);
    }

    public boolean shouldClose() {
        return GLFW.glfwWindowShouldClose(handle);
    }

    public void requestClose() {
        GLFW.glfwSetWindowShouldClose(handle, true);
    }

    public void pollEvents() {
        GLFW.glfwPollEvents();
    }

    public long handle() {
        return handle;
    }

    public void dispose() {
        if (handle != MemoryUtil.NULL) {
            GLFW.glfwDestroyWindow(handle);
            handle = MemoryUtil.NULL;
        }
        GLFW.glfwTerminate();
        if (errorCallback != null) {
            errorCallback.free();
            errorCallback = null;
        }
    }
}
