package com.bruno.grpc.controller;

import com.bruno.grpc.*;
import com.bruno.grpc.entities.Jogador;
import com.bruno.grpc.repository.JogadorRepository;
import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ControllerImpl extends ControllerGrpc.ControllerImplBase {

    private final JogadorRepository jogadorRepository = new JogadorRepository();

    private String jogadorAtual = "";
    private String jogadorInicial = "";
    private int rodadaAtual = 0;
    private EstadoJogo estadoJogo = EstadoJogo.ESPERANDO_INICIAR_JOGO;

    // Dica atual da rodada
    private String dicaAtual = "";
    private String autorDicaAtual = "";

    // Observers do chat (stream aberto por cada cliente)
    private final List<StreamObserver<ChatReply>> chatObservers = new CopyOnWriteArrayList<>();

    // -----------------------------------------------------------------------
    // entrar
    // -----------------------------------------------------------------------

    @Override
    public void entrar(JogadorRequest request, StreamObserver<JogadorReply> responseObserver) {
        String nick = request.getNick();

        synchronized (this) {
            if (jogadorRepository.isEmpty()) {
                jogadorInicial = nick;
                estadoJogo = EstadoJogo.ESPERANDO_INICIAR_JOGO;
            }
        }

        String objeto = jogadorRepository.adicionar(nick);

        JogadorReply resposta;
        if (objeto == null) {
            resposta = JogadorReply.newBuilder()
                    .setSucesso(false)
                    .setMessage("Esse nick já está em uso!")
                    .build();
        } else {
            resposta = JogadorReply.newBuilder()
                    .setSucesso(true)
                    .setMessage("Jogador " + nick + " conectado com sucesso!")
                    .setObjeto(objeto)
                    .build();
        }

        responseObserver.onNext(resposta);
        responseObserver.onCompleted();
    }

    // -----------------------------------------------------------------------
    // receberDicas
    // -----------------------------------------------------------------------

    @Override
    public void receberDicas(JogadorRequest request, StreamObserver<DicaReply> responseObserver) {
        String nick = request.getNick();
        Jogador jogador = jogadorRepository.buscar(nick);

        if (jogador == null) {
            responseObserver.onError(
                    Status.NOT_FOUND
                            .withDescription("Jogador '" + nick + "' não encontrado. Use entrar() primeiro.")
                            .asRuntimeException()
            );
            return;
        }

        jogador.setObserver(responseObserver);

        DicaReply boasVindas = DicaReply.newBuilder()
                .setMessage("[Servidor] Stream de dicas aberto para " + nick + ". Aguardando jogo iniciar...")
                .build();
        responseObserver.onNext(boasVindas);
    }

    // -----------------------------------------------------------------------
    // enviarDica
    // -----------------------------------------------------------------------

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

        // Registra a dica atual da rodada
        dicaAtual = dica;
        autorDicaAtual = autor;

        notificarTodos(DicaReply.newBuilder()
                .setMessage("[Dica de " + autor + "] " + dica)
                .build());

        jogadorRepository.resetarChancesAdvinhar();
        estadoJogo = EstadoJogo.ESPERANDO_ADVINHAR;

        notificarTodos(DicaReply.newBuilder()
                .setMessage("[Servidor] Agora todos podem tentar adivinhar o objeto de " + jogadorAtual + "!")
                .build());

        responseObserver.onNext(DicaReply.newBuilder()
                .setMessage("Dica enviada!")
                .build());
        responseObserver.onCompleted();
    }

    // -----------------------------------------------------------------------
    // adivinharNumero
    // -----------------------------------------------------------------------

    @Override
    public synchronized void adivinharNumero(AdivinharRequest request, StreamObserver<AdivinharReply> responseObserver) {
        Jogador jogadorTentando = jogadorRepository.buscar(request.getJogador());
        Jogador jogadorAlvo = jogadorRepository.buscar(jogadorAtual);

        if (estadoJogo != EstadoJogo.ESPERANDO_ADVINHAR) {
            responseObserver.onNext(AdivinharReply.newBuilder()
                    .setAcertou(false)
                    .setMessage("Agora não é o momento de adivinhar.")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        if (request.getJogador().equals(jogadorAtual)) {
            responseObserver.onNext(AdivinharReply.newBuilder()
                    .setAcertou(false)
                    .setMessage("Você não pode tentar adivinhar o próprio objeto.")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        if (jogadorAlvo == null) {
            responseObserver.onNext(AdivinharReply.newBuilder()
                    .setAcertou(false)
                    .setMessage("Jogador da vez não encontrado.")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        boolean acertou = request.getObjeto().equalsIgnoreCase(jogadorAlvo.getObjeto().getNomeObjeto());
        jogadorTentando.setTentouAdvinhar(true);

        if (acertou) {
            notificarTodos(DicaReply.newBuilder()
                    .setMessage("[Servidor] " + request.getJogador()
                            + " acertou o objeto de " + jogadorAtual + "!")
                    .build());

            rodadaAtual++;
            dicaAtual = "";
            autorDicaAtual = "";
            iniciarVez();

        } else {
            notificarTodos(DicaReply.newBuilder()
                    .setMessage("[Servidor] " + request.getJogador()
                            + " tentou \"" + request.getObjeto() + "\" e errou.")
                    .build());

            if (jogadorRepository.todosTentaramAdvinhar(jogadorAlvo.getNick())) {
                notificarTodos(DicaReply.newBuilder()
                        .setMessage("[Servidor] Ninguém acertou o objeto de " + jogadorAtual + ". Próxima rodada!")
                        .build());

                rodadaAtual++;
                dicaAtual = "";
                autorDicaAtual = "";
                iniciarVez();
            }
        }

        responseObserver.onNext(AdivinharReply.newBuilder()
                .setAcertou(acertou)
                .setMessage(acertou ? "Acertou!" : "Errou!")
                .build());
        responseObserver.onCompleted();
    }

    // -----------------------------------------------------------------------
    // obterEstado — agora inclui dicaAtual e autorDicaAtual
    // -----------------------------------------------------------------------

    @Override
    public void obterEstado(Empty request, StreamObserver<EstadoReply> responseObserver) {
        try {
            EstadoReply resposta = EstadoReply.newBuilder()
                    .setJogadorAtual(jogadorAtual == null ? "" : jogadorAtual)
                    .setJogadorInicial(jogadorInicial == null ? "" : jogadorInicial)
                    .setRodada(rodadaAtual)
                    .setEstado(estadoJogo == null ? EstadoJogo.ESPERANDO_INICIAR_JOGO : estadoJogo)
                    .setDicaAtual(dicaAtual == null ? "" : dicaAtual)
                    .setAutorDicaAtual(autorDicaAtual == null ? "" : autorDicaAtual)
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

    // -----------------------------------------------------------------------
    // iniciarJogo
    // -----------------------------------------------------------------------

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

    // -----------------------------------------------------------------------
    // Chat — enviarMensagemChat e receberMensagensChat
    // -----------------------------------------------------------------------

    @Override
    public void receberMensagensChat(JogadorRequest request, StreamObserver<ChatReply> responseObserver) {
        chatObservers.add(responseObserver);

        // Envia confirmação inicial
        ChatReply boasVindas = ChatReply.newBuilder()
                .setNick("Sistema")
                .setTexto("Chat conectado. Bem-vindo, " + request.getNick() + "!")
                .setHorario(horarioAtual())
                .build();
        responseObserver.onNext(boasVindas);
    }

    @Override
    public void enviarMensagemChat(ChatRequest request, StreamObserver<ChatReply> responseObserver) {
        ChatReply mensagem = ChatReply.newBuilder()
                .setNick(request.getNick())
                .setTexto(request.getTexto())
                .setHorario(request.getHorario().isEmpty() ? horarioAtual() : request.getHorario())
                .build();

        // Distribui para todos os clientes com stream de chat aberto
        List<StreamObserver<ChatReply>> mortos = new ArrayList<>();
        for (StreamObserver<ChatReply> obs : chatObservers) {
            try {
                obs.onNext(mensagem);
            } catch (Exception e) {
                mortos.add(obs);
            }
        }
        chatObservers.removeAll(mortos);

        responseObserver.onNext(ChatReply.newBuilder()
                .setNick("Sistema")
                .setTexto("Mensagem enviada.")
                .setHorario(horarioAtual())
                .build());
        responseObserver.onCompleted();
    }

    // -----------------------------------------------------------------------
    // Helpers internos
    // -----------------------------------------------------------------------

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

    private String horarioAtual() {
        java.time.LocalTime agora = java.time.LocalTime.now();
        return String.format("%02d:%02d", agora.getHour(), agora.getMinute());
    }
}