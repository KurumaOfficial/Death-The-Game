package WeTTeA.core.scene;

import WeTTeA.api.render.RenderFrameContext;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Стек сцен core.
 *
 * <p>Поддерживает push/pop/replace со строгой пара-семантикой
 * {@code onPause}/{@code onResume} для нижней сцены и
 * {@code onEnter}/{@code onExit}/{@code dispose} для верхней. Активна
 * только верхняя сцена; остальные «заморожены» (не получают tick/draw).
 *
 * <h3>Sync vs deferred</h3>
 * <ul>
 *   <li>Sync-варианты ({@link #push(Scene)}, {@link #pop()}, {@link #replace(Scene)}) —
 *       применяются немедленно. Использовать ТОЛЬКО вне {@link #tickTop(double)}/
 *       {@link #drawTop(RenderFrameContext)} (например, до запуска цикла или
 *       в gameplay-event handler'е, который выполняется в фазе apply-events
 *       game-loop'а).</li>
 *   <li>Deferred-варианты ({@link #pushDeferred(Scene)}, {@link #popDeferred()},
 *       {@link #replaceDeferred(Scene)}) — складывают операцию в очередь
 *       {@code pending}. Применить пакет можно через {@link #applyPending()}.
 *       Использовать ИЗНУТРИ tick/draw активной сцены, чтобы не мутировать
 *       стек прямо во время итерации (это бы испортило корректность
 *       lifecycle-вызовов и могло привести к рекурсивному
 *       onPause→onEnter→tick на той же сцене).</li>
 * </ul>
 *
 * <h3>Lifecycle invariants</h3>
 * <ul>
 *   <li>{@code push(B)} поверх {@code A}: {@code A.onPause()} → {@code stack.push(B)} →
 *       {@code B.onEnter()}.</li>
 *   <li>{@code pop()} (top = B, под ним A): {@code B.onExit()} →
 *       {@code B.dispose()} → {@code stack.pop()} → {@code A.onResume()}.</li>
 *   <li>{@code replace(B)} (top = A): атомарная замена — {@code A.onExit()} →
 *       {@code A.dispose()} → {@code stack.pop()} → {@code stack.push(B)} →
 *       {@code B.onEnter()}. {@code onPause}/{@code onResume} НЕ вызываются
 *       (нижняя сцена не меняла «не-top» статуса; A уходит насовсем).</li>
 * </ul>
 *
 * <p>Менеджер потокобезопасен только в game-thread.
 *
 * @author Kuruma
 * @since 0.1.0
 */
public final class SceneManager {

    private final Deque<Scene> stack = new ArrayDeque<>();
    private final List<PendingOp> pending = new ArrayList<>();
    private boolean applyingPending;

    /**
     * Применить push сразу. {@code A.onPause()} → push → {@code B.onEnter()}.
     * Безопасно вызывать только ВНЕ tick/draw текущей top-сцены.
     */
    public void push(Scene scene) {
        Objects.requireNonNull(scene, "scene");
        Scene previousTop = stack.peek();
        if (previousTop != null) {
            previousTop.onPause();
        }
        stack.push(scene);
        scene.onEnter();
    }

    /**
     * Применить pop сразу. {@code top.onExit()} → {@code top.dispose()} → pop →
     * {@code below.onResume()} (если есть {@code below}).
     */
    public void pop() {
        Scene top = stack.peek();
        if (top == null) return;
        top.onExit();
        top.dispose();
        stack.pop();
        Scene below = stack.peek();
        if (below != null) {
            below.onResume();
        }
    }

    /**
     * Заменить top: {@code top.onExit()} → {@code top.dispose()} → pop →
     * push(scene) → {@code scene.onEnter()}. Без onPause/onResume — нижняя
     * сцена не меняла своего «не-top» статуса.
     */
    public void replace(Scene scene) {
        Objects.requireNonNull(scene, "scene");
        Scene top = stack.peek();
        if (top != null) {
            top.onExit();
            top.dispose();
            stack.pop();
        }
        stack.push(scene);
        scene.onEnter();
    }

    /** Deferred вариант {@link #push(Scene)}. См. javadoc класса. */
    public void pushDeferred(Scene scene) {
        Objects.requireNonNull(scene, "scene");
        pending.add(new PendingOp(OpKind.PUSH, scene));
    }

    /** Deferred вариант {@link #pop()}. */
    public void popDeferred() {
        pending.add(new PendingOp(OpKind.POP, null));
    }

    /** Deferred вариант {@link #replace(Scene)}. */
    public void replaceDeferred(Scene scene) {
        Objects.requireNonNull(scene, "scene");
        pending.add(new PendingOp(OpKind.REPLACE, scene));
    }

    /**
     * Применить накопленные deferred-операции по FIFO. Вызывать в фазе
     * apply-events game-loop'а (между tick'ами); не вызывать рекурсивно
     * (защищено флагом).
     *
     * @return сколько операций применено
     */
    public int applyPending() {
        if (applyingPending) {
            throw new IllegalStateException(
                    "applyPending() вызван рекурсивно — операцию нельзя применять из onEnter/onExit/onPause/onResume");
        }
        if (pending.isEmpty()) return 0;
        applyingPending = true;
        int applied = 0;
        try {
            for (PendingOp op : pending) {
                switch (op.kind) {
                    case PUSH -> push(op.scene);
                    case POP -> pop();
                    case REPLACE -> replace(op.scene);
                }
                applied++;
            }
            pending.clear();
        } finally {
            applyingPending = false;
        }
        return applied;
    }

    /** Тикнуть только top-сцену. No-op если стек пуст. */
    public void tickTop(double deltaSeconds) {
        Scene top = stack.peek();
        if (top != null) {
            top.tick(deltaSeconds);
        }
    }

    /** Записать draw-команды только top-сцены. No-op если стек пуст. */
    public void drawTop(RenderFrameContext frame) {
        Scene top = stack.peek();
        if (top != null) {
            top.draw(frame);
        }
    }

    public Scene top() {
        return stack.peek();
    }

    public int depth() {
        return stack.size();
    }

    public int pendingSize() {
        return pending.size();
    }

    /**
     * Освободить все сцены в стеке (top → bottom) с onExit/dispose, очистить
     * pending. Использовать в shutdown-фазе launcher'а.
     */
    public void clear() {
        pending.clear();
        while (!stack.isEmpty()) {
            Scene s = stack.pop();
            try {
                s.onExit();
            } finally {
                s.dispose();
            }
        }
    }

    private enum OpKind { PUSH, POP, REPLACE }

    private record PendingOp(OpKind kind, Scene scene) {
    }
}
