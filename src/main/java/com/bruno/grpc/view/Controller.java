package com.bruno.grpc.view;

import com.bruno.grpc.AdivinharReply;
import com.bruno.grpc.ChatReply;
import com.bruno.grpc.EstadoJogo;
import com.bruno.grpc.EstadoReply;
import com.bruno.grpc.JogadorInfo;
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
import java.util.List;
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
    @FXML private Label esperaLabel;

    // Área central — painel raiz (StackPane)
    @FXML private StackPane painelCentral;

    // Sub-painéis da área central
    @FXML private VBox painelEspera;
    @FXML private VBox painelSuaVez;
    @FXML private VBox painelDica;

    // Imagem e labels da área central
    @FXML private ImageView objetoImagem;
    @FXML private ImageView objetoImagemSuaVez;
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
    private String turnoObjetoAtualizado = "";
    private boolean decisaoContinuarSolicitada = false;
    private boolean resultadoPartidaMostrado = false;
    // Controle de limpeza de notificações por rodada
    private int ultimaRodadaNotificada = -1;

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
        adicionarNotificacao("Seu objeto: " + objeto);

        carregarImagemObjeto(objeto);

        // Abre stream de dicas (notificações do servidor)
        bgExecutor.submit(() -> grpcClient.receberDicas(
                msg -> Platform.runLater(() -> adicionarNotificacao(msg)),
                () -> Platform.runLater(() -> adicionarNotificacao("Stream encerrado.")),
                erro -> Platform.runLater(() -> adicionarNotificacao("Erro: " + erro))
        ));

        // Abre stream do chat gRPC
        bgExecutor.submit(() -> grpcClient.receberMensagensChat(
                chatMsg -> Platform.runLater(() -> adicionarMensagemChat(chatMsg)),
                () -> Platform.runLater(() -> adicionarNotificacao("Chat encerrado.")),
                erro -> Platform.runLater(() -> adicionarNotificacao("Chat: " + erro))
        ));

        // Inicia polling de estado + lista de jogadores
        statePoller = new GameStatePoller(grpcClient, this::aoEstadoMudar);
        statePoller.iniciar();
    }

    // -----------------------------------------------------------------------
    // Reação a mudanças de estado
    // -----------------------------------------------------------------------

    private void aoEstadoMudar(EstadoReply estado, List<JogadorInfo> jogadores) {
        estadoAtual = estado.getEstado();
        jogadorAtualNoServidor = estado.getJogadorAtual();
        int rodada = estado.getRodada();
        String meuNick = grpcClient.getNick();

        turnoLabel.setText(rodada > 0 ? "Rodada " + rodada : "Rodada —");
        turnoJogadorLabel.setText("Vez de: " + (jogadorAtualNoServidor.isEmpty() ? "—" : jogadorAtualNoServidor));

        // Atualiza lista de jogadores com pontuação
        atualizarListaJogadores(jogadores, meuNick);

        // Atualiza pontuação própria a partir da lista
        for (JogadorInfo ji : jogadores) {
            if (ji.getNick().equals(meuNick)) {
                pontuacaoLabel.setText("Pontuação: " + ji.getPontos());
                break;
            }
        }

        // Reseta flag de "já tentou adivinhar" quando o alvo muda
        if (!alvoTentadoNessaRodada.equals(jogadorAtualNoServidor)) {
            jaAdivinhouNessaRodada = false;
            alvoTentadoNessaRodada = jogadorAtualNoServidor;
        }

        // Limpa notificações da rodada anterior ao iniciar nova rodada
        if (rodada > 0 && rodada != ultimaRodadaNotificada) {
            ultimaRodadaNotificada = rodada;
            limparNotificacoesDaRodada();
        }

        switch (estadoAtual) {
            case ESPERANDO_INICIAR_JOGO -> {
                decisaoContinuarSolicitada = false;
                resultadoPartidaMostrado = false;
                boolean souInicial = meuNick.equals(estado.getJogadorInicial());
                btnIniciarJogo.setDisable(!souInicial);
                btnEnviarDica.setDisable(true);
                btnAdivinhar.setDisable(true);
                esperaLabel.setText("Aguardando o jogo iniciar...");
                mostrarPainel(painelEspera);

                if (!souInicial && !estado.getJogadorInicial().isEmpty()) {
                    turnoJogadorLabel.setText("Aguardando " + estado.getJogadorInicial() + " iniciar...");
                }
            }

            case ESPERANDO_DICA -> {
                decisaoContinuarSolicitada = false;
                resultadoPartidaMostrado = false;
                btnIniciarJogo.setDisable(true);
                boolean minhaVez = meuNick.equals(jogadorAtualNoServidor);
                btnEnviarDica.setDisable(!minhaVez);
                btnAdivinhar.setDisable(true);

                if (minhaVez) {
                    atualizarObjetoDaMinhaVez(rodada);
                    atualizarPainelSuaVez();
                    mostrarPainel(painelSuaVez);
                } else {
                    esperaLabel.setText("Aguardando " + jogadorAtualNoServidor + " dar a dica...");
                    mostrarPainel(painelEspera);
                }
            }

            case ESPERANDO_ADVINHAR -> {
                decisaoContinuarSolicitada = false;
                btnIniciarJogo.setDisable(true);
                btnEnviarDica.setDisable(true);
                boolean possoAdivinhar = !meuNick.equals(jogadorAtualNoServidor) && !jaAdivinhouNessaRodada;
                btnAdivinhar.setDisable(!possoAdivinhar);

                String dica = estado.getDicaAtual();
                String autor = estado.getAutorDicaAtual();
                if (!dica.isEmpty()) {
                    dicaTextoLabel.setText(dica);
                    dicaAutorLabel.setText("por " + (autor.isEmpty() ? "—" : autor));
                    mostrarPainel(painelDica);
                } else {
                    esperaLabel.setText("Aguardando dica...");
                    mostrarPainel(painelEspera);
                }
            }

            case AGUARDANDO_CONTINUAR -> {
                btnIniciarJogo.setDisable(true);
                btnEnviarDica.setDisable(true);
                btnAdivinhar.setDisable(true);
                turnoJogadorLabel.setText("Aguardando decisão de: " + estado.getJogadorInicial());
                mostrarResultadoPartida(jogadores);

                if (meuNick.equals(estado.getJogadorInicial())) {
                    esperaLabel.setText("Deseja continuar jogando?");
                    mostrarPainel(painelEspera);
                    solicitarDecisaoContinuar();
                } else {
                    esperaLabel.setText("Aguardando " + estado.getJogadorInicial() + " decidir se o jogo continua...");
                    mostrarPainel(painelEspera);
                }
            }

            case JOGO_ENCERRADO -> {
                btnIniciarJogo.setDisable(true);
                btnEnviarDica.setDisable(true);
                btnAdivinhar.setDisable(true);
                turnoJogadorLabel.setText("Jogo encerrado");
                esperaLabel.setText("Jogo encerrado.");
                mostrarPainel(painelEspera);
            }
        }
    }

    /**
     * Atualiza a ListView de jogadores com nick e pontuação.
     * Destaca o jogador local com " (você)".
     */
    private void atualizarListaJogadores(List<JogadorInfo> jogadores, String meuNick) {
        listJogadores.getItems().clear();
        for (JogadorInfo ji : jogadores) {
            String entrada = ji.getNick() + " — " + ji.getPontos() + " pts";
            if (ji.getNick().equals(meuNick)) {
                entrada += " (você)";
            }
            listJogadores.getItems().add(entrada);
        }
    }

    /**
     * Remove notificações de rodada (que começam com "Turno de" ou "Rodada")
     * para não poluir a tela entre rodadas. Mensagens do stream de dicas
     * (erros, acertos) da rodada anterior são limpas; o chat permanece intacto.
     */
    private void limparNotificacoesDaRodada() {
        // Remove as notificações de sistema mais antigas, mantendo as últimas 5
        // para contexto. Isso evita tela poluída sem apagar tudo abruptamente.
        var items = listNotificacoes.getItems();
        while (items.size() > 5) {
            items.remove(0);
        }
    }

    // -----------------------------------------------------------------------
    // Ações dos botões
    // -----------------------------------------------------------------------

    @FXML
    private void aoIniciarJogo() {
        Optional<Integer> totalRodadas = solicitarTotalRodadas();
        if (totalRodadas.isEmpty()) return;

        btnIniciarJogo.setDisable(true);

        bgExecutor.submit(() -> {
            try {
                String msg = grpcClient.iniciarJogo(totalRodadas.get());
                Platform.runLater(() -> adicionarNotificacao(msg));
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
                Platform.runLater(() -> adicionarNotificacao(msg));
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
                    adicionarNotificacao(resp.getMessage());
                    if (resp.getAcertou()) {
                        pontuacaoLabel.setText("Pontuação: " + resp.getPontuacaoAtualizada());
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
                Platform.runLater(() -> adicionarNotificacao("Chat: erro ao enviar."));
            }
        });
    }

    private void solicitarDecisaoContinuar() {
        if (decisaoContinuarSolicitada) return;
        decisaoContinuarSolicitada = true;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Fim da partida");
        alert.setHeaderText("Continuar jogando?");
        alert.setContentText("A partida chegou ao limite de rodadas.");

        ButtonType continuar = new ButtonType("Continuar");
        ButtonType encerrar = new ButtonType("Encerrar");
        alert.getButtonTypes().setAll(continuar, encerrar);

        Optional<ButtonType> resultado = alert.showAndWait();
        boolean deveContinuar = resultado.isPresent() && resultado.get() == continuar;

        bgExecutor.submit(() -> {
            try {
                String msg = grpcClient.decidirContinuar(deveContinuar);
                Platform.runLater(() -> adicionarNotificacao(msg));
            } catch (Exception e) {
                Platform.runLater(() -> {
                    decisaoContinuarSolicitada = false;
                    adicionarNotificacao("Erro ao enviar decisão.");
                });
            }
        });
    }

    private void mostrarResultadoPartida(List<JogadorInfo> jogadores) {
        if (resultadoPartidaMostrado || jogadores == null || jogadores.isEmpty()) return;
        resultadoPartidaMostrado = true;

        int maiorPontuacao = jogadores.stream()
                .mapToInt(JogadorInfo::getPontos)
                .max()
                .orElse(0);

        List<String> vencedores = jogadores.stream()
                .filter(jogador -> jogador.getPontos() == maiorPontuacao)
                .map(JogadorInfo::getNick)
                .toList();

        String tituloResultado = vencedores.size() == 1 ? "Vencedor" : "Empate";
        String mensagemResultado = vencedores.size() == 1
                ? vencedores.get(0) + " venceu com " + maiorPontuacao + " pts."
                : String.join(", ", vencedores) + " empataram com " + maiorPontuacao + " pts.";

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Fim da partida");
        alert.setHeaderText(tituloResultado);
        alert.setContentText(mensagemResultado);
        alert.showAndWait();
    }

    private Optional<Integer> solicitarTotalRodadas() {
        while (true) {
            TextInputDialog dialog = new TextInputDialog("5");
            dialog.setTitle("Configurar partida");
            dialog.setHeaderText("Quantas rodadas deseja jogar?");
            dialog.setContentText("Rodadas:");

            Optional<String> resultado = dialog.showAndWait();
            if (resultado.isEmpty()) {
                return Optional.empty();
            }

            try {
                int totalRodadas = Integer.parseInt(resultado.get().trim());
                if (totalRodadas > 0) {
                    return Optional.of(totalRodadas);
                }
            } catch (NumberFormatException ignored) {
                // Mostra alerta abaixo.
            }

            mostrarAlerta("Valor inválido", "Informe um número de rodadas válido", "Use um número inteiro maior que zero.");
        }
    }

    // -----------------------------------------------------------------------
    // Utilitários de UI
    // -----------------------------------------------------------------------

    private void mostrarPainel(VBox painel) {
        painelEspera.setVisible(false);
        painelSuaVez.setVisible(false);
        painelDica.setVisible(false);
        painel.setVisible(true);
    }

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

    private void atualizarObjetoDaMinhaVez(int rodada) {
        if (grpcClient == null) return;

        String chaveTurno = rodada + ":" + jogadorAtualNoServidor;
        if (chaveTurno.equals(turnoObjetoAtualizado)) return;
        turnoObjetoAtualizado = chaveTurno;

        bgExecutor.submit(() -> {
            try {
                String objetoAtual = grpcClient.atualizarMeuObjeto();
                Platform.runLater(() -> {
                    objetoLabel.setText("Seu objeto: " + objetoAtual);
                    atualizarPainelSuaVez();
                });
            } catch (Exception e) {
                Platform.runLater(() -> adicionarNotificacao("Erro ao atualizar objeto."));
            }
        });
    }

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

    private void adicionarMensagemChat(ChatReply chatMsg) {
        Mensagem m = new Mensagem(chatMsg.getTexto(), chatMsg.getNick(), LocalDateTime.now());
        listMensagens.getItems().add(m);
        int ultimo = listMensagens.getItems().size() - 1;
        if (ultimo >= 0) listMensagens.scrollTo(ultimo);
    }

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
