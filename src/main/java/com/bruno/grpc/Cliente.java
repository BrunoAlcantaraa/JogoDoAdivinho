package com.bruno.grpc;

import io.grpc.Channel;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;

import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class Cliente {

    private final ControllerGrpc.ControllerBlockingStub blockingStub;

    private String nick;
    private boolean jogadorInicial;
    private boolean jogadorAtual;

    public Cliente(Channel channel) {
        blockingStub = ControllerGrpc.newBlockingStub(channel);
    }

    public void entrar(String nick) {
        // Faz uma requisição de jogador
        JogadorRequest request = JogadorRequest.newBuilder().setNick(nick).build();

        // Obtém a resposta
        JogadorReply response = blockingStub.entrar(request);

        // Mostra a mensagem
        System.out.println(response.getMessage());

        // Se deu tudo certo
        if (response.getSucesso()) {
            System.out.println("Número sorteado: " + response.getNumero());
        }

    }

    public void enviarDica(String dica) {

        DicaRequest request = DicaRequest.newBuilder()
                .setAutor(nick)
                .setDica(dica)
                .build();

        DicaReply response = blockingStub.enviarDica(request);

        System.out.println(response.getMessage());

    }

    public void tentarAdvinhar() {

        Scanner scanner = new Scanner(System.in);

        System.out.print("Quem você quer adivinhar? ");
        String alvo = scanner.nextLine();

        System.out.print("Digite o número: ");
        int numero = scanner.nextInt();

        AdvinharRequest request =
                AdvinharRequest.newBuilder()
                        .setJogador(nick)
                        .setAlvo(alvo)
                        .setNumero(numero)
                        .build();

        AdvinharReply response =
                blockingStub.advinharNumero(request);

        System.out.println(response.getMessage());
    }

    public void menu() {

        Scanner scanner = new Scanner(System.in);

        while (true) {

            JogadorRequest request = JogadorRequest.newBuilder().setNick(nick).build();
            EstadoReply estadoReply = blockingStub.obterEstado(request);

            if (estadoReply.getRodada() == 0) {
                System.out.print("Digite qualquer coisa para iniciar o jogo: ");
                scanner.next();

                // iniciar
            }

            if (estadoReply.getJogadorAtual()) {
                System.out.print("Digite a dica: ");
                String dica = scanner.nextLine();

                enviarDica(dica);
            } else {

                // Tentar advinhar

            }

            System.out.println("\n===== MENU =====");
            System.out.println("1 - Enviar dica");
            System.out.println("2 - Tentar adivinhar");
            System.out.println("3 - Sair");

            int opcao = scanner.nextInt();
            scanner.nextLine();

            switch (opcao) {

                case 1:

                    System.out.print("Digite a dica: ");
                    String dica = scanner.nextLine();

                    enviarDica(dica);

                    break;

                case 2:

                    tentarAdvinhar();

                    break;

                case 3:

                    System.out.println("Saindo...");
                    return;

                default:

                    System.out.println("Opção inválida");
            }
        }
    }

    public static void main(String[] args) throws Exception {

        // Nome Client
        String nick = "Bruno";

        // Porta
        String target = "localhost:50051";

        // Faz conexão
        ManagedChannel channel = Grpc.newChannelBuilder(target, InsecureChannelCredentials.create()).build();

        try {

            Cliente client = new Cliente(channel);

            client.entrar(nick);

            client.menu();

        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }

    }

}