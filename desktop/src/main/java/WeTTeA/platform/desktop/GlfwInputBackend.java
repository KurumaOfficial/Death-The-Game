package WeTTeA.platform.desktop;

import WeTTeA.api.input.InputAxis;
import WeTTeA.api.input.InputBackend;
import WeTTeA.api.input.InputSource;
import WeTTeA.api.input.RawInputEvent;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.glfw.GLFWScrollCallback;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Десктопный {@link InputBackend} поверх GLFW callbacks.
 *
 * <p>Регистрирует 4 callback'а на окне и буферизует события в очередь
 * {@link ArrayDeque}. {@code GLFW.glfwPollEvents()} вызывается на main thread,
 * поэтому очередь не нуждается в внешней синхронизации (GLFW гарантирует,
 * что callbacks выполнятся именно из этого вызова).
 *
 * <p>{@link #pollEvents(RawInputEventConsumer)} вызывается из game-loop'а в
 * начале кадра — он вытаскивает все накопленные события и отдаёт в
 * {@code InputRouter}. После этого очередь пуста.
 *
 * <p>Маппинг GLFW → {@link RawInputEvent}:
 * <ul>
 *   <li>Key callback ({@code GLFW_PRESS} / {@code GLFW_RELEASE}) →
 *       {@link RawInputEvent#button(InputSource, int, boolean, long)} с
 *       {@link InputSource#KEYBOARD}; {@code GLFW_REPEAT} игнорируется
 *       (роутер сам решает, считать ли длительное удержание новым событием).</li>
 *   <li>Mouse button callback → то же самое с {@link InputSource#MOUSE};
 *       значения GLFW кодов кнопок (0/1/2) уже совпадают с
 *       {@code WeTTeA.api.input.KeyCodes.MOUSE_BUTTON_*}.</li>
 *   <li>Cursor pos callback → {@link RawInputEvent#pointer(InputSource, int, boolean, float, float, long)}
 *       с {@code keyCode = -1}, {@code pressed = false}.</li>
 *   <li>Scroll callback → {@link RawInputEvent#axis(InputSource, InputAxis, float, long)}
 *       с {@link InputAxis#CAMERA_ZOOM}, {@code value = scrollY}.</li>
 * </ul>
 *
 * <p><b>Lifecycle.</b> Конструктор сохраняет хендлы callback'ов; {@link #dispose()}
 * освобождает их через {@code Callback.free()} — обязательное требование LWJGL.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class GlfwInputBackend implements InputBackend {

    private final long windowHandle;
    private final long bootNanos;
    private final Deque<RawInputEvent> queue = new ArrayDeque<>(64);

    private GLFWKeyCallback keyCb;
    private GLFWMouseButtonCallback mouseCb;
    private GLFWCursorPosCallback cursorCb;
    private GLFWScrollCallback scrollCb;
    private boolean disposed;

    public GlfwInputBackend(long windowHandle) {
        if (windowHandle == 0L) {
            throw new IllegalArgumentException("windowHandle == NULL");
        }
        this.windowHandle = windowHandle;
        this.bootNanos = System.nanoTime();

        keyCb = GLFW.glfwSetKeyCallback(windowHandle, this::onKey);
        mouseCb = GLFW.glfwSetMouseButtonCallback(windowHandle, this::onMouseButton);
        cursorCb = GLFW.glfwSetCursorPosCallback(windowHandle, this::onCursorPos);
        scrollCb = GLFW.glfwSetScrollCallback(windowHandle, this::onScroll);
    }

    @Override
    public void pollEvents(RawInputEventConsumer consumer) {
        if (consumer == null) {
            throw new IllegalArgumentException("consumer == null");
        }
        // Сами callbacks уже сработали в GLFW.glfwPollEvents() (вызывается
        // в DesktopLauncher) — события уже в очереди.
        while (!queue.isEmpty()) {
            consumer.accept(queue.pollFirst());
        }
    }

    @Override
    public void captureMouse(boolean capture) {
        GLFW.glfwSetInputMode(
                windowHandle,
                GLFW.GLFW_CURSOR,
                capture ? GLFW.GLFW_CURSOR_DISABLED : GLFW.GLFW_CURSOR_NORMAL);
    }

    @Override
    public void showVirtualKeyboard(boolean show) {
        // Десктоп — no-op. Виртуальная клавиатура актуальна только на mobile.
    }

    @Override
    public void dispose() {
        if (disposed) return;
        disposed = true;
        if (keyCb != null) { keyCb.free(); keyCb = null; }
        if (mouseCb != null) { mouseCb.free(); mouseCb = null; }
        if (cursorCb != null) { cursorCb.free(); cursorCb = null; }
        if (scrollCb != null) { scrollCb.free(); scrollCb = null; }
        queue.clear();
    }

    /** Ручной push — для headless-smoke и юнит-тестов без открытого окна. */
    public void enqueueSynthetic(RawInputEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event == null");
        }
        queue.addLast(event);
    }

    // -- GLFW callbacks --------------------------------------------------------

    private void onKey(long window, int key, int scancode, int action, int mods) {
        if (action == GLFW.GLFW_REPEAT) return;
        boolean pressed = action == GLFW.GLFW_PRESS;
        queue.addLast(RawInputEvent.button(InputSource.KEYBOARD, key, pressed, timestamp()));
    }

    private void onMouseButton(long window, int button, int action, int mods) {
        boolean pressed = action == GLFW.GLFW_PRESS;
        queue.addLast(RawInputEvent.button(InputSource.MOUSE, button, pressed, timestamp()));
    }

    private void onCursorPos(long window, double xpos, double ypos) {
        queue.addLast(RawInputEvent.pointer(InputSource.MOUSE, -1, false, (float) xpos, (float) ypos, timestamp()));
    }

    private void onScroll(long window, double xoffset, double yoffset) {
        queue.addLast(RawInputEvent.axis(InputSource.MOUSE, InputAxis.CAMERA_ZOOM, (float) yoffset, timestamp()));
    }

    private long timestamp() {
        return System.nanoTime() - bootNanos;
    }
}
