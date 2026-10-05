# Especificação de Produto

> **Nome provisório:** FlowGym. O nome aparece apenas em `strings.xml` (`app_name`), no `applicationId`
> (um único ponto em `android-app/app/build.gradle.kts`) e nesta documentação. O pacote Java é
> neutro (`io.github.thiagojosetj.gym`) para que a marca possa mudar sem refatorar o código.

## 1. Visão

Aplicativo de acompanhamento de musculação **offline-first**, com foco em registrar treinos com
poucos toques, comparar o desempenho com sessões anteriores e visualizar evolução de forma honesta
(sem métricas infladas). Primeira plataforma: Android (Java). Depois: backend Spring Boot com
contas e sincronização; e, mais tarde, iOS (Swift/SwiftUI) consumindo a mesma API.

O HEVY é referência **conceitual** apenas. Não copiamos código, textos, imagens, GIFs, banco de
exercícios, identidade visual ou layout.

## 2. Princípios de produto

1. **Offline-first.** Toda função essencial funciona sem internet. O banco local é a fonte de verdade
   durante o uso; a rede só sincroniza.
2. **Nada se perde.** Séries são persistidas no momento em que são registradas. Treino em andamento
   sobrevive a fechamento do app, morte do processo e reinício do aparelho.
3. **Histórico é imutável por padrão.** Uma sessão realizada guarda snapshots suficientes para
   continuar correta mesmo se o template ou o exercício mudarem depois.
4. **Planejado ≠ realizado ≠ sugerido.** Os três valores coexistem e nunca se sobrescrevem.
5. **Métricas honestas.** Quando um número não pode ser calculado com correção (ex.: volume de
   exercício com peso corporal sem peso corporal conhecido), ele não é inventado — é omitido e a
   omissão é informada.
6. **Poucos toques durante o treino.** Registrar uma série deve exigir, no caso comum, um toque.
7. **Privacidade.** Peso corporal, bioimpedância, histórico e desempenho são privados e nunca vão
   para logs, analytics, crash reports ou links de compartilhamento.
8. **Dados configuráveis, não codificados.** Grupos musculares, equipamentos, técnicas avançadas e
   conquistas são dados, não constantes espalhadas na UI.

## 3. Glossário

| Termo | Significado |
|---|---|
| **Exercício** | Movimento da biblioteca (ex.: "Supino reto com barra"). Do sistema ou criado pelo usuário. |
| **Grupo / subgrupo muscular** | Hierarquia anatômica (ex.: Costas → Latíssimo do dorso). |
| **Treino (template)** | Plano reutilizável: lista ordenada de exercícios com séries planejadas. |
| **Sessão** | Execução real de um treino em uma data. Histórico. |
| **Série planejada** | Meta do template (ex.: 8–10 reps, 40 kg). |
| **Série realizada** | O que o usuário efetivamente fez e confirmou. |
| **Valor sugerido** | Pré-preenchimento (em geral, da sessão anterior). Só vira "realizado" quando confirmado. |
| **Técnica** | Método de treino (drop-set, rest-pause, supersérie...). Dado configurável. |
| **Grupo de exercícios** | Exercícios ligados (supersérie, bi-set, tri-set, giant set). |
| **Rotina** | Cronograma semanal fixo ou ciclo de N dias com treinos e descansos. |
| **Slot** | Uma posição da rotina (um dia: treino X ou descanso). |
| **Volume (de carga)** | Σ carga efetiva × repetições das séries elegíveis (ver §9). |
| **PR** | Recorde pessoal detectado automaticamente (ver §10). |

## 4. Funcionalidades e fases

Os IDs abaixo são usados no ROADMAP, nos commits e nos testes. Estado atualizado em
[ROADMAP.md](ROADMAP.md).

### 4.1 Biblioteca de exercícios (Fase 1)
- **LIB-01** Listar exercícios ativos do sistema e do usuário.
- **LIB-02** Busca rápida por nome e apelidos, tolerante a maiúsculas/minúsculas, acentos e trechos
  do nome ("supino", "SUPINO", "súpino" e "pino" encontram "Supino reto com barra").
- **LIB-03** Filtros combináveis: grupo, subgrupo, equipamento e papel do músculo
  (principal / secundário / ambos). O **subgrupo é escolhido por imagem**: ao selecionar um grupo,
  aparece uma linha com uma silhueta por parte, com aquela parte acesa, e o nome embaixo. A
  primeira opção é o **grupo inteiro**, que acende tudo o que suas partes acendem. O motivo é o
  próprio problema: "vasto medial" e "semitendinoso" são nomes que quem está começando não conhece,
  e uma lista só de nomes torna o filtro inútil justamente para quem mais precisaria dele.
- **LIB-04** Detalhe: descrição, instruções, dicas, erros comuns, observações, equipamentos, músculo
  principal em destaque e secundários em seção menor.
- **LIB-05** Mídia: ≥ 2 imagens (posição inicial/final) quando disponível; animação curta opcional
  (ver ARCHITECTURE §9).
- **LIB-06** (futuro) Exercícios personalizados do usuário (o modelo já suporta: `owner_user_id`).

### 4.2 Treinos / templates (Fase 2)
- **TPL-01** Criar, editar, duplicar, arquivar e excluir treinos (exclusão lógica; histórico preservado).
- **TPL-02** Adicionar exercícios pela biblioteca (seleção múltipla). Padrão ao adicionar: **3 séries × 12 repetições**, descanso padrão do app (90 s).
- **TPL-03** Por exercício: séries planejadas (reps fixas ou faixa 8–10, carga opcional), descanso,
  observações permanentes ("Banco no terceiro encaixe").
- **TPL-04** Reordenar exercícios (arrastar e soltar).
- **TPL-05** Técnicas por série (aquecimento, drop-set...) e grupos (supersérie...).
- **TPL-06** Observações do treino (permanentes, diferentes das observações de uma sessão).

### 4.3 Treino em andamento (Fase 3)
- **ACT-01** Uma tela com **todos** os exercícios em cards expansíveis; a ordem planejada não é obrigatória.
- **ACT-02** Por série: planejado, anterior (discreto/translúcido) e atual (campos editáveis).
- **ACT-03** Pré-preenchimento com a última sessão (configurável), distinguindo sugerido de confirmado.
- **ACT-04** Adicionar/remover séries, marcar concluída, aquecimento, técnica, observação da série e do exercício na sessão.
- **ACT-05** Cronômetro geral baseado em timestamps; pausa/retomada com intervalos registrados.
- **ACT-06** Descanso automático ao concluir série; +15 s / +30 s / −15 s; alerta configurável (som, vibração, notificação).
- **ACT-07** Notificação persistente de treino em andamento com cronômetro e ações Pausar/Abrir.
- **ACT-08** Recuperação: "Você possui um treino em andamento. [CONTINUAR]".
- **ACT-09** Finalizar: validar séries parcialmente preenchidas (nunca descartar silenciosamente) e mostrar resumo.

**Implementado (27/09/2026, registro por lado em 28/09):** ACT-01, ACT-02, ACT-04 (exceto
segmentos de drop-set), ACT-05,
ACT-06, ACT-07, ACT-08 e ACT-09. O pré-preenchimento (ACT-03) existe e vem da sessão anterior, mas
ainda **não é configurável**; uma faixa planejada (8–10) de propósito **não** pré-preenche as
repetições — escolher 8 pelo usuário seria inventar um resultado. Falta validar em aparelho a
notificação, o serviço em primeiro plano e o alerta com a tela apagada.

### 4.4 Histórico e resumo (Fase 4)
- **HIS-01** Resumo: nome, data, horário, duração total e efetiva, pausas, exercícios, séries, repetições, volume, recordes, comparação, medalhas, avaliação subjetiva opcional (1–5).
- **HIS-02** Calendário com dias treinados; tocar numa data abre as sessões do dia.
- **HIS-03** Sessão antiga em detalhe, a partir dos snapshots (nunca do template atual).
- **HIS-04** Tabela de comparação Exercício | Anterior | Atual | Variação (↑ ↓ =), expansível série a série.

- **HIS-06** **Avaliação 1–5** no resumo que aparece ao finalizar: "Como foi o treino?". É
  **opcional**, e tocar no número escolhido de novo tira a avaliação — ausência de nota não é
  nota zero. Grava **a cada toque**, não ao fechar: o diálogo também fecha no botão voltar, e
  uma nota esperando na memória sumiria sem avisar (ADR-0031). Nota fora de 1–5 é recusada em
  vez de gravada. **Não feito:** observação em texto da sessão (a coluna `notes` existe), e
  avaliar depois pela tela de detalhe.
- **HIS-05** **Excluir uma sessão do histórico**, pelo menu da linha, com confirmação que nomeia
  a sessão. Ela sai da lista e deixa de contar nos números; o treino que a gerou continua em
  Treinos. Exclusão é **soft** (`deleted_at`), para poder ser sincronizada. Não fere a
  imutabilidade: a sessão sai inteira, nada que ela registrou é reescrito (ADR-0041).
  **Lacuna conhecida:** uma sessão posterior ainda compara com a sessão excluída, porque o
  pareamento é gravado no início e não refiltra.
- **HIS-02** **Calendário do mês** acima da lista: cada dia com pelo menos uma sessão concluída
  aparece marcado, e tocar nele deixa na lista só as sessões daquele dia. Tocar no mesmo dia de
  novo, ou em "Ver todas", mostra o histórico inteiro — o gesto que filtra é o que desfiltra, e o
  filtro não tem como ficar preso. Tocar num dia **sem** treino responde "Nenhuma sessão neste
  dia" em vez de deixar a tela vazia, que se lê como defeito.
  - O dia é o `local_date` gravado pela sessão, **nunca** derivado de `started_at`: um treino às
    23:30 em São Paulo é daquele dia mesmo depois que o telefone muda de fuso (§11).
  - As setas param onde o treino para — do mês da sessão mais antiga ao da mais recente, incluindo
    sempre o mês atual. O limite é a **última sessão**, não hoje: um aparelho com o relógio
    adiantado grava um dia no futuro, e parar em hoje esconderia essa sessão em vez de mostrá-la.
  - Mudar de mês limpa o filtro de dia: as linhas não podem continuar mostrando um dia que a grade
    acima não tem mais.
  - A grade e a lista saem da **mesma** emissão (a mesma LiveData, mapeada), não de duas consultas.
    É estrutural, não testável aqui: os testes rodam com executores síncronos, onde duas consultas
    também pareceriam consistentes.

**Implementado (28/09/2026):** HIS-01 (menos recordes e medalhas, que dependem das Fases 5 e 7),
HIS-03 e a comparação de sessão do HIS-04. **HIS-02 (calendário) foi feito em 05/10/2026.**

A comparação do resumo é com a **sessão anterior do mesmo template**; a comparação série a série
continua sendo a do ponteiro congelado no início da sessão (a última sessão concluída que tem aquele
exercício, de qualquer treino — ADR-0033). São perguntas diferentes e ficam separadas de propósito.

O percentual só aparece quando o valor anterior é **maior que zero**: `MetricChange.hasPercent()`
responde isso e `MetricChange.percent()` **lança exceção** se ninguém perguntou. Uma tela que
esquecer quebra um teste, em vez de imprimir um número que o usuário acreditaria.

A lista **não mostra volume de carga**, e isso é decisão de produto, não esquecimento: calcular
volume com honestidade exige aplicar as regras do §9 série a série, ou seja, ler o histórico inteiro
para desenhar uma tela. O volume fica na tela da sessão, calculado pelo mesmo código que o calculou
quando o treino foi finalizado.

### 4.5 Progresso (Fase 5)
- **PRG-01** Gráficos de carga, volume e repetições por exercício e do treino.
- **PRG-02** Filtros: últimas 5, 10, 30 sessões, período personalizado, todo o histórico.
- **PRG-03** Tela de progresso por exercício (histórico, maior carga, volume, PRs, gráfico).
- **PRG-04** Estatísticas semanais/mensais e por grupamento muscular.
- **PRG-05** Registro de peso corporal e gráfico (antecipado da Fase 11 — ver ROADMAP).

### 4.6 Rotinas, metas e notificações (Fase 6)
- **ROU-01** Rotina semanal fixa (segunda → Push...).
- **ROU-02** Ciclo de N dias independente do dia da semana (ex.: A, B, C, descanso, D, E, descanso).
- **ROU-03** Próximo treino na tela inicial, com ações: iniciar, adiantar, pular, remarcar, treino diferente — sempre com confirmação quando o cronograma muda.
- **ROU-04** Notificação "Treino de hoje: Pull" (liga/desliga, horário, antecedência).
- **ROU-05** Metas (ex.: 4 treinos por semana) e sequências (aderência ≠ consistência — §12).

### 4.7 Conquistas (Fase 7), Backend (Fase 8), Sync (Fase 9), Compartilhamento (Fase 10), Dados corporais (Fase 11)
Descritos em §13–§16 e nos documentos [SYNC.md](SYNC.md) e [ARCHITECTURE.md](ARCHITECTURE.md).

## 5. Biblioteca: modelo de músculos

- Hierarquia flexível: um nó muscular pode ter pai (`parent_id`). Hoje usamos **dois níveis**
  (grupo → subgrupo); o esquema permite mais níveis no futuro.
- Grupos iniciais: peito, costas, ombros, bíceps, tríceps, antebraço, quadríceps, posteriores de
  coxa, glúteos, adutores, panturrilhas, abdômen, lombar.
- "Costas" é subdividida (latíssimo do dorso, trapézio superior, trapézio médio/inferior, romboides,
  redondo maior). Eretores da espinha ficam no grupo **Lombar** para não duplicar o conceito.
- Um exercício tem 1+ músculos **principais** (o primeiro é o destaque) e 0+ **secundários**. Cada
  vínculo aponta para um grupo ou subgrupo.
- Filtro por grupo inclui seus subgrupos. O filtro de papel decide se considera principal,
  secundário ou ambos.

## 6. Treinos, séries e técnicas

### 6.1 Séries
Cada série (planejada ou realizada) pode ter: número/posição, tipo (normal, aquecimento, técnica),
carga, repetições (ou faixa), duração, descanso, status, horário de conclusão e observação.

### 6.2 Técnicas avançadas (dados, não enum)
Cada técnica tem: **código curto** (badge), nome, descrição, instrução, **escopo** e parâmetros
opcionais.

| Escopo | Técnicas iniciais (código) |
|---|---|
| Série | Aquecimento (`AQ`), Drop-set (`D`), Rest-pause (`RP`), Myo-reps (`MR`), Cluster (`CL`), Até a falha (`F`) |
| Exercício (sequência de séries) | Pirâmide crescente (`PIR`), Pirâmide invertida (`PIV`) |
| Grupo de exercícios | Supersérie (`SS`), Bi-set (`BI`), Tri-set (`TRI`), Giant set (`GS`) |

Regras dos códigos: únicos, maiúsculos, 1–3 caracteres, **sem colisão com o badge de recorde
(`PR`)** e sem ambiguidade com rótulos de grupo (A1, A2 — letra + número). Ao lado do badge, um
ícone ⓘ abre a explicação da técnica (o que é + como registrar). Aquecimento não conta para volume
nem PRs (`counts_as_working_set = 0`).

**Implementado:** as 12 técnicas existem como dados (`training_technique`), e as de escopo **série**
podem ser escolhidas por série no editor de treino, com badge e ⓘ. As de escopo exercício (pirâmides)
e grupo (supersérie e companhia) já estão no catálogo. Os **grupos** entraram em 01/10/2026 (§6.3),
mas ainda são criados como agrupamento simples: escolher SS/BI/TRI/GS para um grupo não tem
interface, então essas quatro técnicas continuam sem uso.

Técnicas com várias etapas (drop-set, rest-pause, myo-reps, cluster) registram cada etapa como um
**segmento** da mesma série (ex.: 40 kg × 10 → 30 kg × 8 → 20 kg × 6).

### 6.3 Grupos (supersérie etc.)
Exercícios de um template podem pertencer a um grupo com rótulo (A, B...) e técnica de grupo.
Exibição: A1 Supino, A2 Crucifixo. O descanso automático ocorre após o **último** exercício da
rodada do grupo.

**Implementado no banco e nos dados (01/10/2026); falta a interface.** Banco na **versão 4**
(`template_exercise_group`, `session_exercise_group`, colunas `group_id`).

Regras decididas ao implementar:

- **O descanso é da RODADA.** Começa quando **nenhum exercício do grupo ainda deve** a série daquela
  rodada — não quando o "último por posição" termina. A leitura literal quebraria a ACT-01, que diz
  que a ordem planejada não é obrigatória: quem faz A2 antes de A1 nunca descansaria. Série **pulada**
  também encerra (não se deve mais nada); só `PENDING` segura a rodada.
- **Grupo desigual não trava.** Um exercício com menos séries que o índice da rodada não deve nada,
  então 3×A1 com 2×A2 descansa em todas as rodadas.
- **Etapa não é rodada.** Os segmentos de um drop-set ficam dentro da série (ADR-0037), então um
  drop-set dentro de uma supersérie é **uma** rodada e as etapas não deslocam o índice.
- **Rótulos são derivados, não digitados.** A, B, C seguem a ordem do primeiro exercício de cada
  grupo no treino. Criar um grupo antes de outro renumera; apagar o A faz o B virar A. Dois grupos
  nunca compartilham rótulo. A **sessão guarda o rótulo que valia no dia**.
- **Grupo tem no mínimo 2 exercícios.** Um grupo que cai para 1 (por exemplo, o editor removeu o
  outro exercício) é **desfeito**, e o exercício que sobra fica sem grupo. Técnica × tamanho
  (SS/BI = 2, TRI = 3) **não** é cobrado.
- **Salvar e duplicar um treino preservam os grupos.** Os dois caminhos reescrevem os exercícios a
  partir de um rascunho que não conhece grupos; sem tratar isso, qualquer edição desagrupava tudo e
  duplicar perdia as superséries em silêncio.
- **Uma confirmação que não inicia descanso encerra o que estiver correndo.** O descanso é da sessão,
  não do exercício: sem isso o alerta da rodada N tocaria no meio da rodada N+1.

**Rodada é rodada de séries válidas.** Aquecimento fica fora: contá-lo parearia o aquecimento de
um exercício com a primeira série de verdade do outro, encerraria a rodada ali e dispararia o
descanso do grupo depois de um aquecimento — e todas as rodadas seguintes ficariam deslocadas
pelo número de aquecimentos daquele exercício. Confirmar um aquecimento num grupo não inicia
descanso de rodada (corrigido em 03/10/2026).

### 6.4 Carga: halteres, unilateral e peso corporal
- **Halteres (dois implementos):** registra-se o peso **de cada halter** ("12 kg por halter"). A UI
  nunca exibe 24 kg como se fosse a carga da série.
- **Unilateral:** registro conjunto ("10 reps por lado") ou por lado (E 10 / D 9). A opção só
  aparece em exercícios unilaterais. Repetições são sempre contadas **por lado**.

  **Implementado (28/09/2026).** Quando o treino marca o exercício como "por lado", a série mostra
  dois campos (E e D) no lugar do campo único. A regra de confirmação é sobre **o que foi digitado**,
  antes de qualquer sugestão entrar:

  | O que o usuário digitou | O que acontece ao tocar ✓ |
  |---|---|
  | Os dois lados | Grava os dois. |
  | **Só um lado** | **Recusa**, com aviso. Por lado existe justamente para lados **diferentes**: preencher D com o plano quando o usuário digitou E grava um número que ele não fez, e o `FinishReview` chamaria essa mesma série de "parcial" (§8). |
  | Nenhum lado | A sugestão preenche os dois — é o mesmo "fiz o que estava planejado" que o campo único já significa quando é confirmado vazio. |

  O campo combinado e os campos por lado nunca aparecem juntos, e uma série gravada por lado não
  guarda `reps` combinado: o total viria do ramo por lado e o número combinado deixaria de ser
  contado, em silêncio.

  > **Correção de 28/09/2026.** A primeira versão checava "os dois lados" **depois** de adotar a
  > sugestão, então a checagem nunca disparava quando havia o que adotar — a mesma tela (E digitado,
  > D vazio) concluía ou recusava dependendo de o rascunho já ter ido para o banco. O commit que
  > entregou o recurso descrevia o comportamento correto, não o que o código fazia. Um teste escrito
  > contra esta especificação pegou a diferença.

  **Corrige um erro que já estava no app:** dava para marcar "por lado" no treino e registrar no
  campo combinado. O valor ia para `reps`, e como `sideMode` era `PER_SIDE` o domínio **não**
  dobrava — um exercício unilateral contava metade das repetições e metade do volume.
- **Peso corporal:** exercício com rastreamento "peso corporal" aceita carga **adicional** (+10 kg)
  ou **assistência** (−25 kg) como valor com sinal. Sem valor = peso corporal puro. Nenhum número é
  inventado para o peso corporal.

### 6.5 Planejado × realizado × sugerido
- O planejado da sessão é um **snapshot** do template no início da sessão. Alterar o template
  depois não altera sessões passadas.
- A sugestão (em geral, a série equivalente da última sessão daquele exercício) aparece como valor
  pré-preenchido em estilo diferente. Só vira realizado quando o usuário confirma a série.

## 7. Treino em andamento: tempo

- **Cronômetro geral:** `decorrido = agora − início − Σ pausas`. Nunca é um contador incrementado;
  é sempre calculado a partir de timestamps persistidos.
- **Pausa:** cada pausa é um intervalo `[início, fim]` persistido. Durante a pausa, o tempo efetivo
  e o descanso param.
- **Descanso:** ao concluir uma série, grava-se `descanso_termina_em`. Restante = `termina_em − agora`.
  +15 s / +30 s / −15 s ajustam o timestamp. Ao chegar a zero: notificação, som e vibração
  opcionais (configuráveis).
- **Durações no resumo:** total = fim − início; efetiva = total − pausas.

## 8. Finalização

Ao finalizar, séries com dados parciais (ex.: peso preenchido e repetições vazias; série não marcada
como concluída mas com valores) são listadas. O usuário escolhe: concluir as que têm dados, descartar
as vazias ou voltar. Nada é descartado sem confirmação.

## 9. Volume — definição

Volume de carga de uma série elegível = **carga efetiva × repetições totais**.

- **Série elegível:** concluída, não é aquecimento, carga conhecida e reps > 0.
- **Carga efetiva:**
  - Barra, máquina, polia, smith (base *total*): a carga registrada.
  - Halteres/kettlebells (base *por implemento*): carga registrada × nº de implementos usados
    simultaneamente (2 halteres × 12 kg = 24 kg no **cálculo**; a exibição continua "12 kg por halter").
  - Máquinas: o número mostrado na máquina é usado como está. O volume só é comparável dentro do
    mesmo exercício/máquina — por isso gráficos de progresso são **por exercício**.
- **Repetições totais:** bilateral = reps; unilateral = reps × 2 (conjunto) ou E + D (por lado).
- **Peso corporal:** **excluído do volume de carga**, porque a fração do peso corporal deslocada
  varia por exercício (flexão ≠ barra fixa) e não queremos números falsos. Esses exercícios
  contribuem para séries e repetições. Futuro: fator por exercício configurável, sempre rotulado
  como "estimado".
- **Duração (prancha):** sem volume de carga; contribui com tempo sob tensão.
- **Carga desconhecida:** não entra no volume.
- **Transparência:** totais exibem "N séries não incluídas no volume" quando houver exclusões.
- Cálculo interno em gramas; exibição em kg ou lb.

### 9.1 Segmentos (drop-set, rest-pause) — decidido em 28/09/2026, antes do código

Um drop-set é `40 kg × 10 → 30 kg × 8 → 20 kg × 6`. A pergunta é o que cada etapa faz com os
números. Decisão:

| Métrica | Regra | Por quê |
|---|---|---|
| **Volume** | **Soma de todos os segmentos** (40×10 + 30×8 + 20×6 = 760 kg) | As repetições a 30 kg e a 20 kg **aconteceram**. Contar só a carga do topo descartaria trabalho real, e §9 já proíbe descartar em silêncio. Não é decisão nova: é a regra do §9 aplicada a cada etapa. |
| **Séries** | **Uma.** O drop-set inteiro conta como **1 série** | Um drop-set é uma série levada além da falha em quedas, não três séries. Contar três infla "séries feitas" e estragaria as séries por grupo muscular por semana (Fase 5), que é métrica de treino de verdade. O banco já diz isso: `parent_set_id` faz do segmento um **filho** da série, não um irmão. |
| **Repetições** | **Soma de todos os segmentos** (24) | Foram executadas. |
| **"N séries não incluídas no volume"** | Conta **séries-pai**. Uma série só entra na linha quando o conjunto inteiro (pai + segmentos) deu volume zero | Senão um drop-set com um segmento sem carga apareceria **dentro e fora** do volume ao mesmo tempo. |
| **Recordes (Fase 5)** | O segmento de **maior carga** é o que vale para "maior carga" | Anotado agora para o dia em que os PRs entrarem; não implementado aqui. |

A primeira etapa **é** a própria série (a linha com `parent_set_id IS NULL`); as etapas seguintes são
filhas dela. Por isso toda consulta que conta séries continua filtrando `parent_set_id IS NULL` e
continua certa sem mudança — e por isso isto **não precisa de migration**: a coluna existe desde a v3.

**Desfazer uma série desfaz as etapas dela.** Etapa é parte da série: deixá-la marcada como feita
sob uma série desfeita faria o banco dizer que as repetições aconteceram enquanto nenhum total as
conta — e o descanso, que pertence à última etapa, continuaria correndo por um trabalho que foi
desfeito. Os valores digitados permanecem, porque desfazer não é apagar (ADR-0031).

**O descanso vem depois da última etapa, e é o da série.** Um drop-set é uma série, então o
descanso automático é o que a série planejou (ou o da rodada, se ela estiver num grupo) e começa
quando a última etapa é confirmada. Ler o descanso da própria etapa daria zero — a etapa nasce
sem plano — e a série mais cansativa do treino terminaria **sem descanso nenhum**, ainda por cima
cancelando o que a série tinha acabado de iniciar (corrigido em 03/10/2026).

**Ao finalizar, a etapa é resolvida junto com a série** (§8 vale para ela também): etapa com
tudo o que precisa vira **feita**, etapa vazia ou pela metade vira **pulada**, e o diálogo diz
quantas etapas serão resolvidas — em linha separada, porque o drop-set continua contando como
**uma** série. Deixar a etapa pendente dentro de uma sessão encerrada era descarte silencioso:
as repetições aconteceram, não entravam em nada e ninguém era avisado (corrigido em 03/10/2026).

## 10. Recordes pessoais (PRs)

Por exercício, considerando apenas séries elegíveis (§9, exceto que peso corporal conta para PRs de
repetições e carga adicional):

| Tipo | Regra |
|---|---|
| Maior carga | Maior carga registrada (por implemento em halteres; carga adicional em peso corporal) com ≥ 1 rep. |
| Mais reps com a carga X | Maior nº de reps para a mesma carga exata (comparada em gramas). |
| Maior volume de série | Maior carga efetiva × reps em uma série. |
| Maior volume de sessão | Maior soma do exercício em uma sessão. |
| 1RM estimado (futuro) | Fórmula documentada (ex.: Epley), só para ≤ 10 reps, sempre rotulada "estimado". |

- **Empate não é PR.** A primeira ocorrência de um exercício cria uma **linha de base** (sem
  celebração).
- PRs são **derivados** do histórico e podem ser recalculados; o histórico de PRs é preservado
  (cada novo PR é um novo registro).

## 11. Comparação com o anterior

- **Durante o treino:** "Anterior" = a última sessão concluída que contém o mesmo exercício
  (qualquer treino), pareando séries de trabalho pela posição.
- **No resumo:** comparação com a sessão anterior do mesmo template (se houver) e por exercício.
- Indicadores ↑ ↓ = por métrica; percentual só quando o valor anterior é > 0 e as unidades são
  comparáveis. O app **apresenta dados** — não declara que a sessão "foi melhor" por causa de um
  único número.

## 12. Rotinas, aderência e consistência

### 12.1 Tipos
- **Semanal fixa:** slot por dia da semana. Não "desliza".
- **Ciclo de N dias:** sequência de slots (treino ou descanso) a partir de uma **data âncora**,
  repetida indefinidamente, independente do dia da semana. Ex.: `A, B, C, desc, D, E, desc`.

### 12.2 Ajustes (sempre com confirmação e prévia do novo calendário)
Todos são **eventos** registrados (append-only), nunca edição destrutiva do cronograma:

| Ação | Efeito |
|---|---|
| Iniciar o planejado | Cumpre o slot do dia. |
| Adiantar | Faz o próximo treino hoje; o cronograma a partir de hoje é deslocado −k dias. |
| Remarcar / adiar | Desloca o cronograma +k dias a partir de hoje. |
| Pular | Marca o slot de hoje como pulado; o cronograma não se move. |
| Treino diferente | Executa outro treino; o usuário escolhe se **substitui** o planejado (slot cumprido) ou é **extra** (slot continua pendente). |

### 12.3 Métricas
- **Consistência:** fatos brutos (dias treinados seguidos, treinos por semana, meta semanal).
- **Aderência:** dias em que o **plano (já ajustado)** foi cumprido. Descanso planejado respeitado
  **conta como cumprido**. Na proposta padrão: pular quebra a sequência de aderência; remarcar não
  quebra (é um ajuste consciente), mas fica registrado nas estatísticas.

## 13. Conquistas (Fase 7)

Definições são **dados**: nome, descrição, ícone, categoria (força, consistência, aderência, volume,
experiência, PRs), regra (tipo + parâmetros), níveis (Bronze, Prata, Ouro, Platina) com limiares
configuráveis — inclusive por exercício ("Supino — 80 kg", progresso 76/80 kg). Desbloqueio grava
data e sessão de origem. A animação de desbloqueio é agradável e não intrusiva (não bloqueia o
registro de séries).

## 14. Compartilhamento (Fase 10)

- Compartilha **somente o template**: estrutura, exercícios, séries, reps, descansos, técnicas.
- **Nunca** inclui histórico, peso corporal, bioimpedância ou dados de desempenho.
- Cargas planejadas e observações do template: **não incluídas por padrão** (opção explícita).
- Importar cria **cópia independente** (origem = importado). Alterações do criador não afetam cópias.
- Link/código/QR/deep link com token aleatório, revogável e com expiração opcional.

## 15. Dados corporais (peso: Fase 5; bioimpedância: Fase 11)

Medição com data/hora e métricas opcionais (peso, % gordura, massa gorda, massa muscular, massa
magra, água, gordura visceral e outras). Valores ausentes são aceitos. Métricas são definidas como
dados, pois cada equipamento de bioimpedância fornece um conjunto diferente.

## 16. Configurações

Tema (claro/escuro/sistema), unidade (kg padrão; lb suportado), som, vibração, descanso padrão,
tamanho da interface (compacto/padrão/ampliado), notificações, pré-preenchimento pela sessão
anterior, comportamento ao concluir série, conta (futuro).

**Implementado:** tema (aparelho) e, na conta, **descanso padrão (90 s de fábrica, editável)**,
**som** e **vibração** do fim do descanso — ligados de fábrica, conforme decidido em 23/09/2026. As
preferências da conta ficam em `user_setting` e vão sincronizar; o tema fica só no aparelho.

## 17. Acessibilidade (desde o início)

Contraste AA, `contentDescription` em ícones, alvos de toque ≥ 48 dp, fontes escaláveis (sp),
navegação por teclado/TalkBack, e estados nunca indicados apenas por cor (ex.: série concluída tem
ícone ✓ e texto, não só cor verde).

## 18. Decisões em aberto (para o dono do produto)

| # | Pergunta | Proposta atual |
|---|---|---|
| Q1 | Nome definitivo e `applicationId` de publicação. | "FlowGym" provisório; definir antes da 1ª publicação. |
| Q2 | Pular um slot deve quebrar a sequência de aderência? | Sim (remarcar não quebra). |
| Q3 | Treinar num dia de descanso planejado afeta a aderência? | Não; conta como treino extra. |
| Q4 | Cargas planejadas no compartilhamento por padrão? | Não incluídas por padrão. |
| Q5 | Conquistas desbloqueadas são revogadas se o usuário apagar sessões? | Não são revogadas. |
| Q6 | Fórmula do 1RM estimado. | Epley, reps ≤ 10, rotulada "estimado". |
