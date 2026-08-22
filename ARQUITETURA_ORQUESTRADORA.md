# API Java Orquestradora

## Objetivo

Esta aplicação é o único backend acessível pelo frontend. Ela não conhece os recursos de negócio e não acessa bancos de dados. Sua responsabilidade é encaminhar qualquer requisição HTTP ao backend líder e trocar de líder automaticamente quando houver falha.

Por ser um proxy genérico, novos recursos como `/autores` ou `/editoras` não exigem controllers ou services novos na aplicação Java. Os backends cadastrados precisam oferecer o mesmo contrato HTTP.

## Fluxo de uma requisição

1. O frontend chama a API Java, por exemplo `POST /livros`.
2. O `ProxyController` recebe método, caminho, query string, headers e corpo sem interpretar o recurso.
3. O `BackendRegistry` informa o líder atual.
4. O `ProxyService` monta a URL do líder e encaminha a requisição.
5. Status, corpo e headers relevantes do backend são devolvidos ao frontend.
6. A resposta inclui `X-Active-Backend`, identificando o serviço que respondeu.

Os headers HTTP de transporte (`Host`, `Content-Length`, `Connection` e equivalentes) não são copiados, pois precisam ser recalculados em cada conexão.

## Configuração dos backends

O `.env` não possui arrays nativos. Por isso, `BACKENDS` usa uma lista de URLs separadas por vírgula:

```dotenv
BACKENDS=http://api-python:8000,http://api-javascript:3000
```

A posição define a prioridade inicial. O primeiro backend saudável será escolhido na primeira eleição. É possível adicionar instâncias sem alterar o código:

```dotenv
BACKENDS=http://api-python:8000,http://api-javascript:3000,http://api-go:8081
```

Copie `.env.example` para `.env` e preencha `BACKENDS`. O Spring Boot não lê `.env` sozinho. No Docker Compose use `env_file: .env`; na execução local exporte as variáveis ou configure-as na IDE.

Se `BACKENDS` não for informado, a execução direta usa os defaults de desenvolvimento `http://localhost:8000,http://localhost:3000` definidos em `application.properties`.

## Health-check e estados

A cada `HEALTH_CHECK_INTERVAL`, todos os backends são consultados em paralelo no caminho configurado por `BACKEND_HEALTH_PATH`.

| Resposta do backend | Estado interno | Pode ser líder |
| --- | --- | --- |
| HTTP 200 e `status: ok` | `HEALTHY` | Sim |
| HTTP 200 e `status: degradado` | `DEGRADED` | Sim |
| HTTP 503 ou `status: indisponivel` | `UNAVAILABLE` imediatamente | Não |
| Timeout, conexão recusada ou resposta inválida | `UNAVAILABLE` após o limite | Não |

Antes do primeiro health-check, o estado é `UNKNOWN` e o nó ainda não pode ser líder.

O formato esperado do backend é:

```json
{
  "status": "ok",
  "servico": "api-python",
  "banco_principal": { "nome": "mysql", "conectado": true },
  "banco_replica": { "nome": "postgres", "conectado": true },
  "timestamp": "2026-08-20T13:45:00Z"
}
```

## Eleição e failover

- O líder atual é mantido enquanto permanecer elegível.
- Falhas consecutivas são contabilizadas por nó.
- Ao atingir `FAILURE_THRESHOLD`, o líder é marcado como indisponível.
- O próximo backend elegível de maior prioridade é promovido.
- Um backend recuperado volta apenas como backup; ele não toma a liderança automaticamente.
- Sem nenhum backend elegível, o proxy responde HTTP 503.
- Toda troca de líder é registrada em log com origem, destino e motivo.

Falhas HTTP funcionais, como 400 e 404, não contam como falha do backend. Respostas 5xx e erros de comunicação contam.

Requisições seguras (`GET`, `HEAD` e `OPTIONS`) podem ser repetidas uma vez no novo líder quando a falha causar uma troca. Escritas não são repetidas automaticamente após timeout, porque o backend anterior pode ter confirmado a operação antes de a resposta se perder. Repetição segura de escritas exigiria um contrato de idempotência entre todas as APIs.

## API de gerenciamento

Os endpoints da própria orquestradora usam um recurso REST versionado e não são encaminhados:

- `GET /api/v1/orchestrator`: representação atual da orquestradora, incluindo líder, estado e métricas básicas dos nós.
- `GET /api/v1/orchestrator/health`: HTTP 200 se há líder; HTTP 503 caso contrário.

Qualquer outro caminho é tratado como recurso do backend e encaminhado sem conhecimento de domínio.

## Variáveis

| Variável | Default | Descrição |
| --- | --- | --- |
| `BACKENDS` | localhost:8000 e localhost:3000 | URLs separadas por vírgula |
| `SERVER_PORT` | `8080` | Porta da API Java |
| `BACKEND_HEALTH_PATH` | `/health` | Caminho de saúde em cada backend |
| `HEALTH_CHECK_INTERVAL` | `3000` | Intervalo entre verificações, em ms |
| `HEALTH_CHECK_TIMEOUT` | `1000` | Timeout do health-check, em ms |
| `PROXY_CONNECT_TIMEOUT` | `1000` | Timeout de conexão, em ms |
| `PROXY_READ_TIMEOUT` | `3000` | Timeout de uma chamada encaminhada, em ms |
| `FAILURE_THRESHOLD` | `3` | Falhas consecutivas antes de remover um nó |

## Execução

Com Java 21 instalado:

```bash
cp .env.example .env
# edite BACKENDS e exporte as variáveis do arquivo
./mvnw spring-boot:run
```

Exemplo de verificação:

```bash
curl http://localhost:8080/api/v1/orchestrator
curl -i http://localhost:8080/livros
```
