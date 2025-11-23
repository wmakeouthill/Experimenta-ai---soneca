#!/bin/bash
# Script de deploy para Google Cloud Run

set -e

# Cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}🚀 Deploy do Snackbar App para Google Cloud Run${NC}"

# Verificar se gcloud está instalado
if ! command -v gcloud &> /dev/null; then
    echo -e "${RED}❌ gcloud CLI não está instalado. Instale em: https://cloud.google.com/sdk/docs/install${NC}"
    exit 1
fi

# Verificar se está autenticado
if ! gcloud auth list --filter=status:ACTIVE --format="value(account)" | grep -q .; then
    echo -e "${YELLOW}⚠️  Você não está autenticado. Fazendo login...${NC}"
    gcloud auth login
fi

# Solicitar PROJECT_ID se não fornecido
if [ -z "$1" ]; then
    echo -e "${YELLOW}📋 Projetos disponíveis:${NC}"
    gcloud projects list --format="table(projectId,name)"
    echo ""
    read -p "Digite o PROJECT_ID: " PROJECT_ID
else
    PROJECT_ID=$1
fi

# Configurar projeto
echo -e "${GREEN}⚙️  Configurando projeto: ${PROJECT_ID}${NC}"
gcloud config set project $PROJECT_ID

# Habilitar APIs necessárias
echo -e "${GREEN}🔧 Habilitando APIs necessárias...${NC}"
gcloud services enable \
    cloudbuild.googleapis.com \
    run.googleapis.com \
    secretmanager.googleapis.com \
    containerregistry.googleapis.com

# Verificar se secrets existem, se não, criar
echo -e "${GREEN}🔐 Configurando secrets...${NC}"

# DB Password
if ! gcloud secrets describe db-password --project=$PROJECT_ID &> /dev/null; then
    echo -e "${YELLOW}⚠️  Secret 'db-password' não existe. Criando...${NC}"
    read -sp "Digite a senha do banco de dados: " DB_PASS
    echo ""
    echo -n "$DB_PASS" | gcloud secrets create db-password \
        --data-file=- \
        --replication-policy="automatic" \
        --project=$PROJECT_ID
    echo -e "${GREEN}✅ Secret 'db-password' criado${NC}"
else
    echo -e "${GREEN}✅ Secret 'db-password' já existe${NC}"
fi

# JWT Secret
if ! gcloud secrets describe jwt-secret --project=$PROJECT_ID &> /dev/null; then
    echo -e "${YELLOW}⚠️  Secret 'jwt-secret' não existe. Criando...${NC}"
    read -sp "Digite o JWT secret (mínimo 256 bits): " JWT_SECRET
    echo ""
    echo -n "$JWT_SECRET" | gcloud secrets create jwt-secret \
        --data-file=- \
        --replication-policy="automatic" \
        --project=$PROJECT_ID
    echo -e "${GREEN}✅ Secret 'jwt-secret' criado${NC}"
else
    echo -e "${GREEN}✅ Secret 'jwt-secret' já existe${NC}"
fi

# Dar permissão ao Cloud Build para acessar secrets
echo -e "${GREEN}🔑 Configurando permissões...${NC}"
PROJECT_NUMBER=$(gcloud projects describe $PROJECT_ID --format="value(projectNumber)")
gcloud secrets add-iam-policy-binding db-password \
    --member="serviceAccount:${PROJECT_NUMBER}@cloudbuild.gserviceaccount.com" \
    --role="roles/secretmanager.secretAccessor" \
    --project=$PROJECT_ID

gcloud secrets add-iam-policy-binding jwt-secret \
    --member="serviceAccount:${PROJECT_NUMBER}@cloudbuild.gserviceaccount.com" \
    --role="roles/secretmanager.secretAccessor" \
    --project=$PROJECT_ID

# Build da imagem
echo -e "${GREEN}🏗️  Fazendo build da imagem Docker...${NC}"
gcloud builds submit \
    --tag gcr.io/$PROJECT_ID/snackbar-app:latest \
    --project=$PROJECT_ID

# Deploy no Cloud Run
echo -e "${GREEN}🚀 Fazendo deploy no Cloud Run...${NC}"
gcloud run deploy snackbar-app \
    --image gcr.io/$PROJECT_ID/snackbar-app:latest \
    --region us-central1 \
    --platform managed \
    --allow-unauthenticated \
    --memory 2Gi \
    --cpu 2 \
    --timeout 300 \
    --max-instances 10 \
    --port 8080 \
    --set-secrets="DB_PASSWORD=db-password:latest,JWT_SECRET=jwt-secret:latest" \
    --set-env-vars="DB_HOST=localhost,DB_PORT=3306,DB_NAME=snackbar_db,DB_USERNAME=snackbar_user,SERVER_PORT=8080,JWT_EXPIRATION=86400,SHOW_SQL=false,LOG_LEVEL=INFO" \
    --project=$PROJECT_ID

# Obter URL do serviço
SERVICE_URL=$(gcloud run services describe snackbar-app \
    --region us-central1 \
    --format="value(status.url)" \
    --project=$PROJECT_ID)

echo ""
echo -e "${GREEN}✅ Deploy concluído com sucesso!${NC}"
echo -e "${GREEN}🌐 URL do serviço: ${SERVICE_URL}${NC}"
echo ""
echo -e "${YELLOW}⚠️  IMPORTANTE:${NC}"
echo -e "${YELLOW}   - O MySQL está embarcado na imagem (dados não persistem entre reinicializações)${NC}"
echo -e "${YELLOW}   - Para produção, considere usar Cloud SQL${NC}"
echo -e "${YELLOW}   - A primeira inicialização pode levar alguns minutos${NC}"

