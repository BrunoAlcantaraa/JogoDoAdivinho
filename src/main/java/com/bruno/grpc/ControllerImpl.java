package com.bruno.grpc;

import com.bruno.grpc.repository.JogadorRepository;
import io.grpc.stub.StreamObserver;

import java.util.Map;

public class ControllerImpl extends ControllerGrpc.ControllerImplBase {

    // CONFIGS
    private final int QTD_JOGADORES = 100;

    // "Banco de dados" dos jogadores
    private final JogadorRepository jogadorRepository = new JogadorRepository(QTD_JOGADORES);

    // SETS
    private String primeiroJogador;
    private String jogadorAtual;
    private EstadoJogo estadoJogo;
    private int rodadaAtual;

    @Override
    public void entrar(JogadorRequest request, StreamObserver<JogadorReply> responseObserver) {

        // Pega o nick do jogador
        String nick = request.getNick();

        // Verifica se é o primeiro
        if (jogadorRepository.getJogadores().isEmpty()) {
            primeiroJogador = nick;
        }

        // Tenta adicionar o jogador a lista

        int numSorteado = jogadorRepository.adicionar(nick);

        JogadorReply resposta;

        // jogador já existe
        if (numSorteado == -1) {
            resposta = JogadorReply.newBuilder()
                    .setSucesso(false)
                    .setMessage("Esse nick já está em uso!")
                    .build();

        } else { // Jogador não exista
            resposta = JogadorReply.newBuilder()
                    .setSucesso(true)
                    .setMessage("Jogador conectado com sucesso!")
                    .setNumero(numSorteado)
                    .build();
        }

        responseObserver.onNext(resposta);
        responseObserver.onCompleted();
    }

    @Override
    public void enviarDica(DicaRequest request, StreamObserver<DicaReply> responseObserver) {

        String autor = request.getAutor();
        String dica = request.getDica();

        DicaReply mensagem = DicaReply.newBuilder()
                .setMessage(autor + ": " + dica)
                .build();

        // envia para todos
//        for (Map.Entry<String,
//                StreamObserver<DicaReply>> entry
//                : observers.entrySet()) {
//
//            String nick = entry.getKey();
//
//            // opcional: não enviar pra si mesmo
//            if (!nick.equals(autor)) {
//
//                StreamObserver<DicaReply> observer =
//                        entry.getValue();
//
//                observer.onNext(mensagem);
//            }
//        }

        // responde ao autor
        DicaReply resposta = DicaReply.newBuilder()
                .setMessage("Dica enviada!")
                .build();

        responseObserver.onNext(resposta);
        responseObserver.onCompleted();
    }

    @Override
    public void advinharNumero(AdvinharRequest request, StreamObserver<AdvinharReply> responseObserver) {

        // Obtém o nick do Alvo
        String jogadorAlvo = request.getAlvo();

        // Número do devido player
        int numeroCorreto = jogadorRepository.getJogadores().get(jogadorAlvo);

        // Verifica se acertou
        boolean acertou = request.getNumero() == numeroCorreto;

        // Devolve
        AdvinharReply resposta = AdvinharReply.newBuilder().setAcertou(acertou).setMessage(acertou ? "Acertou!" : "Errou!").build();
        responseObserver.onNext(resposta);
        responseObserver.onCompleted();
    }

    @Override
    public void obterEstado(JogadorRequest request, StreamObserver<EstadoReply> responseObserver) {

        // Pega o nick do client
        String nick = request.getNick();

        // Verifica se é o jogador inicial
        boolean jogadorInicial = nick.equals(primeiroJogador);

        EstadoReply resposta = EstadoReply.newBuilder()
                .setJogadorAtual(true)
                .setJogadorInicial(jogadorInicial)
                .setRodada(rodadaAtual)
                .build();

        responseObserver.onNext(resposta);
        responseObserver.onCompleted();
    }

    public void iniciar() {
        for (String nickDono : jogadorRepository.getJogadores().keySet()) {

            // Solicitar mensagem dica do jogador
            jogadorAtual = nickDono;
            estadoJogo = EstadoJogo.ESPERANDO_DICA;

            for (String nickAdvinho : jogadorRepository.getJogadores().keySet()) {
                // Mandar a dica para cada um
            }

            // Dar a chance de tentarem advinhar

            rodadaAtual++;

        }
    }

}