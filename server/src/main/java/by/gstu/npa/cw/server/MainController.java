package by.gstu.npa.cw.server;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.ResourceBundle;

public final class MainController {

    private static final Logger LOGGER = LogManager.getLogger(MainController.class);

    private ResourceBundle strings;
    private Stage stage;

    private SocketSmoothManager socketSmoothManager;
    private BufferedImage choosedImage;
    private BufferedImage smoothedImage;

    @FXML
    private GridPane imageLayout;

    @FXML
    private ImageView imageView;

    @FXML
    private Label clientsCountLabel;
    @FXML
    private Spinner<Integer> clientsCountInput;
    @FXML
    private Label kernelSizeLabel;
    @FXML
    private Spinner<Integer> kernelSizeInput;
    @FXML
    private Label repeatCountLabel;
    @FXML
    private Spinner<Integer> repeatCountInput;

    @FXML
    private Button chooseImageButton;
    @FXML
    private Button smoothImageButton;
    @FXML
    private Button saveImageButton;

    @FXML
    private Label activeClientsInfoLabel;
    @FXML
    private Label activeClientsLabel;
    @FXML
    private Label processingTimeInfoLabel;
    @FXML
    private Label processingTimeLabel;

    public void init(Stage stage, String languageTag) {
        this.stage = stage;
        Locale locale = Locale.getDefault();
        if (languageTag != null) {
            locale = Locale.forLanguageTag(languageTag);
        }
        strings = ResourceBundle.getBundle("strings", locale);
        stage.setTitle(strings.getString("app_name"));
        setTextToUi();
        clientsCountInput.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 20));
        kernelSizeInput.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(3, 29, 3, 2));
        repeatCountInput.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 20));
        socketSmoothManager = new SocketSmoothManager();
        socketSmoothManager.setClientCountCallback(clients ->
                Platform.runLater(() -> activeClientsLabel.setText(String.valueOf(clients))));

        imageLayout.widthProperty().addListener((observable, oldValue, newValue) ->
                imageView.setFitWidth((Double) newValue - 2 * imageLayout.getPadding().getTop()));
        imageLayout.heightProperty().addListener((observable, oldValue, newValue) ->
                imageView.setFitHeight((Double) newValue - 2 * imageLayout.getPadding().getTop()));
    }

    private void setTextToUi() {
        clientsCountLabel.setText(strings.getString("clients_count_label"));
        kernelSizeLabel.setText(strings.getString("kernel_size_label"));
        repeatCountLabel.setText(strings.getString("repeat_count_label"));
        chooseImageButton.setText(strings.getString("choose_image_button"));
        smoothImageButton.setText(strings.getString("smooth_image_button"));
        saveImageButton.setText(strings.getString("save_image_button"));
        activeClientsInfoLabel.setText(strings.getString("active_clients_info_label"));
        processingTimeInfoLabel.setText(strings.getString("processing_time_info_label"));
    }

    public void destroy() {
        socketSmoothManager.close();
    }

    @FXML
    public void onOpenImageClick() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(strings.getString("choose_image"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG", "*.png"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JPEG", "*.jpg;*.jpeg"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(strings.getString("all_files"), "*.*"));
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }
        try {
            smoothedImage = null;
            choosedImage = ImageIO.read(file);
            imageView.setImage(SwingFXUtils.toFXImage(choosedImage, null));
        } catch (Exception e) {
            LOGGER.info(e);
            choosedImage = null;
            imageView.setImage(null);
            showAlert("file_is_not_image");
        }
    }

    @FXML
    public void onSmoothImageClick() {
        if (choosedImage == null) {
            showAlert("no_image_selected");
            return;
        }
        final int clients = clientsCountInput.getValue();
        if (socketSmoothManager.activeClientsCount() < clients) {
            showAlert("number_of_clients_less");
            return;
        }
        final int kernelSize = kernelSizeInput.getValue();
        final int repeatCount = repeatCountInput.getValue();
        chooseImageButton.setDisable(true);
        smoothImageButton.setDisable(true);
        saveImageButton.setDisable(true);
        socketSmoothManager.smoothImage(choosedImage, clients, kernelSize, repeatCount, (image, processingTime) ->
                Platform.runLater(() -> {
                    smoothedImage = image;
                    imageView.setImage(SwingFXUtils.toFXImage(image, null));
                    processingTimeLabel.setText(String.valueOf(processingTime * 0.000_001));
                    chooseImageButton.setDisable(false);
                    smoothImageButton.setDisable(false);
                    saveImageButton.setDisable(false);
                }));
    }

    @FXML
    public void onSaveImageClick() {
        if (smoothedImage == null) {
            showAlert("no_smoothed_image");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG", "*.png"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(strings.getString("all_files"), "*.*"));
        chooser.setTitle(strings.getString("save_image"));
        File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }
        try {
            ImageIO.write(smoothedImage, "png", file);
        } catch (IOException e) {
            LOGGER.info(e);
        }
    }

    private void showAlert(String key) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(strings.getString("error"));
        alert.setHeaderText(strings.getString(key));
        alert.showAndWait();
    }
}
