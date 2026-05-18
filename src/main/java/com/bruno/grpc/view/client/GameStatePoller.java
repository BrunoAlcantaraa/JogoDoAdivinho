package com.bruno.grpc.view.client;

import com.bruno.grpc.EstadoReply;
import com.bruno.grpc.JogadorInfo;
import javafx.application.Platform;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

/**
 * Faz polling periódico de obterEstado() e listarJogadores() no servidor.
 * Notifica o controller quando o estado ou a lista de jogadores muda.
 * Roda em thread de background; as notificações são despachadas via
 * Platform.runLater para a thread do JavaFX.
 */
public class GameStatePoller {

    private static final long INTERVALO_MS = 1000;

    private final GrpcGameClient client;
    private final BiConsumer<EstadoReply, List<JogadorInfo>> onEstadoMudou;

    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "estado-poller");
                t.setDaemon(true);
                return t;
            });

    private EstadoReply ultimoEstado = null;
    private int ultimoTamanhoLista = -1;
    private Map<String, Integer> ultimasPontuacoes = new HashMap<>();
    private volatile boolean rodando = false;

    public GameStatePoller(GrpcGameClient client, BiConsumer<EstadoReply, List<JogadorInfo>> onEstadoMudou) {
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
                List<JogadorInfo> jogadores = client.listarJogadores();

                boolean estadoMudou = ultimoEstado == null
                        || !ultimoEstado.getEstado().equals(estado.getEstado())
                        || !ultimoEstado.getJogadorAtual().equals(estado.getJogadorAtual())
                        || ultimoEstado.getRodada() != estado.getRodada()
                        || !ultimoEstado.getDicaAtual().equals(estado.getDicaAtual());

                boolean listaMudou = jogadores.size() != ultimoTamanhoLista
                        || pontuacoesMudaram(jogadores);

                if (estadoMudou || listaMudou) {
                    ultimoEstado = estado;
                    ultimoTamanhoLista = jogadores.size();
                    ultimasPontuacoes = criarSnapshotPontuacoes(jogadores);
                    Platform.runLater(() -> onEstadoMudou.accept(estado, jogadores));
                }
            } catch (Exception e) {
                // Ignora erros de rede transitórios; o poller continua tentando
            }
        }, 0, INTERVALO_MS, TimeUnit.MILLISECONDS);
    }

    private boolean pontuacoesMudaram(List<JogadorInfo> jogadores) {
        return !ultimasPontuacoes.equals(criarSnapshotPontuacoes(jogadores));
    }

    private Map<String, Integer> criarSnapshotPontuacoes(List<JogadorInfo> jogadores) {
        Map<String, Integer> snapshot = new HashMap<>();
        for (JogadorInfo jogador : jogadores) {
            snapshot.put(jogador.getNick(), jogador.getPontos());
        }
        return snapshot;
    }

    /** Para o polling. */
    public void parar() {
        rodando = false;
        executor.shutdownNow();
    }
}
