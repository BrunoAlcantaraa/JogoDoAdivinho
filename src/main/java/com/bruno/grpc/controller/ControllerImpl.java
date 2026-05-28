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
    private static final int LIMITE_RODADAS_PADRAO = 5;

    private final JogadorRepository jogadorRepository = new JogadorRepository();

    private String jogadorAtual = "";
    private String jogadorInicial = "";
    private int rodadaAtual = 0;
    private int indiceJogadorAtual = 0;
    private EstadoJogo estadoJogo = EstadoJogo.ESPERANDO_INICIAR_JOGO;

    private String dicaAtual = "";
    private String autorDicaAtual = "";
    private int acertosNaRodada = 0;
    private boolean bonusDicaConcedidoNaRodada = false;
    private boolean aguardandoDecisaoContinuar = false;
    private int limiteRodadasPartida = LIMITE_RODADAS_PADRAO;

    private final List<StreamObserver<ChatReply>> chatObservers = new CopyOnWriteArrayList<>();

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

        jogadorRepository.resetarChancesAdivinhar();
        estadoJogo = EstadoJogo.ESPERANDO_ADVINHAR;

        notificarTodos(DicaReply.newBuilder()
                .setMessage("Adivinhe o objeto de " + jogadorAtual + "!")
                .build());

        responseObserver.onNext(DicaReply.newBuilder()
                .setMessage("Dica enviada.")
                .build());
        responseObserver.onCompleted();
    }

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

        if (jogadorTentando.isTentouAdivinhar()) {
            responseObserver.onNext(AdivinharReply.newBuilder()
                    .setAcertou(false)
                    .setMessage("Você já tentou nesta rodada.")
                    .setPontuacaoAtualizada(jogadorTentando.getPontos())
                    .build());
            responseObserver.onCompleted();
            return;
        }

        boolean acertou = request.getObjeto().equalsIgnoreCase(jogadorAlvo.getObjeto().getNomeObjeto());
        jogadorTentando.setTentouAdivinhar(true);

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

            if (jogadorRepository.todosTentaramAdivinhar(jogadorAlvo.getNick())) {
                String mensagemFimRodada = acertosNaRodada == 0
                        ? "Ninguém acertou. Próxima rodada!"
                        : "Rodada encerrada.";

                notificarTodos(DicaReply.newBuilder()
                        .setMessage(mensagemFimRodada)
                        .build());

                finalizarRodada(jogadorAlvo);
            }
        }

        if (acertou && jogadorRepository.todosTentaramAdivinhar(jogadorAlvo.getNick())) {
            notificarTodos(DicaReply.newBuilder()
                    .setMessage("Rodada encerrada.")
                    .build());

            finalizarRodada(jogadorAlvo);
        }

        responseObserver.onNext(AdivinharReply.newBuilder()
                .setAcertou(acertou)
                .setMessage(acertou ? "Acertou!" : "Errou!")
                .setPontuacaoAtualizada(pontuacaoAtualizada)
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

    @Override
    public synchronized void iniciarJogo(IniciarJogoRequest request, StreamObserver<DicaReply> responseObserver) {
        if (jogadorRepository.getTodos().isEmpty()) {
            responseObserver.onNext(DicaReply.newBuilder()
                    .setMessage("Nenhum jogador conectado.")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        limiteRodadasPartida = Math.max(1, request.getTotalRodadas());
        rodadaAtual = 1;
        indiceJogadorAtual = 0;
        aguardandoDecisaoContinuar = false;
        iniciarVez();

        responseObserver.onNext(DicaReply.newBuilder()
                .setMessage("Jogo iniciado com " + limiteRodadasPartida + " rodadas!")
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public synchronized void decidirContinuar(ContinuarRequest request, StreamObserver<DicaReply> responseObserver) {
        if (!request.getJogador().equals(jogadorInicial)) {
            responseObserver.onNext(DicaReply.newBuilder()
                    .setMessage("Apenas o jogador inicial pode decidir.")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        if (!aguardandoDecisaoContinuar || estadoJogo != EstadoJogo.AGUARDANDO_CONTINUAR) {
            responseObserver.onNext(DicaReply.newBuilder()
                    .setMessage("O jogo não está aguardando decisão.")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        aguardandoDecisaoContinuar = false;

        if (request.getContinuar()) {
            rodadaAtual = 1;
            indiceJogadorAtual = 0;
            notificarTodos(DicaReply.newBuilder()
                    .setMessage("Nova partida iniciada com " + limiteRodadasPartida + " rodadas.")
                    .build());
            iniciarVez();

            responseObserver.onNext(DicaReply.newBuilder()
                    .setMessage("Continuando o jogo.")
                    .build());
        } else {
            jogadorAtual = "";
            dicaAtual = "";
            autorDicaAtual = "";
            indiceJogadorAtual = 0;
            estadoJogo = EstadoJogo.JOGO_ENCERRADO;

            notificarTodos(DicaReply.newBuilder()
                    .setMessage("Jogo encerrado.")
                    .build());

            responseObserver.onNext(DicaReply.newBuilder()
                    .setMessage("Jogo encerrado.")
                    .build());
        }

        responseObserver.onCompleted();
    }

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

    public synchronized void iniciarVez() {
        List<Jogador> jogadores = jogadorRepository.getTodos()
                .stream()
                .toList();

        if (jogadores.isEmpty()) {
            jogadorAtual = "";
            estadoJogo = EstadoJogo.ESPERANDO_INICIAR_JOGO;
            return;
        }

        if (indiceJogadorAtual >= jogadores.size()) {
            indiceJogadorAtual = 0;
        }

        Jogador dono = jogadores.get(indiceJogadorAtual);

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

    private void finalizarRodada(Jogador jogadorAlvo) {
        renovarObjetoSeFoiAcertado(jogadorAlvo);
        dicaAtual = "";
        autorDicaAtual = "";
        acertosNaRodada = 0;
        bonusDicaConcedidoNaRodada = false;

        int totalJogadores = jogadorRepository.getTodos().size();
        boolean rodadaCompleta = totalJogadores <= 1 || indiceJogadorAtual >= totalJogadores - 1;

        if (rodadaCompleta) {
            if (rodadaAtual >= limiteRodadasPartida) {
                jogadorAtual = "";
                estadoJogo = EstadoJogo.AGUARDANDO_CONTINUAR;
                aguardandoDecisaoContinuar = true;

                notificarTodos(DicaReply.newBuilder()
                        .setMessage("Fim da partida. Aguardando decisão de " + jogadorInicial + ".")
                        .build());
                return;
            }

            rodadaAtual++;
            indiceJogadorAtual = 0;
        } else {
            indiceJogadorAtual++;
        }

        iniciarVez();
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
