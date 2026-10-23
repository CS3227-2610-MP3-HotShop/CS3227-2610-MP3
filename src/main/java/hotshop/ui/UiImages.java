package hotshop.ui;

import java.io.File;
import java.nio.file.Path;
import java.util.function.Supplier;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

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

    /** Binds browser file selection while the trigger is rendered, before its first click. */
    static PreparedChooser prepareChooser(Window owner, Node trigger, ImageStorage.Limits limits) {
        return new PreparedChooser(owner, trigger, limits);
    }

    static final class PreparedChooser {
        private final Window owner;
        private final ImageStorage.Limits limits;
        private final boolean browser;
        private final WebAPI.SingleFileUploader uploader;
        private final RuntimeException unavailable;
        private final AtomicReference<Selection> current = new AtomicReference<>();

        PreparedChooser(Window owner, Node trigger, ImageStorage.Limits limits) {
            this.owner = owner;
            this.limits = limits;
            browser = WebAPI.isBrowser();
            WebAPI.SingleFileUploader prepared = null;
            RuntimeException preparationFailure = null;
            if (browser) {
                try {
                    prepared = WebAPI.getWebAPI(owner).makeFileUploadNode(trigger);
                    prepared.setSelectFileOnClick(true);
                    prepared.supportedExtensions().setAll(".jpg", ".jpeg", ".png");
                    prepared.setOnFileSelected(this::fileSelected);
                } catch (RuntimeException failure) {
                    preparationFailure = new IllegalArgumentException("Image picker is unavailable");
                }
            }
            uploader = prepared;
            unavailable = preparationFailure;
        }

        Selection choose() {
            if (!browser) {
                Path selected = UiImages.choose(owner);
                CompletableFuture<Void> started = CompletableFuture.completedFuture(null);
                if (selected == null) {
                    return new Selection(CompletableFuture.completedFuture(null), started);
                }
                CompletableFuture<Path> validated = CompletableFuture.supplyAsync(() -> {
                    try {
                        ImageStorage.validateImage(selected, limits);
                        return selected;
                    } catch (IOException failure) {
                        throw new CompletionException(failure);
                    }
                });
                return new Selection(validated, started);
            }
            if (unavailable != null) {
                return new Selection(CompletableFuture.failedFuture(unavailable),
                        CompletableFuture.completedFuture(null));
            }
            Selection selection = new Selection(new CompletableFuture<>(), new CompletableFuture<>());
            Selection previous = current.getAndSet(selection);
            if (previous != null) {
                previous.result.complete(null);
            }
            return selection;
        }

        void cancel() {
            Selection pending = current.getAndSet(null);
            if (pending != null) {
                pending.result.complete(null);
            }
        }

        private void fileSelected(String filename) {
            Selection selection = current.get();
            if (selection == null || selection.result.isDone()) {
                return;
            }
            selection.started.complete(null);
            if (uploader.getSelectedFileSize() < 1 || uploader.getSelectedFileSize() > limits.maximumBytes()) {
                selection.result.completeExceptionally(new IllegalArgumentException(
                        "Image file exceeds the size limit"));
                return;
            }
            uploader.uploadFile();
            uploader.getJSFile().getUploadedFileFuture().whenComplete((uploaded, failure) -> {
                if (failure != null) {
                    selection.result.completeExceptionally(new IllegalArgumentException("Image upload failed"));
                    return;
                }
                try {
                    Path temporary = ImageUploadFiles.copyUploadedFile(uploaded, limits);
                    if (!selection.result.complete(temporary)) {
                        ImageUploadFiles.release(temporary);
                    }
                } catch (IOException | IllegalArgumentException invalid) {
                    selection.result.completeExceptionally(invalid);
                } finally {
                    current.compareAndSet(selection, null);
                }
            });
        }
    }

    static final class Selection {
        private final CompletableFuture<Path> result;
        private final CompletableFuture<Void> started;

        Selection(CompletableFuture<Path> result, CompletableFuture<Void> started) {
            this.result = result;
            this.started = started;
        }

        CompletableFuture<Path> result() {
            return result;
        }

        CompletableFuture<Void> started() {
            return started;
        }
    }

    static void release(Path selected) {
        ImageUploadFiles.release(selected);
    }
}
