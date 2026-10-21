package hotshop.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import hotshop.ApplicationRuntime;
import hotshop.support.PostgresTestSupport;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;

/** Verifies the safe unavailable feedback while ordinary search remains available. */
class AiSearchUnavailableUiTest extends PostgresTestSupport {
    @TempDir
    Path directory;
    private ApplicationRuntime runtime;
    private Stage stage;

    @BeforeAll
    static void startToolkit() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                started.countDown();
            });
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(started::countDown);
        }
        assertTrue(started.await(15, TimeUnit.SECONDS));
    }

    @BeforeEach
    void openRuntime() throws Exception {
        runtime = openTestRuntime(directory);
    }

    @AfterEach
    void closeRuntime() throws Exception {
        if (stage != null) {
            onFx(() -> {
                stage.hide();
                return null;
            });
        }
        runtime.close();
    }

    @Test
    void missingProvider_showsSafeUnavailableFeedbackAndLeavesManualSearchEnabled() throws Exception {
        var user = runtime.getAccounts().register("aifallback", "Sample1!", "Buyer").join();
        onFx(() -> {
            stage = new Stage();
            MarketplaceUi ui = new MarketplaceUi(stage, runtime);
            ui.loggedIn(user);
            stage.show();
            Button ai = (Button) stage.getScene().lookup("#ai-search");
            Button manual = (Button) stage.getScene().lookup("#search-submit");
            Label status = (Label) stage.getScene().lookup("#ai-search-status");
            assertNotNull(ai);
            assertTrue(ai.isDisabled());
            assertNotNull(manual);
            assertFalse(manual.isDisabled());
            assertEquals("AI Search is unavailable. Configure the service to use it.", status.getText());
            assertFalse(status.getText().contains("test-key"));
            return null;
        });
    }

    private <T> T onFx(java.util.concurrent.Callable<T> task) throws Exception {
        FutureTask<T> future = new FutureTask<>(task);
        Platform.runLater(future);
        return future.get(10, TimeUnit.SECONDS);
    }
}
