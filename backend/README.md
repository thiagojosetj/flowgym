# Backend (Fase 8 — ainda não implementado)

API REST que permitirá contas, sincronização entre aparelhos (Fase 9) e compartilhamento de
treinos (Fase 10). O app Android funciona **sem** este backend (offline-first).

## Stack planejada
- Java 21 (LTS) · Spring Boot 4.1 · Spring Web · Spring Data JPA · Spring Security
- PostgreSQL · Flyway (migrations) · springdoc-openapi (spec em [`/api`](../api))
- Testes: JUnit 5, Spring Boot Test, Testcontainers (PostgreSQL real nos testes)
- Build: Maven (com Maven Wrapper) — ver ADR-0021

## Organização planejada: monólito modular
```
backend/src/main/java/io/github/thiagojosetj/gym/
├── identity/     usuários, credenciais, tokens, verificação de e-mail, recuperação de senha
├── catalog/      músculos, equipamentos, exercícios do sistema, técnicas (versionado)
├── training/     templates, sessões, recordes
├── routine/      rotinas, ajustes, metas
├── achievement/  definições e desbloqueios
├── body/         medições corporais
├── sync/         push/pull por agregado (ver docs/SYNC.md)
└── sharing/      links/códigos de compartilhamento de templates
```
Cada módulo: `web` (controllers/DTOs) → `application` (casos de uso) → `domain` → `persistence`.
Módulos só conversam por suas APIs públicas; nada de microsserviços.

## Segredos
Credenciais de banco, chaves JWT e client IDs do Google virão de variáveis de ambiente. Um
`.env.example` sem valores reais será versionado; o `.env` real é ignorado pelo Git.
