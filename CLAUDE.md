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

## Quando o Claude está no papel de revisor

Este repositório usa o Claude também para **revisar** o que outro agente entregou. Nesse papel:

1. Siga a **ADR-0028** como está descrita em `AGENTS.md` §11: três lentes independentes, um cético
   por achado cujo trabalho é refutá-lo, e só corrige o que sobrevive.
2. **Rode o portão você mesmo.** "Os testes passam" não é prova; o relatório é.
3. **Tente matar os testes do outro agente com mutação.** Teste que não morre quando a regra é
   quebrada não é teste — é decoração, e isso é um achado.
4. Confira **documento × código**: uma afirmação no `PRODUCT_SPEC` ou no `ROADMAP` que o código não
   cumpre é um achado tão sério quanto um bug.
5. Confira as armadilhas já pagas (`AGENTS.md` §8) uma a uma — elas voltam.
6. Registre o que foi **refutado** e por quê. Achado derrubado não some: vira registro.

## Ambiente

- SDK do Android em `/opt/android-sdk`; `android-app/local.properties` aponta para lá e **não é
  versionado**.
- Se o Maven Central responder **HTTP 429** atrás do proxy, veja a nota sobre o espelho do GCS em
  `docs/HANDOVER.md`. É contorno de ambiente, não configuração do projeto — não comite isso.
