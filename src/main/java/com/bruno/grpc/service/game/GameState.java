package com.bruno.grpc.service.game;

import com.bruno.grpc.EstadoReply;
import com.bruno.grpc.JogadorInfo;
import com.bruno.grpc.service.client.Cliente;
import javafx.application.Platform;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

public class GameState {

    private static final long INTERVALO_MS = 1000;

    private final Cliente client;
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

    public GameState(Cliente client, BiConsumer<EstadoReply, List<JogadorInfo>> onEstadoMudou) {
        this.client = client;
        this.onEstadoMudou = onEstadoMudou;
    }

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
                // Ignora erros de rede
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

    public void parar() {
        rodando = false;
        executor.shutdownNow();
    }
}
