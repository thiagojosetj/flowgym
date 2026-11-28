# HANDOVER — onde o projeto parou e como continuar

Documento de passagem de bastão. Leia depois do [`AGENTS.md`](../AGENTS.md), que é o contrato; este
aqui é o estado das coisas.

**Última atualização:** 26/11/2026.

---

## 1. Em uma tela

| | |
|---|---|
| **O que é** | App de musculação **offline-first**, Android, **Java** + Views/XML + Material 3 |
| **Estado** | 🟡 Dá para treinar com ele. Fases 0–4 feitas, Fase 5 feita em tudo que não toca no banco |
| **Branch de trabalho** | `claude/determined-mccarthy-u3ttk4` |
| **PR aberto** | [#1](https://github.com/thiagojosetj/flowgym/pull/1) contra `main` |
| **Portão** | 570 testes (349 app + 221 domain), 0 falhas, lint só com o aviso de AGP |
| **Banco** | versão **4** |
| **Último ADR** | **ADR-0042** (gráficos em `Canvas`) |
| **Bloqueio principal** | a migração v3 → v4 **nunca foi rodada em aparelho com dados reais** |
| **Quem desenvolve** | outra IA, a partir de [`PROMPT_PARA_A_PROXIMA_IA.md`](PROMPT_PARA_A_PROXIMA_IA.md). O Claude **revisa** (`CLAUDE.md`) |

> ⚠️ **Nada neste projeto foi validado em aparelho.** Tudo foi provado em JVM/Robolectric. A lista
> do que falta está em [`ROADMAP.md`](ROADMAP.md), seção "Pendências conhecidas".

---

## 2. Ambiente: do zero ao portão verde

Você precisa de **JDK 21** e do **Android SDK** com a plataforma `android-37.0`.

```bash
# 1. SDK (se não tiver). Ajuste o destino se quiser outro lugar.
#    Pacotes necessários: "platforms;android-37.0" e "build-tools;37.0.0".
sdkmanager --install "platforms;android-37.0" "build-tools;37.0.0"

# 2. Diga ao Gradle onde o SDK está. Este arquivo NÃO é versionado.
echo "sdk.dir=/opt/android-sdk" > android-app/local.properties

# 3. O portão.
cd android-app
./gradlew :domain:test :app:testDebugUnitTest :app:lintDebug assembleRelease
```

A primeira execução baixa bastante coisa e demora. Depois disso, o portão completo leva cerca de
**2 minutos**.

**Se o Maven Central responder HTTP 429** (acontece atrás de alguns proxies corporativos, e o
culpado costuma ser um jar grande como o do Robolectric), aponte o Central para o espelho do Google
num init script **local da máquina**, nunca no repositório:

```groovy
// ~/.gradle/init.d/central-mirror.gradle  — contorno de ambiente, não configuração do projeto
settingsEvaluated { settings ->
    settings.pluginManagement.repositories { maven { url "https://maven-central.storage-download.googleapis.com/maven2/" }; gradlePluginPortal(); google() }
    settings.dependencyResolutionManagement.repositories { maven { url "https://maven-central.storage-download.googleapis.com/maven2/" }; google() }
}
```

**Não comite isso.** O `settings.gradle.kts` do projeto usa `FAIL_ON_PROJECT_REPOS` de propósito,
para haver um lugar só onde auditar de onde o código vem.

### Teste de mutação

É parte do trabalho (`AGENTS.md` §7). O jeito mais rápido é um script que aplica a mutação, roda um
subconjunto do portão, e **desfaz sempre**:

```python
# mut.py — uso: python3 mut.py ARQUIVO "TRECHO_ANTIGO" "TRECHO_NOVO" ":task::*FiltroDeTeste*" "rótulo"
import subprocess, sys, shutil
FILE, OLD, NEW, FILTER, LABEL = sys.argv[1:6]
shutil.copy(FILE, FILE + ".bak")
s = open(FILE, encoding="utf-8").read()
if OLD not in s:
    print("NAO APLICOU |", LABEL); shutil.move(FILE + ".bak", FILE); sys.exit(2)
open(FILE, "w", encoding="utf-8").write(s.replace(OLD, NEW, 1))
task, tests = FILTER.split("::")
r = subprocess.run(["./gradlew", task, "--tests", tests, "--max-workers=2"],
                   capture_output=True, text=True)
print(("KILLED   | " if r.returncode else "SURVIVED | ") + LABEL)
shutil.move(FILE + ".bak", FILE)
```

Guarde-o **fora do repositório** (ele é ferramenta de quem está trabalhando, não do projeto).

---

## 3. O que já existe

Resumo. O detalhe item a item está no [`ROADMAP.md`](ROADMAP.md); o que cada coisa **afirma** está
no [`PRODUCT_SPEC.md`](PRODUCT_SPEC.md).

| Fase | Estado | O que tem |
|---|---|---|
| 0 — Fundação | 🟡 | Projeto, Room v1, navegação, Material 3 claro/escuro, R8, CI |
| 1 — Biblioteca | 🟡 | 55 exercícios, 13 grupos / 38 subgrupos, busca sem acento + apelidos, filtros, detalhe, **filtro de subgrupo por imagem** (51 silhuetas geradas de uma fonte só) |
| 2 — Templates | 🟡 | Criar/editar/duplicar/excluir, série a série, 12 técnicas, grupos (supersérie) |
| 3 — Treino ativo | 🟡 | Tela única, registro imediato, cronômetro por timestamps, descanso, notificação, registro **por lado**, **drop-set** em etapas, supersérie com descanso de rodada, finalização honesta |
| 4 — Histórico | ✅ em código | Lista, calendário do mês, detalhe só de snapshots, comparação série a série, excluir, avaliar 1–5, observação |
| 5 — Progresso | 🟡 | **PRG-01/02/03/04 prontos.** Falta só o que precisa de banco novo |
| 6+ | ⬜ | Rotinas, conquistas, backend, sync, compartilhamento, dados corporais |

### Mapa rápido do código que você provavelmente vai tocar

```
android-app/domain/.../domain/
├── session/SessionVolume.java        A §9 em código: o que conta no volume. Mexeu aqui,
│                                     mexeu em TODA tela que mostra número.
├── progress/ExercisePersonalRecords  Os recordes da §10, DERIVADOS (não há tabela)
├── progress/ExerciseProgress         Um exercício ao longo do histórico
├── progress/PeriodStatistics         O que uma semana/mês somou
├── progress/PeriodSession            Uma sessão como ponto de gráfico
├── progress/ProgressWindow           As janelas de filtro da PRG-02
└── progress/TrainingPeriod           Semana / mês / intervalo personalizado

android-app/app/.../
├── AppContainer.java                 A injeção manual. Todo repositório nasce aqui.
├── core/AppExecutors.java            diskIO + mainThread. Não existe outra thread.
├── data/local/dao/SessionDao.java    O DAO maior. MINE_SQL é o filtro de dono+não-excluído.
├── data/repository/ProgressRepository
├── ui/common/Today.java              "hoje" no fuso da PESSOA
├── ui/common/PickedDays.java         o seletor de datas fala UTC
├── ui/common/SafeNavigation.java     navegação que ignora toque que a tela não serve mais
└── ui/progress/SessionChartView.java O gráfico, desenhado em Canvas (ADR-0042)
```

---

## 4. Por onde começar

Em ordem. O primeiro item é do **dono**, não seu.

### 4.1 🔴 Bloqueio: a migração v3 → v4 no aparelho (só o dono faz)

Siga `docs/DEVICE_TESTING.md` **a partir do passo 0**, com backup antes. Enquanto isso não
acontecer, **não crie a versão 5 do banco.** Empilhar uma v5 sobre uma v4 que nunca rodou com
dados reais é apostar duas migrações de uma vez.

### 4.2 Depois que a v4 estiver provada: Fase 5, o resto

| item | o que é | por que está parado |
|---|---|---|
| **PRG-05** | registro de peso corporal + gráfico | tabelas novas → migração **v5** |
| **Histórico de recordes** (§10) | guardar **cada PR já batido**, não só o atual | tabela `personal_record` → migração **v5** |

Nota importante sobre o segundo: os recordes de **hoje** já funcionam e são **derivados** do
histórico, sem tabela nenhuma — é o que `ExercisePersonalRecords` faz. O que falta é só o
**histórico** deles ("cada novo PR é um novo registro"), que é a única pergunta que o cálculo não
consegue responder.

### 4.3 Coisas que não precisam de aparelho nem de banco novo

- **Fase 1**: ilustração do movimento por exercício (os 55 prompts já estão em
  `docs/EXERCISE_ART.md`), ampliar o catálogo, **exercícios personalizados** (o modelo já suporta:
  `exercise.owner_user_id` e `is_active`; sem migração). É o maior item que ainda não depende de
  nada do dono.
- **Fase 2**: técnica do grupo, editar grupo depois de criado e **arquivar/desarquivar** já foram
  feitos (27–28/11/2026). Sobra: técnicas de escopo **exercício** (pirâmides), e essa **precisa da
  v5** — `template_exercise` não tem coluna de técnica, então PIR e PIV seguem sem uso.
- **Fase 6 em diante**, se o dono quiser avançar em vez de fechar pendências.

### 4.4 Uma pendência de produto registrada, não resolvida

A §10 compara "a carga exata em gramas". Com isso, **12 kg por halter empata com uma barra de
12 kg**, apesar de ser o dobro do trabalho. Está implementado como a §10 está escrita. Misturar
bases no mesmo exercício é raro (normalmente são dois exercícios diferentes do catálogo), mas se
for para mudar a regra, **muda na §10 primeiro** — e isso é decisão do dono.

---

## 5. O que só o dono pode fazer

Nenhum agente consegue estes. Não marque nenhum como feito.

1. **Rodar `docs/DEVICE_TESTING.md` desde o passo 0** — em especial a migração v3 → v4 com dados
   reais, com backup antes.
2. **Criar a keystore de release e os 4 secrets do GitHub** (`docs/DEVICE_SETUP.md`). Sem a chave,
   o CI não publica APK — e isso é o comportamento certo, porque o Android recusa instalar um APK
   sem assinatura.
3. **Abrir o APK release (R8) pela primeira vez** — uma regra de `keep` faltando só aparece aí.
4. **Conferir com fonte ampliada e com TalkBack.**
5. **Revisar e mesclar o PR #1.**
6. Decidir o que foi deixado como pergunta de produto (seção 4.4) e qualquer ADR com que ele
   discorde.

---

## 6. Antes de abrir o teclado, um checklist

- [ ] Li o `AGENTS.md` inteiro.
- [ ] Li o `PRODUCT_SPEC.md` da parte que vou mexer — inclusive o que ele **se recusa** a afirmar.
- [ ] Procurei no `DECISIONS.md` se já existe um ADR sobre isto.
- [ ] Sei em que branch estou e que **não** devo empurrar para `main`.
- [ ] Rodei o portão **antes** de mudar qualquer coisa, para saber que ele já estava verde.
- [ ] Vou escrever o teste descrevendo a **regra**, e depois quebrar a regra para ver o teste
      morrer.
- [ ] Vou atualizar a documentação **no mesmo commit**.
- [ ] Não vou marcar como feito nada que eu não consiga provar aqui.
