# DevBank API: back-end bancário em Java e Spring Boot

[![CI](https://github.com/opablosilvaa21/devbank-api/actions/workflows/ci.yml/badge.svg)](https://github.com/opablosilvaa21/devbank-api/actions/workflows/ci.yml)
![Java 21](https://img.shields.io/badge/Java-21-orange)
![Spring Boot 3.5](https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen)

API REST que simula as operações centrais de um banco digital: clientes, contas, depósitos, saques,
transferências, extrato e **Pix**. Construí este projeto para estudar e praticar como um back-end bancário
lida com o que mais importa nesse domínio: **dinheiro correto, consistência sob concorrência e proteção de
dados pessoais**.

> O DevBank é um banco fictício. Todos os dados são gerados automaticamente e não correspondem a pessoas reais.

## Destaques técnicos

- **Dinheiro tratado com rigor:** valores em `BigDecimal`, sempre positivos e com no máximo 2 casas decimais.
  O sistema **recusa** valores inválidos em vez de arredondar (`shared/Money.java`).
- **Transferências seguras sob concorrência:** cada transferência roda em uma transação (`@Transactional`) e
  trava as duas contas com **lock pessimista**, sempre na **ordem crescente de ID**. Assim, A→B e B→A ao mesmo
  tempo não geram deadlock, e o saldo nunca fica negativo. Um teste dispara transferências em paralelo para
  provar isso (`TransferConcurrencyTest`).
- **Extrato consistente:** cada transferência grava dois lançamentos (saída e entrada) ligados pelo mesmo
  `transferId`. O saldo de cada conta é sempre igual à soma do seu extrato.
- **Privacidade (LGPD):** o CPF nunca sai completo pela API (`***.456.789-**`) e os logs registram só IDs e
  valores, nunca nomes, CPFs ou saldos.
- **Erros padronizados:** um `@RestControllerAdvice` converte exceções em respostas HTTP claras
  (400, 404, 409, 422), sem vazar detalhes internos.
- **Testes automatizados:** testes unitários (JUnit 5 e Mockito), testes de API com MockMvc, teste de
  concorrência e CI no GitHub Actions a cada push.

## Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21 |
| Framework | Spring Boot 3.5 (Web, Data JPA, Validation) |
| Banco de dados | H2 em memória |
| Documentação | OpenAPI e Swagger UI (springdoc) |
| Testes | JUnit 5, Mockito, MockMvc |
| Build e CI | Maven Wrapper, GitHub Actions |
| Front-end de apoio | HTML, CSS e JavaScript puro |

## Arquitetura

O código é organizado **por domínio**. Cada pacote tem seu Controller, Service, Repository e DTOs:

```
dev.practice.bank
├── account       contas, saldo e status (ATIVA, BLOQUEADA, ENCERRADA)
├── customer      clientes e busca paginada
├── transaction   depósitos, saques e extrato
├── transfer      transferências entre contas
├── pix           chaves Pix e envio de Pix
├── shared        regras comuns: Money, Cpf, exceções base
├── api           tratamento global de erros
└── config        dados fictícios e CORS
```

Fluxo de uma requisição: **Controller** (valida a entrada) → **Service** (regras de negócio e transação) →
**Repository** (JPA) → **banco**. O Service revalida os dados e não confia só na validação do Controller.

## Como executar

Pré-requisito: **JDK 21** instalado. Não é preciso instalar o Maven nem um banco de dados.

```bash
git clone https://github.com/opablosilvaa21/devbank-api.git
cd devbank-api
./mvnw spring-boot:run
```

No Windows (PowerShell), use `.\mvnw.cmd spring-boot:run`.

| O quê | Endereço |
|---|---|
| Interface web | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Console H2 | http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:bankdb`, usuário `sa`, sem senha) |

O banco é em memória: os dados são recriados toda vez que a aplicação sobe.

## Endpoints

A lista completa, com exemplos e botão "Try it out", está no Swagger UI.

| Método | Caminho | O que faz |
|---|---|---|
| GET | `/api/customers?name=&page=&size=` | Lista clientes paginados, com busca por nome |
| GET | `/api/customers/{id}` | Cliente e suas contas |
| GET | `/api/accounts` | Lista contas |
| GET | `/api/accounts/{id}` | Busca conta por ID |
| GET | `/api/accounts/{id}/balance` | Consulta saldo |
| GET | `/api/accounts/{id}/statement?from=&to=&page=&size=` | Extrato, do mais recente ao mais antigo (padrão: últimos 90 dias) |
| POST | `/api/accounts/{id}/deposits` | Depósito |
| POST | `/api/accounts/{id}/withdrawals` | Saque |
| POST | `/api/transfers` | Transferência entre contas |
| GET | `/api/accounts/{id}/pix-keys` | Chaves Pix da conta |
| POST | `/api/accounts/{id}/pix-keys` | Cadastra chave Pix (CPF, EMAIL ou ALEATORIA) |
| GET | `/api/pix/keys/{chave}` | Consulta chave Pix: nome do recebedor e CPF mascarado |
| POST | `/api/pix/transfers` | Envia Pix para uma chave |

Listas paginadas respondem `{"content": [...], "page": {"size", "number", "totalElements", "totalPages"}}`.
O tamanho máximo de página é 100.

### Exemplo: POST /api/transfers

Requisição:
```json
{ "fromAccountId": 1, "toAccountId": 2, "amount": 250.00, "description": "Aluguel" }
```

Resposta `201 Created`:
```json
{ "transferId": "ba87e5a5-...", "channel": "TRANSFERENCIA", "fromAccountId": 1, "toAccountId": 2,
  "amount": 250.00, "currency": "BRL", "description": "Aluguel", "executedAt": "2026-10-08T13:17:05Z" }
```

| Status | Quando |
|---|---|
| 201 | Transferência realizada |
| 400 | Campo ausente, valor ≤ 0, mais de 2 casas decimais, JSON malformado ou origem igual ao destino |
| 404 | Conta de origem ou de destino não existe |
| 422 | Saldo insuficiente, ou conta de origem ou destino bloqueada ou encerrada |

Erros seguem o formato `{"error": "mensagem"}`. Erros de validação incluem `"fields": {"campo": "mensagem"}`.

O Pix usa a mesma lógica da transferência, depois de descobrir a conta de destino pela chave.

## Regras de negócio

- Valores sempre positivos, com no máximo 2 casas decimais. O sistema nunca arredonda dinheiro.
- O saldo nunca fica negativo.
- Contas `BLOQUEADA` ou `ENCERRADA` não fazem depósito, saque, transferência nem Pix (HTTP 422).
- Uma conta só pode ser encerrada com saldo zero.
- A chave Pix do tipo CPF só pode ser o CPF do titular. A chave aleatória é gerada pelo banco.
- No máximo 5 chaves Pix por conta. Uma chave repetida retorna HTTP 409.

## Testes

```bash
./mvnw test
```

A suíte cobre:
- **Unidade:** regras da entidade `Account`, validação de CPF e `TransferService` com Mockito.
- **API (MockMvc + H2):** clientes, contas, extrato, transferências e Pix, incluindo os casos de erro.
- **Dados:** confere que a base gerada é coerente (saldo igual à soma do extrato).
- **Concorrência:** várias transferências simultâneas não deixam o saldo negativo nem causam deadlock.

## Dados fictícios

Ao subir, a aplicação gera uma base **100% fictícia** (`config/DemoData.java`):

- 100 clientes: 3 fixos e 97 com nomes aleatórios.
- Contas corrente e poupança em 5 agências, com status `ATIVA`, `BLOQUEADA` ou `ENCERRADA`.
- Extrato com depósito inicial, salário, pagamentos, saques, Pix e rendimento da poupança.
- Chaves Pix: todo cliente tem chave CPF; alguns também têm e-mail ou chave aleatória.

Os CPFs têm dígitos verificadores válidos, mas são números sorteados. A semente fixa faz a base sair
igual toda vez. Para mudar, edite `application.properties`:

```properties
bank.demo.customers=97   # quantidade de clientes gerados (+ 3 fixos = 100)
bank.demo.seed=42        # outra semente gera outra base
bank.demo.enabled=true   # false sobe o banco vazio
```

Contas fixas, usadas nos exemplos e nos testes:

| ID | Cliente | Saldo inicial |
|---|---|---|
| 1 | Ana Costa | R$ 1.500,00 |
| 2 | Bruno Lima | R$ 850,00 |
| 3 | Carla Souza | R$ 3.200,00 |

## Próximos passos

- [ ] Trocar o H2 por PostgreSQL com Docker Compose
- [ ] Migrações de banco com Flyway
- [ ] Autenticação com Spring Security e JWT
- [ ] Idempotência nas transferências (chave `Idempotency-Key`)

## Autor

**Pablo Araújo Silva**, estudante de Análise e Desenvolvimento de Sistemas.
[LinkedIn](https://www.linkedin.com/in/pablo-araújo-silva) · [GitHub](https://github.com/opablosilvaa21)
