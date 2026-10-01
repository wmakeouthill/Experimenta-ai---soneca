#!/bin/bash
# Regressão: git pull troca o script, mas as funções antigas continuam em memória.
set -euo pipefail
raiz=$(cd "$(dirname "$0")/.." && pwd)
cenario=$(mktemp -d)
trap 'rm -rf "$cenario"' EXIT
mkdir -p "$cenario/config/nginx" "$cenario/releases"
tr -d '\r' < "$raiz/deploy-vps.sh" > "$cenario/novo.sh"
printf 'DOMAIN=exemplo.app\nDB_ROOT_PASSWORD=teste\nDB_PASSWORD=teste\nJWT_SECRET=teste\n' > "$cenario/.env.prod"
printf 'listen 443 ssl;\n' > "$cenario/config/nginx/default.conf.template"
cat > "$cenario/comandos.sh" <<'COMANDOS'
git() {
    case "$1" in
        checkout) printf 'listen 80;\n' > config/nginx/default.conf ;;
        pull) cp novo.sh deploy-vps.sh ;;
    esac
}
docker() { return 0; }
envsubst() { cat; }
curl() {
    grep -q 'listen 443 ssl' config/nginx/default.conf
    printf 'HTTPS validado\n'
}
export -f git docker envsubst curl
COMANDOS
export BASH_ENV="$cenario/comandos.sh"
cd "$cenario"
for comando in atualizar atualizar-frontend atualizar-backend; do
    sed '/        docker exec snackbar-certbot test -s /s/.*/        false/' novo.sh > deploy-vps.sh
    bash deploy-vps.sh "$comando" > saida.log
    grep -q 'listen 443 ssl' config/nginx/default.conf
    grep -q 'HTTPS validado' saida.log
done
# Um TLS inacessível não pode terminar com uma mensagem de sucesso.
printf '\ncurl() { return 7; }\nexport -f curl\n' >> comandos.sh
if bash deploy-vps.sh atualizar > saida.log 2>&1; then
    printf 'ERRO: deploy aceitou HTTPS indisponível\n' >&2
    exit 1
fi
if grep -q 'Atualização completa' saida.log; then
    printf 'ERRO: informou sucesso antes de validar TLS\n' >&2
    exit 1
fi
printf 'OK: atualização do script preserva TLS e falha quando HTTPS não responde\n'
