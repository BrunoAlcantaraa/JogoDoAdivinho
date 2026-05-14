package com.bruno.grpc;

import com.bruno.grpc.repository.JogadorRepository;
import io.grpc.stub.StreamObserver;

public class ControllerImpl extends ControllerGrpc.ControllerImplBase {

    // Quantidade de Números possíveis
    private final int QTD_JOGADORES = 100;

    // "Banco de dados" dos jogadores
    private final JogadorRepository jogadorRepository = new JogadorRepository(QTD_JOGADORES);

    @Override
    public void entrar(JogadorRequest request, StreamObserver<JogadorReply> responseObserver) {

        // Pega o nick do jogador
        String nick = request.getJogador();

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

        DicaReply resposta = DicaReply.newBuilder().setMessage("Dica recebida!").build();

        // Devolve
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
}