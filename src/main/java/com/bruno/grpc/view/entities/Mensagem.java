package com.bruno.grpc.view.entities;

import java.time.LocalDateTime;

public class Mensagem {
    private String texto;
    private String jogadorName;
    private LocalDateTime horaEnviada;

    public Mensagem(String texto, String jogadorName, LocalDateTime horaEnviada){
        this.texto = texto;
        this.jogadorName = jogadorName;
        this.horaEnviada = horaEnviada;
    }

    public String getTexto(){
        return texto;
    }

    public String getJogadorName(){
        return jogadorName;
    }

    public LocalDateTime getHoraEnviada(){
        return horaEnviada;
    }

    public String toString(){
        return "[" + horaEnviada.getHour() + ":" + horaEnviada.getMinute() + "] " + jogadorName + " - " + texto;
    }
}
