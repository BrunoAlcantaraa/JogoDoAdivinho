package com.bruno.grpc.view.client;

import com.bruno.grpc.*;
import com.google.protobuf.Empty;
import io.grpc.*;
import io.grpc.stub.StreamObserver;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Camada de comunicação gRPC para a GUI.
 * Encapsula todos os stubs e chamadas de rede, sem depender de Scanner ou System.out.
 * Toda a lógica de apresentação fica no HelloController via callbacks.
 */
public class GrpcGameClient {

    private static final String HOST = "localhost";
    private static final int PORT = 50051;

    private final ManagedChannel channel;
    private final ControllerGrpc.ControllerBlockingStub blockingStub;
    private final ControllerGrpc.ControllerStub asyncStub;

    private String nick;
    private int meuNumero;

    public GrpcGameClient() {
        channel = Grpc.newChannelBuilderForAddress(HOST, PORT, InsecureChannelCredentials.create()).build();
        blockingStub = ControllerGrpc.newBlockingStub(channel);
        asyncStub = ControllerGrpc.newStub(channel);
    }

    // -----------------------------------------------------------------------
    // entrar
    // -----------------------------------------------------------------------

    /**
     * Entra no jogo com o nick fornecido.
     *
     * @param nick     nick desejado
     * @param onSucesso callback chamado na thread do chamador com (nick, numeroPróprio)
     * @param onErro   callback chamado em caso de nick duplicado ou falha de rede
     */
    public void entrar(String nick, EntradaCallback onSucesso, Consumer<String> onErro) {
        try {
            JogadorRequest req = JogadorRequest.newBuilder().setNick(nick).build();
            JogadorReply resp = blockingStub.entrar(req);

            if (resp.getSucesso()) {
                this.nick = nick;
                this.meuNumero = resp.getNumero();
                onSucesso.aceitar(nick, resp.getNumero(), resp.getMessage());
            } else {
                onErro.accept(resp.getMessage());
            }
        } catch (StatusRuntimeException e) {
            onErro.accept("Erro de conexão: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // receberDicas (stream assíncrono)
    // -----------------------------------------------------------------------

    /**
     * Abre o stream de dicas do servidor.
     * Cada mensagem recebida aciona onMensagem na thread gRPC — use Platform.runLater no controller.
     */
    public void receberDicas(Consumer<String> onMensagem, Runnable onConcluido, Consumer<String> onErro) {
        JogadorRequest req = JogadorRequest.newBuilder().setNick(nick).build();

        asyncStub.receberDicas(req, new StreamObserver<DicaReply>() {
            @Override
            public void onNext(DicaReply dica) {
                onMensagem.accept(dica.getMessage());
            }

            @Override
            public void onError(Throwable t) {
                onErro.accept("Stream encerrado com erro: " + t.getMessage());
            }

            @Override
            public void onCompleted() {
                onConcluido.run();
            }
        });
    }

    // -----------------------------------------------------------------------
    // obterEstado
    // -----------------------------------------------------------------------

    /**
     * Obtém o estado atual do jogo (síncrono — chame em thread de background).
     */
    public EstadoReply obterEstado() {
        return blockingStub.obterEstado(Empty.getDefaultInstance());
    }

    // -----------------------------------------------------------------------
    // iniciarJogo
    // -----------------------------------------------------------------------

    /**
     * Inicia o jogo (somente o jogador inicial pode chamar isso).
     *
     * @return mensagem de retorno do servidor
     */
    public String iniciarJogo() {
        DicaReply resp = blockingStub.iniciarJogo(Empty.getDefaultInstance());
        return resp.getMessage();
    }

    // -----------------------------------------------------------------------
    // enviarDica
    // -----------------------------------------------------------------------

    /**
     * Envia uma dica ao servidor.
     *
     * @return mensagem de retorno do servidor
     */
    public String enviarDica(String dica) {
        DicaRequest req = DicaRequest.newBuilder()
                .setAutor(nick)
                .setDica(dica)
                .build();
        DicaReply resp = blockingStub.enviarDica(req);
        return resp.getMessage();
    }

    // -----------------------------------------------------------------------
    // advinharNumero
    // -----------------------------------------------------------------------

    /**
     * Tenta adivinhar o número do jogadorAlvo.
     *
     * @return AdvinharReply com acertou + mensagem
     */
    public AdvinharReply advinharNumero(String jogadorAlvo, int numero) {
        AdvinharRequest req = AdvinharRequest.newBuilder()
                .setJogador(nick)
                .setAlvo(jogadorAlvo)
                .setNumero(numero)
                .build();
        return blockingStub.advinharNumero(req);
    }

    // -----------------------------------------------------------------------
    // getters utilitários
    // -----------------------------------------------------------------------

    public String getNick() {
        return nick;
    }

    public int getMeuNumero() {
        return meuNumero;
    }

    // -----------------------------------------------------------------------
    // shutdown
    // -----------------------------------------------------------------------

    public void shutdown() {
        try {
            channel.shutdownNow().awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    // -----------------------------------------------------------------------
    // tipos de callback
    // -----------------------------------------------------------------------

    @FunctionalInterface
    public interface EntradaCallback {
        void aceitar(String nick, int numero, String mensagem);
    }
}
