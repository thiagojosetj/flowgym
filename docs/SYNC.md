# Estratégia de Sincronização (desenho — implementação na Fase 9)

Status: **proposta aprovada para guiar a modelagem desde a Fase 0**. Nada de rede existe ainda,
mas o esquema local já contém o que este documento exige, para que a Fase 9 não force uma
reescrita.

## 1. Objetivos e não-objetivos

**Objetivos**
1. Registrar treinos 100% offline; a rede nunca bloqueia a UI.
2. Levar os dados de um aparelho para outro na mesma conta (celular A → servidor → celular B).
3. **Nunca perder dados silenciosamente**, inclusive em edições concorrentes.
4. Ser simples o bastante para ser implementado e testado por uma pessoa, e reimplementado no iOS.

**Não-objetivos (por enquanto)**
- Colaboração em tempo real (vários usuários editando o mesmo template).
- Sincronizar um treino **em andamento** entre aparelhos (ele vive em um aparelho até ser finalizado).
- Merge automático campo a campo de qualquer entidade (CRDT completo).

## 2. Visão geral

```
Room (fonte de verdade local)
   │  alterações marcam sync_status = PENDING
   ▼
SyncEngine (WorkManager: rede disponível + periódico + após finalizar treino)
   │  1. PUSH  agregados PENDING (com server_version base)
   │  2. PULL  mudanças com seq > cursor
   ▼
REST API /api/v1/sync  ─────▶  PostgreSQL (server_version por agregado, change_log por usuário)
```

- **Push antes de pull**, sempre no mesmo job, para que conflitos sejam detectados pelo servidor.
- WorkManager é adequado aqui: é trabalho adiável, precisa sobreviver a reinícios e depende de
  rede (constraint `CONNECTED`), com *backoff* exponencial.

## 3. Unidade de sincronização: o agregado

Sincronizamos **agregados**, não linhas soltas. Um agregado tem uma raiz (com colunas de sync) e
filhos que viajam junto.

| Agregado | Raiz | Filhos |
|---|---|---|
| Treino (template) | `workout_template` | `template_exercise`, `template_set`, `template_exercise_group` |
| Sessão | `workout_session` | `session_exercise`, `set_log`, `session_pause`, `session_exercise_group` |
| Exercício personalizado | `exercise` (com dono) | `exercise_muscle`, `exercise_equipment`, `exercise_media` |
| Rotina | `routine` | `routine_slot` (ajustes são eventos à parte, §5.4) |
| Medição corporal | `body_measurement` | `body_metric_value` |
| Meta | `goal` | — |
| Configuração | `user_setting` (por chave) | — |
| Conquista desbloqueada | `user_achievement` | — |

Por que agregados: um template com a série 3 alterada e o exercício 2 removido só faz sentido como
um todo. Enviar linhas soltas poderia aplicar metade de uma edição.

## 4. Metadados

Em cada raiz (já presentes no esquema v1): `id` (UUID do cliente), `owner_user_id`, `created_at`,
`updated_at`, `deleted_at` (tombstone), `sync_status` (`PENDING`/`SYNCED`), `server_version`
(versão do servidor em que a cópia local se baseia; `NULL` = nunca sincronizado).

No servidor: `server_version BIGINT` incrementado a cada escrita aceita e uma tabela `change_log`
com `seq BIGSERIAL` por alteração (`owner_user_id`, `entity_type`, `entity_id`, `server_version`).

Local, Fase 9: `sync_state(scope, cursor, last_success_at)`.

**Por que não `last_synced_at` em cada linha:** o cursor por escopo substitui essa informação para o
pull, e `sync_status` + `updated_at` bastam para o push. Guardar um timestamp por linha seria
redundante e mais uma fonte de inconsistência.

## 5. Protocolo

### 5.1 Push
```
POST /api/v1/sync/push
{ "changes": [ { "type": "workout_template", "id": "…", "baseVersion": 7,
                 "updatedAt": "…", "deleted": false, "payload": { …agregado completo… } } ] }
```
Para cada agregado, o servidor, em uma transação:
- `baseVersion == versão atual` → aceita, grava, `server_version + 1`, registra no `change_log`.
- `id` inexistente e `baseVersion == null` → cria (upsert idempotente por UUID; reenvio não duplica).
- `baseVersion != versão atual` → **conflito** (§6); responde com a versão do servidor.

O cliente marca `SYNCED` **somente se** `updated_at` local não mudou desde a leitura para o push
(o usuário pode ter editado durante a requisição). Caso contrário, continua `PENDING` com o novo
`server_version` como base.

### 5.2 Pull
```
GET /api/v1/sync/pull?cursor=<seq>&limit=500
→ { "changes": [ …agregados completos ou tombstones… ], "nextCursor": 1234, "hasMore": false }
```
O cliente aplica em transação: se a cópia local está `SYNCED`, substitui; se está `PENDING`, é um
conflito local (§6) — raro, porque o push roda antes.

### 5.3 Exclusões
Exclusão = tombstone (`deleted_at`) propagado como mudança. O servidor retém tombstones por
90 dias; um cliente com cursor mais antigo que isso faz **ressincronização completa**.

### 5.4 Eventos append-only
Ajustes de rotina (`routine_adjustment`), desbloqueios de conquista e PRs são **eventos
imutáveis**: o merge é a **união** por UUID. Não existe conflito possível. É por isso que a posição
do ciclo da rotina é calculada a partir de eventos, e não guardada como um contador.

## 6. Conflitos — estratégia por tipo

"Última alteração vence" (LWW) sozinho foi **rejeitado** como regra geral: com relógios de
aparelhos diferentes (possivelmente errados) e edições offline longas, ele descarta trabalho do
usuário sem avisar. A tabela abaixo mostra onde ele é aceitável e onde não é.

| Dado | Probabilidade de conflito | Estratégia | Risco aceito |
|---|---|---|---|
| Sessão concluída | Muito baixa (histórico raramente é editado) | Versão do servidor vence; a versão local perdedora vai para `sync_conflict` (recuperável) e o usuário é avisado. **Não** criamos cópia visível, para não duplicar estatísticas. | Nenhum dado perdido; resolução manual rara. |
| Template | Média (editar no celular e no tablet) | **Cópia de conflito:** a versão do servidor vira a oficial e a local é salva como novo template "Push A (conflito — Celular A)", com novo UUID. | O usuário pode ter que apagar uma cópia. Nunca perde edições. |
| Exercício personalizado | Baixa | Igual ao template. | Idem. |
| Rotina (definição) | Baixa | Igual ao template; ajustes são eventos (§5.4). | Idem. |
| Medição corporal | Muito baixa | LWW por agregado **com** aviso quando `baseVersion` diverge. | Uma correção de digitação pode ser sobrescrita; aceitável e visível. |
| Configuração (`user_setting`) | Baixa e inofensiva | LWW **por chave**, usando o `updated_at` do servidor no recebimento. | Uma preferência pode voltar ao valor do outro aparelho. |
| Eventos (ajustes, PRs, conquistas) | Nenhuma | União. | — |

Detecção sempre por `server_version` (otimista), **nunca** por comparação de relógios de aparelhos.

## 7. Identidade local → conta

1. Na primeira execução é criado um `user_profile` local (`is_local = 1`) com um UUID; todos os
   dados usam esse `owner_user_id`.
2. Ao **criar conta** nesse aparelho, o servidor cria o usuário e o cliente reescreve
   `owner_user_id` para o id do servidor em uma transação, depois faz push de tudo (`PENDING`).
3. Ao **entrar em conta existente** num aparelho que já tem dados locais: o app pergunta se deve
   mesclar os dados locais na conta. Se sim, reescreve o dono e faz push (UUIDs não colidem, então
   é uma união); se não, os dados locais ficam num perfil separado no aparelho.
4. **Logout:** os dados da conta permanecem no aparelho apenas se o usuário escolher; caso
   contrário, são apagados localmente (continuam no servidor).

## 8. Catálogo do sistema

Músculos, equipamentos, exercícios do sistema, técnicas e definições de conquistas são
**autoritativos no servidor** e versionados (`catalog_version`). O app traz uma cópia embarcada
(JSON em `assets/`) para funcionar offline desde a instalação e baixa atualizações quando houver
rede. O cliente nunca envia linhas do catálogo.

## 9. Segurança e privacidade

- Todo endpoint de sync filtra por `owner_user_id` do token — nunca por id enviado pelo cliente.
- Payloads com HTTPS; tokens armazenados com Android Keystore/EncryptedFile (Fase 8).
- Logs do servidor registram ids e contagens, **nunca** conteúdo (cargas, peso corporal, notas).

## 10. Testes planejados (Fase 9)

- Idempotência (mesmo push duas vezes → um registro).
- Edição durante push → continua `PENDING`.
- Conflito de template → cópia de conflito criada, nada perdido.
- Tombstone propagado; ressincronização completa após expiração do cursor.
- Dois aparelhos simulados contra o backend real (Testcontainers).
