module com.bruno.grpc {
    requires javafx.controls;
    requires javafx.fxml;

    requires io.grpc;
    requires io.grpc.stub;
    requires io.grpc.protobuf;
    requires com.google.protobuf;

    exports com.bruno.grpc;
    exports com.bruno.grpc.service;
    exports com.bruno.grpc.controller;
    exports com.bruno.grpc.entities;
    exports com.bruno.grpc.repository;
    exports com.bruno.grpc.view;

    opens com.bruno.grpc.view to javafx.fxml;
    opens com.bruno.grpc.controller to javafx.fxml;
}