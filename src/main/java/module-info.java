module uz.khoshimjonov.quickpeek {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.fasterxml.jackson.databind;
    requires com.github.kwhat.jnativehook;


    opens uz.khoshimjonov.quickpeek to javafx.fxml;
    exports uz.khoshimjonov.quickpeek;
}