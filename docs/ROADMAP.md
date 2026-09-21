# Roadmap

Desenvolvimento incremental: cada fase termina **compilando, com testes passando e lint limpo**.
Legenda: ✅ concluído · 🟡 em andamento/parcial · ⬜ não iniciado.

## Ajustes em relação ao plano original

| Ajuste | Motivo |
|---|---|
| **Peso corporal (registro + gráfico) antecipado para a Fase 5.** Bioimpedância continua na Fase 11. | Recordes e estatísticas de exercícios com peso corporal (Fase 5) precisam do peso corporal registrado. O modelo de métricas já nasce flexível para a bioimpedância. |
| **Fatia vertical da Fase 1 + Fase 2 entregue junto com a Fase 0.** | "Biblioteca → selecionar → criar treino → salvar → reabrir" valida todas as camadas (UI, ViewModel, repositório, Room, `:domain`) cedo, antes de multiplicar telas. |
| Tema (claro/escuro/sistema) na Fase 0. | Faz parte do design system e afeta todas as telas. |

## Fases

### Fase 0 — Fundação 🟡
- ✅ Inspeção do ambiente; instalação do Android SDK mínimo (autorizada).
- ✅ Git, `.gitignore`, `.gitattributes`, `.editorconfig`.
- ✅ Documentação: produto, arquitetura, banco, sync, roadmap, decisões.
- ⬜ Projeto Android em Java (AGP 9.4.1, Gradle 9.7.1, compile/target 37, min 28), módulos `:app` e `:domain`.
- ⬜ Banco inicial (Room v1) com schema exportado.
- ⬜ Navegação (single-activity + bottom navigation) e design system (Material 3, paleta própria, claro/escuro).
- ⬜ Seletor de tema nas configurações.
- ⬜ CI (GitHub Actions: build + testes + lint) — quando o repositório for publicado.

### Fase 1 — Biblioteca ⬜
- ⬜ Grupos/subgrupos hierárquicos, equipamentos, catálogo inicial (JSON com UUIDs fixos).
- ⬜ Lista, busca tolerante a acentos, filtros combinados (grupo + subgrupo + equipamento + papel).
- ⬜ Detalhe do exercício (principal em destaque, secundários, instruções, dicas, erros comuns).
- ⬜ Mídia (imagens início/fim, animação) + cache.
- ⬜ Ampliar o catálogo.

### Fase 2 — Templates ⬜
- ⬜ Criar, editar, salvar e reabrir; defaults 3 × 12; faixa de reps, carga, descanso, observações.
- ⬜ Reordenar (arrastar e soltar), remover exercício, duplicar e excluir (lógico) treino.
- ⬜ Edição série a série (cargas/reps diferentes por série).
- ⬜ Técnicas avançadas (tabela de técnicas, badges, ⓘ) e grupos (supersérie…).
- ⬜ Arquivar/desarquivar.

### Fase 3 — Treino ativo ⬜
Sessão, snapshots, lista completa em cards, planejado/anterior/atual, sugestões, cronômetro por
timestamps, pausas, descanso, foreground service `health` + notificação, recuperação após morte do
processo, finalização com validação.

### Fase 4 — Histórico ⬜
Resumo, calendário, sessão antiga, comparação com anterior.

### Fase 5 — Progresso ⬜
Gráficos (biblioteca a decidir — ADR pendente), PRs, estatísticas semanais/mensais e por grupo
muscular, **peso corporal**.

### Fase 6 — Rotinas ⬜
Semanal e cíclica (engine no `:domain`), ajustes como eventos, metas, sequências de aderência,
lembretes.

### Fase 7 — Conquistas ⬜
Definições como dados, níveis, progresso, modal de desbloqueio.

### Fase 8 — Backend ⬜
Spring Boot 4 + PostgreSQL + Flyway + OpenAPI; contas (e-mail/senha, recuperação, verificação,
tokens com renovação, logout, Google).

### Fase 9 — Sync ⬜
Conforme [SYNC.md](SYNC.md).

### Fase 10 — Compartilhamento ⬜
Link, código, deep link e QR; importação como cópia; revogação.

### Fase 11 — Bioimpedância ⬜
Métricas flexíveis sobre o modelo de medições da Fase 5.

## Próxima etapa recomendada
Completar a Fase 2 (edição série a série, técnicas e grupos) e iniciar a Fase 3 (treino ativo), que é
o coração do produto.
