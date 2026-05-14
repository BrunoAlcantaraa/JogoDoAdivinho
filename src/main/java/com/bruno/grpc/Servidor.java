package com.bruno.grpc;

import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;

public class Servidor {

    public static void main(String[] args) throws Exception {

        Server server = Grpc.newServerBuilderForPort(
                        50051,
                        InsecureServerCredentials.create()
                )
                .addService(new ControllerImpl())
                .build();

        server.start();

        System.out.println("Servidor rodando na porta 50051");

        server.awaitTermination();
    }
}