package com.bruno.grpc.view.client;

import com.bruno.grpc.EstadoJogo;
import com.bruno.grpc.EstadoReply;
import javafx.application.Platform;

import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Faz polling periódico de obterEstado() no servidor e notifica o controller
 * quando o estado muda. Roda em thread de background; as notificações são
 * despachadas via Platform.runLater para a thread do JavaFX.
 */
public class GameStatePoller {

    private static final long INTERVALO_MS = 1000;

    private final GrpcGameClient client;
    private final Consumer<EstadoReply> onEstadoMudou;

    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "estado-poller");
                t.setDaemon(true);
                return t;
            });

    private EstadoReply ultimoEstado = null;
    private volatile boolean rodando = false;

    public GameStatePoller(GrpcGameClient client, Consumer<EstadoReply> onEstadoMudou) {
        this.client = client;
        this.onEstadoMudou = onEstadoMudou;
    }

    /** Inicia o polling. Pode ser chamado na thread JavaFX sem problemas. */
    public void iniciar() {
        if (rodando) return;
        rodando = true;

        executor.scheduleWithFixedDelay(() -> {
            try {
                EstadoReply estado = client.obterEstado();

                boolean mudou = ultimoEstado == null
                        || !ultimoEstado.getEstado().equals(estado.getEstado())
                        || !ultimoEstado.getJogadorAtual().equals(estado.getJogadorAtual())
                        || ultimoEstado.getRodada() != estado.getRodada();

                if (mudou) {
                    ultimoEstado = estado;
                    Platform.runLater(() -> onEstadoMudou.accept(estado));
                }
            } catch (Exception e) {
                // Ignora erros de rede transitórios; o poller continua tentando
            }
        }, 0, INTERVALO_MS, TimeUnit.MILLISECONDS);
    }

    /** Para o polling. */
    public void parar() {
        rodando = false;
        executor.shutdownNow();
    }
}
