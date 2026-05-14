package com.bruno.grpc;

import io.grpc.Channel;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;

import java.util.concurrent.TimeUnit;

public class Cliente {

    private final ControllerGrpc.ControllerBlockingStub blockingStub;

    public Cliente(Channel channel) {
        blockingStub = ControllerGrpc.newBlockingStub(channel);
    }

    public void entrar(String nick) {
        // Faz uma requisição de jogador
        JogadorRequest request = JogadorRequest.newBuilder().setJogador(nick).build();

        // Obtém a resposta
        JogadorReply response = blockingStub.entrar(request);

        // Mostra a mensagem
        System.out.println(response.getMessage());

        // Se deu tudo certo
        if (response.getSucesso()) {
            System.out.println("Número sorteado: " + response.getNumero());
        }
    }

    public static void main(String[] args) throws Exception {

        // Nome Client
        String user = "Bruno";

        // Porta
        String target = "localhost:50051";

        // Faz conexão
        ManagedChannel channel = Grpc.newChannelBuilder(target, InsecureChannelCredentials.create()).build();

        // Chama o método
        try {
            new Cliente(channel).entrar(user);
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

}