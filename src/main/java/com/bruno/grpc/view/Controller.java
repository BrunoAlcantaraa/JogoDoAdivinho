package com.bruno.grpc.view;

import com.bruno.grpc.ChatReply;
import com.bruno.grpc.EstadoJogo;
import com.bruno.grpc.EstadoReply;
import com.bruno.grpc.AdivinharReply;
import com.bruno.grpc.view.client.GrpcGameClient;
import com.bruno.grpc.view.client.GameStatePoller;
import com.bruno.grpc.view.entities.Mensagem;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Controller {

    // -----------------------------------------------------------------------
    // Campos FXML
    // -----------------------------------------------------------------------

    // Chat
    @FXML private ListView<Mensagem> listMensagens;
    @FXML private TextField campoMensagem;

    // Notificações do servidor (stream de dicas)
    @FXML private ListView<String> listNotificacoes;

    // Lista de jogadores
    @FXML private ListView<String> listJogadores;

    // Labels de status
    @FXML private Label turnoLabel;
    @FXML private Label turnoJogadorLabel;
    @FXML private Label objetoLabel;
    @FXML private Label jogadorLabel;
    @FXML private Label pontuacaoLabel;

    // Área central — painel raiz (StackPane)
    @FXML private StackPane painelCentral;

    // Sub-painéis da área central
    @FXML private VBox painelEspera;
    @FXML private VBox painelSuaVez;
    @FXML private VBox painelDica;

    // Imagem e labels da área central
    @FXML private ImageView objetoImagem;           // (reservado, mantido para compatibilidade)
    @FXML private ImageView objetoImagemSuaVez;    // imagem no painel "sua vez"
    @FXML private Label nomeObjetoSuaVezLabel;
    @FXML private Label dicaTextoLabel;
    @FXML private Label dicaAutorLabel;

    // Botões
    @FXML private Button btnIniciarJogo;
    @FXML private Button btnEnviarDica;
    @FXML private Button btnAdivinhar;
    @FXML private Button btnEnviarMsg;

    // -----------------------------------------------------------------------
    // Infraestrutura gRPC
    // -----------------------------------------------------------------------

    private GrpcGameClient grpcClient;
    private GameStatePoller statePoller;

    private final ExecutorService bgExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "grpc-bg");
        t.setDaemon(true);
        return t;
    });

    // -----------------------------------------------------------------------
    // Estado local da tela
    // -----------------------------------------------------------------------

    private EstadoJogo estadoAtual = EstadoJogo.ESPERANDO_INICIAR_JOGO;
    private String jogadorAtualNoServidor = "";
    private boolean jaAdivinhouNessaRodada = false;
    private String alvoTentadoNessaRodada = "";

    // -----------------------------------------------------------------------
    // Inicialização
    // -----------------------------------------------------------------------

    @FXML
    public void initialize() {
        desabilitarTudo();
        mostrarPainel(painelEspera);
        Platform.runLater(this::solicitarLogin);
    }

    private void solicitarLogin() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Entrar no jogo");
        dialog.setHeaderText("Digite seu nick para entrar:");
        dialog.setContentText("Nick:");

        Optional<String> resultado = dialog.showAndWait();

        if (resultado.isEmpty() || resultado.get().trim().isEmpty()) {
            mostrarAlerta("Erro", "Nick obrigatório", "Você precisa digitar um nick para jogar.");
            solicitarLogin();
            return;
        }

        String nick = resultado.get().trim();
        grpcClient = new GrpcGameClient();

        bgExecutor.submit(() -> {
            grpcClient.entrar(
                    nick,
                    (n, objeto, msg) -> Platform.runLater(() -> aoEntrarComSucesso(n, objeto, msg)),
                    erro -> Platform.runLater(() -> {
                        mostrarAlerta("Erro ao entrar", "Não foi possível entrar no jogo", erro);
                        solicitarLogin();
                    })
            );
        });
    }

    private void aoEntrarComSucesso(String nick, String objeto, String mensagemServidor) {
        jogadorLabel.setText("Jogador: " + nick);
        pontuacaoLabel.setText("Pontuação: 0");
        objetoLabel.setText("Seu objeto: " + objeto);

        adicionarNotificacao(mensagemServidor);
        adicionarNotificacao("Seu objeto secreto é: " + objeto);

        // Carrega a imagem do objeto do jogador local
        carregarImagemObjeto(objeto);

        // Abre stream de dicas (notificações do servidor)
        bgExecutor.submit(() -> grpcClient.receberDicas(
                msg -> Platform.runLater(() -> adicionarNotificacao(msg)),
                () -> Platform.runLater(() -> adicionarNotificacao("[Sistema] Stream encerrado.")),
                erro -> Platform.runLater(() -> adicionarNotificacao("[Erro] " + erro))
        ));

        // Abre stream do chat gRPC
        bgExecutor.submit(() -> grpcClient.receberMensagensChat(
                chatMsg -> Platform.runLater(() -> adicionarMensagemChat(chatMsg)),
                () -> Platform.runLater(() -> adicionarNotificacao("[Chat] Stream de chat encerrado.")),
                erro -> Platform.runLater(() -> adicionarNotificacao("[Chat] " + erro))
        ));

        // Inicia polling de estado
        statePoller = new GameStatePoller(grpcClient, this::aoEstadoMudar);
        statePoller.iniciar();
    }

    // -----------------------------------------------------------------------
    // Reação a mudanças de estado
    // -----------------------------------------------------------------------

    private void aoEstadoMudar(EstadoReply estado) {
        estadoAtual = estado.getEstado();
        jogadorAtualNoServidor = estado.getJogadorAtual();
        int rodada = estado.getRodada();
        String meuNick = grpcClient.getNick();

        turnoLabel.setText(rodada > 0 ? "Rodada " + rodada : "Rodada —");
        turnoJogadorLabel.setText("Vez de: " + (jogadorAtualNoServidor.isEmpty() ? "—" : jogadorAtualNoServidor));

        // Reseta flag de "já tentou adivinhar" quando o alvo muda
        if (!alvoTentadoNessaRodada.equals(jogadorAtualNoServidor)) {
            jaAdivinhouNessaRodada = false;
            alvoTentadoNessaRodada = jogadorAtualNoServidor;
        }

        switch (estadoAtual) {
            case ESPERANDO_INICIAR_JOGO -> {
                boolean souInicial = meuNick.equals(estado.getJogadorInicial());
                btnIniciarJogo.setDisable(!souInicial);
                btnEnviarDica.setDisable(true);
                btnAdivinhar.setDisable(true);
                mostrarPainel(painelEspera);

                if (!souInicial && !estado.getJogadorInicial().isEmpty()) {
                    turnoJogadorLabel.setText("Aguardando " + estado.getJogadorInicial() + " iniciar...");
                }
            }

            case ESPERANDO_DICA -> {
                btnIniciarJogo.setDisable(true);
                boolean minhaVez = meuNick.equals(jogadorAtualNoServidor);
                btnEnviarDica.setDisable(!minhaVez);
                btnAdivinhar.setDisable(true);

                if (minhaVez) {
                    // É minha vez: mostrar imagem + nome do meu objeto
                    atualizarPainelSuaVez();
                    mostrarPainel(painelSuaVez);
                } else {
                    // Outro jogador vai dar dica
                    mostrarPainel(painelEspera);
                }
            }

            case ESPERANDO_ADVINHAR -> {
                btnIniciarJogo.setDisable(true);
                btnEnviarDica.setDisable(true);
                boolean possoAdivinhar = !meuNick.equals(jogadorAtualNoServidor) && !jaAdivinhouNessaRodada;
                btnAdivinhar.setDisable(!possoAdivinhar);

                // Mostra a dica atual no centro
                String dica = estado.getDicaAtual();
                String autor = estado.getAutorDicaAtual();
                if (!dica.isEmpty()) {
                    dicaTextoLabel.setText(dica);
                    dicaAutorLabel.setText("por " + (autor.isEmpty() ? "—" : autor));
                    mostrarPainel(painelDica);
                } else {
                    mostrarPainel(painelEspera);
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Ações dos botões
    // -----------------------------------------------------------------------

    @FXML
    private void aoIniciarJogo() {
        btnIniciarJogo.setDisable(true);

        bgExecutor.submit(() -> {
            try {
                String msg = grpcClient.iniciarJogo();
                Platform.runLater(() -> adicionarNotificacao("[Servidor] " + msg));
            } catch (Exception e) {
                Platform.runLater(() -> {
                    mostrarAlerta("Erro", "Falha ao iniciar jogo", e.getMessage());
                    btnIniciarJogo.setDisable(false);
                });
            }
        });
    }

    @FXML
    private void aoEnviarDica() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Enviar Dica");
        dialog.setHeaderText("Digite sua dica (uma única palavra):");

        Optional<String> resultado = dialog.showAndWait();
        if (resultado.isEmpty() || resultado.get().trim().isEmpty()) return;

        String dica = resultado.get().trim();
        if (dica.split("\\s+").length > 1) {
            mostrarAlerta("Dica inválida", "Apenas uma palavra", "A dica deve ser uma única palavra.");
            return;
        }

        btnEnviarDica.setDisable(true);

        bgExecutor.submit(() -> {
            try {
                String msg = grpcClient.enviarDica(dica);
                Platform.runLater(() -> adicionarNotificacao("[Servidor] " + msg));
            } catch (Exception e) {
                Platform.runLater(() -> {
                    mostrarAlerta("Erro", "Falha ao enviar dica", e.getMessage());
                    if (grpcClient.getNick().equals(jogadorAtualNoServidor)
                            && estadoAtual == EstadoJogo.ESPERANDO_DICA) {
                        btnEnviarDica.setDisable(false);
                    }
                });
            }
        });
    }

    @FXML
    private void aoAdivinhar() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Adivinhar");
        dialog.setHeaderText("Qual é o objeto de " + jogadorAtualNoServidor + "?");
        dialog.setContentText("Objeto (Pedra, Papel ou Tesoura):");

        Optional<String> resultado = dialog.showAndWait();
        if (resultado.isEmpty() || resultado.get().trim().isEmpty()) return;

        String objeto = resultado.get().trim();

        btnAdivinhar.setDisable(true);
        jaAdivinhouNessaRodada = true;

        final String alvo = jogadorAtualNoServidor;

        bgExecutor.submit(() -> {
            try {
                AdivinharReply resp = grpcClient.adivinharObjeto(alvo, objeto);
                Platform.runLater(() -> {
                    adicionarNotificacao("[Servidor] " + resp.getMessage());
                    if (resp.getAcertou()) {
                        mostrarInfo("Acertou!", "Parabéns!", "Você acertou o objeto de " + alvo + "!");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    jaAdivinhouNessaRodada = false;
                    mostrarAlerta("Erro", "Falha ao adivinhar", e.getMessage());
                });
            }
        });
    }

    /** Envia mensagem no chat via gRPC. */
    @FXML
    private void aoEnviarMSG() {
        String texto = campoMensagem.getText();
        if (texto == null || texto.trim().isEmpty()) return;

        String mensagem = texto.trim();
        campoMensagem.clear();

        if (grpcClient == null) return;

        bgExecutor.submit(() -> {
            try {
                grpcClient.enviarMensagemChat(mensagem);
            } catch (Exception e) {
                Platform.runLater(() -> adicionarNotificacao("[Chat] Erro ao enviar mensagem: " + e.getMessage()));
            }
        });
    }

    // -----------------------------------------------------------------------
    // Utilitários de UI
    // -----------------------------------------------------------------------

    /** Exibe apenas o sub-painel informado na área central. */
    private void mostrarPainel(VBox painel) {
        painelEspera.setVisible(false);
        painelSuaVez.setVisible(false);
        painelDica.setVisible(false);
        painel.setVisible(true);
    }

    /** Atualiza a imagem e o nome do objeto no painel "sua vez". */
    private void atualizarPainelSuaVez() {
        String objeto = grpcClient != null ? grpcClient.getObjeto() : null;
        if (objeto == null) return;

        nomeObjetoSuaVezLabel.setText(objeto);

        try {
            String caminhoImagem = "/img/" + objeto + ".png";
            Image img = new Image(Objects.requireNonNull(
                    getClass().getResourceAsStream(caminhoImagem)));
            objetoImagemSuaVez.setImage(img);
        } catch (Exception e) {
            objetoImagemSuaVez.setImage(null);
        }
    }

    /** Carrega a imagem do objeto para uso futuro no painelSuaVez. */
    private void carregarImagemObjeto(String nomeObjeto) {
        try {
            String caminhoImagem = "/img/" + nomeObjeto + ".png";
            Image img = new Image(Objects.requireNonNull(
                    getClass().getResourceAsStream(caminhoImagem)));
            objetoImagemSuaVez.setImage(img);
            nomeObjetoSuaVezLabel.setText(nomeObjeto);
        } catch (Exception e) {
            // Imagem não encontrada — ignora silenciosamente
        }
    }

    /** Adiciona uma mensagem recebida pelo stream do chat. */
    private void adicionarMensagemChat(ChatReply chatMsg) {
        // Exibe como "[HH:MM] nick - texto"
        Mensagem m = new Mensagem(chatMsg.getTexto(), chatMsg.getNick(), LocalDateTime.now());
        listMensagens.getItems().add(m);
        int ultimo = listMensagens.getItems().size() - 1;
        if (ultimo >= 0) listMensagens.scrollTo(ultimo);
    }

    /** Adiciona notificação do servidor na lista de notificações (direita). */
    private void adicionarNotificacao(String texto) {
        listNotificacoes.getItems().add(texto);
        int ultimo = listNotificacoes.getItems().size() - 1;
        if (ultimo >= 0) listNotificacoes.scrollTo(ultimo);
    }

    private void desabilitarTudo() {
        if (btnIniciarJogo != null) btnIniciarJogo.setDisable(true);
        if (btnEnviarDica != null)  btnEnviarDica.setDisable(true);
        if (btnAdivinhar != null)   btnAdivinhar.setDisable(true);
    }

    private void mostrarAlerta(String titulo, String cabecalho, String conteudo) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(titulo);
        alert.setHeaderText(cabecalho);
        alert.setContentText(conteudo);
        alert.showAndWait();
    }

    private void mostrarInfo(String titulo, String cabecalho, String conteudo) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(titulo);
        alert.setHeaderText(cabecalho);
        alert.setContentText(conteudo);
        alert.showAndWait();
    }

    // -----------------------------------------------------------------------
    // Cleanup ao fechar a janela
    // -----------------------------------------------------------------------

    public void shutdown() {
        if (statePoller != null) statePoller.parar();
        if (grpcClient != null) grpcClient.shutdown();
        bgExecutor.shutdownNow();
    }
}