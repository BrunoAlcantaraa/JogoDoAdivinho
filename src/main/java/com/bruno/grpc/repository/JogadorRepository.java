package com.bruno.grpc.repository;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class JogadorRepository {

    // Números que serão distribuídos entre os clients
    private final int qtdNumeros;

    // Jogadores
    private final Map<String, Integer> jogadores = new ConcurrentHashMap<>();

    public JogadorRepository(int qtdNumeros) {
        this.qtdNumeros = qtdNumeros;
    }

    /*
        Método para adicionar um jogador
     */
    public Integer adicionar(String nick) {
        if (jogadores.get(nick) != null) return -1;

        // Sorteia um número
        Random random = new Random();
        int numeroSorteado = random.nextInt(qtdNumeros) + 1;

        // Adiciona ao "banco"
        jogadores.put(nick, numeroSorteado);

        return numeroSorteado;
    }

    public Map<String, Integer> getJogadores() {
        return jogadores;
    }

}
