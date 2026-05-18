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

    // O acerto vale mais para quem acerta primeiro na rodada.
    private static final int PONTOS_PRIMEIRO_ACERTO = 5;
    private static final int PONTOS_SEGUNDO_ACERTO = 3;
    private static final int PONTOS_DEMAIS_ACERTOS = 1;
    private static final int PONTOS_BONUS_DICA = 2;

    private final JogadorRepository jogadorRepository = new JogadorRepository();

    private String jogadorAtual = "";
    private String jogadorInicial = "";
    private int rodadaAtual = 0;
    private EstadoJogo estadoJogo = EstadoJogo.ESPERANDO_INICIAR_JOGO;

    private String dicaAtual = "";
    private String autorDicaAtual = "";
    private int acertosNaRodada = 0;
    private boolean bonusDicaConcedidoNaRodada = false;

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
                    .setMessage("Bem-vindo, " + nick + "!")
                    .setObjeto(objeto)
                    .build();
        }

        responseObserver.onNext(resposta);
        responseObserver.onCompleted();
    }

    // -----------------------------------------------------------------------
    // listarJogadores
    // -----------------------------------------------------------------------

    @Override
    public void listarJogadores(Empty request, StreamObserver<ListaJogadoresReply> responseObserver) {
        ListaJogadoresReply.Builder builder = ListaJogadoresReply.newBuilder();
        for (Jogador j : jogadorRepository.getTodos()) {
            builder.addJogadores(
                JogadorInfo.newBuilder()
                    .setNick(j.getNick())
                    .setPontos(j.getPontos())
                    .build()
            );
        }
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void obterMeuObjeto(JogadorRequest request, StreamObserver<JogadorReply> responseObserver) {
        Jogador jogador = jogadorRepository.buscar(request.getNick());

        if (jogador == null) {
            responseObserver.onNext(JogadorReply.newBuilder()
                    .setSucesso(false)
                    .setMessage("Jogador não encontrado.")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        responseObserver.onNext(JogadorReply.newBuilder()
                .setSucesso(true)
                .setMessage("Objeto atual.")
                .setObjeto(jogador.getObjeto().getNomeObjeto())
                .build());
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
                            .withDescription("Jogador '" + nick + "' não encontrado.")
                            .asRuntimeException()
            );
            return;
        }

        jogador.setObserver(responseObserver);

        DicaReply boasVindas = DicaReply.newBuilder()
                .setMessage("Jogador Conectado!")
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
                    .setMessage("Não é hora de enviar dica.")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        if (!autor.equals(jogadorAtual)) {
            responseObserver.onNext(DicaReply.newBuilder()
                    .setMessage("Não é sua vez.")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        dicaAtual = dica;
        autorDicaAtual = autor;
        acertosNaRodada = 0;
        bonusDicaConcedidoNaRodada = false;

        notificarTodos(DicaReply.newBuilder()
                .setMessage("Dica de " + autor + ": " + dica)
                .build());

        jogadorRepository.resetarChancesAdvinhar();
        estadoJogo = EstadoJogo.ESPERANDO_ADVINHAR;

        notificarTodos(DicaReply.newBuilder()
                .setMessage("Adivinhe o objeto de " + jogadorAtual + "!")
                .build());

        responseObserver.onNext(DicaReply.newBuilder()
                .setMessage("Dica enviada.")
                .build());
        responseObserver.onCompleted();
    }

    // -----------------------------------------------------------------------
    // adivinharNumero
    // -----------------------------------------------------------------------

    @Override
    public synchronized void adivinharObjeto(AdivinharRequest request, StreamObserver<AdivinharReply> responseObserver) {
        Jogador jogadorTentando = jogadorRepository.buscar(request.getJogador());
        Jogador jogadorAlvo = jogadorRepository.buscar(jogadorAtual);

        if (estadoJogo != EstadoJogo.ESPERANDO_ADVINHAR) {
            responseObserver.onNext(AdivinharReply.newBuilder()
                    .setAcertou(false)
                    .setMessage("Não é hora de adivinhar.")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        if (jogadorTentando == null) {
            responseObserver.onNext(AdivinharReply.newBuilder()
                    .setAcertou(false)
                    .setMessage("Jogador não encontrado.")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        if (request.getJogador().equals(jogadorAtual)) {
            responseObserver.onNext(AdivinharReply.newBuilder()
                    .setAcertou(false)
                    .setMessage("Você não pode adivinhar o próprio objeto.")
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

        if (jogadorTentando.isTentouAdvinhar()) {
            responseObserver.onNext(AdivinharReply.newBuilder()
                    .setAcertou(false)
                    .setMessage("Você já tentou nesta rodada.")
                    .setPontuacaoAtualizada(jogadorTentando.getPontos())
                    .build());
            responseObserver.onCompleted();
            return;
        }

        boolean acertou = request.getObjeto().equalsIgnoreCase(jogadorAlvo.getObjeto().getNomeObjeto());
        jogadorTentando.setTentouAdvinhar(true);

        int pontuacaoAtualizada = jogadorTentando.getPontos();

        if (acertou) {
            acertosNaRodada++;
            int pontosAcerto = calcularPontosAcerto();
            pontuacaoAtualizada = jogadorTentando.getPontos() + pontosAcerto;
            jogadorTentando.setPontos(pontuacaoAtualizada);

            notificarTodos(DicaReply.newBuilder()
                    .setMessage(request.getJogador() + " acertou! +" + pontosAcerto + " pts")
                    .build());

            if (!bonusDicaConcedidoNaRodada) {
                jogadorAlvo.setPontos(jogadorAlvo.getPontos() + PONTOS_BONUS_DICA);
                bonusDicaConcedidoNaRodada = true;

                notificarTodos(DicaReply.newBuilder()
                        .setMessage(jogadorAlvo.getNick() + " ganhou +" + PONTOS_BONUS_DICA + " pts pela dica")
                        .build());
            }

        } else {
            // Regra: erro não remove pontos, apenas não ganha
            notificarTodos(DicaReply.newBuilder()
                    .setMessage(request.getJogador() + " errou.")
                    .build());

            if (jogadorRepository.todosTentaramAdvinhar(jogadorAlvo.getNick())) {
                String mensagemFimRodada = acertosNaRodada == 0
                        ? "Ninguém acertou. Próxima rodada!"
                        : "Rodada encerrada.";

                notificarTodos(DicaReply.newBuilder()
                        .setMessage(mensagemFimRodada)
                        .build());

                renovarObjetoSeFoiAcertado(jogadorAlvo);
                rodadaAtual++;
                dicaAtual = "";
                autorDicaAtual = "";
                acertosNaRodada = 0;
                bonusDicaConcedidoNaRodada = false;
                iniciarVez();
            }
        }

        if (acertou && jogadorRepository.todosTentaramAdvinhar(jogadorAlvo.getNick())) {
            notificarTodos(DicaReply.newBuilder()
                    .setMessage("Rodada encerrada.")
                    .build());

            renovarObjetoSeFoiAcertado(jogadorAlvo);
            rodadaAtual++;
            dicaAtual = "";
            autorDicaAtual = "";
            acertosNaRodada = 0;
            bonusDicaConcedidoNaRodada = false;
            iniciarVez();
        }

        responseObserver.onNext(AdivinharReply.newBuilder()
                .setAcertou(acertou)
                .setMessage(acertou ? "Acertou!" : "Errou!")
                .setPontuacaoAtualizada(pontuacaoAtualizada)
                .build());
        responseObserver.onCompleted();
    }

    // -----------------------------------------------------------------------
    // obterEstado
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
                    .setMessage("Nenhum jogador conectado.")
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
    // Chat
    // -----------------------------------------------------------------------

    @Override
    public void receberMensagensChat(JogadorRequest request, StreamObserver<ChatReply> responseObserver) {
        chatObservers.add(responseObserver);

        ChatReply boasVindas = ChatReply.newBuilder()
                .setNick("Sistema")
                .setTexto("Chat conectado")
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
        acertosNaRodada = 0;
        bonusDicaConcedidoNaRodada = false;

        notificarTodos(DicaReply.newBuilder()
                .setMessage("Turno de " + jogadorAtual + " — Rodada " + rodadaAtual)
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

    private void renovarObjetoSeFoiAcertado(Jogador jogadorAlvo) {
        if (acertosNaRodada == 0) return;

        String novoObjeto = jogadorRepository.sortearNovoObjeto(jogadorAlvo.getNick());
        if (novoObjeto == null) return;

        notificarTodos(DicaReply.newBuilder()
                .setMessage(jogadorAlvo.getNick() + " receberá um novo objeto.")
                .build());
    }

    private int calcularPontosAcerto() {
        if (acertosNaRodada == 1) {
            return PONTOS_PRIMEIRO_ACERTO;
        }
        if (acertosNaRodada == 2) {
            return PONTOS_SEGUNDO_ACERTO;
        }
        return PONTOS_DEMAIS_ACERTOS;
    }
}
