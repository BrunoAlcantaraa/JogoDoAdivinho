package com.bruno.grpc.view;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import com.bruno.grpc.view.entities.ImagemJogo;
import com.bruno.grpc.view.entities.Mensagem;
import com.bruno.grpc.view.util.ImagemGerenciador;
//import org.example.jogodoadivinhogui.ClassesCoisas.ImagemJogo;
//import org.example.jogodoadivinhogui.ClassesCoisas.Mensagem;
//import org.example.jogodoadivinhogui.Gerenciadores.ImagemGerenciador;

import java.time.LocalDateTime;
import java.util.*;

public class HelloController {

    private String PedirMensagem(String titulo, String mensagemRequisitada){ //padrão para pedir as mensagens
        TextInputDialog requisitar = new TextInputDialog(); //cria a caixa de texto

        requisitar.setTitle(titulo); //seta o titulo
        requisitar.setHeaderText(mensagemRequisitada); //seta a mensagem da caixa

        Optional<String> resultado =  requisitar.showAndWait(); //pega o que o user escreveu

        String dica = resultado.get().trim(); //tira os espaços a mais caso tenha
        String[] palavras = dica.split("\\s+"); //separa as palavras

        return resultado.orElse(null); //retorna a mensagem ou retorna null caso não escreva nada
    }

    private void CriarAlerta(String titulo, String mensagem, String feedback){ //mostra uma tela de erro para o usuário
        Alert alerta = new Alert(Alert.AlertType.ERROR);

        alerta.setTitle(titulo); //seta o titulo
        alerta.setHeaderText(mensagem); //seta a mensagem
        alerta.setContentText(feedback); //seta um feedback para o usuário
        alerta.showAndWait(); //espera o usuário fechar
    }

    private void ValidarDica(String titulo, String mensagem) { //funcao para pegar a dica global
        boolean continuar = true;
        String[] palavras = new String[0];
        
        while (continuar) { //abre um while até ele fechar a tela ou passar por ela
            String userResposta = PedirMensagem(
                    titulo,
                    mensagem);

            if(userResposta != null) { //caso tenha alguma mensagem, ele vai criar um vetor para verificar depois o tamanho da dica
                String dica = userResposta.trim();
                palavras = dica.split("\\s+");
            }

            if (userResposta == null) { //caso ele aperte em cancelar
                return; //volta para a tela normal
            } else if (userResposta.isBlank()){ //caso esteja em branco
                CriarAlerta(
                        "ERRO",
                        "Dica está vazia",
                        "Não deixe a dica em branco"
                );
            } else if (palavras.length > 1){ //se a dica for muito longa (ter mais de uma palavra)
                CriarAlerta(
                        "ERRO",
                        "Dica muito longa",
                        "Escreva apenas uma palavra"
                );
            } else {
                continuar = false; //termina o loop
            }
        }
    }

    @FXML
    private void RequisitarDicaGlobal(){
        ValidarDica("Enviar dica global", "Digite no máximo uma palavra");
        EnviarMsgSistema("O jogador X enviou uma dica a todos os jogadores");
    }

    private void RequisitarDicaPrivada(){
        ValidarDica("Enviar dica privada", "Digite no máximo uma palavra e a dica pode ser mentira");
    }

    private void RequisitarPalpite() { //caso algum objeto possa ter mais que uma palavra, vamos ter que mudar um pouco o código
        ValidarDica("Enviar palpite", "Digite o objeto (no máximo uma palavra)");
    }

    @FXML
    private ListView<Mensagem> listMensagens;

    @FXML
    private TextField campoMensagem;

    @FXML
    private void EnviarMSG(){ //envia mensagem no chat
        String texto = campoMensagem.getText(); //pega o texto do campo

        if(texto == null || texto.trim().isEmpty()){ //verifica se o botao for apertado enqt vazio
            return;
        }

        Mensagem msg = new Mensagem(
                texto,
                "Ian",
                LocalDateTime.now()
        );

        listMensagens.getItems().add(msg); //adiciona na lista

        campoMensagem.clear(); //limpa o campo
    }

    private void EnviarMsgSistema(String texto){
        Mensagem msg = new Mensagem(
                texto,
                "Sistema",
                LocalDateTime.now()
        );

        listMensagens.getItems().add(msg);
    }

    @FXML
    private ListView<String> listJogadores; //dps troque para <jogador> para ele listar jogadores mesmo, isso é so um exemplo

    /*
    parte de teste, essa lista é inicializada pra testar a parte de selecionar jogador
    quando for integrar com o rpc provavelmente será necessário transformar em uma lista de classe jogador
    igual fiz com mensagem, mas dai depende da funcionalidade de jogadores
     */
    List<String> jogadores = Arrays.asList(
            "Ian",
            "Brunin",
            "Japones safado",
            "Igão",
            "Jefinho"
    );

    private String RequisitarJogador(String titulo, String Mensagem){ //padrão para usar em mais funções
        ChoiceDialog<String> dialog = new ChoiceDialog<>("Jogador", jogadores);

        dialog.setTitle(titulo);
        dialog.setHeaderText(Mensagem);

        Optional<String> resultado = dialog.showAndWait();

        return resultado.orElse(null);
    }

    @FXML
    private void RequisitarJogadorDicaPrivada(){
        boolean continuar = true;

        while (continuar) {
            String jogador = RequisitarJogador("Requisitar troca de dicas", "Selecione um jogador"); //abrir a dialogchoice

            if (Objects.equals(jogador, "Jogador")) { //caso a opção seleciona seja a padrão
                CriarAlerta(
                        "ERRO",
                        "Jogador inválido selecionado",
                        "Selecione um jogador válido"
                );
            }else if(jogador == null){ //caso ele aperte em cancelar
                return;
            }
            else{ //caso ele selecione um jogador
                RequisitarDicaPrivada(); //faz o processo de receber uma dica
                EnviarMsgSistema("Jogador X trocou uma dica com o jogador " + jogador); //sistema informa a troca de dicas
                continuar = false; //sai do loop para voltar a tela
            }
        }
    }

    @FXML
    private void RequisitarJogadorAdivinhar(){
        boolean continuar = true;

        while (continuar) {
            String jogador = RequisitarJogador("Adivinhar", "Selecione um jogador para adivinhar");
            if (Objects.equals(jogador, "Jogador")) {
                CriarAlerta("ERRO",
                        "Jogador inválido selecionado",
                        "Selecione um jogador válido"
                );
            }else if (jogador == null){
                return;
            }else{
                RequisitarPalpite();
                EnviarMsgSistema("O jogador X tentou fazer um palpite"); //mudar o X para o jogador em si
                continuar = false;
            }
        }

    }

    @FXML
    private Label turnoLabel = new Label();
    int numeroTurno = 1;

    @FXML
    private void PassarTurno(){ //a lógica depende um pouco do back, ent eu vou só fazer uma função que passa o numero
        numeroTurno++;
        turnoLabel.setText("Turno " + numeroTurno);
    }

    @FXML
    private Label objetoLabel = new Label(); //dps disso a lógica para definir o nome do objeto na tela

    @FXML
    private Label jogadorLabel = new Label(); //dps disso a lógica para mudar o nome do jogador na tela

    @FXML
    private Label turnoJogadorLabel = new Label(); //esse é o label q fala de qual jogador está a vez

    @FXML
    private Label pontuacaoLabel = new Label(); //dps disso a lógica para mudar a pontuação

    @FXML
    private ImageView objetoImagem = new ImageView(); //imagem do objeto na interface

    ImagemGerenciador imagemGerenciador = new ImagemGerenciador(); //gerenciador para pegar o objeto

    int id = 1; //DEPOIS ISSO PRECISA SER MUDADO PARA SER O NUMERO Q O JOGADOR RECEBE NO INICIO

    private ImagemJogo BuscarObjeto(int id){
        for(ImagemJogo imagem : imagemGerenciador.imagens){ //vai passando por todos os objetos na lista criada no gerenciador
            if(imagem.getIdObjeto() == id){ //se for igual ele retorna o objeto
                return imagem;
            }
        }
        return null;
    }

    private Image DefinirImagem(){

        ImagemJogo imagem = BuscarObjeto(id); //pega a imagem

        if(imagem != null) {
            objetoLabel.setText("Objeto: " + imagem.getNomeObjeto()); //define o nome do objeto
            return new Image(
                    Objects.requireNonNull(
                            getClass().getResourceAsStream(
                                    imagem.getCaminhoImagem() //define o caminho para o imageview
                            )
                    )
            );
        }

        return null;
    }

    @FXML
    public void initialize() { //ao iniciar a tela ele já pega a imagem e seta

        objetoImagem.setImage(
                DefinirImagem()
        );
    }
}
