# Vertex AI — conta de serviço do chat IA

Lembrete de como criar a conta de serviço que o chat IA usa para chamar o Gemini
no Vertex AI. Deploy, rollback e rotação da chave estão no
[runbook](docs/runbooks/chat-ia-gemini-vertex.md).

| O quê | Valor |
| --- | --- |
| Conta de serviço | `chat-ia-vertex@<ID_DO_PROJETO>.iam.gserviceaccount.com` |
| Papel (o único) | **Usuário da Vertex AI** (`roles/aiplatform.user`) |
| API | **Vertex AI API** (`aiplatform.googleapis.com`) |
| Chave | JSON, salva como `secrets/vertex-sa.json` |
| Modelos | `gemini-3.8-flash` (principal) e `gemini-3.5-flash-lite` (fallback) |

## Onde colocar a chave JSON

| Ambiente | Caminho | Depois |
| --- | --- | --- |
| Dev com Docker | `secrets/vertex-sa.json` na raiz deste repositório (ao lado do `docker-compose.dev.yml`) | `GEMINI_PROJECT_ID=<ID_DO_PROJETO>` no `.env` |
| Produção (VPS) | `~/snackbar/secrets/vertex-sa.json` | daqui, com a chave já em `secrets/`: `.\enviar-chave-vertex.ps1 -Vps deploy@<ip da VPS>` |
| Dev fora do Docker (IDE, `mvn`) | qualquer pasta fora do repositório | defina `GOOGLE_APPLICATION_CREDENTIALS=<caminho absoluto do JSON>` |

- Os dois compose montam `./secrets` em `/run/secrets` (somente leitura) e já
  definem `GOOGLE_APPLICATION_CREDENTIALS=/run/secrets/vertex-sa.json`.
- O `enviar-chave-vertex.ps1` faz três coisas: copia a chave por SSH, dá o arquivo
  ao uid 1001 com modo 400 e põe o `GEMINI_PROJECT_ID` no `.env.prod`. O dono uid
  1001 é obrigatório, porque o container roda como `appuser` (uid 1001). Rode o
  script de novo para trocar a chave.
- `secrets/` está no `.gitignore`. **Nunca** coloque o conteúdo do JSON no `.env`,
  em log, no chat ou em commit.

Sem a chave ou sem o `GEMINI_PROJECT_ID`, o backend sobe normalmente e o chat
responde "o assistente está indisponível no momento".

## Permissões

**Na conta de serviço:** só **Usuário da Vertex AI** (`roles/aiplatform.user`).
Esse papel inclui `aiplatform.endpoints.predict`, que é o que o `generateContent`
do Gemini exige.

Não dê Editor, Proprietário, Administrador da Vertex AI nem Criador de token da
conta de serviço. Com um papel só, uma chave vazada serve apenas para gastar
cota do Vertex.

**Na sua conta (quem cria):** Proprietário do projeto, ou estes quatro papéis:

| Para | Papel |
| --- | --- |
| Ativar a API | Administrador do Service Usage (`roles/serviceusage.serviceUsageAdmin`) |
| Criar a conta de serviço | Administrador de contas de serviço (`roles/iam.serviceAccountAdmin`) |
| Dar o papel à conta | Administrador do IAM do projeto (`roles/resourcemanager.projectIamAdmin`) |
| Gerar a chave | Administrador de chaves de contas de serviço (`roles/iam.serviceAccountKeyAdmin`) |

O projeto precisa ter **faturamento ativo**.

## Pelo Console

1. Em [console.cloud.google.com](https://console.cloud.google.com), escolha o
   projeto no seletor do topo e anote o **ID do projeto** (não o nome). Esse é o
   `GEMINI_PROJECT_ID`.
2. Em **Faturamento**, confirme que o projeto está vinculado a uma conta de faturamento.
3. Em **APIs e serviços → Biblioteca**, busque **Vertex AI API** e clique em **Ativar**.
4. Em **IAM e administrador → Contas de serviço**, clique em **+ Criar conta de serviço**:
   - nome `chat-ia-vertex`, depois **Criar e continuar**;
   - papel **Usuário da Vertex AI**, depois **Continuar** e **Concluído**.
5. Na conta criada, abra a aba **Chaves → Adicionar chave → Criar nova chave**,
   escolha **JSON** e clique em **Criar**. O navegador baixa o arquivo.
6. Renomeie o arquivo para `vertex-sa.json`, coloque-o no caminho da tabela acima
   e apague a cópia da pasta Downloads.
7. Em **Faturamento → Orçamentos e alertas**, crie um orçamento com alerta. O
   `/api/chat-ia` é público, então qualquer pessoa pode consumir cota.

## Pelo terminal (gcloud)

```bash
PROJETO=seu-projeto-gcp
SA=chat-ia-vertex@$PROJETO.iam.gserviceaccount.com

gcloud config set project "$PROJETO"
gcloud services enable aiplatform.googleapis.com
gcloud iam service-accounts create chat-ia-vertex --display-name="Chat IA - Vertex AI"
gcloud projects add-iam-policy-binding "$PROJETO" \
  --member="serviceAccount:$SA" --role="roles/aiplatform.user"
gcloud iam service-accounts keys create vertex-sa.json --iam-account="$SA"
```

## Se der erro

| Sintoma | Causa | O que fazer |
| --- | --- | --- |
| Criar chave falha com "criação de chaves de conta de serviço desativada" | Política da organização `iam.disableServiceAccountKeyCreation`, ligada por padrão em organizações criadas desde maio/2024 | Em **IAM e administrador → Políticas da organização**, abra essa política e substitua por "não aplicar" só neste projeto (exige `roles/orgpolicy.policyAdmin` na organização). Projeto de conta Gmail sem organização não tem essa trava. |
| `403 PERMISSION_DENIED` | Papel ausente, API desligada ou IAM ainda propagando | Confira os passos 3–4 e espere alguns minutos |
| Log `Credenciais do Google não encontradas` | Arquivo ausente, com outro nome ou ilegível | Confira o caminho, o nome `vertex-sa.json` e o `chown 1001` na VPS |
| `404 ... Publisher Model ... not found` | ID do modelo errado ou indisponível no `global` | Veja o smoke test do [runbook §4](docs/runbooks/chat-ia-gemini-vertex.md#4-smoke-test-dos-dois-modelos-antes-do-deploy) |

## Depois de criar

- **Testar os dois modelos com a chave:** runbook §4.
- **Subir na VPS e validar:** runbook §5 e §6.
- **Trocar a chave a cada 90 dias** (ou na hora, se vazar): runbook §8.
