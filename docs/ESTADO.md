# ESTADO — o bastão entre chats

> **Este arquivo é a memória que sobrevive à troca de chat.** Quem assume lê
> [`AGENTS.md`](../AGENTS.md), [`COORDENACAO.md`](COORDENACAO.md) e este arquivo, e continua.
>
> **Atualize-o em todo ponto de parada**, e commite junto com o código. Um `ESTADO.md` velho é pior
> que nenhum: ele faz o chat novo trabalhar com confiança sobre uma informação falsa.
>
> Escreva no passado e em fatos. "Deve funcionar" não vai aqui — vai em "pendente de aparelho".

---

## Atualizado em

**28/11/2026** · por: Claude (sessão de revisão/handover) · commit da época: `45bde34`

## Onde estamos

| | |
|---|---|
| **Branch** | `claude/determined-mccarthy-u3ttk4` |
| **PR aberto** | [#1](https://github.com/thiagojosetj/flowgym/pull/1) contra `main`, 59 commits, `clean`, CI verde |
| **Último portão rodado** | **570 testes** (349 app + 221 domain), **0 falhas**, lint só com `AndroidGradlePluginVersion` |
| **Banco** | versão **4** |
| **Último ADR** | **ADR-0042** (gráficos em `Canvas`, sem biblioteca) |
| **Quem desenvolve** | o Coordenador e seu time (este arquivo). O **Claude** entrou como **revisor** (`CLAUDE.md`) |

## O que está pronto e provado

Fases 0 a 4 inteiras, e a Fase 5 em tudo que não toca no banco. Em 27–28/11/2026 fecharam os
últimos itens da Fase 2 que não dependiam de esquema: **método do grupo** (SS/BI/TRI/GS deixaram de
ser dados sem uso), **editar um grupo** depois de criado, e **arquivar/desarquivar treino**
(PRODUCT_SPEC §6.3.1).

O detalhe item por item, com as mutações de cada um, está no [`ROADMAP.md`](ROADMAP.md). Não copie
para cá: lá é a fonte.

## Em andamento agora

**Nada.** A árvore está limpa e o último commit está empurrado. Quem assume começa uma tarefa nova.

## Próximo passo recomendado

Livre de aparelho **e** de banco novo — pode começar hoje:

1. **Exercícios personalizados do usuário.** O modelo **já suporta**: `exercise.owner_user_id` e
   `exercise.is_active`. **Sem migração.** É o maior item livre, e o que mais muda o app para quem
   usa (hoje o catálogo é fechado). Vale checar antes: as leituras da biblioteca já filtram
   `owner_user_id`/`is_active`? O arquivamento de treino de 28/11 foi exatamente esse caso — a
   coluna existia desde a v1 e todas as leituras já filtravam; faltava a **escrita** e um jeito de
   **ver** o resultado.
2. **Ampliar o catálogo** de exercícios (`CatalogSeeder` + o asset de seed).
3. **Ilustração do movimento por exercício** — os 55 prompts já estão em
   [`EXERCISE_ART.md`](EXERCISE_ART.md).

## Bloqueado, e em quem

**No dono** (nenhum agente consegue; não marque como feito):

1. Rodar [`DEVICE_TESTING.md`](DEVICE_TESTING.md) desde o passo 0 — em especial a **migração
   v3 → v4 com dados reais, com backup antes**.
2. Criar a keystore de release e os 4 secrets do GitHub ([`DEVICE_SETUP.md`](DEVICE_SETUP.md)).
3. Abrir o APK release (R8) pela primeira vez.
4. Conferir com fonte ampliada e com TalkBack.
5. Revisar e mesclar o PR #1.

**Na migração v5** (que por sua vez espera o item 1 acima):

- **PRG-05 (peso corporal)** e o **histórico** de recordes da §10.
- **Técnicas de escopo exercício** (pirâmides, PIR/PIV): `template_exercise` não tem coluna de
  técnica, então as duas técnicas seguem no catálogo sem uso.

## Decisões deste chat que ainda não estão em ADR

Nenhuma. As decisões de 27–28/11 estão no `PRODUCT_SPEC` (§6.3.1) e no `ROADMAP`; nenhuma delas
contradiz ADR existente, então não houve ADR novo.

## Perguntas abertas para o dono

1. **§10, empate entre bases de carga.** A §10 compara "a carga exata em gramas", então **12 kg por
   halter empata com uma barra de 12 kg**, apesar de ser o dobro do trabalho. Está implementado como
   a §10 está escrita. Misturar bases no mesmo exercício é raro (normalmente são dois exercícios
   diferentes do catálogo). Se for para mudar a regra, **muda na §10 primeiro** — é decisão de
   produto, não de código.

## Armadilhas medidas que voltam a morder

As antigas estão em `AGENTS.md` §8 — **leia a lista inteira**, elas voltam. As duas mais recentes:

- **`ActionMenuItemView` esconde o texto quando o item tem ícone**, em largura de telefone
  (`config_allowActionMenuItemTextWithIcon` é `false`). O item da toolbar que mostra a contagem de
  arquivados ficou **sem ícone** por isso. Medido imprimindo a hierarquia real da toolbar.
- **Teste de ausência pelo rótulo engana.** Checar que "1 arquivado" não existe passava sem provar
  nada, porque com a contagem em zero o rótulo seria "0 arquivado". Ausência de item de menu se
  checa **pelo id**.
