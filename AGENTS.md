# AGENTS.md — como trabalhar neste repositório

Este arquivo é o contrato. Vale para **qualquer** agente de IA que mexa aqui (Claude, Codex,
Gemini, Cursor, o que for) e para gente também. Se algo neste arquivo conflitar com o que você acha
que é boa prática, **este arquivo ganha** — e se você acha que ele está errado, a saída é escrever
um ADR, não ignorá-lo.

Leia **este arquivo inteiro** antes de tocar em qualquer coisa. Depois leia
[`docs/HANDOVER.md`](docs/HANDOVER.md), que diz onde o projeto parou e por onde começar.

---

## 1. O que é o projeto

**FlowGym** — app de musculação **offline-first** para Android, em **Java**, com Views/XML e
Material 3. Projeto pessoal e de portfólio do dono do repositório, que está **aprendendo Java e
Android**. O repositório é **público**.

```
flowgym/
├── android-app/        o app. É aqui que quase todo o trabalho acontece.
│   ├── app/            módulo Android (UI, Room, repositórios)
│   └── domain/         Java puro, sem Android nenhum: as regras
├── docs/               a especificação, as decisões e o roadmap. Leia antes de codar.
├── tools/              scripts Python que GERAM assets (não são parte do app)
├── api/, backend/      só README. Planejados, não começados.
└── .github/workflows/  o portão de CI
```

### Ordem de leitura recomendada

| # | arquivo | por quê |
|---|---|---|
| 1 | este `AGENTS.md` | as regras |
| 2 | `docs/HANDOVER.md` | onde paramos, por onde começar, armadilhas |
| 3 | `docs/PRODUCT_SPEC.md` | **o que** o app faz e, principalmente, o que ele se recusa a afirmar |
| 4 | `docs/ARCHITECTURE.md` | camadas, threads, navegação |
| 5 | `docs/DATABASE.md` | esquema, versões, migrações |
| 6 | `docs/DECISIONS.md` | os ADRs. **Toda** decisão estrutural está aqui |
| 7 | `docs/ROADMAP.md` | o que está feito, o que falta, e o que falta **validar em aparelho** |

`README.md` é a vitrine do projeto para humanos; não é especificação.

---

## 2. Não-negociáveis

Estas são do dono do projeto. Não são preferências minhas nem sugestões.

### Tecnologia
- **Java**, Views/XML e **Material 3**. **NUNCA migrar para Kotlin nem para Jetpack Compose.**
- **Sem RxJava.** Concorrência é `AppExecutors` + `LiveData`.
- **ViewModel + LiveData**, Repository Pattern, **DI manual via `AppContainer`** — sem Hilt, sem
  Dagger.
- **minSdk 28**, compile/target **37**.
- **Não adicione dependência nova sem um ADR** que justifique. "É mais fácil assim" não é
  justificativa; "isto resolve X, as alternativas são Y e Z, e custa W no APK" é.

### Produto
- **Offline-first.** O SQLite é a fonte da verdade. Registrar uma série **nunca** pode depender de
  rede.
- **Honestidade de dados é requisito de produto, não estilo.** Seção 6 abaixo.
- **Não copie nada do HEVY** — código, texto, imagem, layout, identidade. Ele é referência
  conceitual e nada além disso.

### Processo
- **Portão verde a cada passo.** Seção 3.
- **Toda mudança de esquema = versão nova + `Migration` explícita + teste de migração.** Nunca
  `fallbackToDestructiveMigration`. Seção 5.
- **Nunca versione segredo nenhum** (`local.properties`, keystores, tokens, `.env`). O repositório
  é **público**.
- **Não altere a autenticação Git/GitHub da máquina.**
- **Se uma ideia contradiz um ADR existente, escreva um ADR novo.** Nunca mude uma decisão em
  silêncio.
- **Não refatore código que funciona de passagem.** Anote e avise; não misture com a entrega.
- **Não valide nada que exija aparelho físico.** Você não tem um. Deixe pendente em
  `docs/ROADMAP.md` e **não marque como feito o que você não consegue provar**.

---

## 3. O portão verde

Rode em `android-app/`:

```bash
./gradlew :domain:test :app:testDebugUnitTest :app:lintDebug assembleRelease
```

É exatamente o que o CI roda (`.github/workflows/ci.yml`). Tem de passar **antes de cada commit**,
não só no fim.

- **O job reprova em QUALQUER achado de lint**, não só em erro (ADR-0035). A única exceção é
  `AndroidGradlePluginVersion`, que é tratado como aviso.
- ⚠️ **Atenção: o `./gradlew` local NÃO reprova em aviso.** O `abortOnError` do Gradle só derruba
  o build em *erro* de lint; quem exige zero achado é um passo separado do CI, que lê o XML. Ou
  seja: **um portão verde na sua máquina ainda pode ficar vermelho no CI.** Depois de rodar o
  portão, **leia o relatório**, não só o código de saída:

  ```bash
  python3 - <<'PY'
  import json
  d = json.load(open("app/build/reports/lint-results-debug.sarif"))
  res = d["runs"][0].get("results", [])
  for r in res:
      print(("advisory" if r["ruleId"] == "AndroidGradlePluginVersion" else "BLOQUEIA"),
            r["ruleId"], "|", r["message"]["text"][:110])
  print(len([r for r in res if r["ruleId"] != "AndroidGradlePluginVersion"]), "achado(s) que barram")
  PY
  ```
- **Nunca baixe a régua.** Não desabilite regra de lint, não adicione `@SuppressLint`, não marque
  teste como `@Ignore` para passar. Corrija a causa. Se achar que a regra está errada no caso,
  isso é um ADR.
- `assembleRelease` está no portão de propósito: ele roda o **R8**, e uma regra de `keep` faltando
  só aparece ali — nunca no debug.
- `:app:lintDebug` usa `checkDependencies = true`, então também analisa o `:domain`. **Mas veja a
  armadilha medida na seção 8.1: isso NÃO cobre tudo.**

---

## 4. Arquitetura: o que pode e o que não pode

```
UI (Fragment)  →  ViewModel  →  Repository  →  DAO (Room)  →  SQLite
      ↑                              ↓
   LiveData                     :domain (Java puro)
```

- **`:domain` não conhece Android.** Nada de `android.*`, `androidx.*`, nada de `Context`. É onde
  moram as regras que precisam de teste rápido e sem emulador.
- **Regra de produto mora no `:domain`, não no widget.** Se você está escrevendo um `if` sobre o
  significado de um dado dentro de um `Fragment`, provavelmente está no lugar errado. Exemplos
  bons: `ProgressWindow` (quais janelas de filtro existem), `TrainingPeriod` (o que é uma semana),
  `SessionVolume` (o que conta no volume).
- **Repositórios são concretos** (sem interface + impl) — ADR-0017.
- **Navegação**: single-activity + Navigation Component, **sem Safe Args** (ADR-0018). Argumentos
  via `static Bundle args(...)` em cada Fragment, e sempre por `SafeNavigation` — duas batidas
  rápidas no mesmo botão derrubam o app sem ela.
- **Uma tela, uma leitura.** Se dois dados da mesma tela vêm de duas consultas observadas
  independentes, eles vão discordar em algum momento. Esse bug já aconteceu aqui (o calendário e a
  lista do histórico). O padrão é: **uma** consulta observada, e o resto derivado dela —
  `Transformations.switchMap` garante que um cálculo lento não caia em cima de um mais novo.
- **Dado imutável se lê uma vez, não se observa** (ADR-0036). Uma sessão encerrada não muda.
- **Escrita imediata por gesto, sem rascunho em memória** no treino ativo (ADR-0031).

---

## 5. Banco de dados

Versão atual: **4**. Schemas exportados em `android-app/app/schemas/`.

Qualquer mudança no esquema exige, no mesmo commit:

1. `@Database(version = N+1)`;
2. uma `Migration` **explícita** (nada de destrutivo);
3. o `N+1.json` exportado;
4. um **teste de migração** que vai de 1 até N+1 com `MigrationTestHelper`;
5. atualizar **todos** os lugares que fixam a versão (já foram 5; confira, não adivinhe);
6. `docs/DATABASE.md` atualizado no mesmo passo.

> ⚠️ **Hoje há um bloqueio real:** a migração **v3 → v4 ainda não foi verificada em aparelho com
> dados reais**. Até o dono fazer isso, **não crie a v5.** Os dois itens que dependem dela estão
> marcados no roadmap (peso corporal e o histórico de recordes).

---

## 6. Honestidade de dados — requisito de produto

Esta é a alma do projeto. Um número errado aqui não é um bug de cálculo, é o app mentindo para
alguém que vai mudar o treino por causa dele.

- **Nenhum número inventado.** Se você não consegue derivar algo honestamente, a tela **diz que
  não consegue**. Nunca preencha com zero: zero é uma medição.
- **Sugestão nunca vira resultado sem confirmação** de quem treinou.
- **Nada é descartado em silêncio.** Se a tela esconde, filtra ou exclui algo, ela **conta em voz
  alta** quantos itens ficaram de fora.
- **Halteres sempre "por halter"** (§6.4). O número sozinho não é a afirmação: "20 kg" e
  "20 kg/halter" são o mesmo número e o dobro do trabalho. A base viaja com o dado, por sessão,
  porque pertence ao snapshot daquela sessão.
- **Peso corporal e exercícios por tempo ficam FORA do volume de carga** (§9), e a tela informa
  quantas séries ficaram de fora.
- **Histórico é imutável**: os números de uma sessão encerrada nunca são reescritos. Excluir a
  sessão inteira não fere isso (ADR-0041); editá-la feriria.
- **Empate não é recorde**, e a primeira ocorrência é **linha de base**, não conquista (§10).
- **Sem kg por músculo.** Os 400 kg de um supino não se dividem honestamente entre peito e
  tríceps. São duas contagens de séries (principal e auxiliar) e elas **não somam**.
- **Gráfico nunca com dois eixos Y**, e nunca é o único caminho para um valor: sempre existe a
  mesma informação como texto (ADR-0042).

Quando estiver em dúvida se pode afirmar algo: **não afirme, e escreva na tela por que não.**

---

## 7. Como se testa aqui

- **JUnit 4 + Robolectric + Espresso, tudo na JVM.** Não há emulador nem aparelho.
- `@Config(application = TestGymApplication.class, qualifiers = "pt-rBR-w411dp-h891dp")` é o padrão
  dos testes de tela. `TestGymApplication` traz um `MutableClock` (o relógio de teste está em
  **segunda-feira, 21 de setembro de 2026**).
- **Teste comportamento, não implementação.** O teste descreve o que a tela **afirma**, e o nome do
  teste é uma frase que diz a regra.
- **Teste de mutação é o padrão aqui, não um extra.** Depois de escrever o teste, **quebre a regra
  de propósito** no código de produção e confirme que algum teste morre. Se nenhum morrer, seu
  teste não prova nada. Há um script auxiliar descrito no `HANDOVER.md`.
- Quando uma mutação **sobrevive** e você decide manter o código assim mesmo, **escreva o motivo no
  ROADMAP**. Já há dois casos registrados assim.
- Comentários de teste explicam **por que a regra existe**, não o que a linha faz.

### O que os testes NÃO provam (medido, não suposto)

| o teste roda assim | logo, ele não prova |
|---|---|
| executores **síncronos** | nada sobre corrida de threads |
| Robolectric mede texto a **1 px por caractere** | **nada sobre layout** — se o rótulo cabe, se o texto trunca |
| Robolectric roda em **UTC** por padrão | nada sobre bug de fuso: é o único fuso onde o certo e o errado coincidem |
| Robolectric usa gráficos **legados** por padrão | nada sobre desenho — `Canvas` não pinta nada até você pôr `@GraphicsMode(NATIVE)` |
| sem aparelho | nada sobre notificação, foreground service, teclado real, fonte ampliada, migração com dados reais |

Tudo que cai nessas linhas vai para a lista **"Validar no aparelho"** do `docs/ROADMAP.md`. Não
marque como feito.

---

## 8. Armadilhas já pagas

Cada uma destas custou um bug real neste repositório. Não repita.

### 8.1 `java.time` e APIs novas no `:domain` — **o portão NÃO te protege**

Medido em 26/11/2026: pôr `java.util.List.of(...)` num arquivo do `:domain` e rodar o portão
inteiro dá **BUILD SUCCESSFUL e zero achados de lint**. O *core library desugaring* está
**desligado** neste projeto, então isso vira `NoSuchMethodError` num celular em API 28–33.

Proibidos no `:domain` (e cuidado no `:app`):
- `List.of`, `List.copyOf`, `Map.of`, `Set.of` → use `Collections.unmodifiableList(new ArrayList<>(...))`
- `String.isBlank`, `String.strip`
- `LocalDate.ofInstant(Instant, ZoneId)` → **use `instant.atZone(zone).toLocalDate()`**. Esta é
  API 34 e **já quebrou este app uma vez**.

Na dúvida sobre um método de `java.time`: confira em qual API ele entrou no Android, não em qual
versão do Java.

### 8.2 Fuso horário

- O dia de um treino é o **`local_date` vivido**, gravado no fuso em que a pessoa estava. Nunca
  derive o dia de um instante com o fuso do aparelho.
- "Hoje" é o dia **onde a pessoa está** — use `ui/common/Today`, não `LocalDate.now()` com um
  `Clock` UTC. O relógio do app já foi UTC e o calendário abria no mês seguinte a partir das 21:00
  em São Paulo.
- O seletor de datas do Material fala **milissegundos UTC**, sempre. Use `ui/common/PickedDays`.

### 8.3 Testes de tela

- `isDisplayed()` falha para o que está **abaixo da dobra**. Use `scrollTo()` ou, quando a pergunta
  é sobre o estado e não sobre a rolagem, `withEffectiveVisibility(...)`.
- `android:rotation` numa view **engole cliques do Espresso**. Use um drawable espelhado.
- `RecyclerView` com animação de mudança ligada **reconstrói o holder** a cada rebind:
  `setSupportsChangeAnimations(false)` quando você depende de reuso.
- Diálogos (ex.: `MaterialDatePicker`) precisam de `.inRoot(isDialog())`.

### 8.4 Localização

- `tools:locale="pt-rBR"` **está declarado**, e por isso o lint exige a categoria `many` do CLDR
  em **todos** os `<plurals>`. Ao acrescentar um plural novo, acrescente as três formas. A `many`
  do português é a forma com "de" — "1.000.000 **de** séries" —, exceto quando o numeral não
  quantifica um substantivo logo depois; aí ela é igual à de `other`, de propósito.
- O CLDR do português põe **zero no singular** (a categoria `one` cobre 0 e 1). Duas consequências:
  "0 série" é errado duas vezes, então prefira não dizer o papel que não aconteceu a dizê-lo com
  zero; e **toda forma `one` tem de trazer o número** (`%d`). Uma forma `one` com o algarismo
  escrito à mão afirma uma quantidade que ninguém passou — é o que o lint chama de
  `ImpliedQuantity`, e já havia duas aqui.
- Mês abreviado em pt já termina em ponto ("set."), então frase que continua depois dele fica com
  dois pontos finais. Em texto falado isso é pior.

---

## 9. Documentação e ADRs

**A documentação entra no mesmo commit que o código.** Não existe "documento depois".

| arquivo | o que guarda |
|---|---|
| `docs/PRODUCT_SPEC.md` | o que o app faz, item por item, e as regras de honestidade |
| `docs/ARCHITECTURE.md` | camadas, threads, navegação |
| `docs/DATABASE.md` | esquema e migrações |
| `docs/DECISIONS.md` | **ADRs** — toda decisão estrutural, com alternativas e consequências |
| `docs/ROADMAP.md` | o que está feito **de verdade**, o que falta, e o que falta validar em aparelho |

Sobre ADRs:
- Numeração sequencial (`ADR-0001`…). O último hoje é **ADR-0042**.
- Formato: **Contexto / Decisão / Por quê / Consequências / Alternativas**. Escreva as alternativas
  de verdade, com o motivo de cada uma não servir.
- **Um ADR nunca é editado para mudar de ideia.** Escreva um novo e marque o antigo como
  substituído.
- Se você tomou uma decisão que o dono teria querido opinar, **diga isso em voz alta** na entrega,
  apontando o ADR, para ele poder discordar.

O ROADMAP tem de refletir a realidade. Um item marcado ✅ que não está pronto é pior do que um item
não feito.

---

## 10. Commits, branch e PR

- **Commits pequenos e frequentes**, empurrados conforme avança. Não acumule.
- Mensagem em **inglês**, convenção do repositório: `feat(escopo):`, `fix(...)`, `docs(...)`,
  `test(...)`, `chore(...)`.
- **O corpo explica POR QUÊ**, não o que o diff já mostra. Inclua: a regra de produto por trás, o
  que você recusou fazer e por quê, as mutações que você rodou, e o resultado do portão.
- Trabalhe numa branch; **não empurre para `main`** sem permissão explícita.
- **Não abra PR a menos que o dono peça.**
- Mantenha o título e a descrição do PR **atualizados** se o PR crescer.

---

## 11. Revisão — o que vai ser cobrado de você (ADR-0028)

Todo trabalho feito aqui passa por **revisão adversarial**. Não é leitura de diff.

1. **Três lentes independentes**, no mínimo, cada uma procurando coisa diferente:
   - regras de domínio × `PRODUCT_SPEC`;
   - dados, esquema, consultas e threads;
   - UI, acessibilidade, build, segurança e privacidade;
   - e sempre: **documentação × código** (afirmação de doc que o código não cumpre é achado).
2. **Um cético por achado**, cujo trabalho é **refutá-lo** (rastreando o código, o comportamento
   real da plataforma e a intenção do produto).
3. **Só se corrige o que sobrevive à refutação.** Achado refutado é registrado como refutado, com o
   motivo — não some.

O revisor **roda o portão ele mesmo**. Afirmação de que "os testes passam" não é prova; o relatório
do teste é.

### O que reprova na revisão, independentemente de o código "funcionar"

- Teste que não mata nenhuma mutação da regra que ele diz testar.
- Item marcado ✅ sem prova, ou prova que o ambiente não consegue dar (aparelho).
- Número na tela que o app não consegue derivar honestamente.
- Mudança de esquema sem migração testada.
- Decisão estrutural sem ADR, ou ADR editado para mudar de ideia.
- Regra de lint desligada, teste ignorado, ou régua baixada para o portão passar.
- Documentação que ficou para depois.
- Segredo versionado — isso é incidente, não achado.

---

## 12. Segurança e privacidade

- O repositório é **público**. Trate tudo que você escreve como publicado.
- `local.properties`, keystores, `*.jks`, tokens e `.env` **nunca** entram no Git (já estão no
  `.gitignore`; confira antes de `git add -A`).
- A assinatura do release vem de **variáveis de ambiente/secrets**, nunca de arquivo no repositório
  (ADR-0039). Sem a chave, o release sai sem assinatura e o CI simplesmente não publica artefato —
  isso é o comportamento correto, não um defeito.
- O app não faz rede. Se você for adicionar a primeira chamada de rede, isso é um ADR e uma
  conversa, não um commit.

---

## 13. Como explicar o trabalho

O dono do repositório está **aprendendo Java e Android**. Explique as decisões de engenharia de
forma **pedagógica**: o que você escolheu, o que descartou, e por quê — em português, sem jargão
gratuito. Uma entrega que funciona e que ele não entende é meia entrega.
