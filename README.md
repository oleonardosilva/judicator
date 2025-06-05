# Judicator - Em Desenvolvimento

**Judicator** é um plugin de moderação para servidores Velocity de Minecraft. Ele oferece uma ampla variedade de comandos para punições, silenciamentos e gerenciamento de jogadores, facilitando o trabalho da equipe de moderação no servidor.

---

## 📋 Comandos disponíveis

Os comandos abaixo são utilizados para aplicar, remover ou consultar punições de jogadores e IPs.

* Argumentos entre `{}` são opcionais.
* Argumentos entre `()` são obrigatórios.

---

### 🔨 Punições permanentes

* **`/ban (jogador) (motivo)`**
  Bane permanentemente um jogador do servidor.

* **`/banip (jogador) (motivo)`**
  Bane permanentemente o IP associado ao jogador, impedindo novas contas do mesmo IP.

* **`/mute (jogador) (motivo)`**
  Silencia permanentemente o jogador no chat.

* **`/muteip (jogador) (motivo)`**
  Silencia permanentemente todos os jogadores com o mesmo IP.

---

### ⏳ Punições temporárias

* **`/tempban (jogador) (tempo) {motivo}`**
  Bane temporariamente o jogador. Ex: `/tempban Steve 3:dias,2:horas Uso de hack`.

* **`/tempbanip (jogador) (tempo) {motivo}`**
  Bane temporariamente o IP do jogador.

* **`/tempmute (jogador) (tempo) {motivo}`**
  Silencia o jogador por um período determinado.

* **`/tempmuteip (jogador) (tempo) {motivo}`**
  Silencia temporariamente todos os jogadores do mesmo IP.

---

### 🚫 Advertências e expulsões

* **`/warn (jogador) {motivo}`**
  Emite uma advertência ao jogador.

* **`/tempwarn (jogador) (tempo) {motivo}`**
  Emite uma advertência que expira após o tempo definido.

* **`/unwarn (jogador/id)`**
  Remove uma advertência específica ou todas de um jogador.

* **`/warns (jogador)`**
  Exibe todas as advertências do jogador.

* **`/kick (jogador) {motivo}`**
  Expulsa o jogador do servidor sem aplicar uma punição duradoura.

---

### 🔍 Consulta e histórico

* **`/phistory (jogador)`**
  Mostra o histórico completo de punições do jogador.

* **`/pview (id)`**
  Visualiza os detalhes de uma punição específica com base no ID.

---

### ✅ Remoção de punições

* **`/unban (jogador)`**
  Remove um banimento ativo do jogador.

* **`/unmute (jogador)`**
  Remove um silenciamento ativo do jogador.

* **`/unpunish (id)`**
  Remove qualquer punição com base no ID.

---

### 📝 Relatórios

* **`/reportar (jogador) {motivo}`**
  Envia um relatório de má conduta para a equipe de moderação.

* **`/reportes`**
  Exibe a lista de relatórios pendentes para análise.

---

### ⚙️ Outros

* **`/punish (jogador) {motivo}`**
  Interação rápida de punição já registrada, podendo definir o tipo e duração da punição pelo arquivo 'config.yml'.
