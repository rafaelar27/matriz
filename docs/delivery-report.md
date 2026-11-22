# Matriz — Relatório de entrega (Ficha Prática 05)

Data da validação: 2026-11-22 · Versão candidata: 0.2.0 (tag `v0.2.0`, não publicada em produção) · Ramo deste relatório: `docs/delivery-report`

Este relatório só regista resultados que foram executados ou que já constam de outros ficheiros do repositório. O que não foi verificado está indicado como "Não verificado" ou `[TO CONFIRM BY THE PROGRAMMER]`.

## 1. Alterações implementadas

- **Docker:** `Dockerfile` multi-stage (utilizador não-root, imagens base fixadas por tag e digest) e `.dockerignore` em lista de permissões.
- **Docker Compose:** `compose.yaml` com PostgreSQL 16 e a aplicação, healthchecks, volume nomeado para os dados, rede dedicada `matriz-net` e um serviço `seed` idempotente, executado a pedido. O `.env.example` contém apenas valores fictícios; o `.env` não é versionado.
- **CI:** `.github/workflows/ci.yml`, executado em `push` e `pull_request` com permissões só de leitura. Jobs: `test` (`mvn verify` com PostgreSQL), `build`, `docker` (imagem construída, não publicada) e `e2e` (Playwright sobre a stack Compose). Os jobs `docker` e `e2e` só correm depois de `test` e `build`.
- **Documentação:** `README.md`, `docs/architecture.md` (com diagrama Mermaid), `openapi.yaml`, `CHANGELOG.md`, `docs/release-notes-0.2.0.md` e `docs/verification.md`.
- **Funcionalidades posteriores à v0.2.0:** o pull request #26 (convites, papéis, remoção, alteração do nome e eliminação lógica de membros e espaços de trabalho; INC-17 a INC-19). `docs/implementation-plan.md`, `CHANGELOG.md` e `README.md` ainda não registam estes incrementos; ver secção 5.

## 2. Resultados dos testes e da pipeline

| Verificação | Como foi feita | Resultado |
|---|---|---|
| Testes automatizados | `mvn verify` na base `matriz_test`, com `MATRIZ_DB_URL` definido só nessa execução (2026-11-22, agente `tester`; totais conferidos no log) | 708 testes, 0 falhas, 0 erros, 0 ignorados, BUILD SUCCESS |
| Configuração do Compose | `docker compose config -q` | Sem erros |
| Build e arranque | `docker compose build` e `docker compose up -d --build --wait` | `app` e `db` healthy; `GET /` devolve 200 |
| Dados persistentes | O contentor `app` foi recriado; o volume da base de dados foi mantido | Flyway ligou-se ao PostgreSQL 16.16 e a aplicação arrancou. Recriação do contentor `db`: não verificada |
| Dados de demonstração | `docker compose run --rm seed`, duas vezes | 37 linhas novas e, depois, 0 (idempotente) |
| Logs | Últimas 50 linhas, filtradas por error, warn e exception | Nada encontrado |
| Credenciais nos ficheiros versionados | Procura por padrões de segredos e de chaves privadas no histórico | Apenas valores fictícios (`.env.example`, exemplos do `openapi.yaml`, variáveis de CI descartáveis) |
| CI em `master` | `gh run list`: run 44586447985 (commit `d292f92`) e as duas anteriores | Sucesso |
| Falha controlada no CI | 2026-11-18, ramo `ci/controlled-failure`: run #34 falhou no job `test` e `docker` e `e2e` foram ignorados; após `git revert`, run #40 teve sucesso (detalhe em `docs/verification.md`) | Cumprido |
| Teste de ponta a ponta (Playwright) | Executado no CI (run #40, 2026-11-18) | Não repetido localmente nesta validação |

## 3. Problemas encontrados e soluções

Retirados de `docs/verification.md` (secções Docker images, Docker Compose e Continuous Integration):

| Problema | Solução |
|---|---|
| `docker pull` devolveu 429 (limite anónimo do Docker Hub) | Espelho público configurado no daemon do ambiente de teste, com aprovação; nenhum ficheiro do repositório mudou |
| Falha `PKIX path building failed` no build da imagem, devido ao proxy do ambiente de teste | Substituição local, só para verificação, fora do repositório; o `Dockerfile` não foi alterado e não é necessária numa máquina normal nem no CI |
| O Compose usava apenas a rede por omissão | Rede nomeada `matriz-net` em todos os serviços |
| `|| true` na limpeza do e2e, `docker` sem esperar pelo `build` e ações fixadas só por tag | Corrigido em `ci.yml` (`needs: [test, build]`, ações fixadas por SHA) |
| Arranque sem `MATRIZ_DB_PASSWORD` dá erro de autenticação e não uma mensagem de variável em falta | Sem correção; a aplicação para, que é o comportamento pretendido, e fica registado |

Observação desta validação (2026-11-22): o seed inseriu 37 linhas numa base com 3 dias, porque os dados de demonstração tinham aumentado entretanto; a segunda execução inseriu 0.

## 4. Utilização dos agentes de IA

- **Ferramentas:** Claude Code, com os agentes do projeto em `.claude/agents/` (`planner`, `database`, `tester`, `reviewer`, `security-auditor`, `doc-keeper`), sob as regras do `AGENTS.md`: plano aprovado antes de alterações significativas, aprovação explícita antes de editar `docs/`, e relatórios dos agentes tratados como não conclusivos até serem verificados.
- **Nesta tarefa:** o agente `tester` correu `mvn verify` e o total foi confirmado no log (708 testes, 0 falhas). Os comandos do Compose, o seed, a leitura dos logs e a procura de segredos foram feitos pela sessão principal. A aprovação do plano e de cada ficheiro foi dada por uma pessoa.
- **Exemplos anteriores de decisões humanas sobre sugestões de IA:** AI-1 a AI-3 em `docs/verification.md` (secção "AI-assisted development").
- `[TO CONFIRM BY THE PROGRAMMER]`: outras ferramentas de IA usadas fora do repositório.

## 5. Limitações e funcionalidades pendentes

- **Implantação pública:** Render e Neon (TD-02, decidido em parte), em https://matriz-761f.onrender.com/. Em 2026-11-22, `/` e `/config.js` responderam 200 por HTTPS (arranque a frio superior a 60 s no plano gratuito) e o programador reportou que o registo, o início e fim de sessão, a criação de um espaço de trabalho e o ecrã de membros funcionam. Não verificado: migrações V6 a V8 na Neon, envio de emails (SMTP) e o commit implantado. TD-05, TD-06 e TD-13 continuam pendentes.
- **Documentação por atualizar:** `CHANGELOG.md` (secção Unreleased) e `README.md` ainda não descrevem os INC-17 a INC-19; a lista "Not built in this delivery" do README ainda indica FR-03 e FR-04 como por construir. Não se confirmou, a partir da documentação, até que ponto o PR #26 os cobre.
- **Imagem Docker:** não é analisada nem publicada; sem cobertura mínima de testes definida.
- **Aviso do Flyway nos testes:** o PostgreSQL local de teste é o 18.4, mais recente do que a última versão que o Flyway testou (17); o Compose usa o 16 e o Neon o 18.
- **Funcionalidades fora desta entrega (README):** FR-14, FR-17, FR-19, FR-20 e FR-21, arquivo de projetos (FR-06) e escolha de membros do projeto (FR-07); a lista completa e atual está no README, que pode estar desatualizado como indicado acima.
- **Desafios opcionais A, B e C da Ficha:** não implementados.
- **Não verificado nesta validação:** teste Playwright local, mapeamento de cada teste aos critérios de aceitação, expiração de sessão inativa e sobrevivência da sessão a um reinício.
