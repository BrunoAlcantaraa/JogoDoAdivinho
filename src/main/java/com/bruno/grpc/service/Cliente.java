package com.bruno.grpc.service;

import com.bruno.grpc.*;
import com.google.protobuf.Empty;
import io.grpc.*;
import io.grpc.stub.StreamObserver;

import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class Cliente {

    private final ControllerGrpc.ControllerBlockingStub blockingStub;
    private final ControllerGrpc.ControllerStub asyncStub;

    private String nick;
    private String ultimoAlvoTentado = "";

    public Cliente(Channel channel) {
        blockingStub = ControllerGrpc.newBlockingStub(channel);
        asyncStub = ControllerGrpc.newStub(channel);
    }

    public boolean entrar(String nick) {
        this.nick = nick;

        JogadorRequest request = JogadorRequest.newBuilder()
                .setNick(nick)
                .build();

        JogadorReply response = blockingStub.entrar(request);

        System.out.println(response.getMessage());

        if (response.getSucesso()) {
            System.out.println("Seu objeto: " + response.getObjeto());
            return true;
        }

        return false;
    }

    public void enviarDica(String dica) {
        DicaRequest request = DicaRequest.newBuilder()
                .setAutor(nick)
                .setDica(dica)
                .build();

        DicaReply response = blockingStub.enviarDica(request);
        System.out.println(response.getMessage());
    }

    public void receberDicas() {
        JogadorRequest request = JogadorRequest.newBuilder()
                .setNick(nick)
                .build();

        asyncStub.receberDicas(request, new StreamObserver<DicaReply>() {
            @Override
            public void onNext(DicaReply dica) {
                System.out.println("\n[STREAM] " + dica.getMessage());
                System.out.print("> ");
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("[STREAM] Erro no stream de dicas: " + t.getMessage());
            }

            @Override
            public void onCompleted() {
                System.out.println("[STREAM] Stream de dicas encerrado pelo servidor.");
            }
        });
    }

    public void tentarAdivinhar(String alvo, Scanner scanner) {
        System.out.print("> Tente adivinhar o número de " + alvo + ": ");

        String objeto = scanner.nextLine();

        AdivinharRequest request = AdivinharRequest.newBuilder()
                .setJogador(nick)
                .setAlvo(alvo)
                .setObjeto(objeto)
                .build();

        AdivinharReply response = blockingStub.adivinharNumero(request);
        System.out.println(response.getMessage());
    }

    public void menu() throws InterruptedException {
        Scanner scanner = new Scanner(System.in);

        receberDicas();

        while (true) {
            EstadoReply estadoReply = blockingStub.obterEstado(Empty.getDefaultInstance());

            switch (estadoReply.getEstado()) {

                case ESPERANDO_INICIAR_JOGO:
                    ultimoAlvoTentado = "";

                    if (estadoReply.getJogadorInicial().equals(nick)) {
                        System.out.print("Pressione ENTER para iniciar o jogo: ");
                        scanner.nextLine();

                        DicaReply response = blockingStub.iniciarJogo(Empty.getDefaultInstance());
                        System.out.println(response.getMessage());
                    } else {
                        System.out.println("Aguardando " + estadoReply.getJogadorInicial() + " iniciar o jogo...");
                        Thread.sleep(1000);
                    }
                    break;

                case ESPERANDO_DICA:
                    ultimoAlvoTentado = "";

                    if (estadoReply.getJogadorAtual().equals(nick)) {
                        System.out.print("É sua vez! Digite a dica: ");
                        String dica = scanner.nextLine();
                        enviarDica(dica);
                    } else {
                        System.out.println("Aguardando dica de " + estadoReply.getJogadorAtual() + "...");
                        Thread.sleep(1000);
                    }
                    break;

                case ESPERANDO_ADVINHAR:
                    if (!estadoReply.getJogadorAtual().equals(nick)) {

                        if (!ultimoAlvoTentado.equals(estadoReply.getJogadorAtual())) {
                            tentarAdivinhar(estadoReply.getJogadorAtual(), scanner);
                            ultimoAlvoTentado = estadoReply.getJogadorAtual();
                        } else {
                            System.out.println("Você já tentou nessa rodada. Aguardando...");
                            Thread.sleep(1000);
                        }

                    } else {

                        System.out.println("Os outros estão tentando adivinhar seu número...");
                        Thread.sleep(1000);
                    }
                    break;

                default:
                    System.out.println("Estado desconhecido: " + estadoReply.getEstado());
                    Thread.sleep(1000);
                    break;
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Scanner input = new Scanner(System.in);

        System.out.print("> Digite seu nick: ");
        String nick = input.nextLine();

        String target = "localhost:50051";

        ManagedChannel channel = Grpc.newChannelBuilder(
                target,
                InsecureChannelCredentials.create()
        ).build();

        try {
            Cliente client = new Cliente(channel);

            if (client.entrar(nick)) {
                client.menu();
            }

        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}