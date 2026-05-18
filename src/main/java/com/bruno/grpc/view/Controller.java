package com.bruno.grpc.view;

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

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Controller {

    @FXML private ListView<Mensagem> listMensagens;
    @FXML private TextField campoMensagem;
    @FXML private ListView<String> listJogadores;

    @FXML private Label turnoLabel;
    @FXML private Label turnoJogadorLabel;
    @FXML private Label objetoLabel;
    @FXML private Label jogadorLabel;
    @FXML private Label pontuacaoLabel;

    @FXML private ImageView objetoImagem;

    @FXML private Button btnIniciarJogo;
    @FXML private Button btnEnviarDica;
    @FXML private Button btnAdivinhar;
    @FXML private Button btnEnviarMsg;

    // -----------------------------------------------------------------------
    // Infraestrutura gRPC
    // -----------------------------------------------------------------------

    private GrpcGameClient grpcClient;
    private GameStatePoller statePoller;

    /** Executor de background para chamadas de rede bloqueantes. */
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
        // Deixa tudo desabilitado até o login
        desabilitarTudo();
        carregarImagemPadrao();

        // Pede o nick logo ao abrir a tela
        Platform.runLater(this::solicitarLogin);
    }

    /** Abre um dialog de login e conecta ao servidor. */
    private void solicitarLogin() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Entrar no jogo");
        dialog.setHeaderText("Digite seu nick para entrar:");
        dialog.setContentText("Nick:");

        // Impede que o usuário feche sem digitar
        Optional<String> resultado = dialog.showAndWait();

        if (resultado.isEmpty() || resultado.get().trim().isEmpty()) {
            mostrarAlerta("Erro", "Nick obrigatório", "Você precisa digitar um nick para jogar.");
            solicitarLogin();
            return;
        }

        String nick = resultado.get().trim();

        // Cria o cliente e entra em background (evita travar JavaFX)
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

    /** Chamado na thread JavaFX após entrar com sucesso. */
    private void aoEntrarComSucesso(String nick, String objeto, String mensagemServidor) {
        // Atualiza labels com dados do jogador
        jogadorLabel.setText("Jogador: " + nick);
        pontuacaoLabel.setText("Pontuação: 0");
        objetoLabel.setText("Seu número: " + objeto);

        adicionarMensagemSistema(mensagemServidor);
        adicionarMensagemSistema("Seu Objeto secreto é: " + objeto);

        // Abre o stream de dicas em background
        bgExecutor.submit(() -> grpcClient.receberDicas(
                msg -> Platform.runLater(() -> adicionarMensagemSistema(msg)),
                () -> Platform.runLater(() -> adicionarMensagemSistema("[Sistema] Stream encerrado.")),
                erro -> Platform.runLater(() -> adicionarMensagemSistema("[Erro] " + erro))
        ));

        // Inicia o polling de estado
        statePoller = new GameStatePoller(grpcClient, this::aoEstadoMudar);
        statePoller.iniciar();
    }

    // -----------------------------------------------------------------------
    // Reação a mudanças de estado (chamado pelo GameStatePoller via Platform.runLater)
    // -----------------------------------------------------------------------

    private void aoEstadoMudar(EstadoReply estado) {
        estadoAtual = estado.getEstado();
        jogadorAtualNoServidor = estado.getJogadorAtual();
        int rodada = estado.getRodada();

        // Atualiza labels de turno/rodada
        turnoLabel.setText("Rodada " + rodada);
        turnoJogadorLabel.setText("Vez de: " + (jogadorAtualNoServidor.isEmpty() ? "—" : jogadorAtualNoServidor));

        String meuNick = grpcClient.getNick();

        // Reseta flag de "já tentou adivinhar" quando o alvo muda
        if (!alvoTentadoNessaRodada.equals(jogadorAtualNoServidor)) {
            jaAdivinhouNessaRodada = false;
            alvoTentadoNessaRodada = jogadorAtualNoServidor;
        }

        // Habilita/desabilita botões conforme estado e quem é o jogador local
        switch (estadoAtual) {
            case ESPERANDO_INICIAR_JOGO -> {
                boolean souInicial = meuNick.equals(estado.getJogadorInicial());
                btnIniciarJogo.setDisable(!souInicial);
                btnEnviarDica.setDisable(true);
                btnAdivinhar.setDisable(true);

                if (!souInicial && !estado.getJogadorInicial().isEmpty()) {
                    turnoJogadorLabel.setText("Aguardando " + estado.getJogadorInicial() + " iniciar...");
                }
            }
            case ESPERANDO_DICA -> {
                btnIniciarJogo.setDisable(true);
                boolean minhaVez = meuNick.equals(jogadorAtualNoServidor);
                btnEnviarDica.setDisable(!minhaVez);
                btnAdivinhar.setDisable(true);
            }
            case ESPERANDO_ADVINHAR -> {
                btnIniciarJogo.setDisable(true);
                btnEnviarDica.setDisable(true);
                boolean possoAdivinhar = !meuNick.equals(jogadorAtualNoServidor) && !jaAdivinhouNessaRodada;
                btnAdivinhar.setDisable(!possoAdivinhar);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Ações dos botões (todos chamam rede em background)
    // -----------------------------------------------------------------------

    @FXML
    private void aoIniciarJogo() {
        btnIniciarJogo.setDisable(true);

        bgExecutor.submit(() -> {
            try {
                String msg = grpcClient.iniciarJogo();
                Platform.runLater(() -> adicionarMensagemSistema("[Servidor] " + msg));
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
        dialog.setHeaderText("Digite sua dica (uma palavra):");

        Optional<String> resultado = dialog.showAndWait();
        if (resultado.isEmpty() || resultado.get().trim().isEmpty()) return;

        String dica = resultado.get().trim();
        String[] palavras = dica.split("\\s+");

        if (palavras.length > 1) {
            mostrarAlerta("Erro", "Dica inválida", "A dica deve ser uma única palavra.");
            return;
        }

        btnEnviarDica.setDisable(true);

        bgExecutor.submit(() -> {
            try {
                String msg = grpcClient.enviarDica(dica);
                Platform.runLater(() -> adicionarMensagemSistema("[Servidor] " + msg));
            } catch (Exception e) {
                Platform.runLater(() -> {
                    mostrarAlerta("Erro", "Falha ao enviar dica", e.getMessage());
                    // Re-habilita se ainda for a vez do jogador
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
        // Pede o número a adivinhar
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Adivinhar");
        dialog.setHeaderText("Tente adivinhar o número de " + jogadorAtualNoServidor + " (1–100):");

        Optional<String> resultado = dialog.showAndWait();
        if (resultado.isEmpty() || resultado.get().trim().isEmpty()) return;

        String objeto = resultado.get().trim();

        btnAdivinhar.setDisable(true);
        jaAdivinhouNessaRodada = true;

        final String alvo = jogadorAtualNoServidor;
        final String objetoFinal = objeto;

        bgExecutor.submit(() -> {
            try {
                AdivinharReply resp = grpcClient.adivinharObjeto(alvo, objetoFinal);
                Platform.runLater(() -> {
                    adicionarMensagemSistema("[Servidor] " + resp.getMessage());
                    if (resp.getAcertou()) {
                        mostrarInfo("Acertou!", "Parabéns!", "Você acertou o número de " + alvo + "!");
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

    /** Envio de mensagem local no chat (sem integração gRPC — campo de chat local). */
    @FXML
    private void aoEnviarMSG() {
        String texto = campoMensagem.getText();
        if (texto == null || texto.trim().isEmpty()) return;

        String nick = grpcClient != null ? grpcClient.getNick() : "Eu";
        listMensagens.getItems().add(new Mensagem(texto.trim(), nick, LocalDateTime.now()));
        campoMensagem.clear();
    }

    // -----------------------------------------------------------------------
    // Utilitários de UI
    // -----------------------------------------------------------------------

    private void adicionarMensagemSistema(String texto) {
        listMensagens.getItems().add(new Mensagem(texto, "Sistema", LocalDateTime.now()));
        // Rola para a última mensagem
        int ultimo = listMensagens.getItems().size() - 1;
        if (ultimo >= 0) listMensagens.scrollTo(ultimo);
    }

    private void desabilitarTudo() {
        if (btnIniciarJogo != null) btnIniciarJogo.setDisable(true);
        if (btnEnviarDica != null)  btnEnviarDica.setDisable(true);
        if (btnAdivinhar != null)   btnAdivinhar.setDisable(true);
    }

    private void carregarImagemPadrao() {
        try {
            Image img = new Image(Objects.requireNonNull(
                    getClass().getResourceAsStream("")));
            if (objetoImagem != null) objetoImagem.setImage(img);
        } catch (Exception e) {
            // Imagem não encontrada — ignora silenciosamente
        }
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
    // Cleanup ao fechar a janela (chamar em HelloApplication.stop())
    // -----------------------------------------------------------------------

    public void shutdown() {
        if (statePoller != null) statePoller.parar();
        if (grpcClient != null) grpcClient.shutdown();
        bgExecutor.shutdownNow();
    }
}
