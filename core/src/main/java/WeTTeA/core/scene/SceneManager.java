package WeTTeA.core.scene;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * Стек сцен core.
 *
 * <p>Поддерживает push/pop/replace. Активна только верхняя сцена; остальные
 * заморожены (не получают tick/render). Менеджер потокобезопасен только в
 * game-thread.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class SceneManager {

    private final Deque<Scene> stack = new ArrayDeque<>();

    public void push(Scene scene) {
        Objects.requireNonNull(scene, "scene");
        stack.push(scene);
        scene.onEnter();
    }

    public void pop() {
        Scene top = stack.peek();
        if (top == null) return;
        top.onExit();
        top.dispose();
        stack.pop();
    }

    public void replace(Scene scene) {
        Objects.requireNonNull(scene, "scene");
        pop();
        push(scene);
    }

    public Scene top() {
        return stack.peek();
    }

    public int depth() {
        return stack.size();
    }
}
