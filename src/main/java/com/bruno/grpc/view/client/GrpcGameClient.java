package com.bruno.grpc.view.client;

import com.bruno.grpc.*;
import com.google.protobuf.Empty;
import io.grpc.*;
import io.grpc.stub.StreamObserver;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Camada de comunicação gRPC para a GUI.
 * Encapsula todos os stubs e chamadas de rede, sem depender de Scanner ou System.out.
 * Toda a lógica de apresentação fica no Controller via callbacks.
 */
public class GrpcGameClient {

    private static final String HOST = "localhost";
    private static final int PORT = 50051;

    private final ManagedChannel channel;
    private final ControllerGrpc.ControllerBlockingStub blockingStub;
    private final ControllerGrpc.ControllerStub asyncStub;

    private String nick;
    private String objeto;

    public GrpcGameClient() {
        channel = Grpc.newChannelBuilderForAddress(HOST, PORT, InsecureChannelCredentials.create()).build();
        blockingStub = ControllerGrpc.newBlockingStub(channel);
        asyncStub = ControllerGrpc.newStub(channel);
    }

    // -----------------------------------------------------------------------
    // Jogo
    // -----------------------------------------------------------------------

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

    public String iniciarJogo() {
        DicaReply resp = blockingStub.iniciarJogo(Empty.getDefaultInstance());
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

    /**
     * Retorna a lista de jogadores com pontuação atual.
     */
    public List<JogadorInfo> listarJogadores() {
        ListaJogadoresReply resp = blockingStub.listarJogadores(Empty.getDefaultInstance());
        return resp.getJogadoresList();
    }

    // -----------------------------------------------------------------------
    // Chat gRPC
    // -----------------------------------------------------------------------

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

    // -----------------------------------------------------------------------
    // Getters
    // -----------------------------------------------------------------------

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
