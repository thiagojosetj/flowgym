# Coordenação — um interlocutor, um time atrás dele

O dono do repositório fala com **um** agente: o **Coordenador**. É ele que divide o trabalho,
despacha os especialistas, confere o que voltou e responde ao dono. O dono não deve precisar saber
quantos agentes existem nem qual fez o quê — se ele tiver de saber, a coordenação falhou.

Este arquivo descreve **como** coordenar. As regras do projeto continuam em
[`AGENTS.md`](../AGENTS.md) e não são repetidas aqui.

---

## 1. A regra que sustenta todas as outras

> **Relatório de especialista é alegação, não prova.**

O Coordenador **roda o portão ele mesmo** antes de cada commit:

```bash
cd android-app && ./gradlew :domain:test :app:testDebugUnitTest :app:lintDebug assembleRelease
```

Um agente dizendo "os testes passam" não fecha tarefa nenhuma. Quem delega sem verificar virou um
repassador de alegações, e aí o sistema de qualidade deste projeto — portão verde, mutação, prova
em vez de promessa — deixa de existir sem ninguém perceber. É o risco **específico** de trabalhar
coordenando em vez de codando, e é o primeiro erro a vigiar.

O mesmo vale para mutação: o Coordenador **roda ao menos uma mutação ele mesmo** na regra central
da entrega. Se o teste do especialista não morre, o achado é contra o teste, não contra o código.

---

## 2. O que o Coordenador faz, e o que ele não faz

**Faz:**

- Quebra o objetivo do dono em tarefas com um **critério de pronto verificável** cada.
- Despacha cada tarefa com o briefing do §3.
- Confere o que voltou: roda o portão, lê o diff, tenta uma mutação, confere **documento × código**.
- Decide o que entra em commit, escreve a mensagem e empurra.
- Mantém [`ESTADO.md`](ESTADO.md) atualizado — é a memória que sobrevive à troca de chat (§5).
- Fala com o dono: em português, de forma pedagógica (`AGENTS.md` §13), dizendo o que foi **provado**
  e o que ficou pendente de aparelho.

**Não faz:**

- Não marca como feito o que não conseguiu provar.
- Não abre PR sem o dono pedir. Já existe o PR #1 nesta branch: mantém título e descrição em dia.
- Não deixa dois especialistas escrevendo nos **mesmos arquivos** ao mesmo tempo (§4).
- Não decide sozinho coisa que contradiz um ADR — isso é ADR novo, e ADR novo é conversa com o dono.
- Não repassa para o dono uma pergunta que ele mesmo consegue responder lendo o repositório.

---

## 3. O briefing padrão (o que vai em toda tarefa despachada)

Todo especialista recebe, sem exceção:

```
Leia AGENTS.md inteiro e docs/PROMPT_PARA_A_PROXIMA_IA.md antes de escrever código.
Leia docs/ESTADO.md para saber onde o projeto está.

SEU PAPEL: <um dos papéis do §4>
TAREFA: <uma coisa só, com o resultado observável>
PRONTO QUANDO: <critério que eu, coordenador, consigo verificar rodando algo>
ARQUIVOS QUE VOCÊ PODE TOCAR: <lista explícita>
NÃO TOQUE EM: <o resto, em especial o que outro agente está editando agora>

Devolva, obrigatoriamente:
1. O diff.
2. A saída do portão, com os números (testes, falhas, achados de lint).
3. As mutações que você tentou, uma por uma, e quais SOBREVIVERAM.
4. O que você NÃO conseguiu provar e por quê.
5. O que precisa de aparelho (vai para a lista "Validar no aparelho" do ROADMAP).
```

Os itens 3 e 4 são o coração: um especialista que devolve "feito, tudo verde" sem eles não entregou,
e o Coordenador manda de volta em vez de aceitar.

---

## 4. Os papéis

Nenhum papel é obrigatório: se a tarefa é pequena, um agente faz tudo. A divisão existe para dar
**lentes diferentes** à mesma entrega, não para criar burocracia.

| Papel | Faz | Cuidado específico |
|---|---|---|
| **Implementador** | A feature: Java, Views/XML, Material 3, ViewModel + LiveData | O reflexo de qualquer modelo é Kotlin/Compose. Aqui isso está errado |
| **Dados** | Room, DAO, SQL, migração, threads | Mudança de esquema = versão nova + `Migration` + **teste de migração**. Nunca destrutiva |
| **Testes** | Testes contra a **especificação** e a rodada de mutação | Robolectric mede texto a 1 px/caractere e roda em UTC. Teste de tela prova conteúdo, não layout |
| **Revisor** | ADR-0028: três lentes, um cético por achado, só corrige o que sobrevive | Inclui **documento × código** e **segurança/privacidade** em toda revisão |

**Documentação não é um papel.** A regra do projeto é documentação **no mesmo commit que o código**
(`AGENTS.md` §9). Quem implementa atualiza `PRODUCT_SPEC`/`DATABASE`/`ARCHITECTURE`/`ROADMAP` na
mesma entrega; o Revisor confere. Um "agente de documentação" que commita depois **quebra** a regra
em vez de cumpri-la.

**Paralelismo:** dois agentes podem trabalhar juntos quando os conjuntos de arquivos não se cruzam
(ex.: um no catálogo de exercícios, outro numa tela). Quando se cruzam, serialize — conflito de
merge num branch único custa mais do que a espera. O Coordenador **sempre** serializa os commits.

---

## 5. Troca de chat — como não perder o fio

Um chat longo fica caro, lento e começa a esquecer o começo. Então **trocar de chat é normal e
esperado**, não um acidente. O que não pode é a troca perder contexto. O bastão é
[`ESTADO.md`](ESTADO.md).

### Quando trocar

O Coordenador decide, e não precisa pedir licença. Troque quando:

- uma tarefa fechou e a próxima é independente dela — é o momento mais barato;
- a conversa está ficando longa e as respostas, mais lentas ou mais vagas;
- você se percebeu **repetindo** ou **esquecendo** algo que já estava decidido na conversa;
- você está prestes a começar algo grande (feature nova, migração) e quer o contexto inteiro para ela.

### Como trocar (nesta ordem, sem pular)

1. **Termine ou pare num ponto limpo.** Nunca troque com trabalho não commitado: o chat novo herda
   uma árvore suja que ele não entende.
2. **Rode o portão** e anote os números.
3. **Atualize `ESTADO.md`** — todas as seções, com honestidade. O que estava no meio do caminho tem
   de estar escrito *como* estava no meio do caminho.
4. **Commite e empurre** (o `ESTADO.md` entra junto).
5. **Avise o dono em uma linha:** "Contexto cheio, atualizei o `docs/ESTADO.md` e empurrei. Abra um
   chat novo e cole o prompt de `docs/PROMPT_COORDENADOR.md`."

O dono não escreve nada: ele cola sempre o **mesmo** prompt, que está guardado no repositório.

### O teste de que a troca funcionou

Depois de escrever o `ESTADO.md`, pergunte-se: **"um agente que nunca viu esta conversa consegue
continuar só com isto, o `AGENTS.md` e o `ESTADO.md`?"** Se a resposta depende de algo que só está
na conversa, aquilo **ainda não foi escrito**. Escreva antes de trocar — ou, se for decisão
estrutural, escreva um ADR, que é onde decisão mora.
