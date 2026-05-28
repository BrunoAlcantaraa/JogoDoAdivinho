package com.bruno.grpc.service.client;

import com.bruno.grpc.*;
import com.google.protobuf.Empty;
import io.grpc.*;
import io.grpc.stub.StreamObserver;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class Cliente {

    private static final String HOST = "localhost";
    private static final int PORT = 50051;

    private final ManagedChannel channel;
    private final GameServiceGrpc.GameServiceBlockingStub blockingStub;
    private final GameServiceGrpc.GameServiceStub asyncStub;

    private String nick;
    private String objeto;

    public Cliente() {
        channel = Grpc.newChannelBuilderForAddress(HOST, PORT, InsecureChannelCredentials.create()).build();
        blockingStub = GameServiceGrpc.newBlockingStub(channel);
        asyncStub = GameServiceGrpc.newStub(channel);
    }

    public void entrar(String nick, EntradaCallback onSucesso, Consumer<String> onErro) {
        try {
            JogadorRequest req = JogadorRequest.newBuilder().setNick(nick).build();
            JogadorReply resp = blockingStub.entrar(req);

            if (resp.getSucesso()) {
                this.nick = nick;
                this.objeto = resp.getObjeto();
                onSucesso.aceitar(nick, resp.getObjeto(), resp.getMessage());
            } else {
                onErro.accept(resp.getMessage());
            }
        } catch (StatusRuntimeException e) {
            onErro.accept("Erro de conexão: " + e.getMessage());
        }
    }

    public void receberDicas(Consumer<String> onMensagem, Runnable onConcluido, Consumer<String> onErro) {
        JogadorRequest req = JogadorRequest.newBuilder().setNick(nick).build();

        asyncStub.receberDicas(req, new StreamObserver<DicaReply>() {
            @Override
            public void onNext(DicaReply dica) {
                onMensagem.accept(dica.getMessage());
            }

            @Override
            public void onError(Throwable t) {
                onErro.accept("Stream de dicas encerrado: " + t.getMessage());
            }

            @Override
            public void onCompleted() {
                onConcluido.run();
            }
        });
    }

    public EstadoReply obterEstado() {
        return blockingStub.obterEstado(Empty.getDefaultInstance());
    }

    public String iniciarJogo(int totalRodadas) {
        IniciarJogoRequest req = IniciarJogoRequest.newBuilder()
                .setTotalRodadas(totalRodadas)
                .build();
        DicaReply resp = blockingStub.iniciarJogo(req);
        return resp.getMessage();
    }

    public String decidirContinuar(boolean continuar) {
        ContinuarRequest req = ContinuarRequest.newBuilder()
                .setJogador(nick)
                .setContinuar(continuar)
                .build();
        DicaReply resp = blockingStub.decidirContinuar(req);
        return resp.getMessage();
    }

    public String enviarDica(String dica) {
        DicaRequest req = DicaRequest.newBuilder()
                .setAutor(nick)
                .setDica(dica)
                .build();
        DicaReply resp = blockingStub.enviarDica(req);
        return resp.getMessage();
    }

    public AdivinharReply adivinharObjeto(String jogadorAlvo, String objetoAdivinhar) {
        AdivinharRequest req = AdivinharRequest.newBuilder()
                .setJogador(nick)
                .setAlvo(jogadorAlvo)
                .setObjeto(objetoAdivinhar)
                .build();
        return blockingStub.adivinharObjeto(req);
    }

    public String atualizarMeuObjeto() {
        JogadorRequest req = JogadorRequest.newBuilder().setNick(nick).build();
        JogadorReply resp = blockingStub.obterMeuObjeto(req);

        if (resp.getSucesso()) {
            this.objeto = resp.getObjeto();
        }

        return this.objeto;
    }

    public List<JogadorInfo> listarJogadores() {
        ListaJogadoresReply resp = blockingStub.listarJogadores(Empty.getDefaultInstance());
        return resp.getJogadoresList();
    }

    public void receberMensagensChat(Consumer<ChatReply> onMensagem,
                                     Runnable onConcluido,
                                     Consumer<String> onErro) {
        JogadorRequest req = JogadorRequest.newBuilder().setNick(nick).build();

        asyncStub.receberMensagensChat(req, new StreamObserver<ChatReply>() {
            @Override
            public void onNext(ChatReply msg) {
                onMensagem.accept(msg);
            }

            @Override
            public void onError(Throwable t) {
                onErro.accept("Stream de chat encerrado: " + t.getMessage());
            }

            @Override
            public void onCompleted() {
                onConcluido.run();
            }
        });
    }

    public void enviarMensagemChat(String texto) {
        String horario = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        ChatRequest req = ChatRequest.newBuilder()
                .setNick(nick)
                .setTexto(texto)
                .setHorario(horario)
                .build();
        blockingStub.enviarMensagemChat(req);
    }

    public String getNick() {
        return nick;
    }

    public String getObjeto() {
        return objeto;
    }

    public void shutdown() {
        try {
            channel.shutdownNow().awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    public interface EntradaCallback {
        void aceitar(String nick, String objeto, String mensagem);
    }
}
