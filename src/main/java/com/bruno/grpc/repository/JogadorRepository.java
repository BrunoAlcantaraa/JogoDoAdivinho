package com.bruno.grpc.repository;

import com.bruno.grpc.DicaReply;
import com.bruno.grpc.entities.Jogador;
import com.bruno.grpc.view.entities.ImagemObjeto;
import com.bruno.grpc.view.util.ImagemGerenciador;
import io.grpc.stub.StreamObserver;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class JogadorRepository {

    private final Map<String, Jogador> jogadores = new ConcurrentHashMap<>();

    public JogadorRepository() {
    }

    public String adicionar(String nick) {
        if (jogadores.containsKey(nick)) return null;

        ImagemObjeto imagem = ImagemGerenciador.getImagemAleatoria();

        // Cria o Jogador sem observer por enquanto (será vinculado em receberDicas)
        Jogador jogador = new Jogador(nick, imagem, null);
        jogadores.put(nick, jogador);

        return imagem.getNomeObjeto();
    }

    public String sortearNovoObjeto(String nick) {
        Jogador jogador = jogadores.get(nick);
        if (jogador == null) return null;

        ImagemObjeto objetoAtual = jogador.getObjeto();
        ImagemObjeto novoObjeto = ImagemGerenciador.getImagemAleatoria();

        if (ImagemGerenciador.imagens.size() > 1 && objetoAtual != null) {
            while (novoObjeto.getNomeObjeto().equals(objetoAtual.getNomeObjeto())) {
                novoObjeto = ImagemGerenciador.getImagemAleatoria();
            }
        }

        jogador.setObjeto(novoObjeto);
        return novoObjeto.getNomeObjeto();
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
