# Atualização remota dos Electron do balcão e totem — plano de implementação

**Objetivo:** os NSIS do balcão e do totem baixam versões publicadas na KingHost e oferecem instalação por reinício confirmado pelo operador.

**Arquitetura:** `electron-updater` consulta feeds estáticos HTTPS separados em `/updates/balcao/` e `/updates/totem/`. O Nginx lê os artefatos de um volume persistente da VPS. A publicação do Electron é um comando explícito, separado do deploy de Angular/backend.

**Stack:** Electron 28 (balcão) e 44 (totem), electron-builder, NSIS, electron-updater, Nginx, Docker Compose e PowerShell/OpenSSH.

**Spec:** `docs/specs/atualizacao-electron-balcao.md`.

## Restrições

- Atualizar somente Windows NSIS; não executar no desenvolvimento nem no portable.
- Não fechar uma venda para instalar. O operador confirma o reinício.
- HTTPS e validação SHA-512 do instalador informado no feed. A publicação exige assinatura Windows válida com publicador correspondente.
- Não publicar nem instalar em clientes durante a implementação local.
- Não criar commit, push ou PR sem instrução do usuário.

## Foco da revisão

- Falha de rede durante a checagem: o app e a impressão continuam funcionando.
- Checagem manual repetida durante download: não cria downloads simultâneos.
- Atualização pronta e operador escolhe “Depois”: o menu permite instalar mais tarde.
- Arquivo ausente no volume: Nginx responde 404 em vez da SPA.
- Artefato publicado parcialmente: `latest.yml` só passa a apontar para o novo binário depois do upload completo.

## Tarefa 1: feed estático persistente

**Arquivos:** `docker-compose.prod.yml`, `config/nginx/default.conf.template`, `deploy-vps.sh`.

- [ ] Montar `./releases/electron:/usr/share/nginx/html/updates:ro` no container frontend.
- [ ] Criar `location /updates/` com `try_files $uri =404`, sem cache de metadados e sem fallback SPA no template HTTPS. Preservar `default.conf`, gerado e modificado na VPS.
- [ ] Renderizar o template HTTPS nos deploys posteriores quando o certificado já existir.
- [ ] Validar a sintaxe Nginx, quando Docker estiver disponível, e verificar que rota desconhecida não cai em `index.html`.

## Tarefa 2: atualização no balcão

**Arquivos:** `frontend/electron/package.json`, `frontend/electron/package-lock.json`, `frontend/electron/main.js`, `frontend/electron/atualizacao.js`, `frontend/electron/tests/atualizacao.test.js`.

- [ ] Testar com emissor de eventos falso: sem atualização, nova versão baixada, “Depois”, reinstalação confirmada, erro de rede e checagem concorrente.
- [ ] Adicionar `electron-updater` como dependência de produção e o provedor `generic` HTTPS em `build.publish`; criar comando de build só do NSIS.
- [ ] Implementar uma unidade pequena que inicia checagem automática ao abrir, expõe checagem manual ao menu e mantém o estado de release baixada.
- [ ] Inserir “Verificar atualizações” no menu. Antes de `quitAndInstall`, encerrar o servidor local de impressão e evitar que o handler de fechamento existente force `app.exit` no meio da atualização.
- [ ] Rodar testes Node, checagem de sintaxe e build NSIS se o ambiente permitir.

## Tarefa 2b: instalador e atualização do totem

**Arquivos:** `frontend/electron-totem/package.json`, `package-lock.json`, `main.js`, `print/index.js`, `README.md`.

- [ ] Empacotar TEF e impressão, copiando o core compartilhado do balcão para `resources/print-core` no NSIS.
- [ ] Usar Electron com Node 22+ para preservar o `WebSocket` do driver Auttar.
- [ ] Consultar `/updates/totem/` ao iniciar e a cada seis horas; disponibilizar `Ctrl+Shift+U` ao operador sem mostrar menu ao cliente.
- [ ] Confirmar a instalação somente entre vendas e ler a configuração `.env` do diretório `userData` instalado.
- [ ] Rodar testes Node, checagem de sintaxe e build NSIS; inspecionar os arquivos empacotados.

## Tarefa 3: publicação e recuperação

**Arquivos:** `frontend/electron/README-atualizacoes.md` ou documentação equivalente curta, script de publicação se reduzir passos manuais com segurança.

- [ ] Documentar primeira instalação manual do novo NSIS nas máquinas existentes.
- [ ] Documentar versão, build, teste em uma máquina, upload de `.exe` e `.blockmap`, publicação de `latest.yml` por último e verificação HTTPS, por canal.
- [ ] Documentar recuperação: retirar o feed da nova versão antes de afetar outras máquinas e publicar versão corrigida com número maior caso uma máquina já tenha atualizado.
- [ ] Registrar que o deploy normal do site não cria release do Electron.
- [ ] Documentar criação de um certificado autoassinado fora do repositório, build assinado dos dois apps e instalação do CER público em cada máquina controlada.
