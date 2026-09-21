# Contrato da API (Fase 8 — ainda não criado)

Esta pasta conterá `openapi.yaml`, o contrato REST compartilhado pelo app Android e pelo futuro app
iOS (Swift/SwiftUI).

## Princípios
- Versionamento no caminho: `/api/v1/...`.
- JSON; datas/horas em ISO-8601 UTC; datas locais como `YYYY-MM-DD` + fuso IANA quando relevante.
- **UUIDs gerados pelo cliente** (v7) — criação offline sem colisão.
- Cargas em **gramas** (inteiros), como no banco local.
- Sincronização por agregado com `baseVersion` (ver [docs/SYNC.md](../docs/SYNC.md)).
- A spec gerada pelo backend (springdoc) será versionada aqui, e um teste falhará se o código e o
  arquivo divergirem — assim a revisão de mudanças de API acontece por diff.
