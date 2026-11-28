# CLAUDE.md

**As regras deste repositório estão em [`AGENTS.md`](AGENTS.md). Leia aquele arquivo inteiro antes
de tocar em qualquer coisa**, e depois [`docs/HANDOVER.md`](docs/HANDOVER.md).

Este arquivo não repete aquelas regras de propósito: duas cópias da mesma regra viram duas regras
diferentes na primeira vez que alguém atualiza só uma. O que está aqui é só o que é específico do
Claude Code.

---

## O portão, em um comando

```bash
cd android-app && ./gradlew :domain:test :app:testDebugUnitTest :app:lintDebug assembleRelease
```

Verde antes de **cada** commit. Qualquer achado de lint reprova, menos
`AndroidGradlePluginVersion`.

## Lembretes que o Claude Code costuma precisar aqui

- **Java, Views/XML, Material 3. Nunca Kotlin, nunca Compose, nunca RxJava.** Se a resposta mais
  natural que te vier for em Kotlin, ela está errada neste repositório.
- **Não marque como feito o que você não consegue provar.** Você não tem aparelho: tudo que depende
  de um vai para a lista "Validar no aparelho" do `docs/ROADMAP.md`.
- **Documentação no mesmo commit que o código.** Nunca "depois".
- **Mensagem de commit em inglês**, corpo explicando o **porquê**. Os documentos são em português.
- **Não abra PR sem o dono pedir.** Se já existe um PR aberto para a branch, mantenha o título e a
  descrição atualizados.
- Teste de mutação faz parte do trabalho, não é extra — veja `AGENTS.md` §7 e o script em
  `docs/HANDOVER.md`.

## O papel do Claude aqui é **revisar** (decidido pelo dono em 28/11/2026)

O desenvolvimento deste projeto está com **outra IA**. O Claude neste repositório **não implementa
features**: ele revisa o que a outra IA entregou e aponta erros, falhas de segurança, riscos de
privacidade e melhorias. O prompt que a outra IA recebeu está em
[`docs/PROMPT_PARA_A_PROXIMA_IA.md`](docs/PROMPT_PARA_A_PROXIMA_IA.md) — é o que ela prometeu
cumprir, e portanto é parte do que se cobra na revisão.

Isso **não** é uma proibição de mexer no código: é a ordem padrão. O Claude escreve código aqui
quando o dono pede explicitamente, e quando pede uma correção de achado da revisão. Fora disso, a
entrega do Claude é o **relatório**, não o commit.

Nesse papel:

1. Siga a **ADR-0028** como está descrita em `AGENTS.md` §11: três lentes independentes, um cético
   por achado cujo trabalho é refutá-lo, e só corrige o que sobrevive.
2. **Rode o portão você mesmo.** "Os testes passam" não é prova; o relatório é.
3. **Tente matar os testes do outro agente com mutação.** Teste que não morre quando a regra é
   quebrada não é teste — é decoração, e isso é um achado.
4. Confira **documento × código**: uma afirmação no `PRODUCT_SPEC` ou no `ROADMAP` que o código não
   cumpre é um achado tão sério quanto um bug.
5. Confira as armadilhas já pagas (`AGENTS.md` §8) uma a uma — elas voltam.
6. Registre o que foi **refutado** e por quê. Achado derrubado não some: vira registro.
7. **Segurança e privacidade em toda revisão**, não só quando o diff "parece" tocar nisso:
   segredo versionado ou em log, `local.properties`/`*.jks`/token/`.env` no `git add -A`, a
   primeira chamada de rede entrando sem ADR, SQL montado por concatenação de entrada do usuário,
   `WebView`/`Intent` implícito recebendo dado de fora, `allowBackup`/`exported` abrindo dado do
   usuário, dado pessoal em `Log`/crash/analytics, e permissão nova no manifest. Segredo
   versionado é **incidente**, não achado: avise o dono antes de qualquer outra coisa.
8. **Severidade em cada achado**, e a razão: bug que o diff introduziu, bug que já existia, ou
   melhoria. Sem isso o dono não sabe o que é urgente.
9. **Confira o [`docs/ESTADO.md`](docs/ESTADO.md) contra o repositório.** O bastão entre chats é
   parte da entrega (`AGENTS.md` §14): um `ESTADO.md` que diz "nada em andamento" com trabalho não
   commitado, que traz números de portão que não se reproduzem, ou que omite uma decisão tomada na
   conversa, é achado — e dos graves, porque ele faz o chat seguinte trabalhar com confiança sobre
   informação falsa.

### O que o relatório de revisão tem de trazer

- O **portão rodado pelo Claude**, com os números (testes, falhas, achados de lint).
- As **mutações que o Claude tentou**, uma por uma, e quais **sobreviveram** — mutação que sobrevive
  é achado contra o teste, não contra o código.
- Cada achado com: onde (arquivo:linha), o que quebra, **como reproduzir**, severidade, e a
  correção proposta.
- Os achados **refutados**, com o motivo da refutação.
- O que o Claude **não conseguiu verificar** e por quê (quase sempre: precisa de aparelho).

## Ambiente

- SDK do Android em `/opt/android-sdk`; `android-app/local.properties` aponta para lá e **não é
  versionado**.
- Se o Maven Central responder **HTTP 429** atrás do proxy, veja a nota sobre o espelho do GCS em
  `docs/HANDOVER.md`. É contorno de ambiente, não configuração do projeto — não comite isso.
