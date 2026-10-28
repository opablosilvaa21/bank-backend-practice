# DevBank API

[![CI](https://github.com/opablosilvaa21/bank-backend-practice/actions/workflows/ci.yml/badge.svg)](https://github.com/opablosilvaa21/bank-backend-practice/actions/workflows/ci.yml)
![Java 21](https://img.shields.io/badge/Java-21-orange)
![Spring Boot 3.5](https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen)
![PostgreSQL 17](https://img.shields.io/badge/PostgreSQL-17-336791)
![Kafka 4](https://img.shields.io/badge/Kafka-4%20(KRaft)-231F20)
![Testes](https://img.shields.io/badge/testes-JUnit%205%20%7C%20Mockito%20%7C%20MockMvc-blue)

API REST de um banco digital fictício, feita em **Java 21 e Spring Boot 3.5**. Ela cobre clientes, contas,
depósitos, saques, transferências, extrato e **Pix**.

O foco do projeto é o que mais importa em um back-end bancário:

- **dinheiro correto**: nada de `double` e nada de arredondamento silencioso;
- **consistência sob concorrência**: o saldo nunca fica negativo, mesmo com transferências simultâneas;
- **nada de pagar duas vezes**: pedidos repetidos com a mesma `Idempotency-Key` não movimentam dinheiro de novo;
- **mensageria confiável**: cada transferência gera um evento no Kafka via **Transactional Outbox**, consumido
  por um consumidor idempotente com dead letter topic;
- **proteção de dados pessoais (LGPD)**: o CPF sai mascarado e os logs não carregam dados sensíveis.

> O DevBank é um banco fictício. Todos os dados são gerados automaticamente e não correspondem a pessoas reais.

## Sumário

- [Destaques técnicos](#destaques-técnicos)
- [Stack](#stack)
- [Arquitetura](#arquitetura)
- [Como executar](#como-executar)
- [Endpoints](#endpoints)
- [Mensageria](#mensageria)
- [Regras de negócio](#regras-de-negócio)
- [Decisões técnicas](#decisões-técnicas)
- [Testes](#testes)
- [Dados fictícios](#dados-fictícios)
- [Próximos passos](#próximos-passos)

## Destaques técnicos

| Desafio | Como foi resolvido |
|---|---|
| Precisão monetária | Valores em `BigDecimal`, sempre positivos e com no máximo 2 casas decimais. Valores inválidos são **recusados**, nunca arredondados ([`Money.java`](src/main/java/dev/practice/bank/shared/Money.java)). |
| Concorrência | Cada transferência roda em uma transação e trava as duas contas com **lock pessimista** (`SELECT ... FOR UPDATE`), sempre em **ordem crescente de ID**. A→B e B→A ao mesmo tempo não geram deadlock. |
| Integridade do extrato | Cada transferência grava dois lançamentos (débito e crédito) ligados pelo mesmo `transferId`. O saldo é sempre igual à soma do extrato. O banco reforça com `CHECK (balance >= 0)`. |
| Idempotência | Cabeçalho `Idempotency-Key` em transferências e Pix: repetir o pedido devolve a resposta original sem movimentar de novo, mesmo com as repetições chegando em paralelo. |
| Mensageria confiável | O evento da transferência é gravado no **outbox na mesma transação** do débito e do crédito. Um relay publica no Kafka; o consumidor deduplica por `eventId` e manda mensagens com defeito para a **DLT**. |
| Schema versionado | Flyway com migrations SQL; o Hibernate só valida (`ddl-auto=validate`). |
| Privacidade (LGPD) | O CPF nunca sai completo pela API (`***.456.789-**`). Os logs registram só IDs e valores, nunca nomes, CPFs ou saldos. |
| Erros previsíveis | Um `@RestControllerAdvice` converte exceções em respostas HTTP padronizadas (400, 404, 409, 422) sem expor stack traces. |
| Qualidade | 99 testes: unitários, de API, de concorrência e de mensageria, todos num PostgreSQL real e num Kafka embutido. CI no GitHub Actions a cada push. |

## Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21 |
| Framework | Spring Boot 3.5 (Web, Data JPA, Validation) |
| Banco de dados | PostgreSQL 17 (instalado ou via Docker Compose), schema versionado com Flyway |
| Mensageria | Apache Kafka 4 (modo KRaft, sem ZooKeeper) com Spring Kafka |
| Documentação | OpenAPI 3 e Swagger UI (springdoc) |
| Testes | JUnit 5, Mockito, MockMvc, Kafka embutido (spring-kafka-test), Awaitility |
| Build e CI | Maven Wrapper, GitHub Actions |
| Interface de demonstração | HTML, CSS e JavaScript puro |

## Arquitetura

O código é organizado **por domínio** (package by feature). Cada pacote reúne seu Controller, Service,
Repository e DTOs:

```
dev.practice.bank
├── account       contas, saldo e status (ATIVA, BLOQUEADA, ENCERRADA)
├── customer      clientes e busca paginada
├── transaction   depósitos, saques e extrato
├── transfer      transferências entre contas, Idempotency-Key e o evento TransferCompleted
├── pix           chaves Pix e envio de Pix
├── notification  avisos de "você enviou / você recebeu" e o consumidor do Kafka
├── messaging     outbox, relay, tópicos e tratamento de erros do Kafka
├── shared        regras comuns: Money, Cpf, exceções base
├── api           tratamento global de erros
└── config        dados fictícios, CORS e @Async
```

Uma requisição passa por **Controller** (valida a entrada) → **Service** (regras de negócio e transação) →
**Repository** (Spring Data JPA) → **banco**. O Service revalida os dados e não depende só da validação do
Controller, porque também é chamado internamente (o Pix, por exemplo, reaproveita o núcleo da transferência).

### Fluxo de uma transferência

```mermaid
sequenceDiagram
    participant C as Cliente
    participant TC as TransferController
    participant TS as TransferService
    participant DB as Banco

    C->>TC: POST /api/transfers (Idempotency-Key opcional)
    TC->>TC: Bean Validation (@Valid)
    TC->>TS: transfer(request, key)
    Note over TS,DB: @Transactional
    TS->>TS: Money.requireValidAmount()
    TS->>DB: SELECT ... FOR UPDATE (menor ID)
    TS->>DB: SELECT ... FOR UPDATE (maior ID)
    TS->>DB: chave já usada? devolve a transferência original
    TS->>TS: contas ativas? saldo suficiente?
    TS->>DB: INSERT transfers + débito + crédito (mesmo transferId)
    TS->>DB: INSERT outbox_events (com mensageria ligada)
    TS->>DB: UPDATE saldos (no commit)
    TS-->>TC: TransferResult
    TC-->>C: 201 Created
```

## Como executar

**Pré-requisitos:** JDK 21 e um PostgreSQL 17 na porta 5432, pelo Docker ou instalado na máquina.
Não é preciso instalar o Maven.

**1. Banco de dados**, escolha uma opção:

- **Com Docker:** `docker compose up -d` sobe o PostgreSQL do [`compose.yaml`](compose.yaml) já com o
  banco `bank`, o usuário `bank` e a senha `bank`.
- **PostgreSQL instalado** (no Windows: `winget install PostgreSQL.PostgreSQL.17`): crie o usuário e o banco
  uma única vez, entrando como `postgres`:

  ```sql
  CREATE ROLE bank LOGIN PASSWORD 'bank';
  CREATE DATABASE bank OWNER bank;
  ```

**2. Aplicação:**

```bash
git clone https://github.com/opablosilvaa21/bank-backend-practice.git
cd bank-backend-practice
./mvnw spring-boot:run
```

No Windows (PowerShell), use `.\mvnw.cmd spring-boot:run`. As tabelas são criadas ao subir.

| Recurso | Endereço |
|---|---|
| Interface web | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| PostgreSQL | `localhost:5432`, banco `bank`, usuário `bank`, senha `bank` |

Os dados ficam salvos no PostgreSQL e continuam lá depois que a aplicação para. A base fictícia só é gerada
quando o banco está vazio. Para consultar o banco pelo terminal: `psql -h localhost -U bank -d bank`
(com Docker: `docker exec -it postgres psql -U bank -d bank`).

Para começar com uma base nova: `docker compose down -v` (o `-v` apaga o volume) ou, no PostgreSQL instalado,
`DROP DATABASE bank;` seguido de `CREATE DATABASE bank OWNER bank;`.

A conexão pode ser trocada pelas variáveis de ambiente `DB_URL`, `DB_USERNAME` e `DB_PASSWORD`.

### Teste rápido

```bash
# Saldo da conta 1
curl http://localhost:8080/api/accounts/1/balance

# Transferência de R$ 250,00 da conta 1 para a conta 2
curl -X POST http://localhost:8080/api/transfers \
  -H "Content-Type: application/json" \
  -d '{"fromAccountId": 1, "toAccountId": 2, "amount": 250.00, "description": "Aluguel"}'
```

## Endpoints

A documentação completa, com exemplos e o botão "Try it out", está no Swagger UI.

### Clientes e contas

| Método | Caminho | Descrição |
|---|---|---|
| GET | `/api/customers?name=&page=&size=` | Lista clientes paginados, com busca por nome |
| GET | `/api/customers/{id}` | Cliente e suas contas |
| GET | `/api/accounts` | Lista contas |
| GET | `/api/accounts/{id}` | Busca conta por ID |
| GET | `/api/accounts/{id}/balance` | Consulta saldo |
| GET | `/api/accounts/{id}/notifications?page=&size=` | Avisos de transferências e Pix enviados e recebidos |

### Movimentações

| Método | Caminho | Descrição |
|---|---|---|
| POST | `/api/accounts/{id}/deposits` | Depósito |
| POST | `/api/accounts/{id}/withdrawals` | Saque |
| GET | `/api/accounts/{id}/statement?from=&to=&page=&size=` | Extrato, do mais recente ao mais antigo (padrão: últimos 90 dias) |
| POST | `/api/transfers` | Transferência entre contas (aceita `Idempotency-Key`) |

### Pix

| Método | Caminho | Descrição |
|---|---|---|
| GET | `/api/accounts/{id}/pix-keys` | Chaves Pix da conta |
| POST | `/api/accounts/{id}/pix-keys` | Cadastra chave Pix (`CPF`, `EMAIL` ou `ALEATORIA`) |
| GET | `/api/pix/keys/{chave}` | Consulta chave: nome do recebedor e CPF mascarado |
| POST | `/api/pix/transfers` | Envia Pix para uma chave (aceita `Idempotency-Key`) |

Listas paginadas respondem `{"content": [...], "page": {"size", "number", "totalElements", "totalPages"}}`,
com no máximo 100 itens por página.

### Exemplo: `POST /api/transfers`

Requisição:

```json
{ "fromAccountId": 1, "toAccountId": 2, "amount": 250.00, "description": "Aluguel" }
```

Resposta `201 Created`:

```json
{
  "transferId": "ba87e5a5-...",
  "channel": "TRANSFERENCIA",
  "fromAccountId": 1,
  "toAccountId": 2,
  "amount": 250.00,
  "currency": "BRL",
  "description": "Aluguel",
  "executedAt": "2026-10-08T13:17:05Z"
}
```

| Status | Quando |
|---|---|
| 201 | Transferência realizada (ou repetida: veja Idempotency-Key abaixo) |
| 400 | Campo ausente, valor ≤ 0, mais de 2 casas decimais, JSON malformado, origem igual ao destino ou Idempotency-Key inválida |
| 404 | Conta de origem ou de destino não existe |
| 422 | Saldo insuficiente, conta bloqueada ou encerrada, ou Idempotency-Key já usada com outros dados |

Os erros seguem o formato `{"error": "mensagem"}`. Erros de validação trazem também
`"fields": {"campo": "mensagem"}`.

O Pix reaproveita o mesmo núcleo da transferência, depois de resolver a conta de destino pela chave.

### Idempotency-Key: repetir sem pagar duas vezes

Se a resposta se perde (timeout, queda de rede), o cliente não sabe se a transferência aconteceu. Para poder
repetir com segurança, ele envia um cabeçalho `Idempotency-Key` com um valor único por operação (um UUID, por
exemplo). Vale para `POST /api/transfers` e `POST /api/pix/transfers`. O cabeçalho é opcional.

```bash
curl -i -X POST http://localhost:8080/api/transfers   -H "Content-Type: application/json" -H "Idempotency-Key: 7f9c1e2a-pedido-1"   -d '{"fromAccountId": 1, "toAccountId": 2, "amount": 250.00}'
```

| Situação | Resultado |
|---|---|
| Chave nova | Transfere e responde `201` |
| Mesma chave e mesmos dados | **Não transfere de novo.** Responde `201` com o mesmo corpo da primeira vez e `Idempotent-Replayed: true` |
| Mesma chave com outros dados | `422`: a chave pertence a outra operação |
| Mesma chave chegando em paralelo | Uma executa; as outras esperam o lock das contas e recebem a resposta repetida |

A chave fica na tabela `transfers` (coluna `UNIQUE`), junto com um SHA-256 dos dados do pedido. Ela é conferida
depois do `SELECT ... FOR UPDATE` das contas, e por isso pedidos repetidos em paralelo não passam juntos
([`TransferConcurrencyTest`](src/test/java/dev/practice/bank/transfer/TransferConcurrencyTest.java)).

## Mensageria

Toda transferência (TED interna ou Pix) gera o evento `TransferCompleted`. Hoje ele vira as notificações
"Você enviou R$ 50,00 para Bruno Lima por Pix" e "Você recebeu R$ 50,00 de Ana Costa por Pix". Qualquer outro
serviço pode assinar o mesmo tópico depois (antifraude, extrato por e-mail, analytics).

```mermaid
flowchart LR
    API[POST /api/transfers] --> TS[TransferService]
    subgraph tx[Uma transação no PostgreSQL]
        TS --> T[(transfers + lançamentos)]
        TS --> O[(outbox_events)]
    end
    O -->|relay a cada 1 s<br/>FOR UPDATE SKIP LOCKED| K{{Kafka<br/>bank.transfers}}
    K --> C[TransferNotificationConsumer]
    C -->|mesma transação| P[(processed_events)]
    C -->|mesma transação| N[(notifications)]
    C -. erro sem solução .-> D{{bank.transfers.dlt}}
```

| Peça | O que garante |
|---|---|
| [`TransferOutboxWriter`](src/main/java/dev/practice/bank/messaging/TransferOutboxWriter.java) | O evento é gravado no outbox **na mesma transação** da transferência. Ou os dois são confirmados, ou nenhum. |
| [`OutboxRelay`](src/main/java/dev/practice/bank/messaging/OutboxRelay.java) | Publica os pendentes em ordem e só marca como publicado depois da confirmação do Kafka. Com o Kafka fora do ar, o evento espera (com as tentativas registradas) e é publicado quando ele voltar. |
| [`TransferNotificationConsumer`](src/main/java/dev/practice/bank/notification/TransferNotificationConsumer.java) | Grava o `eventId` em `processed_events` com `ON CONFLICT DO NOTHING` na mesma transação das notificações: mensagem repetida é ignorada. |
| [`MessagingConfig`](src/main/java/dev/practice/bank/messaging/MessagingConfig.java) | Erro transitório: novas tentativas com intervalo. Mensagem inválida (JSON quebrado, versão desconhecida): direto para a DLT. Esgotou as tentativas: DLT, com o erro nos cabeçalhos. |

A entrega é **pelo menos uma vez** (at-least-once): o relay pode publicar a mesma mensagem duas vezes se cair
entre a confirmação do Kafka e o commit. O consumidor idempotente garante que o **efeito** aconteça uma vez só.

### Dois modos

A mensageria é opcional (`bank.messaging.enabled`), para a aplicação continuar subindo sem Kafka:

| Modo | Caminho do evento |
|---|---|
| Desligada (padrão) | `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` cria as notificações na própria aplicação ([`TransferNotificationListener`](src/main/java/dev/practice/bank/notification/TransferNotificationListener.java)). Simples, mas se a aplicação cair logo após o commit, o aviso se perde. |
| Ligada | Outbox → relay → Kafka → consumidor idempotente, como no diagrama acima. Nada se perde. |

### Rodando com Kafka

**1. Kafka**, escolha uma opção:

- **Com Docker:** `docker compose up -d` também sobe o Kafka 4.4 do [`compose.yaml`](compose.yaml).
- **Kafka instalado** (roda na JVM, sem Docker): baixe o binário em
  [kafka.apache.org/downloads](https://kafka.apache.org/downloads) e extraia. No `config/server.properties`,
  troque o `log.dirs` (o padrão é `/tmp`) e, para o broker ouvir só a sua máquina, use
  `listeners=PLAINTEXT://localhost:9092,CONTROLLER://localhost:9093`. No Windows os scripts ficam em
  `bin\windows\*.bat`:

  ```powershell
  # uma vez só: gera o ID do cluster e formata o armazenamento (modo KRaft)
  $id = .\bin\windows\kafka-storage.bat random-uuid
  .\bin\windows\kafka-storage.bat format --standalone -t $id -c config\server.properties

  # sempre que for usar (Ctrl+C para parar)
  .\bin\windows\kafka-server-start.bat config\server.properties
  ```

**2. Aplicação com a mensageria ligada:**

```powershell
$env:BANK_MESSAGING_ENABLED = "true"
.\mvnw.cmd spring-boot:run
```

Os tópicos são criados ao subir. Para ver as mensagens chegando:

```powershell
.\bin\windows\kafka-console-consumer.bat --bootstrap-server localhost:9092 --topic bank.transfers --from-beginning --property print.key=true
```

No banco, os eventos pendentes no outbox:
`SELECT event_id, attempts, last_error FROM outbox_events WHERE published_at IS NULL;`

## Regras de negócio

- Valores sempre positivos, com no máximo 2 casas decimais. O sistema nunca arredonda dinheiro.
- O saldo nunca fica negativo.
- Contas `BLOQUEADA` ou `ENCERRADA` não fazem depósito, saque, transferência nem Pix (HTTP 422).
- Uma conta só pode ser encerrada com saldo zero (regra da entidade `Account`).
- A chave Pix do tipo CPF só pode ser o CPF do titular. A chave aleatória é gerada pelo banco.
- Cada conta tem no máximo 5 chaves Pix. Uma chave já cadastrada retorna HTTP 409.
- Toda transferência e todo Pix geram um aviso para quem enviou e outro para quem recebeu. Uma transferência
  desfeita (rollback) ou repetida com a mesma `Idempotency-Key` não gera aviso.

## Decisões técnicas

**Por que lock pessimista e não otimista?** Em transferências, o conflito é esperado (a mesma conta pode
receber e enviar ao mesmo tempo). Com lock otimista (`@Version`), o cliente receberia erros e precisaria
repetir a operação. O lock pessimista serializa só as operações que tocam as mesmas contas. Travar sempre na
mesma ordem (menor ID primeiro) elimina o risco de deadlock.

**Por que recusar em vez de arredondar?** Se `10.005` virasse `10.01` sem aviso, centavos apareceriam ou
sumiriam sem registro. Recusar com HTTP 400 obriga o cliente a enviar o valor exato.

**Por que o Service revalida o que o Controller já validou?** A validação do Controller (Bean Validation)
protege só a entrada HTTP. O Service é o dono da regra e também é chamado por outros fluxos, como o Pix.

**Por que dois lançamentos por transferência?** É o princípio das partidas dobradas: todo débito tem um
crédito correspondente. O `transferId` em comum permite auditar e reconciliar as duas pontas.

**Por que a Idempotency-Key é conferida depois do lock?** Se fosse antes, duas cópias do mesmo pedido chegando
juntas veriam "chave nova" e as duas transfeririam. Depois do `SELECT ... FOR UPDATE`, a segunda cópia espera a
primeira terminar e já encontra a chave gravada. O `UNIQUE` na coluna é a última barreira.

**Por que outbox e não publicar direto no Kafka?** Banco e Kafka não fazem commit juntos. Publicar antes do
commit pode anunciar uma transferência que sofreu rollback; publicar depois pode perder o evento se a aplicação
cair no meio. Com o outbox, o evento é só mais uma linha da mesma transação, e o relay cuida do resto.

**Por que o listener sem Kafka é `@Async`?** O `AFTER_COMMIT` roda antes de a conexão voltar ao pool. Gravar a
notificação ali pedia uma segunda conexão por transferência e, com 10 transferências em paralelo, o pool do
Hikari esgotava e tudo travava. O teste de concorrência pegou isso.

**Por que não Testcontainers?** O ambiente de desenvolvimento deste projeto não usa Docker. Os testes rodam num
PostgreSQL real (local ou de serviço no CI) e num Kafka embutido na JVM. Assim nenhum teste depende de um H2
fingindo ser PostgreSQL.

## Testes

Os testes usam um PostgreSQL de verdade, no banco `bank_test` (separado do `bank` da aplicação). Crie-o uma
vez com `CREATE DATABASE bank_test OWNER bank;`. Com Docker, rode antes
`docker exec postgres createdb -U bank bank_test`. Outra conexão pode ser passada por `TEST_DB_URL`.

```bash
./mvnw test
```

Cada contexto de teste apaga o schema, roda as migrations do Flyway e gera a base fictícia de novo
([`TestDatabaseConfig`](src/test/java/dev/practice/bank/TestDatabaseConfig.java)). Os testes de API usam
`@Transactional` e desfazem tudo no fim. Os de concorrência, eventos e mensageria fazem commits de verdade, por
isso cada um roda num schema próprio. Os de mensageria sobem um Kafka dentro da própria JVM
([`@MessagingTest`](src/test/java/dev/practice/bank/messaging/MessagingTest.java)): não é preciso instalar nada.

| Tipo | O que cobre |
|---|---|
| Unidade | Regras da entidade `Account`, validação de CPF e `TransferService` com Mockito (idempotência e evento incluídos) |
| API (MockMvc + PostgreSQL) | Clientes, contas, extrato, transferências, Pix e `Idempotency-Key`, incluindo os casos de erro |
| Dados | A base gerada é coerente (saldo igual à soma do extrato) |
| Concorrência | Transferências simultâneas não deixam saldo negativo nem causam deadlock; 10 pedidos com a mesma chave em paralelo viram uma transferência só ([`TransferConcurrencyTest`](src/test/java/dev/practice/bank/transfer/TransferConcurrencyTest.java)) |
| Eventos | Notificação só depois do commit; rollback e repetição não geram aviso |
| Mensageria (Kafka embutido) | Outbox na mesma transação, relay publicando, Kafka fora do ar, ponta a ponta, mensagem repetida, JSON inválido, versão desconhecida e erro com retry + DLT |

O GitHub Actions roda a suíte inteira a cada push e em cada pull request, com um PostgreSQL 17 de serviço.

## Dados fictícios

Ao subir, a aplicação gera uma base **100% fictícia**
([`DemoData.java`](src/main/java/dev/practice/bank/config/DemoData.java)):

- 100 clientes: 3 fixos e 97 com nomes aleatórios.
- Contas corrente e poupança em 5 agências, com status `ATIVA`, `BLOQUEADA` ou `ENCERRADA`.
- Extrato com depósito inicial, salário, pagamentos, saques, Pix e rendimento da poupança.
- Chaves Pix: todo cliente tem chave CPF, e alguns também têm e-mail ou chave aleatória.

Os CPFs têm dígitos verificadores válidos, mas são números sorteados. A semente fixa gera sempre a mesma
base. Para mudar, edite o `application.properties`:

```properties
bank.demo.customers=97   # quantidade de clientes gerados (+ 3 fixos = 100)
bank.demo.seed=42        # outra semente gera outra base
bank.demo.enabled=true   # false sobe o banco vazio
```

Como a base só é gerada com o banco vazio, depois de mudar esses valores recrie o banco (veja
[Como executar](#como-executar)).

Contas fixas, usadas nos exemplos e nos testes:

| ID | Cliente | Saldo inicial |
|---|---|---|
| 1 | Ana Costa | R$ 1.500,00 |
| 2 | Bruno Lima | R$ 850,00 |
| 3 | Carla Souza | R$ 3.200,00 |

## Próximos passos

- [x] Trocar o H2 por PostgreSQL com Docker Compose
- [x] Versionar o schema com Flyway
- [x] Rodar os testes num PostgreSQL real
- [x] Idempotência nas transferências (cabeçalho `Idempotency-Key`)
- [x] Evento `TransferCompleted` com `@TransactionalEventListener`
- [x] Kafka (KRaft) com Transactional Outbox
- [x] Consumidor idempotente com dead letter topic
- [ ] Autenticação com Spring Security e JWT (cada cliente só movimenta as próprias contas)
- [ ] Cadastro de clientes e abertura, bloqueio e encerramento de contas pela API
- [ ] Limpeza dos eventos antigos do outbox e expiração das chaves de idempotência
- [ ] Observabilidade: Spring Boot Actuator, métricas do outbox (pendentes, tentativas) e da DLT

## Autor

**Pablo Araújo Silva**, estudante de Análise e Desenvolvimento de Sistemas.

[LinkedIn](https://www.linkedin.com/in/pablo-araújo-silva) · [GitHub](https://github.com/opablosilvaa21)
