# 🎯 Jogo do Adivinho

Jogo de adivinhação multiplayer em turnos desenvolvido em Java utilizando gRPC e JavaFX.

Cada jogador recebe um objeto aleatório e deve fornecer dicas para que os outros participantes tentem acertar corretamente. O jogo possui sistema de turnos, chat em tempo real e atualização automática do estado da partida.

---

## ✨ Funcionalidades

* 🎲 Objetos sorteados aleatoriamente
* 🔄 Sistema de turnos
* 💬 Chat em tempo real
* 🏆 Sistema de pontuação
* ⚡ Atualização automática do estado do jogo
* 🌐 Comunicação distribuída utilizando gRPC

---

## 🛠️ Tecnologias Utilizadas

* ☕ Java
* 🎨 JavaFX
* 🌐 gRPC
* 📦 Protocol Buffers (Protobuf)
* 🔧 Maven

---

## 📁 Estrutura do Projeto

```text id="estrutura"
src/
├── entities/
├── repository/
├── service/
│   ├── client/
│   ├── game/
│   └── server/
├── util/
├── view/
│   ├── controller/
│   └── fxml/
├── proto/
└── resources/
    ├── gui/
    └── img/
```

### 📌 Organização

* `entities/` → Entidades
* `repository/` → Gerenciamento de dados dos jogadores
* `service/` → Cliente, servidor e lógicas do jogo
* `util/` → Classes auxiliares
* `view/` → Interface gráfica
* `proto/` → Arquivo .proto
* `resources/` → Main.fxml e Imagens dos objetos

---

## 🚀 Como Executar

### Pré-requisitos

* Java 17+
* Maven

---

### 📥 Clonar o projeto

```bash
git clone https://github.com/seu-usuario/jogo-do-advinho.git
```

---

### ⚙️ Compilar o projeto

```bash
mvn clean install
```

---

### 🖥️ Executar o servidor

```bash
mvn exec:java -Dexec.mainClass="com.bruno.grpc.service.Servidor"
```

---

### 👤 Executar os clientes

```bash
mvn exec:java -Dexec.mainClass="com.bruno.grpc.view.client.Cliente"
```

Execute um cliente para cada jogador.

---

## 📌 Observações

O projeto utiliza comunicação cliente-servidor baseada em gRPC, permitindo troca de mensagens em tempo real entre múltiplos jogadores.

Além disso, o estado do jogo é atualizado automaticamente para todos os clientes conectados.
