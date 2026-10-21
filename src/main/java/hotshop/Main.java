package hotshop;

import java.io.IOException;
import java.nio.file.Path;
import java.net.URI;
import java.sql.SQLException;
import java.util.Map;

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

    /**
     * JavaFX invokes init off the application thread, before displaying the login window.
     */
    @Override
    public void init() {
        try {
            String defaultDirectory = Path.of(System.getProperty("user.home"), ".hotshop").toString();
            runtime = ApplicationRuntime.open(Path.of(System.getProperty("hotshop.dataDir", defaultDirectory)));
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
                alert.setContentText("Check PostgreSQL and private image storage operator settings and connectivity. "
                        + "Ask the operator to verify provisioning and image storage bindings.");
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
     * Closes the runtime and releases its resources when JavaFX stops.
     *
     * @throws IOException if releasing the application lock or its channel fails
     */
    @Override
    public void stop() throws IOException {
        if (aiSearchClient != null) {
            aiSearchClient.close();
            aiSearchClient = null;
        }
        if (runtime != null) {
            runtime.close();
        }
    }
}
