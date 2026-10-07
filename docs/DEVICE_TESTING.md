# Teste em aparelho — Galaxy S24+

Tudo neste repositório foi provado em JVM (JUnit + Robolectric). **Isso não prova nada de aparelho**:
os testes usam executores sincronizados, então nenhuma consulta chega "depois", e nada do foreground
service, da notificação, do som com a tela apagada ou do teclado real roda ali. Três bugs sérios da
Fase 3 passaram exatamente por esse buraco.

Este documento é o roteiro para fechar essas pendências. A ordem importa: o **passo 0** só dá para
fazer **uma vez**, antes de instalar a versão nova.

> **Primeira vez?** Faça antes o [`DEVICE_SETUP.md`](DEVICE_SETUP.md): ele instala o Android Studio
> e o SDK, libera a depuração no aparelho e deixa o `adb` respondendo. O passo 0 logo abaixo já usa
> `adb`, então sem aquilo pronto ele não roda.

---

## 0. ANTES DE INSTALAR — a migration v3 → v4 com dados reais

Este PR sobe o banco da **versão 3 para a 4**. O teste automatizado (`MigrationOnDeviceTest`) prova
que a migration roda e que o Room valida o resultado, mas não prova nada sobre **os seus dados**.

Se você já tem o FlowGym instalado com treinos e sessões reais, faça isto primeiro:

```bash
# 1. Confirme que o app atual está instalado e qual é o applicationId
adb shell pm list packages | grep flowgym
#   release → io.github.thiagojosetj.flowgym
#   debug   → io.github.thiagojosetj.flowgym.debug   (instalam lado a lado)

# 2. Cópia de segurança do banco, por baixo do app (precisa de run-as, não de root)
adb exec-out run-as io.github.thiagojosetj.flowgym.debug \
  cat databases/gym.db > gym-v3-backup.db
adb exec-out run-as io.github.thiagojosetj.flowgym.debug \
  cat databases/gym.db-wal > gym-v3-backup.db-wal 2>/dev/null || true
```

Guarde esses arquivos. Se a migration der errado, você consegue voltar ao estado anterior:

```bash
adb shell am force-stop io.github.thiagojosetj.flowgym.debug
cat gym-v3-backup.db | adb shell run-as io.github.thiagojosetj.flowgym.debug \
  sh -c 'cat > databases/gym.db'
```

**Antes de atualizar, anote** (ou tire print): quantos treinos você tem, quantas sessões no
histórico, e o nome + o volume de uma sessão específica. É com isso que você compara depois.

### O teste em si

Instale a versão nova **por cima**, sem desinstalar (desinstalar apaga o banco e o teste perde o
sentido):

```bash
cd android-app
./gradlew :app:installDebug
```

Abra o app. **O que tem de acontecer:**

- O app abre. Não crasha.
- Todos os treinos continuam lá, com os mesmos exercícios e as mesmas séries planejadas.
- O histórico mostra as mesmas sessões, com a mesma duração e o mesmo volume que você anotou.
- Nenhum exercício aparece com rótulo de grupo (A1/A2), porque nenhum grupo existia na v3.

**Como falha:** `IllegalStateException: Migration didn't properly handle...` ou
`Room cannot verify the data integrity`, com crash ao abrir. Pegue o log:

```bash
adb logcat -d | grep -iE "room|migration|sqlite|flowgym" | tail -40
```

Se isso acontecer, **pare** e me mande esse trecho — é defeito da migration, e os seus dados ainda
estão no backup.

---

## 1. Preparar o aparelho

Depurador ligado, cabo conectado e app instalado: tudo isso está no
[`DEVICE_SETUP.md`](DEVICE_SETUP.md), passo a passo. Daqui em diante este roteiro assume que

```bash
adb devices          # lista o aparelho como "device", não "unauthorized"
```

já funciona, e que a versão `debug` está instalada (a `release` não é assinada e o Android recusa
instalá-la).

Antes de começar, anote em qual API você está:

```bash
adb shell getprop ro.build.version.sdk    # 34 (Android 14), 35 (15) ou 36 (16)
```

Esse número importa: as regras de tipo de foreground service que nunca rodaram aqui valem **da API
34 em diante**. `minSdk` do projeto é 28 e `targetSdk` é 37.

---

## 2. Testes instrumentados (automáticos, no aparelho)

São 4, e cobrem o que o Robolectric não alcança:

```bash
cd android-app
./gradlew :app:connectedDebugAndroidTest
```

| Teste | O que prova |
|---|---|
| `MigrationOnDeviceTest.migratesFromVersion1KeepingThePlannedSets` | v1 → v4 no SQLite de verdade, preservando as séries |
| `MigrationOnDeviceTest.migratesFromVersion2CreatingTheSessionTables` | v2 → v4 |
| `DeviceSmokeTest.createsATemplateWithOneExerciseAndRemovesIt` | o fluxo básico na UI real |
| `ActiveWorkoutDeviceTest.startingAWorkoutPostsTheOngoingNotificationAndSurvivesLeavingTheApp` | **o foreground service `health` sobe e a notificação aparece** |

O relatório sai em `android-app/app/build/reports/androidTests/connected/`.

O quarto é o mais importante: é o único teste automatizado que exercita
`startForeground(..., FOREGROUND_SERVICE_TYPE_HEALTH)` numa API 34+. Se ele falhar com
`MissingForegroundServiceTypeException` ou `ForegroundServiceStartNotAllowedException`, o problema é
de manifest/permissão e eu preciso ver o stack trace.

---

## 3. Roteiro manual

Faça com `adb logcat` rodando numa janela:

```bash
adb logcat | grep -iE "flowgym|ActiveWorkoutService|ForegroundService|NotificationManager|Room"
```

### 3.1 Notificação e foreground service (API 34+) — pendência antiga, a mais crítica

1. Primeira vez que você iniciar um treino, o app pede **notificações**. **Permita.**
2. Inicie um treino. Puxe a barra de status.
   - ✅ Notificação persistente com o **nome do treino** e um **cronômetro que anda**.
   - ✅ Ações **Pausar** e **Abrir**.
   - ✅ Não dá para dispensar arrastando (é ongoing).
3. Confirme que o serviço está realmente em primeiro plano:
   ```bash
   adb shell dumpsys activity services io.github.thiagojosetj.flowgym.debug | grep -iE "isForeground|foregroundServiceType|ActiveWorkoutService"
   ```
   - ✅ `isForeground=true` e o tipo `health`.
4. Toque **Pausar** na notificação → o cronômetro congela, e na tela o estado é "pausado".
5. Toque **Abrir** → volta para o treino em andamento, não para a Início.
6. Saia do app pelo gesto de voltar e espere ~2 min com a tela apagada.
   - ✅ A notificação continua lá e o cronômetro continua certo (ele é calculado de timestamps, não
     incrementado — se ficar parado ou pular, é bug).

**Como falha:** o serviço morre logo após iniciar (já aconteceu neste projeto), ou
`ForegroundServiceStartNotAllowedException` no logcat.

### 3.2 Notificação NEGADA — o caminho que não pode perder dado

1. **Ajustes → Apps → FlowGym (debug) → Notificações → desligar.**
2. Inicie um treino e registre 2 séries.
3. Feche o app (gesto de voltar) e reabra.
   - ✅ A Início mostra "você tem um treino em andamento", e as 2 séries estão lá.
   - ✅ **O pior caso é treinar sem notificação. Perder uma série é bug.** (ADR-0034)

### 3.3 Alerta de descanso com a tela apagada

1. **Ajustes do app → descanso padrão → 15 s**, som e vibração ligados.
2. Inicie um treino, confirme uma série, **apague a tela imediatamente**.
3. Espere 15 s sem tocar em nada.
   - ✅ Toca o som **e** vibra, com a tela apagada.
   - ✅ Ao acender, o descanso já terminou e **saiu da tela** (um descanso terminado que continuava
     aparecendo foi bug corrigido na Fase 3 — vale reconferir).
4. Repita no modo **Não perturbe** e anote o que acontece. Não sei dizer sem aparelho.

### 3.4 Force stop e reboot no meio do treino

1. Inicie um treino, registre 2 séries, deixe uma com valor digitado **sem confirmar**.
2. **Ajustes → Apps → FlowGym (debug) → Forçar parada.**
3. Reabra.
   - ✅ Faixa "treino em andamento" na Início; as 2 séries confirmadas estão lá.
   - ✅ O valor digitado e não confirmado também sobreviveu (ele é gravado ao sair do campo —
     ADR-0031).
4. Agora **reinicie o aparelho** com um treino em andamento e reabra.
   - ✅ Mesma coisa. A notificação **não** volta sozinha (não há BOOT_COMPLETED) — isso é esperado;
     ela volta quando você abre o treino.

### 3.5 Teclado real — inclui o que é novo neste PR

Numa sessão com muitos exercícios (monte um treino de ~8 exercícios × 5 séries):

1. Toque no campo de **carga** da primeira série.
   - ✅ Teclado numérico com **vírgula** (pt-BR). Digitar `42,5` funciona.
   - ✅ A linha que você está editando **não fica atrás do teclado**.
2. **Novo — registro por lado:** no editor, abra um exercício **unilateral** (ex.: rosca unilateral),
   marque **"por lado"**, salve, inicie o treino.
   - ✅ No lugar do campo único de reps aparecem **dois**, "E" e "D".
   - ✅ O botão **Próximo** do teclado salta de E para D; **Concluído** fecha.
   - ✅ Preencher só um lado e tocar ✓ → **recusa** com "Preencha os dois lados para concluir a
     série". Preencher os dois → conclui.
   - ✅ E 10 / D 9 e finalizar → o resumo diz **19 repetições**. Não 10, não 20.
3. **Novo — etapas de drop-set:** na linha de uma série, toque o botão **+** no cabeçalho da linha.
   - ✅ Aparece uma linha recuada "Etapa 1" com seus próprios campos.
   - ✅ Registre 40×10 na série e 30×8 / 20×6 em duas etapas, finalize.
   - ✅ O resumo diz **volume 760 kg** e **1 série feita** (não 3). É a decisão do §9.1/ADR-0037.

### 3.6 Barra de 4 abas e fonte ampliada — regressão que este PR corrige

A quarta aba (Histórico) fazia o Material esconder o rótulo das outras três. Foi corrigido com
`labelVisibilityMode="labeled"`, mas quem confirma é o aparelho.

1. Abra o app.
   - ✅ **Todas as quatro** abas mostram ícone **e** texto: Início, Treinos, Exercícios, Histórico.
2. **Ajustes → Tela → Tamanho e estilo da fonte → o maior.** Volte ao app.
   - ⚠️ A 360 dp com fonte máxima, "Exercícios" pode truncar. Não é regressão nova (o rótulo
     selecionado já truncava), mas me diga o que você vê.
3. Confira também, com fonte máxima: o resumo da sessão no histórico, a linha de série com os
   quatro botões no cabeçalho, e a folha de agrupar.

### 3.7 Novo — histórico

1. Finalize 3 sessões do mesmo treino, com cargas diferentes.
2. Aba **Histórico**:
   - ✅ Mais recentes primeiro; "Hoje"/"Ontem" nas recentes, data nas antigas.
   - ✅ Uma sessão **descartada** não aparece.
3. Abra a mais recente:
   - ✅ Duração total e efetiva, séries, repetições, volume.
   - ✅ Se alguma série ficou fora do volume (peso corporal, tempo, sem carga): a linha
     "N séries não incluídas no volume" **tem de estar lá**.
   - ✅ Comparação com a anterior: ↑ ↓ = e **percentual só quando o anterior era > 0**.
4. Teste a imutabilidade: **edite o treino** (renomeie, troque cargas, remova um exercício), volte ao
   histórico e abra a sessão antiga.
   - ✅ **Nada mudou.** Nome, exercícios e planejado continuam como eram no dia.
5. Vire o aparelho na tela de detalhe.
   - ✅ Redesenha igual, sem piscar e sem reconsultar (é carregado uma vez — ADR-0036).

### 3.8 Novo — supersérie

1. No editor de um treino com 2+ exercícios, menu ⋮ de um exercício → **"Agrupar com…"**.
   - ✅ Folha com os outros exercícios e um campo de descanso da rodada.
   - ✅ Marcar só um (ou nenhum) → recusa, nada é gravado.
2. Agrupe dois.
   - ✅ Os cards passam a ler **A1** e **A2**.
   - ✅ O descanso mostrado é o **da rodada**, não o do exercício.
3. **Salve o treino, reabra.** ✅ O grupo continua lá. (Era bug: salvar desagrupava tudo.)
4. **Duplique o treino.** ✅ A cópia também tem a supersérie. (Mesmo bug.)
5. Inicie o treino:
   - ✅ A tela mostra A1 e A2.
   - ✅ Confirme a série 1 de A1 → **não inicia descanso**.
   - ✅ Confirme a série 1 de A2 → **inicia** o descanso da rodada.
   - ✅ Faça fora de ordem (A2 antes de A1): o descanso começa quando o **segundo** terminar,
     qualquer que seja. É a regra do §6.3 como foi implementada.
6. **"Desagrupar"** → os rótulos saem e **os dois exercícios continuam no treino**.

### 3.9 TalkBack (acessibilidade)

**Ajustes → Acessibilidade → TalkBack → ligar.** Depois desligue, é cansativo.

- ✅ Na lista do histórico, a linha é anunciada com nome, data, **duração em palavras** ("58 minutos
  e 12 segundos", não "58 dois pontos 12"), exercícios e séries.
- ✅ Um exercício agrupado é anunciado "Grupo A, exercício 1: Supino", não "A1".
- ✅ Os campos por lado são anunciados "Repetições do lado esquerdo/direito", não "E"/"D".
- ✅ Na comparação, a direção é falada ("aumentou"/"diminuiu"), não só a flecha.

---

## 4. O que me mandar

Para qualquer coisa que falhar:

```bash
adb logcat -d > logcat.txt          # logo depois do problema, antes de reabrir o app
adb bugreport flowgym-bug.zip       # se foi crash ou ANR
```

E diga: qual passo, o que esperava, o que viu, e `adb shell getprop ro.build.version.release` (a
versão do Android).

## 5. Checklist

- [ ] 0. Migration v3 → v4 com dados reais, sem perder nada
- [ ] 2. `connectedDebugAndroidTest` — 4/4 verdes
- [ ] 3.1 Notificação + serviço `health` em primeiro plano
- [ ] 3.2 Notificação negada não perde série
- [ ] 3.3 Som e vibração com a tela apagada
- [ ] 3.4 Force stop e reboot
- [ ] 3.5 Teclado real, por lado, etapas de drop-set
- [ ] 3.6 Quatro abas com rótulo; fonte ampliada
- [ ] 3.7 Histórico, imutabilidade, rotação
- [ ] 3.8 Supersérie: rótulos, descanso da rodada, salvar e duplicar
- [ ] 3.9 TalkBack
