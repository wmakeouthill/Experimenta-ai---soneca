# Runbook — Chat IA com Gemini no Vertex AI

O assistente do chat (tela de pedido da mesa, cardápio) usa o **Gemini no Vertex AI**
no lugar da API da OpenAI:

- modelo principal: `gemini-3.8-flash`;
- fallback: `gemini-3.5-flash-lite` (usado quando o principal falha);
- autenticação: **conta de serviço** do GCP, com a chave JSON montada no container.

O frontend não muda: ele continua chamando `/api/chat-ia`. Toda a troca fica no
`GeminiVertexAdapter` (módulo `chat-ia`).

| Variável | Padrão | Para quê |
| --- | --- | --- |
| `GEMINI_PROJECT_ID` | vazio | Projeto GCP. Vazio = chat responde "indisponível" |
| `GEMINI_LOCATION` | `global` | Endpoint do Vertex (`global` ou região, ex.: `us-central1`) |
| `GEMINI_MODEL` | `gemini-3.8-flash` | Modelo principal |
| `GEMINI_MODELS_FALLBACK` | `gemini-3.5-flash-lite` | Fallbacks, separados por vírgula, na ordem |
| `GEMINI_MAX_TOKENS` | `4000` | `maxOutputTokens` (inclui o raciocínio do modelo) |
| `GOOGLE_APPLICATION_CREDENTIALS` | `/run/secrets/vertex-sa.json` | Fixo no compose; aponta para a chave dentro do container |

A chave fica em `./secrets/vertex-sa.json`, na pasta do deploy. Essa pasta está no
`.gitignore` e o compose a monta como somente leitura em `/run/secrets`.

> **Nunca** coloque o conteúdo da chave no `.env`, em log, no chat ou no repositório.

---

## 1. Pré-requisitos

- `gcloud` instalado e logado com uma conta que seja Owner/IAM Admin do projeto.
- Faturamento ativo no projeto.

```bash
PROJETO=seu-projeto-gcp
gcloud config set project "$PROJETO"
gcloud services enable aiplatform.googleapis.com
```

## 2. Criar a conta de serviço (só o papel necessário)

Passo a passo pelo Console e papéis de quem cria: [VERTEX-AI-CONTA-DE-SERVICO.md](../../VERTEX-AI-CONTA-DE-SERVICO.md).

```bash
SA=chat-ia-vertex@$PROJETO.iam.gserviceaccount.com

gcloud iam service-accounts create chat-ia-vertex \
  --display-name="Chat IA - Vertex AI"

gcloud projects add-iam-policy-binding "$PROJETO" \
  --member="serviceAccount:$SA" \
  --role="roles/aiplatform.user"
```

Não dê `Editor`/`Owner` à conta: se a chave vazar, `aiplatform.user` limita o estrago
ao uso do Vertex AI.

## 3. Gerar a chave e levar para a VPS

Primeiro, veja se a organização bloqueia chaves de conta de serviço:

```bash
gcloud resource-manager org-policies describe \
  iam.disableServiceAccountKeyCreation --project="$PROJETO" --effective
```

Se aparecer `enforced: true`, a criação da chave falha. Um admin da organização
precisa abrir exceção para este projeto. A VPS não roda no GCP, por isso não há
alternativa sem chave (metadata server) aqui.

```bash
gcloud iam service-accounts keys create vertex-sa.json --iam-account="$SA"
```

Coloque o arquivo em `secrets/vertex-sa.json` na raiz do repositório local e envie
para a VPS com o script da raiz (PowerShell):

```powershell
.\enviar-chave-vertex.ps1 -Vps deploy@<ip da VPS>
```

O script:

- copia a chave por SSH para `~/snackbar/secrets/vertex-sa.json`, passando por uma
  pasta temporária `700`;
- aplica dono `1001:1001` (o `appuser` do container) e modo `400`. Sem esse dono, o
  backend não lê a chave. Quem aplica é um container `alpine` descartável, porque o
  `sudo` da VPS pede senha e o usuário `deploy` está no grupo `docker`;
- acrescenta `GEMINI_PROJECT_ID` ao `.env.prod`, lido do `project_id` da chave, se
  ainda não estiver lá.

A chave não passa por git, `.env` nem log.

> Se `secrets/vertex-sa.json` não existir, a aplicação sobe normalmente e o chat
> responde "indisponível". O log mostra `Credenciais do Google não encontradas`.

## 4. Smoke test dos dois modelos (antes do deploy)

Este passo valida o ID de cada modelo, a disponibilidade no endpoint `global` e o
payload exato que o adapter envia (`thinkingLevel: LOW`, sem `temperature`).
Rode na máquina onde a chave foi gerada:

```bash
TOKEN=$(GOOGLE_APPLICATION_CREDENTIALS=./vertex-sa.json gcloud auth application-default print-access-token)

for MODELO in gemini-3.8-flash gemini-3.5-flash-lite; do
  echo "== $MODELO"
  curl -s -X POST \
    -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
    "https://aiplatform.googleapis.com/v1/projects/$PROJETO/locations/global/publishers/google/models/$MODELO:generateContent" \
    -d '{
      "systemInstruction": {"parts": [{"text": "Você é o Soneca. Responda em uma frase."}]},
      "contents": [{"role": "user", "parts": [{"text": "Oi, o que vocês vendem?"}]}],
      "generationConfig": {"maxOutputTokens": 1024, "thinkingConfig": {"thinkingLevel": "LOW"}}
    }' | head -c 600
  echo
done
```

Esperado: `candidates[0].content.parts[].text` com a resposta, para os **dois** modelos.

| Erro | Causa provável | Ação |
| --- | --- | --- |
| `404 ... Publisher Model ... not found` | ID errado ou modelo indisponível no `global` | Confira o ID no Model Garden e ajuste `GEMINI_MODEL`/`GEMINI_MODELS_FALLBACK` |
| `400` citando `thinking_level` | Modelo não aceita `LOW` | Ajuste `NIVEL_RACIOCINIO` no `GeminiVertexAdapter` (exige nova imagem) |
| `403 PERMISSION_DENIED` | Papel ausente ou API desligada | Refaça os passos 1–2 |
| `429 RESOURCE_EXHAUSTED` | Cota | Peça aumento de cota; o fallback cobre picos |

O ID `gemini-3.5-flash-lite` não pôde ser confirmado na documentação durante a
implementação. Se ele der 404, o chat continua funcionando só com o principal, mas
fica sem fallback. Corrija `GEMINI_MODELS_FALLBACK` antes de seguir.

## 5. Deploy na VPS

O deploy passa por `main`, com build na sua máquina (o GitHub Actions não builda mais
no push). Na VPS, **antes** de atualizar, guarde a imagem atual para ter rollback:

```bash
cd ~/snackbar
git rev-parse HEAD > .pre-gemini-commit
docker tag ghcr.io/wmakeouthill/snackbar-backend:latest ghcr.io/wmakeouthill/snackbar-backend:pre-gemini
```

O script do passo 3 já pôs `GEMINI_PROJECT_ID` no `.env.prod`. As outras variáveis
`GEMINI_*` têm padrão no compose e só entram no `.env.prod` para mudar o valor.
Mantenha o `OPENAI_API_KEY` até o Gemini estabilizar, porque o rollback usa essa chave.

Atualize com `main` em dia, na raiz do repositório, na sua máquina. O script builda e
publica as imagens no GHCR, faz backup do banco, roda `deploy-vps.sh atualizar` na VPS
e espera o backend ficar `healthy`:

```powershell
.\deploy-vps.ps1 -Vps deploy@<ip da VPS>
```

## 6. Validar

Em produção o log de `com.snackbar` fica em `WARN`, por isso a prova é a própria
resposta do chat:

```bash
docker compose -f docker-compose.prod.yml exec backend \
  wget -qO- --header='Content-Type: application/json' \
  --post-data='{"message":"Quais lanches vocês têm?","sessionId":"runbook-teste"}' \
  http://localhost:8080/api/chat-ia
```

- ✅ Uma resposta sobre o cardápio: o Gemini está respondendo.
- ❌ "o assistente está indisponível no momento": veja os logs:

```bash
docker compose -f docker-compose.prod.yml logs --since 10m backend \
  | grep -E "GEMINI_PROJECT_ID|Credenciais do Google|Modelo .* falhou|Nenhum modelo do Vertex"
```

| Log | Causa |
| --- | --- |
| `GEMINI_PROJECT_ID não definido` | Variável ausente no `.env.prod` |
| `Credenciais do Google não encontradas` | Chave ausente ou ilegível: rode de novo o `enviar-chave-vertex.ps1` (passo 3) |
| `Modelo gemini-3.8-flash falhou: status 4xx: ...` | Mensagem do Vertex; confira a tabela do passo 4 |

Por fim, teste pela tela de pedido da mesa: abra o chat e faça uma pergunta sobre o cardápio.

### Testar o fallback (opcional)

Com `GEMINI_MODEL=modelo-inexistente` no `.env.prod`, rode
`docker compose -f docker-compose.prod.yml up -d --no-deps backend`. O chat precisa
continuar respondendo, e o log mostra `Modelo modelo-inexistente falhou: status 404`.
Depois volte o valor para `gemini-3.8-flash` e suba de novo.

## 7. Rollback (voltar para a OpenAI)

A imagem antiga só funciona com o compose antigo, que ainda repassa as variáveis `OPENAI_*`:

```bash
cd ~/snackbar
git checkout "$(cat .pre-gemini-commit)" -- docker-compose.prod.yml
TAG=pre-gemini docker compose -f docker-compose.prod.yml up -d --no-deps backend
```

Para voltar ao Gemini, rode `git checkout HEAD -- docker-compose.prod.yml` e depois
`bash ./deploy-vps.sh atualizar`. Depois de alguns dias estável, remova `OPENAI_API_KEY`
do `.env.prod`, revogue a chave no painel da OpenAI e apague a tag local
(`docker rmi ghcr.io/wmakeouthill/snackbar-backend:pre-gemini`).

## 8. Rotação e revogação da chave

Faça a rotação a cada 90 dias, ou na hora se houver suspeita de vazamento:

```bash
gcloud iam service-accounts keys create vertex-sa-nova.json --iam-account="$SA"
# Substitua secrets/vertex-sa.json local pela nova e rode .\enviar-chave-vertex.ps1 (passo 3)
# Na VPS (a chave é lida na subida):
docker compose -f docker-compose.prod.yml restart backend
# Valide (passo 6), depois apague a chave antiga:
gcloud iam service-accounts keys list --iam-account="$SA"
gcloud iam service-accounts keys delete ID_DA_CHAVE_ANTIGA --iam-account="$SA"
```

Em caso de vazamento, apague a chave **primeiro**: o chat fica indisponível até a
nova chave entrar, e isso é aceitável.

## 9. Custos e LGPD

- `/api/chat-ia` é público (`permitAll`), então qualquer pessoa pode consumir cota.
  Configure um **alerta de orçamento** no Billing do projeto.
- O prompt leva nome e histórico de pedidos do cliente logado. O endpoint `global`
  **não garante residência de dados**. A exposição é a mesma que existia com a
  OpenAI, mas se for preciso manter os dados em uma região, use
  `GEMINI_LOCATION=<região>` e confirme que os dois modelos existem nela (passo 4
  com a URL regional `https://<região>-aiplatform.googleapis.com/...`).

## 10. Desenvolvimento local

O `docker-compose.dev.yml` monta `./secrets` do mesmo jeito. Há duas opções:

- **Chave de conta de serviço:** copie uma chave de dev para `secrets/vertex-sa.json`
  e defina `GEMINI_PROJECT_ID` no `.env`.
- **Sua própria conta:** rode `gcloud auth application-default login`, depois
  `gcloud auth application-default set-quota-project $PROJETO`, e copie o
  `application_default_credentials.json` gerado para `secrets/vertex-sa.json`.
  O arquivo fica em `%APPDATA%\gcloud\` no Windows e em `~/.config/gcloud/` no Linux/macOS.

Sem nada disso, o backend sobe normalmente e o chat responde "indisponível".
