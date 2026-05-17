package com.bruno.grpc.view.entities;

public class ImagemJogo {
    private int idObjeto;
    private String nomeObjeto;
    private String caminhoImagem;

    public ImagemJogo(int idObjeto,String nomeObjeto, String caminhoImagem) {
        this.idObjeto = idObjeto;
        this.nomeObjeto = nomeObjeto;
        this.caminhoImagem = caminhoImagem;
    }

    public int getIdObjeto() {
        return idObjeto;
    }

    public String getNomeObjeto(){
        return nomeObjeto;
    }

    public String getCaminhoImagem(){
        return caminhoImagem;
    }
}
