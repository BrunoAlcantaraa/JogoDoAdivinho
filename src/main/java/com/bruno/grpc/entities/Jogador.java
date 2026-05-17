package com.bruno.grpc.entities;

import com.bruno.grpc.DicaReply;
import io.grpc.stub.StreamObserver;

public class Jogador {

    private String nick;
    private int numero;
    private int pontos;
    private StreamObserver<DicaReply> observer;
    private boolean tentouAdvinhar = false;

    public Jogador(String nick, int numero, StreamObserver<DicaReply> observer) {
        this.nick = nick;
        this.numero = numero;
        this.observer = observer;
    }

    public String getNick() {
        return nick;
    }

    public int getNumero() {
        return numero;
    }

    public int getPontos() {
        return pontos;
    }

    public void setPontos(int pontos) {
        this.pontos = pontos;
    }

    public boolean isTentouAdvinhar() {
        return tentouAdvinhar;
    }

    public void setTentouAdvinhar(boolean tentouAdvinhar) {
        this.tentouAdvinhar = tentouAdvinhar;
    }

    public StreamObserver<DicaReply> getObserver() {
        return observer;
    }

    /**
     * Vincula o observer ao jogador após ele se inscrever no stream receberDicas().
     */
    public void setObserver(StreamObserver<DicaReply> observer) {
        this.observer = observer;
    }

    /**
     * Indica se o jogador já abriu seu stream de dicas.
     */
    public boolean isConectado() {
        return observer != null;
    }
}
