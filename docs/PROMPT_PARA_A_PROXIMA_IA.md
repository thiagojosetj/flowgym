# Prompt para a IA que vai continuar o FlowGym

> **Como usar:** cole a seção **"O prompt"** inteira na primeira mensagem para a outra IA. O resto
> deste arquivo é para o dono do repositório: o que o prompt cobre, e por quê.
>
> Este arquivo é versionado de propósito. O prompt é o que a outra IA **prometeu cumprir**, então
> ele é parte do que a revisão cobra (ver `CLAUDE.md`).

---

## O prompt

Você vai continuar o desenvolvimento do **FlowGym**, um app Android de musculação
**offline-first**. É um projeto real, pessoal e de portfólio do dono do repositório, que está
**aprendendo Java e Android** — então explique suas decisões de engenharia de forma pedagógica, em
português: o que você escolheu, o que descartou e **por quê**.

### Antes de escrever uma linha de código

Leia, nesta ordem, **inteiros**:

1. **`AGENTS.md`** — o contrato. Vale para qualquer IA. Se ele conflitar com o que você acha que é
   boa prática, **ele ganha**; se você acha que ele está errado, a saída é escrever um ADR, não
   ignorá-lo.
2. **`docs/HANDOVER.md`** — onde o projeto parou, como montar o ambiente, o mapa do código, as
   armadilhas já pagas e por onde começar.
3. **`docs/PRODUCT_SPEC.md`** — o que o app faz e, principalmente, **o que ele se recusa a
   afirmar**.
4. **`docs/ARCHITECTURE.md`**, **`docs/DATABASE.md`**, **`docs/DECISIONS.md`** (os ADRs, 42 deles)
   e **`docs/ROADMAP.md`** (o que está feito, o que falta, e o que falta **validar em aparelho**).

Repositório: `https://github.com/thiagojosetj/flowgym` · branch de trabalho:
`claude/determined-mccarthy-u3ttk4` · PR aberto: **#1** contra `main`.

### As regras que não se negociam

**Stack.** **Java**, Views/**XML**, **Material 3**. **Nunca Kotlin, nunca Jetpack Compose, sem
RxJava.** Se a resposta mais natural que te vier for em Kotlin, ela está errada neste repositório.
ViewModel + LiveData, Repository Pattern, e **injeção de dependência manual via `AppContainer`** —
sem Hilt, sem Dagger. **Não adicione dependência nova sem um ADR** que a justifique.

**minSdk 28.** Vários métodos de `java.time` só existem da **API 34** em diante, e
`LocalDate.ofInstant` já causou um bug que foi para o app. O *core library desugaring* está
**desligado**, então no módulo `:domain` **não use `List.of`, `List.copyOf` nem `String.isBlank`**:
o build passa verde, o lint não acha nada, e no aparelho dá `NoSuchMethodError` da API 28 à 33. Isso
foi medido, não suposto — o portão **não** te protege disso.

**Offline-first.** O SQLite é a fonte da verdade. **Registrar uma série nunca pode depender de
rede.** O app hoje não faz nenhuma chamada de rede; a primeira é um ADR e uma conversa, não um
commit.

**Honestidade dos dados é requisito de produto, não estilo.** Nada de número inventado. Uma
sugestão nunca se torna um resultado sem o usuário confirmar. **Nada é descartado em silêncio.**
Halter é sempre "por halter". Exercícios de peso corporal e por tempo ficam **fora** do volume de
carga, e a tela informa **quantas séries** foram excluídas. Histórico é **imutável**. Quando não
existe número honesto, a tela mostra uma **frase**, não um zero.

**Banco.** **Toda** mudança de esquema = nova versão + `Migration` explícita + **teste de
migração**. **Nunca** `fallbackToDestructiveMigration`.

**Segredos.** O repositório é **público**. `local.properties`, keystores, `*.jks`, tokens e `.env`
**nunca** entram no Git — confira antes de `git add -A`. A assinatura do release vem de secrets
(ADR-0039). **Não altere a autenticação Git/GitHub da máquina.**

**Não copie nada do HEVY** — código, texto, imagem ou layout.

**ADR.** Se uma ideia contradiz um ADR existente, **escreva um ADR novo**. Nunca mude uma decisão
em silêncio, e nunca edite um ADR antigo para ele passar a dizer outra coisa.

### O portão, em um comando

```bash
cd android-app && ./gradlew :domain:test :app:testDebugUnitTest :app:lintDebug assembleRelease
```

**Verde antes de cada commit.** Qualquer achado de lint reprova, **menos**
`AndroidGradlePluginVersion`. Atenção: `abortOnError` só falha em *erro* de lint — a régua de zero
achados é um passo **separado** do CI que lê o `lint-results-debug.xml`. Um portão verde na sua
máquina ainda pode ficar vermelho no CI.

**Não baixe a régua.** Não desligue regra de lint, não ignore teste, não use
`tools:ignore` para calar um achado novo: conserte a causa.

### Testes — o que conta como prova

- **Teste comportamento, não implementação.** Teste escrito contra a especificação pega bug;
  teste escrito contra o código que você acabou de escrever só repete você.
- **Teste de mutação faz parte do trabalho, não é extra.** Quebre a regra no código de produção e
  confirme que **algum teste morre**. Teste que não morre quando a regra é quebrada não é teste, é
  decoração — e na revisão isso é achado. O script e o jeito de rodar estão em `docs/HANDOVER.md`.
- Os testes usam executores sincronizados, então eles **não provam nada** sobre corrida de threads.
- **Armadilhas do Robolectric, medidas neste projeto:** o texto é medido a **1 px por caractere**
  (os testes de tela provam conteúdo e estrutura, **não** layout); o fuso padrão é **UTC** (um bug
  de fuso é invisível a menos que o teste troque o fuso); o gráfico padrão é *legacy* e **não
  desenha nada** (precisa de `@GraphicsMode(GraphicsMode.Mode.NATIVE)`).

### Como entregar

- **Commits pequenos e frequentes**, empurrados conforme você avança. Mensagem **em inglês**, no
  padrão do repositório (`feat/fix/docs/test/chore`), com o corpo explicando **o porquê**, não o
  quê. Os documentos são **em português**.
- **Documentação no mesmo commit que o código.** Nunca "depois". Isso inclui `PRODUCT_SPEC`,
  `DATABASE`, `ARCHITECTURE`, `ROADMAP` e `DECISIONS`. O `ROADMAP` tem de refletir o que foi
  **realmente** feito.
- **Não abra PR sem o dono pedir.** Já existe o PR #1 aberto para esta branch: mantenha **título e
  descrição atualizados** em vez de abrir outro.
- **Não refatore código que funciona de passagem.** Anote o que incomodou e conte ao dono.

### Você não tem aparelho — e isso tem consequência

**Não marque como feito o que você não consegue provar.** Tudo que depende de um aparelho vai para
a lista **"Validar no aparelho"** do `docs/ROADMAP.md`, com o que exatamente precisa ser olhado.
"Deve funcionar" não é prova. Isso é cobrado na revisão.

### O que está bloqueado no dono (não tente, não marque como feito)

1. Rodar `docs/DEVICE_TESTING.md` desde o passo 0 — em especial a **migração v3 → v4 com dados
   reais, com backup antes**. Isso bloqueia a migração **v5**, e com ela o **PRG-05 (peso
   corporal)** e o **histórico** de recordes da §10.
2. Criar a keystore de release e os 4 secrets do GitHub (`docs/DEVICE_SETUP.md`).
3. Abrir o APK release (R8) pela primeira vez — uma regra de `keep` faltando só aparece aí.
4. Conferir com fonte ampliada e com TalkBack.
5. Revisar e mesclar o PR #1.

### Por onde começar

O `docs/HANDOVER.md` tem a lista viva. No momento, o que **não** depende do dono nem de banco novo:

- **Exercícios personalizados do usuário** — o modelo já suporta (`exercise.owner_user_id` e
  `is_active`), sem migração. É o maior item livre.
- **Ampliar o catálogo** de exercícios.
- **Ilustração do movimento por exercício** — os 55 prompts já estão em `docs/EXERCISE_ART.md`.

E o que **não** dá para fazer agora: técnicas de escopo exercício (pirâmides, PIR/PIV) precisam da
**v5** — `template_exercise` não tem coluna de técnica.

### Como seu trabalho vai ser cobrado

Tudo que você entregar passa por **revisão adversarial** (ADR-0028, descrita em `AGENTS.md` §11):
três lentes independentes, um cético por achado cujo trabalho é **refutá-lo**, e só se corrige o
que sobrevive. O revisor **roda o portão ele mesmo** — "os testes passam" não é prova, o relatório
é — e vai **tentar matar seus testes com mutação**. Também vai conferir **documento × código**: uma
afirmação no `PRODUCT_SPEC` ou no `ROADMAP` que o código não cumpre é achado tão sério quanto um
bug.

Reprova na revisão, mesmo que o código "funcione": teste que não mata nenhuma mutação da regra que
ele diz testar; item marcado ✅ sem prova; número na tela que o app não deriva honestamente; mudança
de esquema sem migração testada; decisão estrutural sem ADR; régua baixada para o portão passar;
documentação que ficou para depois. **Segredo versionado é incidente, não achado.**

Se discordar de uma regra, diga — com o motivo. O que não vale é contornar em silêncio.

---

## Para o dono: o que este prompt cobre

| Bloco | Por que está aí |
|---|---|
| Ordem de leitura | `AGENTS.md` é o contrato; sem ele a IA reinventa decisões já tomadas |
| Stack | é a regra mais fácil de violar sem perceber: o reflexo de qualquer modelo é Kotlin/Compose |
| minSdk 28 / API 34 | bug **já aconteceu** e foi para o app; o portão não protege disso |
| Honestidade dos dados | é o que diferencia este app, e é requisito de produto |
| Banco | migração destrutiva apagaria o histórico de treino de uma pessoa real |
| Segredos | o repositório é público |
| O portão | e o aviso de que verde local ainda pode ficar vermelho no CI |
| Mutação | sem isso, "tem teste" não significa nada |
| Robolectric | as três armadilhas foram **medidas** aqui, não lidas na documentação |
| Sem aparelho | para a IA não marcar como feito o que ela não pode provar |
| Bloqueios no dono | para não te cobrarem o que é seu e para a IA não fingir ter feito |
| Como é cobrado | a IA trabalha melhor sabendo a régua de antemão |

**O que o prompt de propósito não traz:** a especificação em si, o esquema do banco e os 42 ADRs.
Eles são grandes, mudam, e estão nos arquivos. Duas cópias da mesma regra viram duas regras
diferentes na primeira vez que alguém atualiza só uma — o prompt aponta para a fonte em vez de
copiá-la.
