package com.bruno.grpc.view.entities;

public class ImagemObjeto {
    private int idObjeto;
    private String nomeObjeto;
    private String caminhoImagem;

    public ImagemObjeto(int idObjeto, String nomeObjeto, String caminhoImagem) {
        this.idObjeto = idObjeto;
        this.nomeObjeto = nomeObjeto;
        this.caminhoImagem = caminhoImagem;
    }

    public String getNomeObjeto(){
        return nomeObjeto;
    }

    public void setNomeObjeto(String nomeObjeto) {
        this.nomeObjeto = nomeObjeto;
    }

    public int getIdObjeto() {
        return idObjeto;
    }

    public String getCaminhoImagem(){
        return caminhoImagem;
    }
}
