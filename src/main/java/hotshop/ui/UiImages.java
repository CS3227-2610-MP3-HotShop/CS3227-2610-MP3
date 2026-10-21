package hotshop.ui;

import java.io.File;
import java.nio.file.Path;
import java.util.function.Supplier;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.Node;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.application.Platform;
import com.jpro.webapi.WebAPI;
import hotshop.storage.ImageStorage;

/** Bounded background image display with an explicit missing-image fallback. */
final class UiImages {
    private UiImages() {
    }

    static Node display(Supplier<Path> source, double width, double height) {
        StackPane pane = new StackPane(UiControls.label(width < 64 ? "?" : "No image", "muted"));
        pane.setAccessibleText("Image");
        pane.setMinSize(width, height);
        pane.setPrefSize(width, height);
        pane.setMaxSize(width, height);
        pane.getStyleClass().add("image-placeholder");
        if (source == null) {
            return pane;
        }
        try {
            Image image = new Image(source.get().toUri().toString(), width, height, true, true, true);
            ImageView view = new ImageView(image);
            view.setPreserveRatio(true);
            view.setFitWidth(width);
            view.setFitHeight(height);
            image.progressProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue.doubleValue() == 1 && !image.isError()) {
                    pane.getChildren().setAll(view);
                }
            });
            if (image.getProgress() == 1 && !image.isError()) {
                pane.getChildren().setAll(view);
            }
        } catch (IllegalArgumentException failure) {
            pane.getChildren().setAll(UiControls.label("Image unavailable", "muted"));
        }
        return pane;
    }

    static Node displayRemote(Supplier<CompletableFuture<byte[]>> source, double width, double height) {
        StackPane pane = new StackPane(UiControls.label(width < 64 ? "?" : "No image", "muted"));
        pane.setAccessibleText("Image");
        pane.setMinSize(width, height);
        pane.setPrefSize(width, height);
        pane.setMaxSize(width, height);
        pane.getStyleClass().add("image-placeholder");
        if (source == null) {
            return pane;
        }
        source.get().whenComplete((bytes, failure) -> Platform.runLater(() -> {
            if (failure != null || bytes == null || pane.getScene() == null) {
                return;
            }
            Image image = new Image(new ByteArrayInputStream(bytes), width, height, true, true);
            if (!image.isError()) {
                ImageView view = new ImageView(image);
                view.setPreserveRatio(true);
                view.setFitWidth(width);
                view.setFitHeight(height);
                pane.getChildren().setAll(view);
            }
        }));
        return pane;
    }

    static Path choose(Window owner) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a JPEG or PNG image");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JPEG / PNG", "*.jpg", "*.jpeg", "*.png"));
        File selected = chooser.showOpenDialog(owner);
        return selected == null ? null : selected.toPath();
    }

    static CompletableFuture<Path> choose(Window owner, Node trigger, ImageStorage.Limits limits) {
        if (!WebAPI.isBrowser()) {
            Path selected = choose(owner);
            if (selected == null) {
                return CompletableFuture.completedFuture(null);
            }
            return CompletableFuture.supplyAsync(() -> {
                try {
                    ImageStorage.validateImage(selected, limits);
                    return selected;
                } catch (IOException failure) {
                    throw new CompletionException(failure);
                }
            });
        }
        CompletableFuture<Path> result = new CompletableFuture<>();
        try {
            WebAPI.SingleFileUploader uploader = WebAPI.getWebAPI(owner).makeFileUploadNode(trigger);
            uploader.supportedExtensions().setAll(".jpg", ".jpeg", ".png");
            uploader.setOnFileSelected(filename -> {
                if (uploader.getSelectedFileSize() < 1 || uploader.getSelectedFileSize() > limits.maximumBytes()) {
                    result.completeExceptionally(new IllegalArgumentException("Image file exceeds the size limit"));
                    return;
                }
                uploader.uploadFile();
                uploader.getJSFile().getUploadedFileFuture().whenComplete((uploaded, failure) -> {
                    if (failure != null) {
                        result.completeExceptionally(new IllegalArgumentException("Image upload failed"));
                        return;
                    }
                    try {
                        Path temporary = ImageUploadFiles.copyUploadedFile(uploaded, limits);
                        if (!result.complete(temporary)) {
                            ImageUploadFiles.release(temporary);
                        }
                    } catch (IOException | IllegalArgumentException invalid) {
                        result.completeExceptionally(invalid);
                    }
                });
            });
        } catch (RuntimeException failure) {
            result.completeExceptionally(new IllegalArgumentException("Image picker is unavailable"));
        }
        return result;
    }

    static void release(Path selected) {
        ImageUploadFiles.release(selected);
    }
}
