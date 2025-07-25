# Judicator - Em Desenvolvimento

**Judicator** é um plugin de moderação para servidores Velocity de Minecraft. Ele oferece uma ampla variedade de comandos para punições, silenciamentos e gerenciamento de jogadores, facilitando o trabalho da equipe de moderação no servidor.

---

## 📋 Comandos disponíveis

Os comandos abaixo são utilizados para aplicar, remover ou consultar punições de jogadores e IPs.

* Argumentos entre `{}` são opcionais.
* Argumentos entre `()` são obrigatórios.
* Cada comando exige uma permissão específica para ser executado.

---

### 🔨 Punições permanentes

* **`/ban (jogador) (motivo)`**
  Bane permanentemente um jogador do servidor.
  **Permissão:** `judicator.ban`

* **`/banip (jogador) (motivo)`**
  Bane permanentemente o IP associado ao jogador.
  **Permissão:** `judicator.ban.ip`

* **`/mute (jogador) (motivo)`**
  Silencia permanentemente o jogador no chat.
  **Permissão:** `judicator.mute`

* **`/muteip (jogador) (motivo)`**
  Silencia permanentemente todos os jogadores com o mesmo IP.
  **Permissão:** `judicator.mute.ip`

---

### ⏳ Punições temporárias

* **`/tempban (jogador) (tempo) {motivo}`**
  Bane temporariamente o jogador.
  **Permissão:** `judicator.tempban`

* **`/tempbanip (jogador) (tempo) {motivo}`**
  Bane temporariamente o IP do jogador.
  **Permissão:** `judicator.tempban.ip`

* **`/tempmute (jogador) (tempo) {motivo}`**
  Silencia o jogador por um período determinado.
  **Permissão:** `judicator.tempmute`

* **`/tempmuteip (jogador) (tempo) {motivo}`**
  Silencia temporariamente todos os jogadores do mesmo IP.
  **Permissão:** `judicator.tempmute.ip`

---

### 🚫 Advertências e expulsões

* **`/warn (jogador) {motivo}`**
  Emite uma advertência ao jogador.
  **Permissão:** `judicator.warn`

* **`/tempwarn (jogador) (tempo) {motivo}`**
  Emite uma advertência temporária.
  **Permissão:** `judicator.tempwarn`

* **`/unwarn (jogador/id)`**
  Remove advertências.
  **Permissão:** `judicator.unwarn`

* **`/warns (jogador)`**
  Exibe todas as advertências do jogador.
  **Permissão:** `judicator.warns`

* **`/kick (jogador) {motivo}`**
  Expulsa o jogador do servidor.
  **Permissão:** `judicator.kick`

---

### 🔍 Consulta e histórico

* **`/phistory (jogador)`**
  Mostra o histórico de punições do jogador.
  **Permissão:** `judicator.history`

* **`/pview (id)`**
  Visualiza os detalhes de uma punição específica.
  **Permissão:** `judicator.view`

---

### ✅ Remoção de punições

* **`/revoke (id)`**
  Remove qualquer punição com base no ID.
  **Permissão:** `judicator.admin`

---

### 📝 Denúncias

* **`/reportar (jogador) {motivo}`**
  Envia um relatório à moderação.
  
* **`/reportes`**
  Exibe relatórios pendentes.
  **Permissão:** `judicator.reports`

---

### ⚙️ Outros

* **`/punish (jogador) {motivo}`**
  Punição rápida com base na configuração.
  **Permissão:** `judicator.punish`

---

### 🔐 Permissão especial

* **`judicator.admin`**
  Acesso completo às funções administrativas do plugin.