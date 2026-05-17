package com.bruno.grpc.view.util;

import com.bruno.grpc.view.entities.ImagemJogo;
//import org.example.jogodoadivinhogui.ClassesCoisas.ImagemJogo;

import java.util.ArrayList;
import java.util.List;

public class ImagemGerenciador {
    public List<ImagemJogo> imagens = new ArrayList<ImagemJogo>();

    public ImagemGerenciador() {
        imagens.add(new ImagemJogo( //vai adicionando as imagens para formar a lista
                1,
                "OldSpice",
                "/imagens/oldspice.png"
        ));
    }
}
