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
- ✅ Projeto Android em Java (AGP 9.4.1, Gradle 9.7.1, compile/target 37, min 28), módulos `:app` e `:domain`.
- ✅ Banco inicial (Room v1) com schema exportado e verificado por teste.
- ✅ Navegação (single-activity + bottom navigation) e design system (Material 3, paleta própria, claro/escuro).
- ✅ Seletor de tema nas configurações.
- ✅ Build de release com R8 (minify + shrink) validado.
- ✅ CI (GitHub Actions): `push` e `pull_request` rodam `:domain:test`, `:app:testDebugUnitTest`,
  `:app:lintDebug` e `assembleRelease`, com cache do Gradle e a plataforma `android-37.0` instalada
  explicitamente. O job **reprova em qualquer achado de lint**, não só em erro (ADR-0035). Badge no
  README. Ainda não observado rodando no GitHub — ver pendências.
- ✅ Testes instrumentados (`androidTest`): smoke test do fluxo principal e migration 1 → 2 com o
  `MigrationTestHelper` — escritos, aguardando a primeira execução em aparelho.

### Fase 1 — Biblioteca 🟡
- ✅ Grupos/subgrupos hierárquicos (13 grupos, 38 subgrupos), 14 equipamentos, 55 exercícios com textos próprios (JSON com UUIDs fixos).
- ✅ Lista, busca tolerante a acentos e apelidos, filtros combinados (grupo + subgrupo + equipamento + papel).
- ✅ Detalhe do exercício (principal em destaque, secundários, instruções, dicas, erros comuns, como registrar).
- 🟡 Mídia: o **filtro de subgrupo é por imagem** — silhueta com a parte acesa, 13 grupos +
  38 subgrupos, desenhados neste projeto e gerados de `tools/musclemap.py` (ADR-0040).
  Falta: ilustração do movimento por exercício (prompts prontos em `docs/EXERCISE_ART.md`,
  imagens ainda não produzidas), imagens de início/fim e animação.
- ⬜ **Validar no aparelho**: a linha de subgrupos com fonte ampliada, e se as regiões
  pequenas (glúteo mínimo, transverso, manguito rotador) se distinguem a 40 dp.
- ⬜ Ampliar o catálogo.
- ⬜ Exercícios personalizados (o modelo já suporta).

### Fase 2 — Templates 🟡
- ✅ Criar, editar, salvar e reabrir; defaults 3 × 12; faixa de reps, carga (por halter / peso corporal ±), descanso, observações.
- ✅ Reordenar (arrastar e soltar + mover pelo menu, acessível), remover exercício, duplicar e excluir (lógico) treino.
- ✅ Edição série a série (cada série com suas reps/carga; "copiar a 1ª para todas" como atalho).
- ✅ Técnicas avançadas como dados (12 métodos), escolha por série com badge e ⓘ explicando o método.
- ✅ Configurações da conta: descanso padrão (90 s de fábrica, editável), som e vibração do descanso.
- ✅ Banco na versão 2 com migration explícita e testada.
- 🟡 Grupos de exercícios (supersérie, bi-set, tri-set, giant set): **banco v4, dados e editor
  prontos**. Agrupar e desagrupar pelo menu do exercício, rótulo A1/A2 derivado (nunca digitado),
  descanso por rodada. Falta escolher a **técnica** do grupo (SS/BI/TRI/GS seguem sem uso) e editar
  descanso/técnica depois de criado (`updateGroup` existe, sem interface). Grupo é gravado na hora,
  fora do rascunho do editor — ADR-0038.
- ⬜ Técnicas de escopo exercício (pirâmides).
- ⬜ Arquivar/desarquivar.

### Fase 3 — Treino ativo 🟡
- ✅ Banco na versão 3: `workout_session`, `session_pause`, `session_exercise`, `set_log`, com
  migration que só cria tabelas e teste que vai de v1 a v3.
- ✅ Iniciar do treino salvo, copiando **snapshot** do plano (editar o template depois não muda o
  histórico) e congelando o ponteiro da sessão anterior.
- ✅ Tela única com todos os exercícios, cards recolhíveis, uma linha por série com planejado /
  anterior / atual, adicionar e remover série, técnica por série com ⓘ.
- ✅ Registro imediato: cada gesto é uma transação; digitação não confirmada é salva ao sair do campo
  e ao fechar a tela (ADR-0031).
- ✅ Cronômetro geral por timestamps, pausa/retomada, descanso automático com −15/+15/+30 e pular.
- ✅ Notificação com cronômetro nativo e ações Pausar/Abrir, em foreground service `health`.
- ✅ Recuperação: a faixa "você tem um treino em andamento" na Início lê só o banco.
- ✅ Finalização com validação (PRODUCT_SPEC §8): mostra o que vai acontecer com cada série não
  confirmada, nada é descartado em silêncio, e o resumo diz quantas séries ficaram fora do volume.
- ✅ Registro por lado (E 10 / D 9): dois campos no lugar do campo único quando o treino marca o
  exercício como "por lado", com os dois lados obrigatórios para concluir a série. Fechou de quebra
  um erro real: dava para marcar "por lado" e registrar no campo combinado, e aí o domínio não
  dobrava as repetições — metade das reps e metade do volume.
- 🟡 Segmentos de drop-set / rest-pause: o botão **+** no cabeçalho da série abre uma linha
  recuada "Etapa N" com campos próprios, gravada como filha em `parent_set_id`. O volume soma
  todas as etapas e o conjunto conta como **uma** série (§9.1, ADR-0037). Falta validar em
  aparelho: os campos da etapa com teclado real.
- 🟡 Grupos de exercícios (supersérie): o treino em andamento mostra A1/A2 e **o descanso é da
  rodada** — começa quando nenhum exercício do grupo ainda deve a série daquela rodada, e não
  quando o "último por posição" termina (a ordem planejada não é obrigatória, ACT-01). Falta
  validar em aparelho.
- ✅ **HIS-06 (parte desta tela) — avaliação 1–5** no resumo que aparece ao finalizar, gravada a
  cada toque e removível tocando de novo (ADR-0041 estende a imutabilidade: a nota é comentário
  de quem treinou, não medição). Avaliar depois e a observação em texto são da Fase 4.
- ⬜ **Validar no aparelho**: promoção do serviço com tipo `health` na API 34+, som e vibração com a
  tela apagada, `POST_NOTIFICATIONS` negado, force stop e reboot, teclado real.

**Revisão de 28/09/2026 (adversarial, ADR-0028):** encontrou 13 defeitos no código da Fase 3, todos
corrigidos com teste de regressão quando era possível testar na JVM. Três só apareciam **fora** do
Robolectric, porque os testes usam executores sincronizados: o serviço se matava logo após iniciar, a
tela do treino voltava sozinha para a Início antes de carregar, e um descanso que terminava continuava
na tela e na notificação. Lição registrada: um teste que depende de executor sincronizado **não prova**
comportamento de corrida — por isso a lista de pendências de aparelho acima não é opcional.

**Revisão de 03/10/2026 (adversarial, ADR-0028) — segmentos e grupos:** os itens de drop-set e de
supersérie tinham entrado **sem** essa passagem. Quatro defeitos, todos com teste escrito antes da
correção e cada metade de cada correção verificada por mutação:

1. **Etapa preenchida ficava sem decisão ao finalizar.** `finishReview` percorria só as séries-mãe, e
   a etapa ficava PENDING dentro de uma sessão encerrada: reps feitas, contando para nada, sem aviso.
2. **Drop-set terminava sem descanso.** A etapa nasce sem plano, então o descanso lido era 0 — e 0
   significa "limpa o cronômetro". A série mais cansativa acabava sem descanso, cancelando o que a
   série tinha iniciado.
3. **Desfazer a série deixava as etapas feitas.** O banco dizia que as reps aconteceram enquanto todos
   os totais as ignoravam; e o descanso, que pertence à última etapa, continuava correndo.
4. **Aquecimento em supersérie deslocava as rodadas.** O aquecimento de A1 pareava com a série de
   verdade de A2, e o descanso do grupo disparava depois de um aquecimento.

Os três primeiros têm a mesma causa: as etapas foram **gravadas** como linhas próprias e tratadas como
parte da série em um único lugar, o volume. Todo caminho que lia a linha isolada tinha o defeito.

**Verificado e sem defeito** (por mutação, não por leitura):

- **Numeração e pareamento com a sessão anterior** (ADR-0033): deixar um drop vazar para qualquer um
  dos lados derruba o teste existente, e seis testes derrubam a partição do mapper.
- **Ordem das linhas lidas** (`ORDER BY` que põe a série antes das etapas dela): inverter o desempate
  derruba seis testes.
- **Mapeamento de grupos** (`SessionMapper.toGroups`): lido, sem mutação. O `LEFT JOIN` cobre o
  exercício que ficou sem séries — ele continua aparecendo na tela e não segura a rodada aberta,
  porque não deve nada. Isto é leitura, não prova: fica como a parte menos verificada desta revisão.

### Fase 2/3 — grupos de exercícios (01/10/2026)
Banco na **versão 4** (`template_exercise_group`, `session_exercise_group`, colunas `group_id`), com
migration explícita e teste que vai de v1 a v4. Dois erros que teriam passado em silêncio foram
achados ao implementar: **salvar** um treino desagrupava tudo (o rascunho não conhece grupos) e
**duplicar** perdia as superséries. Os dois têm teste.

### Fase 4 — Histórico 🟡
- ✅ Aba **Histórico** com a lista de sessões concluídas, mais recentes primeiro. Sessões **ativas** e
  **descartadas** não entram: "terminou" não é "aconteceu".
- ✅ Detalhe de uma sessão antiga montado **só dos snapshots** gravados por ela — editar ou renomear o
  treino depois não muda o que está lá.
- ✅ Resumo (HIS-01, menos recordes e medalhas): duração total e efetiva, séries, repetições, volume e
  a linha "N séries não incluídas no volume", calculados pelo mesmo `SessionVolume` que a tela de
  finalização usa.
- ✅ Comparação com a **sessão anterior do mesmo treino** (§11): volume, séries, repetições e tempo
  efetivo, com ↑ ↓ = e percentual **só** quando o valor anterior é maior que zero — `percent()` lança
  exceção se ninguém checou `hasPercent()`, então esquecer quebra um teste, não a honestidade da tela.
- ✅ Sem mudança de esquema: o banco continua na **versão 3**. `local_date`, `time_zone` e `rating` já
  existiam desde a v3 e só agora são lidos.
- ✅ **HIS-02 — calendário** do mês acima da lista: dias com sessão concluída marcados, tocar num
  dia filtra a lista, tocar de novo (ou "Ver todas") desfiltra, e um dia sem treino diz isso em
  vez de deixar a tela vazia. As setas vão do mês da sessão mais antiga ao da mais recente — o
  limite é a **última sessão**, não hoje, senão uma sessão gravada com o relógio adiantado ficaria
  escondida. O dia é sempre o `local_date` vivido, nunca derivado de `started_at`.
  - A grade e a lista saem da **mesma** LiveData (`observeTrainedDates()` é um `map` da consulta do
    histórico, não uma consulta própria). Isso é **estrutura, não prova**: os testes usam
    executores síncronos, então duas consultas separadas também passariam.
  - Mutação: tirar o filtro, o toggle do mesmo dia, o "Ver todas", a marca de dia treinado, o
    limite das setas, a limpeza do filtro ao mudar de mês e a leitura do `local_date` **matam**
    testes. A trava redundante dentro de `stepMonth` **sobreviveu** — a seta desabilitada já
    impedia o caso — e por isso foi removida em vez de ganhar um teste que a alcançasse.
  - `android:rotation="180"` numa seta **engolia o clique** no Espresso/Robolectric: os testes
    falhavam com o mês parado. Trocado por um `ic_chevron_left` de verdade (também o certo para
    RTL). Medido: com rotação, 2 testes falham; sem ela, 0.
  - Achado pela mutação: reaproveitar os quadrados da grade (em vez de recriar os 42 a cada toque)
    **não fazia nada**, porque o RecyclerView responde a `notifyItemChanged` criando um **segundo**
    holder e fazendo cross-fade — a grade era inflada de novo de qualquer jeito, levando junto o
    foco de acessibilidade. Com `setSupportsChangeAnimations(false)` o mesmo holder é reusado e as
    duas mutações ("o quadrado reusado guarda a marca do mês anterior" e "…a data falada anterior")
    passaram a **matar** teste. O foco do TalkBack em si **não** está provado — o Robolectric não
    roda TalkBack; o que está provado é o reuso sem resíduo.
  - Duas asserções minhas passavam por motivo errado: `doesNotExist()` na faixa de filtro só valia
    porque o holder antigo era destruído. Com o reuso, a faixa fica no hierarquia como `GONE`, que
    é o que o usuário realmente vê — asserção trocada por `not(isDisplayed())`.
  - APK de release: 3.346.855 → 3.373.699 bytes (**+26 KB**) com a grade, o header e o chevron.
- ✅ Comparação **por exercício**, expansível série a série (a parte da HIS-04 que faltava).
  Emparelha pelo exercício e pela *n*-ésima vez que ele aparece, nunca pela posição; séries por
  ordem entre as válidas (§11), aquecimento fora. Exercício só de um lado é **nomeado**, não
  comparado com zero. Cada lado é formatado e medido com o **snapshot da própria sessão**.
  - Sem consulta nova: a sessão anterior já era carregada inteira para o resumo, e agora os
    `SessionExercise` dela seguem junto.
  - Mutação: tirar o emparelhamento por ordinal, comparar aquecimento, emparelhar por posição,
    pagar os dois blocos do mesmo exercício com o primeiro, sumir com o exercício largado, ler a
    série anterior com o snapshot de hoje (volume **e** texto), comparar série inexistente contra
    zero, abrir/fechar as séries e esconder a linha de volume de um exercício sem carga — **todas
    matam** teste.
  - Achado pela mutação: formatar a série anterior com o snapshot de hoje não quebrava nada até
    existir um teste com halteres de um lado e barra do outro. Sem ele, o "/halter" sumia da linha
    de antes sem ninguém notar.
  - APK de release: 3.373.699 → 3.390.427 bytes (**+16 KB**).
  - `TestGymApplication` passou a ter um **relógio móvel** (no mesmo instante do fixo). Sem isso
    duas sessões do mesmo treino num teste compartilham o `started_at` e nenhuma é "a anterior" da
    outra — a consulta pede estritamente mais antiga.
- ⬜ **Validar no aparelho**: a tabela de comparação com um treino longo e a rolagem com todas as
  séries abertas.
- ✅ **HIS-06 — avaliação 1–5** no resumo da finalização **e no detalhe de qualquer sessão
  concluída**, gravada a cada toque e removível tocando de novo (ADR-0041 estende a
  imutabilidade: a nota é comentário de quem treinou, não medição). O mesmo
  `view_session_rating` serve as duas telas; a margem lateral saiu do layout e passou para o
  diálogo, que é quem precisa dela. Se a gravação falhar, a tela **volta** para o que está no
  banco e avisa — nota acesa que não está gravada é mentira.
  - Mutação: mostrar sem gravar, gravar zero ao limpar e **deixar o listener ligado enquanto o
    valor guardado é aplicado** (que faria cada redesenho regravar o que acabou de ler) — todas
    matam teste.
- ✅ **Observação em texto da sessão** no mesmo card (a coluna `notes` já existia desde a v1 —
  **sem migração**). Grava ao perder o foco e ao pausar a tela; em branco vira `NULL`; visitar a
  tela e sair não grava nada.
  - A observação **não** ganhou campo próprio em `SessionDetail`: ela já viajava no
    `SessionHeader`, e duplicá-la criaria duas fontes para o mesmo fato. `withNotes` reconstrói
    pelo header.
  - Mutação: não gravar ao sair, não gravar ao perder o foco, guardar branco como string vazia
    (no repositório **e** no ViewModel, cada um provado no seu nível) e regravar uma observação
    inalterada a cada pausa — **todas matam** teste.
  - APK de release: 3.390.827 → 3.394.563 bytes (**+4 KB**).
  - Dois sobreviventes ensinaram algo: as duas normalizações de branco se **mascaravam**
    mutuamente, e a asserção sobre `updated_at` só vale com o relógio andando entre a gravação e
    a verificação. Ambas as coisas foram corrigidas nos testes, não no código.
- ✅ **HIS-05 — excluir uma sessão do histórico**: menu da linha, confirmação que nomeia a sessão,
  exclusão soft (`deleted_at`) para poder sincronizar (ADR-0041). **Lacuna registrada:** o
  pareamento "anterior" não refiltra, então uma sessão posterior ainda compara com a excluída.
- ⬜ **Validar no aparelho**: a lista com muitas sessões e a rolagem do detalhe de uma sessão longa.

### Fase 5 — Progresso 🟡
- ✅ **PRG-04 — aba Progresso** com estatísticas de uma semana ou de um mês: treinos, séries,
  repetições, volume de carga, tempo efetivo, e **séries por grupo muscular** separadas em
  *principal* e *auxiliar*. Setas limitadas pelos dados (do primeiro ao último dia treinado), e o
  mês/semana sempre pelo `local_date` vivido.
  - **Sem esquema novo e sem dependência nova.** Nada de kg por músculo: os 400 kg de um supino não
    se dividem entre peito e tríceps de forma honesta, e séries por semana é o número que o treino
    usa.
  - **Uma consulta observada.** Só "quais sessões estão neste período" é observado; o resto é
    calculado dela na thread de disco, e o `switchMap` garante que um cálculo lento não caia em
    cima de um mais novo. Os **limites das setas** são lidos no **mesmo** passe que os números
    (`ProgressSnapshot`), então nunca há seta habilitada por uma leitura com números de outra.
  - **Uma consulta por sessão do período**, de propósito: o total honesto é a §9 aplicada série a
    série, que o SQL não expressa, e uma segunda implementação em SQL acabaria discordando do
    número que cada tela de finalização mostrou. Uma semana são três a seis sessões.
  - Mutação: aquecimento contando para o músculo, série não feita contando, drop-set contando como
    três, parear por posição, resolver para o subgrupo em vez do grupo, papel "último vence" em vez
    de "principal vence" (provado **nas duas ordens**, porque o SQL não promete ordem), extremos do
    período excluídos, agrupar por `started_at`, ler só a primeira sessão, setas sempre habilitadas,
    o botão Semana/Mês inerte, e um papel que não aconteceu sendo dito — **todas matam** teste.
  - Dois sobreviventes **mantidos com motivo**: (a) o filtro `deleted_at` em
    `observeSessionIdsBetween` é redundante, porque a leitura do cabeçalho já descarta a sessão
    excluída — mas é a mesma regra enunciada na sua própria camada, como todo o resto do DAO, e uma
    consulta que devolvesse sessões excluídas seria uma armadilha para o próximo a usá-la; (b) o
    `DISTINCT` da consulta de músculos é redução de linhas, não garantia de correção — quem garante
    é o domínio, que indexa por (exercício, grupo).
  - **Gráficos (PRG-01) não entraram**: nem uma barra proporcional, que já é gráfico. Falta a
    decisão de **biblioteca de gráficos ou `Canvas` próprio** (ADR pendente) — um gráfico de linha
    de volume por sessão é ~150 linhas de Canvas, zero dependência e zero APK, mas é decisão do
    dono do produto.
- ⬜ **PRG-01/02/03** — gráficos, filtros de janela e tela por exercício.
- ⬜ **PRG-05** — peso corporal (tabelas novas → migração v5).
- ⬜ Recordes pessoais (§10) — `personal_record`, migração v5.
- ⬜ **Validar no aparelho**: a lista de grupos musculares com fonte ampliada a 360 dp, e o
  desempenho de um mês com muitas sessões (uma consulta por sessão).

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

## Pendências conhecidas (precisam de aparelho/emulador para validar)

- **Ordem do histórico depende do relógio do aparelho, e fica assim de propósito.** `started_at` é
  gravado sem trava de monotonicidade (`finish()` trava `ended_at`, o início não). Se alguém **acertar
  o relógio para trás manualmente** por mais tempo do que o intervalo entre dois treinos iguais, a
  lista inverte e a comparação pode pegar uma sessão posterior. Levantado e **verificado** na revisão
  de 28/09/2026; a decisão é **não corrigir agora**, porque as duas correções óbvias não funcionam:
  `ended_at` vem do mesmo relógio destravado, e o desempate por `id` também — o UUID v7 é construído
  a partir de `System.currentTimeMillis()`. A correção real seria travar `started_at` na criação, o
  que exige uma consulta extra dentro da transação de início e **gravar um instante que o aparelho
  nunca viu**, contra a política de nunca reescrever instantes (`SessionMapper.toClock`). Sync NTP
  comum não alcança isso: só uma sessão ativa por vez, então dois treinos do mesmo template estão a
  dias de distância.
- **Datas e números seguem o idioma do aparelho, mas os textos são pt-BR fixos.** Num aparelho em
  inglês o histórico mostra "Histórico"/"Hoje" ao lado de "Sep 28, 2026". A parte dos **números** já
  era assim antes da Fase 4 (`ExercisePlanSheet` e `Durations` já usavam `Locale.getDefault()`); as
  **datas** são novas porque esta é a primeira tela que mostra uma. O bilhete certo é "publicar um
  segundo idioma (`values-en`) ou fixar pt-BR explicitamente", não desinternacionalizar os
  formatadores.
- **Supersérie no aparelho.** O descanso por rodada, o rótulo A1/A2 com fonte grande, e a folha de
  agrupar com muitos exercícios na lista — nada disso roda em Robolectric de forma que prove a tela.
- **Etapas de drop-set no aparelho:** os dois campos da etapa com teclado real, e a linha da série
  com quatro botões no cabeçalho a 360 dp com fonte ampliada.
- **Calendário no aparelho:** a grade de 42 quadrados a 360 dp com fonte ampliada, e o **foco do
  TalkBack** ao tocar num dia — o reuso dos quadrados existe para preservá-lo, mas o Robolectric
  não roda TalkBack, então isso é raciocínio, não prova.
- **Comparação por exercício no aparelho:** a tabela com um treino longo, a rolagem com todas as
  séries abertas, e as quatro colunas da linha de série a 360 dp com fonte ampliada.
- **Observação da sessão no aparelho:** o campo de texto com teclado real, e se o que foi digitado
  sobrevive a sair pelo botão início (o `onPause` grava, mas só um aparelho mostra o caminho
  inteiro com o teclado aberto).
- **Progresso no aparelho:** a lista de grupos musculares com fonte ampliada a 360 dp, e quanto
  demora um mês com muitas sessões (é uma consulta por sessão).
- **O APK encolhido pelo R8 nunca foi aberto.** O portão prova que o `assembleRelease` **compila**;
  não prova que o app sobe depois de o R8 renomear e remover código. Uma regra `keep` faltando só
  aparece em tempo de execução, e o caminho mais provável é Room com reflexão. Agora que o CI
  publica o APK release assinado (ADR-0039), esse é o primeiro teste a fazer com ele: instalar,
  abrir, criar um treino, registrar uma série e finalizar.
- **A publicação do APK pelo CI depende de secrets que só o dono do repositório pode criar.** Até
  que `FLOWGYM_KEYSTORE_BASE64` e as três senhas existam, o passo anuncia no log que não há chave e
  o release continua saindo sem assinatura. Os comandos estão em `docs/DEVICE_SETUP.md` §8.

Levantadas pela revisão de 22/09/2026 e **não corrigidas às cegas**, porque dependem de ver a tela:

- **Snackbar do editor** pode aparecer mais alto que o necessário: o contêiner de conteúdo já recebe o
  inset da barra de navegação e o Snackbar pode somar o seu. Ajustar depois de medir em um aparelho.
- **Biblioteca em paisagem / telas baixas:** busca + chips + filtros ocupam um bloco fixo no topo e
  sobra pouco espaço para a lista. Provável solução: recolher o cabeçalho ao rolar (AppBarLayout).
- Validar em aparelho o teclado, o arrastar e soltar e o contraste real dos dois temas.
- **Fase 3, nada disso roda em Robolectric:** promoção do foreground service com tipo `health` na
  API 34+ (um tipo errado ou uma permissão-pré-requisito faltando só falha no aparelho, no momento em
  que o usuário toca "Iniciar treino"), cronômetro e contagem regressiva da notificação, som e
  vibração de fim de descanso com a tela apagada, `POST_NOTIFICATIONS` negado, force stop e reboot no
  meio do treino, e rolagem/foco com o teclado real numa sessão de 20 × 5 séries.

## Pendências de qualidade (não precisam de aparelho)

- **Plurais em pt sem a categoria `many`.** O arquivo `values/strings.xml` é pt-BR mas não declara
  `tools:locale`, porque declarar faz o lint exigir um item `many` em **todos** os ~23 `<plurals>`
  (a regra do CLDR para pt pede, para valores como 1.000.000). Sem declarar, o lint corrige a
  ortografia deste arquivo como se fosse inglês e acusa palavras portuguesas de serem erros
  ("eles" → "eels"), o que hoje é contornado reescrevendo a frase. O conserto de verdade é
  **adicionar `many` aos plurais e então declarar o locale**, em um passo só.

## Próxima etapa recomendada
**Rodar as Fases 3 e 4 no aparelho** (`./gradlew :app:connectedDebugAndroidTest` + uso real no
S24+): é o único jeito de provar a notificação, o serviço em primeiro plano na API 34+, o alerta de
descanso com a tela apagada, o teclado, e a migração v3→v4 com dados reais — que convém exercitar
**antes** de a Fase 5 empilhar uma v5 em cima. Depois disso, dentro da **Fase 5**: decidir
gráficos (biblioteca ou `Canvas`, com ADR) e os **recordes pessoais** (§10), que fecham a lacuna
que o resumo de finalização ainda anuncia.
