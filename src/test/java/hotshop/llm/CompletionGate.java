package hotshop.llm;

import java.util.concurrent.Executor;
import java.util.ArrayDeque;

/** Holds completion before dispatch; JavaFX tests supply Platform::runLater as the executor. */
final class CompletionGate {
    private final Executor executor;
    private final ArrayDeque<Runnable> callbacks = new ArrayDeque<>();

    CompletionGate(Executor executor) {
        this.executor = executor;
    }

    synchronized void hold(Runnable callback) {
        callbacks.add(callback);
    }

    void release() {
        while (true) {
            Runnable callback;
            synchronized (this) {
                callback = callbacks.poll();
            }
            if (callback == null) {
                return;
            }
            executor.execute(callback);
        }
    }
}
