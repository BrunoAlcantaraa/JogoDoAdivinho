package com.bruno.grpc.controller;

import com.bruno.grpc.*;
import com.bruno.grpc.entities.Jogador;
import com.bruno.grpc.repository.JogadorRepository;
import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

public class ControllerImpl extends ControllerGrpc.ControllerImplBase {

    private final int QTD_NUMEROS = 100;

    private final JogadorRepository jogadorRepository = new JogadorRepository(QTD_NUMEROS);

    private String jogadorAtual = "";
    private String jogadorInicial = "";
    private int rodadaAtual = 0;
    private EstadoJogo estadoJogo = EstadoJogo.ESPERANDO_INICIAR_JOGO;

    @Override
    public void entrar(JogadorRequest request, StreamObserver<JogadorReply> responseObserver) {

        String nick = request.getNick();

        // Primeiro jogador a entrar será o jogadorInicial
        synchronized (this) {
            if (jogadorRepository.isEmpty()) {
                jogadorInicial = nick;
                estadoJogo = EstadoJogo.ESPERANDO_INICIAR_JOGO;
            }
        }

        int numSorteado = jogadorRepository.adicionar(nick);

        JogadorReply resposta;

        if (numSorteado == -1) {
            resposta = JogadorReply.newBuilder()
                    .setSucesso(false)
                    .setMessage("Esse nick já está em uso!")
                    .build();
        } else {
            resposta = JogadorReply.newBuilder()
                    .setSucesso(true)
                    .setMessage("Jogador " + nick + " conectado com sucesso!")
                    .setNumero(numSorteado)
                    .build();
        }

        responseObserver.onNext(resposta);
        responseObserver.onCompleted();
    }

    @Override
    public void receberDicas(JogadorRequest request, StreamObserver<DicaReply> responseObserver) {

        String nick = request.getNick();

        Jogador jogador = jogadorRepository.buscar(nick);

        if (jogador == null) {
            responseObserver.onError(
                    io.grpc.Status.NOT_FOUND
                            .withDescription("Jogador '" + nick + "' não encontrado. Use entrar() primeiro.")
                            .asRuntimeException()
            );
            return;
        }

        // Vincula o responseObserver ao objeto Jogador para envios futuros
        jogador.setObserver(responseObserver);

        // Envia confirmação inicial
        DicaReply boasVindas = DicaReply.newBuilder()
                .setMessage("[Servidor] Stream de dicas aberto para " + nick + ". Aguardando dicas...")
                .build();
        responseObserver.onNext(boasVindas);

    }

    @Override
    public synchronized void enviarDica(DicaRequest request, StreamObserver<DicaReply> responseObserver) {

        String autor = request.getAutor();
        String dica = request.getDica();

        if (estadoJogo != EstadoJogo.ESPERANDO_DICA) {
            responseObserver.onNext(DicaReply.newBuilder()
                    .setMessage("Agora não é o momento de enviar dica.")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        if (!autor.equals(jogadorAtual)) {
            responseObserver.onNext(DicaReply.newBuilder()
                    .setMessage("Não é sua vez de enviar dica.")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        notificarTodos(DicaReply.newBuilder()
                .setMessage("[Dica de " + autor + "] " + dica)
                .build());

        jogadorRepository.resetarChancesAdvinhar();
        estadoJogo = EstadoJogo.ESPERANDO_ADVINHAR;

        notificarTodos(DicaReply.newBuilder()
                .setMessage("[Servidor] Agora todos podem tentar adivinhar o número de " + jogadorAtual + "!")
                .build());

        responseObserver.onNext(DicaReply.newBuilder()
                .setMessage("Dica enviada!")
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public synchronized void advinharNumero(AdvinharRequest request, StreamObserver<AdvinharReply> responseObserver) {

        Jogador jogadorTentandoAdvinhar = jogadorRepository.buscar(request.getJogador());
        Jogador jogadorAlvo = jogadorRepository.buscar(jogadorAtual);

        if (estadoJogo != EstadoJogo.ESPERANDO_ADVINHAR) {
            responseObserver.onNext(AdvinharReply.newBuilder()
                    .setAcertou(false)
                    .setMessage("Agora não é o momento de adivinhar.")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        if (request.getJogador().equals(jogadorAtual)) {
            responseObserver.onNext(AdvinharReply.newBuilder()
                    .setAcertou(false)
                    .setMessage("Você não pode tentar adivinhar o próprio número.")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        if (jogadorAlvo == null) {
            responseObserver.onNext(AdvinharReply.newBuilder()
                    .setAcertou(false)
                    .setMessage("Jogador da vez não encontrado.")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        boolean acertou = request.getNumero() == jogadorAlvo.getNumero();

        jogadorTentandoAdvinhar.setTentouAdvinhar(true);

        if (acertou) {
            notificarTodos(DicaReply.newBuilder()
                    .setMessage("[Servidor] " + request.getJogador()
                            + " acertou o número de " + jogadorAtual + "!")
                    .build());

            rodadaAtual++;
            iniciarVez();

        } else {
            notificarTodos(DicaReply.newBuilder()
                    .setMessage("[Servidor] " + request.getJogador()
                            + " tentou " + request.getNumero() + " e errou.")
                    .build());

            if (jogadorRepository.todosTentaramAdvinhar(jogadorAlvo.getNick())) {
                notificarTodos(DicaReply.newBuilder()
                        .setMessage("[Servidor] Ninguém acertou o número de " + jogadorAtual + ". Próxima vez!")
                        .build());

                rodadaAtual++;
                iniciarVez();
            }
        }

        responseObserver.onNext(AdvinharReply.newBuilder()
                .setAcertou(acertou)
                .setMessage(acertou ? "Acertou!" : "Errou!")
                .build());

        responseObserver.onCompleted();
    }

    @Override
    public void obterEstado(Empty request, StreamObserver<EstadoReply> responseObserver) {
        try {
            EstadoReply resposta = EstadoReply.newBuilder()
                    .setJogadorAtual(jogadorAtual == null ? "" : jogadorAtual)
                    .setJogadorInicial(jogadorInicial == null ? "" : jogadorInicial)
                    .setRodada(rodadaAtual)
                    .setEstado(estadoJogo == null ? EstadoJogo.ESPERANDO_INICIAR_JOGO : estadoJogo)
                    .build();

            responseObserver.onNext(resposta);
            responseObserver.onCompleted();

        } catch (Exception e) {
            e.printStackTrace();

            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription("Erro ao obter estado")
                            .withCause(e)
                            .asRuntimeException()
            );
        }
    }

    @Override
    public synchronized void iniciarJogo(Empty request, StreamObserver<DicaReply> responseObserver) {

        if (jogadorRepository.getTodos().isEmpty()) {
            responseObserver.onNext(DicaReply.newBuilder()
                    .setMessage("Não há jogadores para iniciar o jogo.")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        rodadaAtual = 1;
        iniciarVez();

        responseObserver.onNext(DicaReply.newBuilder()
                .setMessage("Jogo iniciado!")
                .build());
        responseObserver.onCompleted();
    }

    public synchronized void iniciarVez() {

        Jogador dono = jogadorRepository.getTodos()
                .stream()
                .toList()
                .get((rodadaAtual - 1) % jogadorRepository.getTodos().size());

        jogadorAtual = dono.getNick();
        estadoJogo = EstadoJogo.ESPERANDO_DICA;

        notificarTodos(DicaReply.newBuilder()
                .setMessage("[Servidor] Turno de " + jogadorAtual
                        + " — aguardando dica. Rodada " + rodadaAtual)
                .build());
    }

    public void iniciar() {

        for (Jogador dono : jogadorRepository.getTodos()) {

            estadoJogo = com.bruno.grpc.EstadoJogo.ESPERANDO_DICA;

            DicaReply notificacaoTurno = DicaReply.newBuilder()
                    .setMessage("[Servidor] Turno de " + dono.getNick()
                            + " — aguardando dica. (Rodada " + (rodadaAtual + 1) + ")")
                    .build();

            notificarTodos(notificacaoTurno);

            while (true) if (estadoJogo != EstadoJogo.ESPERANDO_DICA) break;

            estadoJogo = EstadoJogo.ESPERANDO_ADVINHAR;

            DicaReply notificacaoAdvinhar = DicaReply.newBuilder().
                    setMessage("[Servidor] Agora todos podem tentar adivinhar o número de " + dono.getNick() + "!").build();

            notificarTodos(notificacaoAdvinhar);

            while (true) if (estadoJogo != EstadoJogo.ESPERANDO_ADVINHAR) break;

            rodadaAtual++;

        }

    }

    public void notificarTodos(DicaReply dicaReply) {
        for (Jogador jogador : jogadorRepository.getTodos()) {
            if (jogador.isConectado()) {
                try {
                    jogador.getObserver().onNext(dicaReply);
                } catch (Exception e) {
                    System.err.println("[Aviso] Falha ao notificar " + jogador.getNick() + ": " + e.getMessage());
                }
            }
        }
    }

}
