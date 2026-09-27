# Registro de Decisões (ADRs)

Formato curto: **Contexto → Decisão → Consequências**. Decisões podem ser revistas; nesse caso, a
ADR antiga é marcada como *substituída* e uma nova é criada. Pontos levantados em revisão (Codex)
são respondidos aqui quando mudam ou confirmam uma decisão.

| # | Decisão | Status |
|---|---|---|
| 0001 | Monorepo com builds independentes | Aceita |
| 0002 | Android em Java + Views/XML (sem Compose) | Aceita |
| 0003 | compileSdk 37, targetSdk 37, minSdk 28 | Aceita |
| 0004 | Módulos `:app` + `:domain` (Java puro) | Aceita |
| 0005 | Injeção de dependência manual (AppContainer) | Aceita |
| 0006 | LiveData + executores (sem RxJava) | Aceita |
| 0007 | UUID v7 gerado no cliente como PK | Aceita |
| 0008 | Cargas em gramas (inteiro) | Aceita |
| 0009 | Enums para comportamento, tabelas para conteúdo | Aceita |
| 0010 | Política de segredos | Aceita |
| 0011 | Scripts Gradle em Kotlin DSL | Aceita (discutível) |
| 0012 | JUnit 4 + Robolectric no Android | Aceita |
| 0013 | Hierarquia muscular autorreferente (2 níveis hoje) | Aceita |
| 0014 | Busca por coluna normalizada + LIKE | Aceita |
| 0015 | Sincronização por agregado com versão do servidor | Aceita |
| 0016 | Catálogo inicial em JSON com UUIDs fixos | Aceita |
| 0017 | Repositórios concretos, sem interfaces por enquanto | Aceita (discutível) |
| 0018 | Navigation sem Safe Args | Aceita |
| 0019 | Edição de template com rascunho e "Salvar" explícito | Aceita |
| 0020 | Idiomas: código em inglês, docs/UI em pt-BR | Aceita |
| 0021 | Backend com Maven | Aceita |
| 0022 | Treino ativo: timestamps + foreground service `health` | Aceita (implementada, ver 0031–0034) |
| 0023 | Biblioteca de gráficos | **Pendente** (Fase 5) |
| 0024 | Android Auto Backup habilitado com regras explícitas | Aceita |
| 0025 | Kotlin embutido do AGP 9 mantido no padrão | Aceita (revisada) |
| 0026 | Pacote Java neutro; nome do app provisório | Aceita |
| 0027 | Testes de UI na JVM com `TestGymApplication` | Aceita |
| 0028 | Revisão multi-lente com verificação adversarial antes de fechar cada etapa | Aceita |
| 0029 | Preferências da conta no banco, do aparelho no SharedPreferences | Aceita |
| 0030 | Técnica por série (não por exercício) | Aceita |
| 0031 | Treino ativo: escrita imediata por ação, sem rascunho em memória | Aceita |
| 0032 | Uma sessão ativa garantida pela transação, não por índice | Aceita |
| 0033 | Pareamento com a sessão anterior é derivado, não armazenado | Aceita |
| 0034 | Foreground service é projeção, nunca pré-requisito | Aceita |

---

### ADR-0001 — Monorepo com builds independentes
**Contexto:** Android, backend, contrato de API e documentação evoluem juntos; uma pessoa desenvolve
e o Codex revisa por diff.
**Decisão:** um repositório com `/android-app`, `/backend`, `/api`, `/docs`. Cada projeto tem seu
próprio build (Gradle no Android, Maven no backend).
**Consequências:** um PR pode mudar API + cliente + docs de forma atômica. CI precisará de jobs
separados por pasta.

### ADR-0002 — Java + Views/XML
**Contexto:** objetivo explícito de consolidar Java. Compose exige Kotlin.
**Decisão:** app em Java, layouts XML, Material Components, AndroidX (ViewModel, LiveData, Room,
Navigation, WorkManager).
**Consequências:** mais código de boilerplate (adapters, mapeamentos) que em Kotlin/Compose, e
algumas APIs AndroidX são pensadas primeiro para Kotlin (Room 2.8 ainda suporta
`annotationProcessor` em Java — verificado em 21/09/2026). Monitorar anúncios de fim de suporte a
Java nas bibliotecas.

### ADR-0003 — Níveis de SDK
**Decisão:** `compileSdk 37`, `targetSdk 37`, `minSdk 28`. Justificativa completa em
ARCHITECTURE §3 (requisito da Play ≥ 36 desde 31/08/2026; `java.time` nativo; WebP animado nativo;
~95% de cobertura).
**Consequências:** precisamos lidar desde já com os comportamentos do Android 17 (tela grande sempre
redimensionável → layouts adaptáveis; áudio em segundo plano exige foreground service).

### ADR-0004 — Módulo `:domain` em Java puro
**Contexto:** regras de negócio não podem ficar espalhadas na UI e devem ser portáveis para iOS.
**Decisão:** módulo `java-library` sem dependências Android contendo modelos, cálculos e validações.
**Consequências:** testes rápidos na JVM; mapeamento explícito entre entidades Room e modelos de
domínio (mais código, porém fronteira clara). Cuidado: o `:domain` compila contra a API do JDK, e
não contra a do Android — só usamos APIs presentes no Android 9 (sem `List.of`, `String.isBlank`
etc.). O lint do `:app` verifica dependências (`checkDependencies`).

### ADR-0005 — DI manual
**Contexto:** projeto de aprendizado; poucas dependências hoje.
**Decisão:** `AppContainer` criado no `Application` monta banco, executores e repositórios;
ViewModels recebem dependências por uma factory.
**Alternativas:** Hilt (padrão da indústria; anotações e geração de código, mais uma dependência
com acoplamento de plugin Gradle ao AGP).
**Consequências:** nada de singletons improvisados — o container é a única raiz de composição.
Reavaliar Hilt quando o container passar de ~20 dependências.

### ADR-0006 — LiveData + executores
**Decisão:** leituras observáveis com `LiveData` do Room; escritas em executor single-thread;
eventos únicos com `Event<T>`.
**Alternativas:** RxJava 3 (poderoso, curva alta, mais uma dependência grande); coroutines/Flow
(exigem Kotlin).
**Consequências:** operadores limitados (`map`, `switchMap`, `MediatorLiveData`) — suficiente para as
telas previstas.

### ADR-0007 — UUID v7 no cliente
**Decisão:** PKs `TEXT` com UUID v7 (RFC 9562) gerado por `Ids.newId()` no `:domain`.
**Consequências:** criação offline sem colisão; ordenação aproximada por criação; PostgreSQL usa
`uuid`. `TEXT` ocupa mais espaço que `INTEGER` no SQLite — irrelevante no volume esperado.

### ADR-0008 — Cargas em gramas
**Decisão:** toda carga é `INTEGER` em gramas; kg/lb é só exibição.
**Consequências:** igualdade exata para PRs de "reps com a carga X"; conversão lb ↔ kg arredonda ao
grama (erro < 0,01 lb, invisível na exibição).

### ADR-0009 — Enums vs tabelas
**Decisão:** enum Java (salvo como `TEXT`) quando o **código** se comporta diferente para cada valor
(tipo de rastreamento, base de carga, lateralidade, papel do músculo). Tabela quando é **conteúdo**
que pode crescer (músculos, equipamentos, técnicas avançadas, conquistas, métricas corporais).
**Consequências:** técnicas avançadas nunca ficam presas a uma enum.

### ADR-0010 — Segredos
**Decisão:** nada de segredo no Git: `local.properties`, keystores, `.env`, `google-services.json`,
`application-local.*` estão no `.gitignore`. Configuração sensível via variáveis de ambiente ou
arquivos locais com um `*.example` versionado.

### ADR-0011 — Gradle em Kotlin DSL (discutível)
**Contexto:** o pedido é "app em Java". Scripts de build não são código do app.
**Decisão:** `build.gradle.kts` + version catalog (`gradle/libs.versions.toml`), que é o padrão
atual do Android Studio e da documentação oficial, com checagem de tipos e autocomplete.
**Alternativa:** Groovy DSL (mais próxima da sintaxe Java, porém com menos suporte de IDE).
**Consequências:** o código-fonte do app permanece 100% Java. Trocar para Groovy é mecânico, se
preferido.

### ADR-0012 — JUnit 4 + Robolectric
**Decisão:** JUnit 4 em `:app` e `:domain`. Robolectric para Room/ViewModel/UI na JVM.
**Motivo:** o runner do Robolectric e o `AndroidJUnit4` são baseados em JUnit 4; usar a mesma versão
nos dois módulos Android evita misturar APIs de assert/anotações. O backend usará JUnit 5 (padrão do
Spring Boot).
**Achados da implementação (21/09/2026):**
- Robolectric 4.17 no JDK 21 exige `--add-exports java.base/jdk.internal.access=ALL-UNNAMED`
  (configurado só na JVM de testes, em `app/build.gradle.kts`).
- **Dívida técnica:** o `MigrationTestHelper` do Room 2.8.5 falha sob Robolectric **no Windows** (o
  driver compara caminhos usando `/`). Por isso, na JVM, o `SchemaTest` compara o schema exportado
  com o gerado pelas entidades (pega "mudou a entidade e esqueceu a versão"), e os testes de
  migration de verdade (a partir da versão 2) ficarão em `androidTest`, rodando em aparelho/emulador
  ou em CI. Reavaliar a cada atualização do Room.

### ADR-0013 — Hierarquia muscular
**Decisão:** tabela `muscle` autorreferente (`parent_id`); dois níveis usados hoje.
**Alternativas:** duas tabelas fixas (grupo/subgrupo) — impede vincular um exercício a um grupo sem
subgrupo; *closure table* — excessiva para dois níveis.

### ADR-0014 — Busca
**Decisão:** `search_text` normalizado (sem acentos, minúsculas, com apelidos) + `LIKE '%termo%'`
com escape. **Por que não FTS:** FTS casa prefixos de tokens, não trechos no meio da palavra, e o
catálogo é pequeno.

### ADR-0015 — Sincronização por agregado
Ver [SYNC.md](SYNC.md). Concorrência otimista via `server_version`; cópias de conflito para
templates; eventos append-only para ajustes de rotina, PRs e conquistas. LWW apenas onde o risco
foi analisado e aceito.

### ADR-0016 — Catálogo em JSON
**Decisão:** `app/src/main/assets/catalog/catalog.json` com UUIDs fixos, um campo raiz `version`,
músculos (grupos com subgrupos aninhados), equipamentos e exercícios (que referenciam músculos e
equipamentos pelo `code`). O `CatalogSeeder` roda quando a constante `CatalogSeeder.BUNDLED_VERSION`
é maior que `app_metadata.catalog_version` — assim uma inicialização normal não precisa ler o JSON. O
`version` do JSON precisa ser igual a `BUNDLED_VERSION` (o `CatalogSeederTest` garante), então os dois
sobem juntos a cada mudança de catálogo.
**Regras do re-seed:** *upsert* (nunca DELETE+INSERT, que quebraria as FKs dos treinos); exercícios que
saírem do JSON são apenas **desativados**, e mantêm seus vínculos de músculos e equipamentos, porque
treinos antigos e estatísticas por grupo muscular ainda os usam.
**Alternativas:** banco pré-empacotado (`createFromAsset`) — binário, impossível de revisar por diff.
**Consequências:** o mesmo arquivo poderá alimentar a migration de seed do backend. Os textos do
catálogo são **autorais** (não copiados de nenhum produto).

### ADR-0017 — Repositórios concretos (discutível)
**Decisão:** repositórios são classes concretas; sem `interface` + `Impl` enquanto existir uma única
implementação.
**Motivo:** YAGNI e menos arquivos para quem está aprendendo. Os testes usam Room em memória
(Robolectric), que exercita o comportamento real.
**Gatilho para rever:** a Fase 9 (fonte remota) ou a necessidade de *fakes* em testes de ViewModel.

### ADR-0018 — Navigation sem Safe Args
**Decisão:** argumentos via `Bundle` com métodos de fábrica estáticos (`ExerciseDetailFragment.args(id)`).
**Motivo:** evita um plugin Gradle a mais acoplado à versão do AGP; poucos argumentos por tela.

### ADR-0019 — Edição de template
**Decisão:** o editor trabalha num **rascunho** (`TemplateDraft`, do `:domain`) guardado no ViewModel,
e o usuário salva explicitamente. Voltar com alterações pede confirmação.
**Consequências:** o rascunho sobrevive a rotação, mas não à morte do processo durante a edição
(risco aceito: edições curtas e raras). O **treino ativo** (Fase 3) terá persistência progressiva.

### ADR-0020 — Idiomas
Código, identificadores e commits em inglês; documentação e interface em pt-BR (`values/strings.xml`
é pt-BR; outros idiomas entram como `values-xx`).

### ADR-0021 — Backend com Maven
Maven é o padrão do ecossistema Spring e já está instalado; Gradle no Android é obrigatório. Aprender
os dois é útil. Maven Wrapper será versionado.

### ADR-0022 — Treino ativo
Tempo sempre derivado de timestamps persistidos; notificação com cronômetro nativo; foreground
service tipo `health` (+ `HIGH_SAMPLING_RATE_SENSORS`) enquanto houver treino ativo; sem
`SYSTEM_ALERT_WINDOW`; WorkManager não é usado para o descanso. Detalhes em ARCHITECTURE §6.

**Complementos ao implementar (27/09/2026):**
- O alerta de fim de descanso é um `Handler.postDelayed` dentro do serviço, sempre re-derivado de
  `rest_ends_at` e idempotente pelo id da série que já alertou. **Sem** `SCHEDULE_EXACT_ALARM`
  (restrito a apps de despertador e calendário) e **sem** `WAKE_LOCK` (um foreground service em
  execução não é congelado).
- Permissões declaradas: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_HEALTH`,
  `HIGH_SAMPLING_RATE_SENSORS` (exigência da plataforma para o tipo `health`; o app não lê sensor
  nenhum — suprimido no lint com essa justificativa), `POST_NOTIFICATIONS` e `VIBRATE`.
- A atualização por segundo **não** é um LiveData observado pelo adapter (isso reenviaria a lista
  inteira a cada segundo), e sim um ticker que alimenta dois TextViews fixos fora da RecyclerView.

### ADR-0023 — Gráficos (pendente)
Candidatos avaliados preliminarmente em 21/09/2026:
- **MPAndroidChart** — Java, Apache 2.0, mas sem release desde 2020 (efetivamente sem manutenção).
- **Vico** — mantida, Apache 2.0, porém API pensada para Kotlin.
- **Views próprias com `Canvas`** — zero dependências, controle total de acessibilidade; mais trabalho.

Tendência: views próprias para linha/barra, a confirmar na Fase 5.

### ADR-0024 — Auto Backup
**Decisão:** `allowBackup=true` com `dataExtractionRules` explícitas (banco e preferências; nada de
cache).
**Motivo:** até existir conta/sync (Fase 9), o backup do Android é a única proteção contra perda do
celular. O backup em nuvem do Android é criptografado com a credencial de bloqueio da tela.
**Revisar** na Fase 9 (dados restaurados + sync).

### ADR-0025 — Kotlin embutido do AGP 9 mantido no padrão
**Contexto:** o AGP 9 liga o suporte embutido a Kotlin por padrão, mesmo em projetos só-Java. A
primeira versão desta ADR desligava-o com `android.builtInKotlin=false`, mas o próprio AGP 9.4
avisou (21/09/2026) que essa opção está **obsoleta e será removida no AGP 10**.
**Decisão:** manter o padrão. Sem fontes Kotlin, as tarefas de compilação Kotlin não fazem nada.
**Consequências:** nenhuma migração forçada no AGP 10. O código do app continua 100% Java; o
`kotlin-stdlib` já chegava de qualquer forma como dependência transitiva das bibliotecas AndroidX
escritas em Kotlin (Room, Lifecycle, Navigation).

### ADR-0026 — Nome e pacote
**Decisão:** pacote/namespace `io.github.thiagojosetj.gym` (neutro em relação à marca, baseado na conta
GitHub do autor); nome "FlowGym" só em `app_name`, `applicationId` e docs.
**Consequências:** trocar a marca = alterar 1 string + `applicationId` (antes da 1ª publicação). O
`applicationId` definitivo precisa ser decidido antes de publicar na Play (não pode mudar depois).

### ADR-0027 — Testes de UI na JVM com `TestGymApplication`
**Contexto:** não há emulador nem aparelho neste ambiente, mas o fluxo principal (biblioteca → treino →
salvar → reabrir) precisa de teste automatizado pela interface real.
**Decisão:** testes de UI em `app/src/test` com Robolectric + Espresso. Uma `TestGymApplication`
(só nos testes) sobrescreve `GymApplication.createContainer()` e monta o app sobre um banco em memória
com executores síncronos; a produção não tem nenhum gancho de teste além desse método protegido.
**Consequências:** testes rápidos e determinísticos, sem *idling resources*. Robolectric não substitui um
aparelho (teclado real, gestos, desempenho): os mesmos cenários devem ganhar versões em
`app/src/androidTest` quando houver dispositivo/CI com emulador.

### ADR-0028 — Revisão multi-lente com verificação adversarial
**Contexto:** o Claude implementa e o Codex revisa; ainda assim, erros sutis passam pelo autor.
**Decisão:** antes de fechar cada etapa, uma revisão com várias lentes independentes (regras de
domínio, dados, ciclo de vida/threads, UI/acessibilidade, build/segurança/privacidade, docs × código),
em que cada achado só é aceito se a maioria de três verificadores independentes (rastreamento de
código, comportamento da plataforma, intenção do produto) não conseguir refutá-lo.
**Primeira execução (22/09/2026):** confirmou, entre outros, que o re-seed do catálogo apagava os
vínculos musculares de exercícios aposentados, que a duplicação podia gravar um nome acima do limite,
que `Weight` aceitava `NaN`/`Long.MIN_VALUE`, e várias afirmações falsas nesta documentação. Todos
corrigidos com testes de regressão (ver histórico do Git).

### ADR-0029 — Preferências: conta no banco, aparelho no SharedPreferences
**Contexto:** o usuário pediu descanso padrão, som e vibração editáveis. Algumas preferências fazem
sentido seguir a conta para outro aparelho; outras são do aparelho.
**Decisão:** preferências **da conta** (descanso padrão, som e vibração do descanso, e no futuro
unidade e pré-preenchimento) ficam na tabela `user_setting`, chave-valor por usuário, com
`sync_status` — sincronizam por chave com última-escrita-vence (docs/SYNC.md §6). Preferências **do
aparelho** (tema, tamanho da interface) continuam em SharedPreferences (`UiPreferences`).
**Consequências:** um valor inválido no banco não pode derrubar o app: `SettingsRepository` cai no
padrão e registra um aviso. Chaves desconhecidas (de uma versão mais nova) são ignoradas, não apagadas.

### ADR-0030 — Técnica por série, não por exercício
**Contexto:** aquecimento, drop-set e rest-pause acontecem em **séries** específicas; pirâmide descreve
a sequência de séries; supersérie liga **exercícios**.
**Decisão:** `template_set.technique_id` guarda a técnica de cada série, e o editor passou a permitir
edição série a série (`ExercisePlanUpdate` recebe um `SetSpec` por série). O domínio valida que a
técnica existe e que seu escopo é `SET`; escopos `EXERCISE` e `GROUP` já existem no catálogo e ficam
disponíveis quando os grupos de exercícios entrarem.
**Consequências:** o card do treino mostra os badges das técnicas usadas, e cada opção do seletor tem um
ⓘ com descrição e instruções (o app explica o método em vez de mostrar só uma sigla).

### ADR-0031 — Treino ativo: escrita imediata por ação, sem rascunho em memória
**Contexto:** a ADR-0019 usa rascunho em memória + "Salvar" no editor de template. O treino ativo é
outra coisa: 90 minutos, tela apagada, processo morto a qualquer momento, e nenhum botão "salvar".
**Decisão:** cada gesto do usuário é uma transação (`ActiveSessionRepository`), e o ViewModel não
guarda nada que possa ser perdido — só o **texto em digitação** e quais cards estão recolhidos. A tela
observa o banco e renderiza o que está gravado.
**Consequências:** a série confirmada está no disco quando o método retorna; um kill só pode perder
digitação não confirmada, que por PRODUCT_SPEC §6.5 ainda não é resultado. Isso é provado pelo
`SessionRecoveryTest`, que fecha o banco em arquivo e reabre com um container novo.
Estende (não contradiz) a [[ADR-0019]], cuja última frase já previa persistência progressiva aqui.

### ADR-0032 — Uma sessão ativa por usuário: transação, não índice
**Contexto:** duas sessões ativas quebrariam o cronômetro, o descanso e a notificação.
**Decisão:** `countActive()` + inserção dentro de `runInTransaction` na thread única de disco. O
`observeActive()` ainda usa `LIMIT 1` como cinto de segurança.
**Por que não um índice único parcial:** o Room não declara `@Index(... WHERE status='ACTIVE')`, e criar
o índice por fora da declaração faz a validação de esquema do Room falhar ao abrir o banco. Uma tabela
de "lock" seria pior: criaria uma **segunda definição** de "ativo", que pode divergir de `status`.

### ADR-0033 — O pareamento com a sessão anterior é derivado, não armazenado
**Contexto:** PRODUCT_SPEC §11 pareia **séries de trabalho** pela posição. A primeira versão do SQL
pareava por `position` bruta: um aquecimento acrescentado hoje deslocava todo o "anterior" em um.
**Decisão:** o ordinal entre séries de trabalho é calculado em `:domain` (`WorkingSetPairing`), a
partir do `counts_as_working_set` da técnica. O SQLite da API 28 não tem funções de janela, então isso
não dá para fazer na consulta.
**Por que não uma coluna `working_position`:** exigiria migration e poderia divergir das técnicas.
Dado derivado se corrige com um release.

### ADR-0034 — O foreground service é projeção, nunca pré-requisito
**Contexto:** nada do serviço roda em teste de JVM, e as regras de tipo de serviço da API 34+ só
falham no aparelho — exatamente no momento em que o usuário toca "Iniciar treino".
**Decisão:** a sessão é gravada **antes** de qualquer tentativa de iniciar o serviço; o start fica em
try/catch; o serviço não guarda estado, observa o banco e se encerra sozinho quando não há sessão
`ACTIVE`; `POST_NOTIFICATIONS` negado degrada apenas a notificação.
**Consequências:** o pior caso é treinar sem notificação, nunca perder uma série. Falta validar em
aparelho (ROADMAP, pendências).

