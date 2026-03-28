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

# ✅ OTIMIZAÇÃO: Ryzen 5600X + 8GB container = Maven MUITO rápido
export MAVEN_OPTS="-Xmx4096m -Xms2048m -XX:+UseG1GC -XX:+TieredCompilation -XX:TieredStopAtLevel=1 -XX:+UseStringDeduplication"

# Garantir que estamos na raiz do projeto
cd /app
echo "📂 Diretório atual: $(pwd)"

# ✅ OTIMIZAÇÃO: Build incremental (não usa clean!) com threads paralelas
# Os targets são preservados em volumes Docker, então só recompila o que mudou
echo "📦 Build incremental dos módulos (preserva compilação anterior)..."
echo "📋 Executando: mvn install -DskipTests -Dskip.frontend.build=true -T 2C -B"

# Verificar se já existe compilação anterior (volumes persistentes)
if [ -d "/app/kernel-compartilhado/target/classes" ]; then
    echo "⚡ Detectada compilação anterior - build será MUITO mais rápido!"
fi

if ! mvn install -DskipTests -Dskip.frontend.build=true -T 2C -B -q; then
    echo "❌ Erro no build. Mostrando detalhes..."
    mvn install -DskipTests -Dskip.frontend.build=true -T 2C -B 2>&1 | tail -100
    exit 1
fi

echo "✅ Build concluído com sucesso!"

# ========================================================
# HOT RELOAD COM POLLING SIMPLES (FUNCIONA NO WINDOWS!)
# ========================================================
echo ""
echo "🔥 =============================================="
echo "🔥  INICIANDO COM HOT RELOAD (OTIMIZADO!)"
echo "🔥 =============================================="
echo ""
echo "⚡ OTIMIZAÇÕES ATIVAS:"
echo "   • Build incremental (preserva compilação anterior)"
echo "   • Threads paralelas (-T 2C)"
echo "   • Cache Maven persistente"
echo "   • Monitor otimizado (detecta só arquivos novos)"
echo ""
echo "📝 Como usar o Hot Reload:"
echo "   1. Edite arquivos .java no VSCode/IntelliJ"
echo "   2. Salve o arquivo (Ctrl+S)"
echo "   3. O sistema detecta em ~5 segundos"
echo "   4. Recompila e reinicia automaticamente"
echo ""
echo "🌐 Backend: http://localhost:8080"
echo "🐛 Debug remoto: porta 5005"
echo ""

cd /app/sistema-orquestrador

# ========================================================
# MONITOR DE RECOMPILAÇÃO COM POLLING (Windows-compatible)
# ========================================================

# ✅ OTIMIZAÇÃO: Checksum apenas de arquivos modificados recentemente (muito mais rápido)
calc_checksum() {
    # Usar find com -newer para pegar apenas arquivos modificados nos últimos 30 segundos
    # Fallback para checksum completo se necessário
    find /app -name "*.java" -type f -newer /tmp/.last_check 2>/dev/null | head -20 | xargs md5sum 2>/dev/null | md5sum | cut -d' ' -f1
}

# Criar arquivo de referência para comparação temporal
touch /tmp/.last_check

# Salvar checksum inicial
LAST_CHECKSUM="initial"
echo "📊 Monitor otimizado iniciado"

# Função de monitoramento
monitor_and_recompile() {
    echo "👀 Monitor de mudanças iniciado (polling a cada 3s - Ryzen 5600X)..."

    while true; do
        sleep 3

        # Verificar se há arquivos .java modificados recentemente
        MODIFIED_FILES=$(find /app -name "*.java" -type f -newer /tmp/.last_check 2>/dev/null | head -5)

        if [ -n "$MODIFIED_FILES" ]; then
            # Detectar quais módulos foram afetados (compila só o necessário)
            MODULES=$(echo "$MODIFIED_FILES" | sed 's|/app/\([^/]*\)/.*|\1|' | sort -u | tr '\n' ',' | sed 's/,$//')

            echo ""
            echo "🔄 ============================================"
            echo "🔄 Mudança detectada nos módulos: $MODULES"
            echo "$MODIFIED_FILES" | head -3 | sed 's|/app/||g' | xargs -I {} echo "   📝 {}"
            echo "🔄 Recompilando apenas módulos afetados..."
            echo "🔄 ============================================"

            # Atualizar timestamp ANTES de compilar
            touch /tmp/.last_check

            cd /app
            # Compilar apenas módulos afetados + dependências (-pl -am)
            if mvn compile -pl "$MODULES" -am -DskipTests -Dskip.frontend.build=true -B -q -T 2C 2>&1; then
                echo "✅ Recompilação concluída ($MODULES)!"
                echo "🔄 Spring DevTools vai reiniciar a aplicação..."
            else
                echo "❌ Erro na compilação - verifique o código"
                echo "   (próxima tentativa em 5s após correção)"
            fi
            echo ""
        fi
    done
}

# Iniciar monitor em background
monitor_and_recompile &
MONITOR_PID=$!
echo "✅ Monitor de recompilação iniciado (PID: $MONITOR_PID)"
echo ""

# Executar Spring Boot com DevTools habilitado
# ⚠️ SKIP FRONTEND BUILD: frontend já roda em container separado com hot reload
# ✅ JVM otimizada para 8GB container: hot reload ultra rápido
exec mvn spring-boot:run \
    -Dskip.frontend.build=true \
    -Dspring-boot.run.jvmArguments="-Xmx2048m -Xms512m -XX:+UseG1GC -XX:+TieredCompilation -XX:TieredStopAtLevel=1 -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 -Dspring.datasource.url=${DB_URL} -Dspring.datasource.username=${DB_USERNAME} -Dspring.datasource.password=${DB_PASSWORD} -Dserver.port=${SERVER_PORT:-8080} -Djwt.secret=${JWT_SECRET} -Djwt.expiration=${JWT_EXPIRATION:-86400} -Dlogging.level.com.snackbar=${LOG_LEVEL:-DEBUG} -Dspring.devtools.restart.enabled=true -Dspring.devtools.restart.poll-interval=500 -Dspring.devtools.restart.quiet-period=200" \
    -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-dev} \
    -B
