package com.bruno.grpc.repository;

import com.bruno.grpc.DicaReply;
import com.bruno.grpc.entities.Jogador;
import io.grpc.stub.StreamObserver;

import java.util.Collection;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class JogadorRepository {

    // Números que serão distribuídos entre os clients
    private final int qtdNumeros;

    // Jogadores: nick -> Jogador (com numero e observer)
    private final Map<String, Jogador> jogadores = new ConcurrentHashMap<>();

    public JogadorRepository(int qtdNumeros) {
        this.qtdNumeros = qtdNumeros;
    }

    /**
     * Adiciona um novo jogador com número sorteado, sem observer ainda.
     * O observer será vinculado quando o cliente chamar receberDicas().
     *
     * @return o número sorteado, ou -1 se o nick já estiver em uso
     */
    public int adicionar(String nick) {
        if (jogadores.containsKey(nick)) return -1;

        Random random = new Random();
        int numeroSorteado = random.nextInt(qtdNumeros) + 1;

        // Cria o Jogador sem observer por enquanto (será vinculado em receberDicas)
        Jogador jogador = new Jogador(nick, numeroSorteado, null);
        jogadores.put(nick, jogador);

        return numeroSorteado;
    }

    public boolean todosTentaramAdvinhar() {
        for (Jogador jogador : jogadores.values()) {
            if (!jogador.isTentouAdvinhar()) {
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

    /**
     * Vincula o StreamObserver ao Jogador já cadastrado.
     * Chamado quando o cliente abre o stream de receberDicas().
     *
     * @return true se o jogador foi encontrado e atualizado, false caso contrário
     */
    public boolean vincularObserver(String nick, StreamObserver<DicaReply> observer) {
        Jogador jogador = jogadores.get(nick);
        if (jogador == null) return false;

        jogador.setObserver(observer);
        return true;
    }

    /**
     * Retorna o Jogador pelo nick, ou null se não existir.
     */
    public Jogador buscar(String nick) {
        return jogadores.get(nick);
    }

    /**
     * Retorna todos os jogadores cadastrados.
     */
    public Collection<Jogador> getTodos() {
        return jogadores.values();
    }

    /**
     * Retorna o mapa completo nick -> Jogador.
     */
    public Map<String, Jogador> getJogadores() {
        return jogadores;
    }

    public boolean isEmpty() {
        return jogadores.isEmpty();
    }
}
