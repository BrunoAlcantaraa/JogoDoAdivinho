package com.bruno.grpc.service.server;

import com.bruno.grpc.service.game.GameServiceImpl;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;

public class Servidor {

    public static void main(String[] args) throws Exception {

        Server server = Grpc.newServerBuilderForPort(
                        50051,
                        InsecureServerCredentials.create()
                )
                .addService(new GameServiceImpl())
                .build();

        server.start();

        System.out.println("Servidor rodando na porta 50051");

        server.awaitTermination();
    }
}