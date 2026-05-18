package com.bruno.grpc.view.util;

import com.bruno.grpc.view.entities.ImagemObjeto;
//import org.example.jogodoadivinhogui.ClassesCoisas.ImagemJogo;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ImagemGerenciador {

    public static final List<ImagemObjeto> imagens = new ArrayList<>();

    static {
        imagens.add(new ImagemObjeto(
                1,
                "Pedra",
                "img/Pedra.png"
        ));

        imagens.add(new ImagemObjeto(
                2,
                "Papel",
                "img/Papel.png"
        ));

        imagens.add(new ImagemObjeto(
                3,
                "Tesoura",
                "img/Tesoura.png"
        ));
    }

    public static ImagemObjeto getImagemAleatoria() {
        Random random = new Random();
        int index = random.nextInt(imagens.size());

        return imagens.get(index);
    }

}
