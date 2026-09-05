/*
 * Copyright 2026 ste.vnc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ste.vnc.demo.javafx;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

/**
 * A small, real JavaFX application demonstrating {@link SimpleVNCView}:
 * a host/port field and a Connect button above the viewer, with a
 * status bar showing connection state and any errors.
 *
 * <p>Run with: {@code mvn javafx:run}
 *
 * <p><b>Not verified by compilation</b>: written without a JavaFX
 * toolchain available in the environment that authored it, so unlike
 * the rest of this project it has not actually been compiled or run.
 * Please report back if it needs adjustment.
 */
public class VNCViewerDemoApp extends Application {

    /**
     * Bumped by hand on every package handed over during development, so
     * it's immediately obvious from the window title whether a rebuild
     * actually picked up the latest changes.
     */
    static final int DEMO_VERSION = 3;

    private static final int DEFAULT_PORT = 5900;
    private static final int WINDOW_WIDTH = 900;
    private static final int WINDOW_HEIGHT = 650;

    @Override
    public void start(final Stage stage) {
        final TextField hostField = new TextField();
        hostField.setPromptText("host");
        hostField.setPrefColumnCount(16);

        final TextField portField = new TextField(String.valueOf(DEFAULT_PORT));
        portField.setPromptText("port");
        portField.setPrefColumnCount(5);

        final Button connectButton = new Button("Connect");

        final SimpleVNCView vncView = new SimpleVNCView();

        final Label statusLabel = new Label();
        statusLabel.textProperty().bind(vncView.statusProperty());
        statusLabel.setPadding(new Insets(4, 8, 4, 8));

        connectButton.setOnAction(e -> {
            final String host = hostField.getText().trim();
            if (host.isEmpty()) {
                return;
            }
            final int port = parsePortOrDefault(portField.getText());
            vncView.connect(host, port);
            vncView.requestFocus(); // so keyboard input goes to the viewer, not the text fields
        });

        final HBox toolbar = new HBox(8, new Label("Host:"), hostField, new Label("Port:"), portField, connectButton);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(8));

        final BorderPane root = new BorderPane();
        root.setTop(toolbar);
        root.setCenter(vncView);
        root.setBottom(statusLabel);

        stage.setTitle("VNC Viewer Demo v" + DEMO_VERSION);
        stage.setScene(new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT));
        stage.show();
    }

    private static int parsePortOrDefault(final String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (final NumberFormatException e) {
            return DEFAULT_PORT;
        }
    }

    public static void main(final String[] args) {
        System.out.println("VNCViewerDemoApp v" + DEMO_VERSION + " starting...");
        launch(args);
    }
}
