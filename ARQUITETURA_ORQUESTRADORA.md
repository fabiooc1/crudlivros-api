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

A posição define a prioridade em cada eleição: o primeiro backend elegível na lista tem preferência. Tanto `HEALTHY` quanto `DEGRADED` são elegíveis, sem preferência de um estado sobre o outro. Um líder elegível é mantido, mesmo que um backend anterior na lista se recupere. É possível adicionar instâncias sem alterar o código:

```dotenv
BACKENDS=http://api-python:8000,http://api-javascript:3000,http://api-go:8081
```

Copie `.env.example` para `.env` e preencha `BACKENDS`, por exemplo `BACKENDS=http://localhost:5001`. A aplicação carrega o `.env` do diretório de execução automaticamente. Execute pela raiz do projeto ou configure esse diretório como working directory na IDE. Use valores sem aspas e sem o prefixo `export`, pois o arquivo é lido como Java properties. Variáveis de ambiente do processo têm prioridade sobre o arquivo. No Docker Compose use `env_file: .env`.

`BACKENDS` é obrigatório e deve conter ao menos uma URL. Reinicie a aplicação após alterar o `.env`.

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
- Nas checagens periódicas e nas respostas 5xx do proxy, atingir `FAILURE_THRESHOLD` marca o nó como indisponível; um health-check que declara indisponibilidade o remove imediatamente.
- Uma falha de comunicação durante o encaminhamento remove o líder imediatamente, sem esperar o limite nem a próxima checagem periódica.
- Nesse caso, os demais backends são consultados no endpoint de saúde, na ordem de prioridade, até confirmar um candidato disponível. Uma falha nessa checagem imediata já descarta o candidato, mesmo que o estado anterior fosse saudável.
- O próximo backend elegível de maior prioridade é promovido.
- Um backend recuperado volta apenas como backup; ele não toma a liderança automaticamente.
- Quando uma requisição chega sem líder, o proxy também tenta uma checagem imediata antes de responder HTTP 503 por falta de backend elegível.
- Toda troca de líder é registrada em log com origem, destino e motivo.

Falhas HTTP funcionais, como 400 e 404, não contam como falha do backend. Respostas 5xx contam para o limite e são devolvidas ao cliente sem repetição automática. Erros de comunicação disparam a recuperação imediata.

Requisições seguras (`GET`, `HEAD` e `OPTIONS`) são repetidas no candidato confirmado. Se ele também falhar na comunicação, a busca continua, sem encaminhar a mesma requisição mais de uma vez ao mesmo nó. Escritas também disparam a eleição, mas não são repetidas automaticamente, porque o backend anterior pode ter confirmado a operação antes de a resposta se perder. Repetição segura de escritas exigiria um contrato de idempotência entre todas as APIs.

A recuperação ainda depende dos timeouts de conexão, requisição e saúde; ela dispensa esperar o próximo intervalo do agendador. Checagens imediatas e periódicas não executam simultaneamente: se já houver uma rodada em andamento, a checagem imediata aguarda sua conclusão.

## API de gerenciamento

Os endpoints da própria orquestradora usam um recurso REST versionado e não são encaminhados:

- `GET /api/v1/orchestrator`: representação atual da orquestradora, incluindo líder, estado e métricas básicas dos nós.
- `GET /api/v1/orchestrator/health`: HTTP 200 se há líder; HTTP 503 caso contrário.

Qualquer outro caminho é tratado como recurso do backend e encaminhado sem conhecimento de domínio.

## Variáveis

| Variável | Default | Descrição |
| --- | --- | --- |
| `BACKENDS` | Sem default (obrigatório) | URLs separadas por vírgula |
| `SERVER_PORT` | `8080` | Porta da API Java |
| `BACKEND_HEALTH_PATH` | `/health` | Caminho de saúde em cada backend |
| `HEALTH_CHECK_INTERVAL` | `3000` | Intervalo entre verificações, em ms |
| `HEALTH_CHECK_TIMEOUT` | `1000` | Timeout do health-check, em ms |
| `PROXY_CONNECT_TIMEOUT` | `1000` | Timeout de conexão, em ms |
| `PROXY_READ_TIMEOUT` | `3000` | Timeout de uma chamada encaminhada, em ms |
| `FAILURE_THRESHOLD` | `3` | Limite para falhas periódicas e respostas 5xx; falhas de comunicação no proxy removem imediatamente |

## Execução

Com Java 21 instalado:

```bash
cp .env.example .env
# edite BACKENDS no .env (não é necessário exportar)
./mvnw spring-boot:run
```

Exemplo de verificação:

```bash
curl http://localhost:8080/api/v1/orchestrator
curl -i http://localhost:8080/livros
```
