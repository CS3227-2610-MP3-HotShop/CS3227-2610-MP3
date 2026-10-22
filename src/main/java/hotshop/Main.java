package hotshop;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.sql.SQLException;
import java.util.Map;

import hotshop.llm.LlmConfiguration;
import hotshop.ui.MarketplaceUi;
import hotshop.llm.SocSearchClient;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

/**
 * Initializes local storage and displays the authenticated marketplace application.
 */
public class Main extends Application {
    private static final System.Logger LOGGER = System.getLogger(Main.class.getName());
    private ApplicationRuntime runtime;
    private Exception startupFailure;
    private SocSearchClient aiSearchClient;

    /** Initialized once across JPro application instances; ordinary runtime opens remain offline. */
    private static final class ServerDescriptions {
        private static final DescriptionRuntime OWNER = create();

        private static DescriptionRuntime create() {
            DescriptionRuntime owner = new DescriptionRuntime(LlmConfiguration.loadFromEnvironment(Path.of(".")));
            Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().name("hotshop-description-shutdown")
                    .unstarted(owner::close));
            return owner;
        }
    }

    /**
     * JavaFX invokes init off the application thread, before displaying the login window.
     */
    @Override
    public void init() {
        try {
            String defaultDirectory = Path.of(System.getProperty("user.home"), ".hotshop").toString();
            runtime = ApplicationRuntime.open(Path.of(System.getProperty("hotshop.dataDir", defaultDirectory)),
                    Clock.systemDefaultZone(), ServerDescriptions.OWNER);
        } catch (Exception exception) {
            startupFailure = exception;
            LOGGER.log(System.Logger.Level.ERROR, "Unable to initialize HotShop; check storage and database setup");
        }
    }

    /**
     * Shows the marketplace window, or a startup error if initialization failed.
     *
     * @param stage the primary JavaFX window
     * @throws IOException if filesystem access or resource loading fails
     */
    @Override
    public void start(Stage stage) throws IOException {
        if (startupFailure != null) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.getDialogPane().getStylesheets().add(MarketplaceUi.stylesheet());
            alert.setTitle("HotShop startup failed");
            alert.setHeaderText("Unable to open HotShop data");
            if (startupFailure instanceof SQLException || startupFailure instanceof IllegalArgumentException) {
                alert.setContentText("Check PostgreSQL connection settings and connectivity. "
                        + "Ask the operator to run migrateDatabase with a compatible application version.");
            } else {
                alert.setContentText("Check that the image data folder is writable and no other HotShop instance "
                        + "is using it. Existing data has not been reset.");
            }
            alert.showAndWait();
            Platform.exit();
            return;
        }
        aiSearchClient = createAiSearchClient(System.getenv());
        new MarketplaceUi(stage, runtime, aiSearchClient == null ? null : aiSearchClient::propose);
        stage.show();
    }

    static SocSearchClient createAiSearchClient(Map<String, String> environment) {
        String baseUrl = environment.get("SOCLAAS_BASE_URL");
        String apiKey = environment.get("SOCLAAS_API_KEY");
        String model = environment.get("SOCLAAS_MODEL");
        if (baseUrl == null || apiKey == null || model == null) {
            return null;
        }
        try {
            return new SocSearchClient(URI.create(baseUrl), apiKey, model);
        } catch (IllegalArgumentException failure) {
            return null;
        }
    }

    /**
     * Starts resource shutdown without blocking JavaFX; the server owner survives other JPro sessions.
     */
    @Override
    public void stop() {
        if (aiSearchClient != null) {
            aiSearchClient.close();
            aiSearchClient = null;
        }
        if (runtime != null) {
            runtime.closeAsync().whenComplete((ignored, failure) -> {
                if (failure != null) {
                    LOGGER.log(System.Logger.Level.ERROR, "Unable to release HotShop data resources");
                }
            });
        }
    }
}
