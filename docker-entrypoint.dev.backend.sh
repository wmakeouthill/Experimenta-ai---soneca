#!/bin/bash
set +e

echo "🚀 Iniciando backend em modo desenvolvimento com HOT RELOAD..."

# ✅ Docker Compose já garante que MySQL está healthy via depends_on: condition: service_healthy
echo "✅ Docker Compose garantiu que MySQL está saudável (healthcheck passou)"

# Aguardar MySQL estar realmente pronto para conexões
echo "⏳ Verificando conexão com MySQL..."
MAX_RETRIES=30
RETRY_COUNT=0
while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
    if mysqladmin ping -h "${DB_HOST:-mysql-dev}" -u "${DB_USERNAME:-snackbar_user}" -p"${DB_PASSWORD:-dev_password}" --silent 2>/dev/null; then
        echo "✅ MySQL está pronto para conexões!"
        break
    fi
    RETRY_COUNT=$((RETRY_COUNT + 1))
    echo "   Tentativa $RETRY_COUNT/$MAX_RETRIES - Aguardando MySQL..."
    sleep 2
done

if [ $RETRY_COUNT -eq $MAX_RETRIES ]; then
    echo "⚠️ Timeout aguardando MySQL, mas continuando mesmo assim..."
fi

# Configurar variáveis de ambiente para Maven/Spring Boot
export MAVEN_OPTS="-Xmx1024m -Xms512m -XX:+UseG1GC"

# Garantir que estamos na raiz do projeto
cd /app
echo "📂 Diretório atual: $(pwd)"

# Primeiro, instalar todos os módulos no repositório local Maven
echo "📦 Instalando módulos no repositório Maven local..."
echo "📋 Executando: mvn clean install -DskipTests -B -q"

if ! mvn clean install -DskipTests -B -q; then
    echo "❌ Erro no build inicial. Mostrando detalhes..."
    mvn clean install -DskipTests -B 2>&1 | tail -50
    exit 1
fi

echo "✅ Build inicial concluído com sucesso!"

# ========================================================
# HOT RELOAD COM SPRING BOOT DEVTOOLS
# ========================================================
echo ""
echo "🔥 =============================================="
echo "🔥  INICIANDO COM HOT RELOAD (spring-boot:run)"
echo "🔥 =============================================="
echo ""
echo "📝 Como funciona o Hot Reload:"
echo "   1. Edite arquivos .java no VSCode"
echo "   2. O Spring DevTools detecta automaticamente"
echo "   3. A aplicação reinicia em ~2-5 segundos"
echo ""
echo "🌐 Backend: http://localhost:8080"
echo "🐛 Debug remoto: porta 5005"
echo ""

cd /app/sistema-orquestrador

# Executar Spring Boot com DevTools habilitado
exec mvn spring-boot:run \
    -Dspring-boot.run.jvmArguments="-Xmx512m -Xms256m -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 -Dspring.datasource.url=${DB_URL} -Dspring.datasource.username=${DB_USERNAME} -Dspring.datasource.password=${DB_PASSWORD} -Dserver.port=${SERVER_PORT:-8080} -Djwt.secret=${JWT_SECRET} -Djwt.expiration=${JWT_EXPIRATION:-86400} -Dlogging.level.com.snackbar=${LOG_LEVEL:-DEBUG} -Dspring.devtools.restart.enabled=true -Dspring.devtools.restart.poll-interval=2000 -Dspring.devtools.restart.quiet-period=1000" \
    -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-dev} \
    -B
