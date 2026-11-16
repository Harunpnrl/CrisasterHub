module com.pnrl.crisasterhub {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires java.sql;

    requires jdk.jsobject;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;

    opens com.pnrl.crisasterhub to javafx.fxml;
    exports com.pnrl.crisasterhub;
    opens com.pnrl.crisasterhub.dbclasses to javafx.base;
}