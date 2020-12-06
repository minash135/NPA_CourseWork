package by.gstu.npa.cw.server;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

public final class App extends Application {

    private static final Logger LOGGER = LogManager.getLogger(App.class);

    private static String languageTag = null;

    private MainController controller;

    public static void main(String[] args) {
        System.out.println("Enter '-h' or '-help' to get information about the arguments.");
        if (args.length == 1) {
            if ("-h".equals(args[0]) || "-help".equals(args[0])) {
                System.out.println("The first argument is a language tag (for example, 'ru' or 'en'). By default, the system language is used.");
                return;
            } else {
                languageTag = args[0];
            }
        }
        launch(args);
    }

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getClassLoader().getResource("main.fxml"));
        Scene scene = new Scene(loader.load());
        controller = loader.getController();
        controller.init(stage, languageTag);
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
        LOGGER.info("App has started");
    }

    @Override
    public void stop() {
        controller.destroy();
        LOGGER.info("App has stoped");
    }
}
