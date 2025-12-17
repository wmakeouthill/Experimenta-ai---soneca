#!/bin/bash
# Não usar set -e aqui para poder capturar erros do Maven
set +e

echo "🚀 Iniciando backend em modo desenvolvimento..."

# ✅ Docker Compose já garante que MySQL está healthy via depends_on: condition: service_healthy
# O healthcheck do MySQL verifica se está aceitando conexões antes do backend iniciar
# Portanto, não precisamos verificar manualmente - confiamos no Docker
echo "✅ Docker Compose garantiu que MySQL está saudável (healthcheck passou)"
echo "🚀 Iniciando backend (Spring Boot tentará conectar ao MySQL automaticamente)..."

# Configurar variáveis de ambiente para Maven/Spring Boot
export MAVEN_OPTS="-Xmx512m -Xms256m"

# Executar Spring Boot em modo desenvolvimento com hot-reload
# O spring-boot-devtools detecta mudanças automaticamente
echo "🔥 Iniciando Spring Boot com hot-reload ativado..."
echo "📝 Logs e mudanças serão refletidas automaticamente"
echo "🌐 Backend disponível em: http://localhost:8080"
echo "🐛 Debug remoto disponível na porta: 5005"

# Garantir que estamos na raiz do projeto
cd /app
echo "📂 Diretório atual: $(pwd)"
echo "📋 Verificando estrutura do projeto..."
ls -la /app/ | head -20

# Primeiro, compilar todos os módulos do projeto multi-módulo
# Isso é necessário para que o Maven possa resolver as dependências
echo "📦 Compilando módulos do projeto (isso pode demorar alguns minutos na primeira vez)..."
echo "📋 Executando: mvn clean install -DskipTests -B"
BUILD_RESULT=$(mvn clean install -DskipTests -B 2>&1)
BUILD_EXIT_CODE=$?

if [ $BUILD_EXIT_CODE -ne 0 ]; then
    echo "❌ Erro: Build inicial falhou com código de saída: $BUILD_EXIT_CODE"
    echo "📋 Últimas linhas do build:"
    echo "$BUILD_RESULT" | tail -30
    echo "💡 Dica: Verifique se todos os módulos estão presentes e se há erros de compilação."
    exit 1
fi

echo "✅ Build concluído com sucesso!"

# Verificar se os módulos foram instalados corretamente
echo "✅ Verificando se os módulos foram instalados no repositório local do Maven..."
if [ ! -d "/root/.m2/repository/com/snackbar/kernel-compartilhado/1.0.0-SNAPSHOT" ]; then
    echo "⚠️ Aviso: Módulos podem não ter sido instalados corretamente"
    echo "🔄 Tentando instalar novamente sem clean..."
    mvn install -DskipTests -B || {
        echo "❌ Falha ao instalar módulos. Tentando compilar módulo por módulo..."
        mvn install -DskipTests -B -pl kernel-compartilhado || true
        mvn install -DskipTests -B -pl gestao-cardapio || true
        mvn install -DskipTests -B -pl gestao-clientes || true
        mvn install -DskipTests -B -pl gestao-pedidos || true
        mvn install -DskipTests -B -pl autenticacao || true
        mvn install -DskipTests -B -pl impressao-cupom-fiscal || true
    }
fi

# Agora executar Spring Boot usando o JAR compilado
# Isso é mais confiável que tentar executar via Maven em projeto multi-módulo
echo "🚀 Iniciando aplicação Spring Boot..."

# Definir caminho do JAR
JAR_FILE="/app/sistema-orquestrador/target/sistema-orquestrador-1.0.0-SNAPSHOT.jar"

# Verificar se o JAR existe
if [ ! -f "$JAR_FILE" ]; then
    echo "❌ Erro: JAR não encontrado em $JAR_FILE"
    echo "📋 Verificando arquivos no diretório target..."
    ls -la /app/sistema-orquestrador/target/ 2>/dev/null || echo "Diretório target não existe"
    echo "💡 Tentando recompilar apenas o módulo sistema-orquestrador..."
    cd /app/sistema-orquestrador
    mvn clean package -DskipTests -B || {
        echo "❌ Falha ao compilar módulo sistema-orquestrador"
        exit 1
    }
fi

# Verificar novamente após possível recompilação
if [ ! -f "$JAR_FILE" ]; then
    # Tentar encontrar qualquer JAR no diretório target
    JAR_FILE=$(find /app/sistema-orquestrador/target -maxdepth 1 -name "*.jar" ! -name "*-sources.jar" ! -name "*-javadoc.jar" 2>/dev/null | head -1)
    if [ -z "$JAR_FILE" ] || [ ! -f "$JAR_FILE" ]; then
        echo "❌ Erro: Não foi possível encontrar o JAR compilado"
        exit 1
    fi
fi

echo "✅ JAR encontrado: $JAR_FILE"
echo "🚀 Executando Spring Boot via JAR..."
cd /app
set -e

# ✅ Otimizações JVM para melhor performance de I/O e throughput
# G1GC: melhor para aplicações web com baixa latência
# Compressed OOPs: reduz uso de memória
# Otimizações de I/O: buffers maiores, menos syscalls
JAVA_OPTS="-Xmx512m -Xms256m \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=200 \
    -XX:+UseStringDeduplication \
    -XX:+OptimizeStringConcat \
    -XX:+UseCompressedOops \
    -XX:+UseCompressedClassPointers \
    -Djava.awt.headless=true \
    -Dfile.encoding=UTF-8 \
    -Djava.net.preferIPv4Stack=true"

# Executar Spring Boot usando o JAR
exec java \
    $JAVA_OPTS \
    -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 \
    -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-dev} \
    -Dspring.datasource.url=${DB_URL} \
    -Dspring.datasource.username=${DB_USERNAME} \
    -Dspring.datasource.password=${DB_PASSWORD} \
    -Dserver.port=${SERVER_PORT:-8080} \
    -Djwt.secret=${JWT_SECRET} \
    -Djwt.expiration=${JWT_EXPIRATION:-86400} \
    -Dlogging.level.com.snackbar=${LOG_LEVEL:-DEBUG} \
    -jar "$JAR_FILE"
