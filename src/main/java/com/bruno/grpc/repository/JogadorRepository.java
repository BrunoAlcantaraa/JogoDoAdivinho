package com.bruno.grpc.repository;

import com.bruno.grpc.DicaReply;
import com.bruno.grpc.entities.Jogador;
import io.grpc.stub.StreamObserver;

import java.util.Collection;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class JogadorRepository {

    private final int qtdNumeros;

    private final Map<String, Jogador> jogadores = new ConcurrentHashMap<>();

    public JogadorRepository(int qtdNumeros) {
        this.qtdNumeros = qtdNumeros;
    }

    public int adicionar(String nick) {
        if (jogadores.containsKey(nick)) return -1;

        Random random = new Random();
        int numeroSorteado = random.nextInt(qtdNumeros) + 1;

        // Cria o Jogador sem observer por enquanto (será vinculado em receberDicas)
        Jogador jogador = new Jogador(nick, numeroSorteado, null);
        jogadores.put(nick, jogador);

        return numeroSorteado;
    }

    public boolean todosTentaramAdvinhar(String dono) {
        for (Jogador jogador : jogadores.values()) {
            if (!jogador.isTentouAdvinhar() && !jogador.getNick().equals(dono)) {
                return false;
            }
        }
        return true;
    }

    public void resetarChancesAdvinhar() {
        for (Jogador jogador : jogadores.values()) {
            jogador.setTentouAdvinhar(false);
        }
    }


    public boolean vincularObserver(String nick, StreamObserver<DicaReply> observer) {
        Jogador jogador = jogadores.get(nick);
        if (jogador == null) return false;

        jogador.setObserver(observer);
        return true;
    }


    public Jogador buscar(String nick) {
        return jogadores.get(nick);
    }

    public Collection<Jogador> getTodos() {
        return jogadores.values();
    }

    public Map<String, Jogador> getJogadores() {
        return jogadores;
    }

    public boolean isEmpty() {
        return jogadores.isEmpty();
    }
}
