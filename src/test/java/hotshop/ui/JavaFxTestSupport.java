package hotshop.ui;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;

/** Shared JavaFX toolkit setup for UI test classes running in the same JVM. */
final class JavaFxTestSupport {
    private JavaFxTestSupport() {
    }

    static synchronized void startToolkit() throws InterruptedException {
        try {
            Platform.startup(() -> Platform.setImplicitExit(false));
        } catch (IllegalStateException alreadyStarted) {
            // Another UI test class has already initialized the shared JavaFX toolkit.
        }
        CountDownLatch ready = new CountDownLatch(1);
        Platform.runLater(() -> {
            Platform.setImplicitExit(false);
            ready.countDown();
        });
        if (!ready.await(15, TimeUnit.SECONDS)) {
            throw new AssertionError("JavaFX toolkit did not become ready");
        }
    }
}
