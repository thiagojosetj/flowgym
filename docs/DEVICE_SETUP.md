# Do zero até o app rodando no Galaxy S24+

Este documento é só o **caminho até conseguir testar**: instalar o que falta no computador, ligar a
depuração no aparelho e pôr o app lá dentro. O que testar depois está em
[`DEVICE_TESTING.md`](DEVICE_TESTING.md).

Faça nesta ordem. Cada parte termina num **✅ confira** — se ele não der o resultado descrito, pare
ali em vez de seguir, porque o problema só fica mais difícil de achar depois.

Dois avisos antes de começar:

- **Instale a versão `debug`, não a `release`.** O `assembleRelease` existe para provar que o R8
  consegue compilar o app encolhido, mas esse APK **não é assinado** — não versionamos keystore
  porque o repositório é público — e um APK sem assinatura o Android recusa instalar. O `debug` é
  assinado automaticamente pelo SDK.
- **O passo 0 do `DEVICE_TESTING.md` só acontece uma vez.** Ele é o teste da migration do banco com
  os seus dados reais, e só vale enquanto o celular ainda tem a versão antiga instalada. Então
  termine este documento, e aí vá para o passo 0 **antes** de instalar a versão nova.

---

## Parte 1 — O que você precisa ter em mãos

| | |
|---|---|
| Computador | Windows, macOS ou Linux, com ~12 GB livres (Studio + SDK + caches do Gradle) |
| Cabo | o USB-C que veio com o aparelho, ou outro **cabo de dados**. Cabo de carregador barato muitas vezes não tem os fios de dados e o computador nunca vê o celular |
| Repositório | clonado no computador |
| Conta | nenhuma. Não precisa de conta Google, nem de Samsung, nem de Play Store |

Não precisa instalar Java separado: o Android Studio vem com o próprio JDK.

---

## Parte 2 — Instalar o Android Studio (é ele que traz o SDK e o `adb`)

1. Baixe em **<https://developer.android.com/studio>** e instale:
   - **Windows:** rode o `.exe` e siga o assistente.
   - **macOS:** abra o `.dmg` e arraste para *Aplicativos*.
   - **Linux:** descompacte o `.tar.gz` e rode `bin/studio.sh`.
2. Abra o Studio. No primeiro assistente, aceite o padrão e **deixe baixar o SDK** (alguns GB).
3. Instale a plataforma que este projeto compila contra: menu **More Actions → SDK Manager**, aba
   **SDK Platforms**, marque **Android 37** (pode aparecer como *37.0*) e aplique. O projeto tem
   `compileSdk = 37` e `targetSdk = 37`; sem essa plataforma o build falha na primeira tentativa.
4. Abra o projeto: **File → Open** e escolha a pasta **`android-app`**, *não* a raiz do repositório.
   A raiz não é um projeto Gradle — o Studio abriria e não acharia nada.
5. Espere o *Gradle sync* terminar (barra de progresso embaixo).

> **Se o Studio reclamar da versão do Gradle ou do plugin** ("unsupported Android Gradle Plugin
> version"), ele está mais velho que o projeto, que usa AGP 9.4.1 e Gradle 9.7.1. Atualize em
> **Help → Check for Updates**. Isso só afeta o Studio: compilar pelo terminal com `./gradlew`
> funciona independente da versão dele.

**✅ confira:** o sync termina sem erro em vermelho, e na árvore de arquivos aparecem as pastas
`app` e `domain`.

---

## Parte 3 — Conseguir usar o `adb` no terminal

O `adb` é o programa que fala com o celular pelo cabo. O Studio já o instalou, mas o terminal ainda
não sabe onde ele está. Ele fica em:

| Sistema | Caminho |
|---|---|
| Windows | `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe` |
| macOS | `~/Library/Android/sdk/platform-tools/adb` |
| Linux | `~/Android/Sdk/platform-tools/adb` |

A maneira mais simples, sem configurar nada: abra o terminal **dentro dessa pasta** e use `./adb`
(ou `.\adb.exe` no Windows). Para poder chamar de qualquer lugar, acrescente a pasta ao `PATH`:

- **Windows:** procure "variáveis de ambiente" no menu Iniciar → *Variáveis de ambiente* → em
  **Path** (do usuário) → *Novo* → cole o caminho da pasta `platform-tools`. Feche e reabra o
  terminal.
- **macOS / Linux:** acrescente ao fim do `~/.zshrc` (macOS) ou `~/.bashrc` (Linux):
  ```bash
  export PATH="$PATH:$HOME/Library/Android/sdk/platform-tools"   # macOS
  export PATH="$PATH:$HOME/Android/Sdk/platform-tools"           # Linux
  ```
  Depois `source ~/.zshrc` (ou `~/.bashrc`), ou feche e reabra o terminal.

**✅ confira:** `adb version` responde algo como `Android Debug Bridge version 1.0.41`.

---

## Parte 4 — Preparar o celular

Os nomes dos menus mudam entre versões da One UI, e eu não tenho como conferir o seu aparelho
daqui. Se um nome não bater exatamente, use a **busca (lupa) dos Ajustes** com a palavra-chave que
eu der.

### 4.1 Liberar as Opções do desenvolvedor

**Ajustes → Sobre o telefone → Informações de software** → toque **7 vezes** em **Número da
versão**. Ele pede o seu PIN e avisa "Modo de desenvolvedor ativado". Um menu novo, **Opções do
desenvolvedor**, aparece no fim dos Ajustes (às vezes dentro de *Ajustes → Primeiros socorros* ou
*Ajustes → Opções do desenvolvedor*, dependendo da versão).

### 4.2 Ligar a Depuração USB

**Ajustes → Opções do desenvolvedor → Depuração USB** → ligar. Confirme o aviso.

### 4.3 Desligar o Bloqueador automático (isto é específico da Samsung)

**Ajustes → Segurança e privacidade → Bloqueador automático** (busque por *"Bloqueador"*). Quando
está ligado, ele **bloqueia comandos pelo cabo USB** — e aí o `adb` vê o aparelho mas não consegue
instalar nada, com um erro que não explica o motivo. Desligue o Bloqueador automático, ou, se houver
a opção separada, apenas **"Bloquear comandos USB"**.

Pode religar depois de terminar os testes.

### 4.4 Conectar o cabo e autorizar

Ligue o cabo no computador. Na **primeira vez**, o celular mostra **"Permitir depuração USB?"** com
uma impressão digital RSA: marque **"Sempre permitir neste computador"** e toque **Permitir**.

Se nada aparecer, abra a notificação de USB ("Carregando este dispositivo via USB") e troque para
**Transferência de arquivos (MTP)**.

**✅ confira:** `adb devices` lista o aparelho com a palavra `device` do lado:

```
List of devices attached
R5CT30XXXXX     device
```

- `unauthorized` → o diálogo do 4.4 não foi aceito. Desconecte, reconecte e olhe a tela do celular.
- lista vazia → cabo sem fios de dados, Bloqueador automático ligado, ou falta driver no Windows
  (instale o *Samsung USB Driver for Mobile Phones*, ou o *Google USB Driver* pelo SDK Manager →
  aba **SDK Tools**).

E confira em qual API você está, porque algumas regras do Android que este projeto nunca pôde testar
valem **da API 34 em diante**:

```bash
adb shell getprop ro.build.version.sdk      # 34 = Android 14, 35 = 15, 36 = 16
```

---

## Parte 5 — Pegar o código deste trabalho

As mudanças novas (histórico, registro por lado, drop-set, supersérie, banco v4) estão numa branch,
ainda **não** na `main`:

```bash
cd caminho/para/flowgym
git fetch origin
git checkout claude/determined-mccarthy-u3ttk4
```

**✅ confira:** `git log --oneline -1` mostra um commit de `fix(ci)` ou `fix(test)`, e
`docs/DEVICE_TESTING.md` existe.

---

## Parte 6 — Instalar o app no celular

Com o aparelho conectado e aparecendo no `adb devices`, escolha **um** dos dois caminhos.

**Pelo Android Studio** (mais simples): no alto da janela, no seletor de dispositivo, escolha o seu
S24+; clique no ▶ (*Run*). Ele compila, instala e abre.

**Pelo terminal:**

```bash
cd android-app
./gradlew :app:installDebug          # Windows: gradlew.bat :app:installDebug
```

A primeira vez demora bastante (baixa Gradle e as dependências); depois fica rápido.

Se der **`SDK location not found`**, crie o arquivo `android-app/local.properties` com uma linha:

```properties
sdk.dir=/Users/seu-usuario/Library/Android/sdk
```

trocando pelo caminho da Parte 3 (no Windows use barras duplas:
`sdk.dir=C:\\Users\\seu-usuario\\AppData\\Local\\Android\\Sdk`). Esse arquivo **nunca é
versionado** — já está no `.gitignore`, e tem que continuar assim, porque guarda um caminho da sua
máquina.

**✅ confira:** o app abre no celular. Ele se instala como **`io.github.thiagojosetj.flowgym.debug`**
e aparece na gaveta de apps; se você já tinha uma versão release instalada, as duas **convivem**,
porque o id de debug termina em `.debug`.

---

## Parte 7 — Agora sim, testar

Vá para **[`DEVICE_TESTING.md`](DEVICE_TESTING.md)** e comece pelo **passo 0**, que é o backup do
banco e a migration v3 → v4 com os seus dados — o único teste que não dá para repetir.

Se você **nunca** teve o FlowGym instalado neste aparelho, não há banco antigo para migrar: pule o
passo 0, diga isso, e siga do passo 1.

---

## Parte 8 — Pegar versões novas sem cabo (configuração única)

Depois disto, toda vez que algo for para o GitHub o CI monta um APK **assinado**, e você baixa pelo
navegador do próprio celular. Nada é automático no aparelho: ninguém empurra atualização para você,
você é que vai buscar. Mas deixa de precisar de computador para isso.

### 8.1 Criar a chave de assinatura (uma vez, na sua máquina)

```bash
keytool -genkeypair -v -keystore flowgym-release.jks -storetype PKCS12   -alias flowgym -keyalg RSA -keysize 2048 -validity 10000
```

Ele pergunta uma senha e alguns dados (nome, organização, país — pode ser o que você quiser).
**Guarde a senha e faça backup do arquivo fora do GitHub.** Perder essa chave significa não
conseguir mais atualizar por cima de um APK já instalado: a única saída seria desinstalar, apagando
o histórico.

Esse arquivo **nunca** entra no repositório. O `.gitignore` já barra `*.jks`.

### 8.2 Guardar a chave como *secret* do repositório

Transforme o arquivo em texto:

```bash
base64 -w0 flowgym-release.jks > flowgym-release.b64     # Linux
base64 -i flowgym-release.jks | tr -d '\n' > flowgym-release.b64   # macOS
```

No GitHub: **Settings → Secrets and variables → Actions → New repository secret**, e crie quatro:

| Nome | Valor |
|---|---|
| `FLOWGYM_KEYSTORE_BASE64` | o conteúdo de `flowgym-release.b64` |
| `FLOWGYM_KEYSTORE_PASSWORD` | a senha do keystore |
| `FLOWGYM_KEY_ALIAS` | `flowgym` |
| `FLOWGYM_KEY_PASSWORD` | a senha da chave (igual à do keystore, se você só deu uma) |

Apague o `.b64` depois. Enquanto os secrets não existirem, nada quebra: o CI avisa no log que não
há chave, o release sai sem assinatura e o portão continua verde — é o que acontece também em
qualquer fork, que por regra do GitHub não recebe secrets.

### 8.3 Baixar no celular

1. No celular, abra o repositório no GitHub e entre em **Actions**.
2. Abra a execução mais recente que está verde, na branch que você quer.
3. Em **Artifacts**, baixe **`flowgym-apk-<sha>`** (vem num `.zip`; o nome traz o commit, então dá
   para saber qual versão é).
4. Descompacte e toque no `app-release.apk`. O Android vai pedir para permitir **"instalar apps
   desconhecidos"** para o navegador ou o gerenciador de arquivos — permita.

Baixar artefato do Actions **exige estar logado no GitHub** no navegador do celular, e o artefato
expira depois de alguns meses. Se isso incomodar, dá para publicar em *Releases*, que é link
público e não expira — me peça.

### 8.4 O que esperar no aparelho

Esse APK é o **release**, não o debug. Então:

- Ele instala como **`io.github.thiagojosetj.flowgym`**, sem o `.debug`, e **convive** com o build
  que você instala pelo cabo. São dois apps, com **bancos separados** — o treino que você registrar
  num não aparece no outro.
- Ele passou pelo **R8** (código encolhido e renomeado). Isso é bom: é a única forma de descobrir
  uma regra `keep` faltando. Também significa que, se algo quebrar só nele, o problema é do R8 e eu
  preciso do `adb logcat`.
- Atualizar é só baixar o APK novo e instalar por cima: **o banco é preservado**, porque o
  `applicationId` e a assinatura são os mesmos.

---

## Quando der errado

| O que você vê | Causa mais provável | O que fazer |
|---|---|---|
| `adb devices` vazio | cabo só de carga, ou Bloqueador automático | troque o cabo; refaça 4.3 |
| `adb devices` diz `unauthorized` | o diálogo RSA não foi aceito | reconecte e olhe a tela do celular |
| `INSTALL_FAILED_USER_RESTRICTED` | Bloqueador automático / instalação por USB bloqueada | refaça 4.3 |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | já existe um app com o mesmo id, assinado com outra chave | `adb uninstall io.github.thiagojosetj.flowgym.debug` — **isso apaga o banco**, então faça o backup do passo 0 antes |
| `SDK location not found` | falta `local.properties` | Parte 6 |
| `Unsupported Android Gradle Plugin version` | Studio mais velho que o projeto | atualize o Studio, ou compile pelo terminal |
| `Failed to install the following SDK components: platforms;android-37` | plataforma não instalada | Parte 2, item 3 |
| APK baixado: "app não instalado" | é o `app-release-unsigned.apk`, ou a chave mudou | baixe o artefato `flowgym-apk-…`, não o APK do seu próprio build; se a chave mudou, desinstale (**apaga o banco**) |
| O release abre e fecha, mas o debug funciona | regra `keep` faltando no R8 | `adb logcat -d \| grep -iE "AndroidRuntime"` e me mande o stack trace |
| O app instala mas fecha ao abrir | pode ser a migration do banco | `adb logcat -d \| grep -iE "room\|migration\|flowgym" \| tail -40` e me mande esse trecho |

Para qualquer falha que não esteja na tabela, junte o log e me mande:

```bash
adb logcat -d > logcat.txt
```
