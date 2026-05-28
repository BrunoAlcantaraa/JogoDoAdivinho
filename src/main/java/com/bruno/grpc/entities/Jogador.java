package com.bruno.grpc.entities;

import com.bruno.grpc.DicaReply;
import io.grpc.stub.StreamObserver;

public class Jogador {

    private String nick;
    private ImagemObjeto objeto;
    private int pontos;
    private StreamObserver<DicaReply> observer;
    private boolean tentouAdvinhar = false;

    public Jogador(String nick, ImagemObjeto objeto, StreamObserver<DicaReply> observer) {
        this.nick = nick;
        this.objeto = objeto;
        this.observer = observer;
    }

    public String getNick() {
        return nick;
    }

    public int getPontos() {
        return pontos;
    }

    public void setPontos(int pontos) {
        this.pontos = pontos;
    }

    public boolean isTentouAdivinhar() {
        return tentouAdvinhar;
    }

    public void setTentouAdivinhar(boolean tentouAdvinhar) {
        this.tentouAdvinhar = tentouAdvinhar;
    }

    public StreamObserver<DicaReply> getObserver() {
        return observer;
    }

    public void setNick(String nick) {
        this.nick = nick;
    }

    public ImagemObjeto getObjeto() {
        return objeto;
    }

    public void setObjeto(ImagemObjeto objeto) {
        this.objeto = objeto;
    }

    public void setObserver(StreamObserver<DicaReply> observer) {
        this.observer = observer;
    }

    public boolean isConectado() {
        return observer != null;
    }
}
