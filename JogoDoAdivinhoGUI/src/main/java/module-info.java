module org.example.jogodoadivinhogui {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.kordamp.bootstrapfx.core;

    opens org.example.jogodoadivinhogui to javafx.fxml;
    exports org.example.jogodoadivinhogui;
}