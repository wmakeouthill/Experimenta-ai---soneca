# Atualização do Electron do balcão

O app instalado abre o Angular hospedado em `experimentaaisoneca.app`. Mudanças só nas telas ou na API entram no deploy web normal. Uma mudança em `main.js`, `preload.js` ou no código local de impressão exige uma nova versão do instalador Electron.

## Como funciona

- O app Windows instalado por NSIS consulta `https://experimentaaisoneca.app/updates/balcao/latest.yml` ao abrir e a cada seis horas. O menu **Opções → Verificar atualizações** permite consultar manualmente.
- Se houver versão maior, `electron-updater` baixa o instalador. O menu mostra a versão e a porcentagem do download, e a barra de tarefas mostra o progresso. Depois do download, o operador pode escolher **Reiniciar e instalar** após concluir as operações em andamento. Se escolher **Depois**, a atualização será instalada automaticamente no encerramento normal do app, sem outra confirmação.
- O menu mostra a versão instalada. Após uma troca de versão, o app confirma a nova versão ao abrir.
- Ao terminar o download, um aviso visual mostra a nova versão e permite iniciar a instalação após confirmar. Depois da instalação, outro aviso confirma a versão em uso.
- O instalador portable e a execução de desenvolvimento não consultam o feed.
- A primeira versão com este atualizador precisa ser instalada manualmente nas máquinas antigas. As versões seguintes podem usar o fluxo remoto.
- Quem já instalou o Balcão 1.0.2 precisa usar **Verificar atualizações → Reiniciar e instalar** uma vez para chegar à versão mais recente; a instalação automática ao sair só passa a valer depois que a nova versão estiver em execução.

## Preparar a KingHost uma vez

O `docker-compose.prod.yml` monta `releases/electron` da VPS no Nginx, fora da imagem do frontend. O `deploy-vps.sh` cria as pastas `balcao` e `totem` e regenera a configuração HTTPS quando o certificado já existe. Depois que essas mudanças forem publicadas no repositório e aplicadas na VPS, execute `./deploy-vps.sh atualizar-frontend` lá. Confirme que `https://experimentaaisoneca.app/updates/balcao/latest.yml` e `/updates/totem/latest.yml` respondem **404** antes da primeira release: uma resposta HTML da SPA indica configuração incorreta.

Os arquivos ficam em `/home/deploy/snackbar/releases/electron/{balcao,totem}/` por padrão. Se o checkout da VPS estiver em outro lugar, informe `-RemoteProjectDir` no script de publicação.

O container Certbot renova o certificado HTTPS automaticamente, mas o Nginx precisa recarregá-lo. Na VPS, o `crontab` do usuário `deploy` executa diariamente `/usr/bin/docker exec snackbar-frontend nginx -s reload`. Sem esse reload, o Nginx pode continuar servindo um certificado vencido mesmo após a renovação.

## Publicar uma nova versão

Na máquina Windows de build, dentro de `frontend/electron`:

```powershell
npm.cmd version patch --no-git-tag-version
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\build-assinado.ps1 -App balcao
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\publicar-atualizacao.ps1 -SshTarget deploy@177.153.39.90 -IdentityFile "$env:USERPROFILE\.ssh\kinghost_deploy_ed25519"
```

O build assinado cria `dist-update/kit-cliente/<nome-do-instalador>/` com EXE, CER público de mesmo nome, TXT `-LEIA-ME` de mesmo nome e o script de confiança. A senha do PFX fica somente na máquina de build. O script de publicação usa o `.exe` indicado em `latest.yml` e exige o `.blockmap` correspondente, assinatura Windows válida e versão maior que a publicada. Envia o instalador e blockmap primeiro; ativa `latest.yml` por último. Confirme via HTTPS que o feed mostra a nova versão e que o instalador baixa. Teste a atualização com **uma máquina instalada** antes de atualizar as demais.

O deploy normal (`deploy-vps.sh atualizar` ou `atualizar-frontend`) **não** gera nem publica instaladores Electron. Como a pasta é um volume persistente, esses deploys não apagam releases.

Para o totem, invoque o mesmo build assinado com `-App totem`. Os dois aplicativos têm versões e feeds independentes; consulte também [`../electron-totem/README.md`](../electron-totem/README.md).

## Assinatura e recuperação

O `publisherName` em `package.json` não assina o instalador. O `electron-updater` verifica a assinatura Windows do instalador recebido; o script de publicação recusa arquivos sem assinatura válida. Não coloque certificado privado ou senha no repositório nem na VPS.

### Certificado gratuito para as máquinas próprias

Crie **uma vez** um certificado autoassinado na máquina de build, com destino fora do repositório:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\criar-certificado-local.ps1
```

O script cria o certificado em `%LOCALAPPDATA%\ExperimentaAiSoneca\assinatura`, exporta o **PFX privado**, o **CER público** e uma senha forte em `experimenta-ai-soneca-code-signing.senha.txt`, instala a confiança no usuário da máquina de build e mostra a impressão digital. Confirme o aviso de confiança que o Windows exibir. Se o script for interrompido nessa etapa, execute-o novamente na sua sessão interativa: ele preserva o mesmo certificado. Guarde o PFX e a senha em local protegido: você precisará da mesma identidade para assinar todas as próximas versões dos dois apps. O `build-assinado.ps1` lê essa senha localmente, sem gravá-la no repositório, no comando ou no kit. Não crie outro certificado a cada release.

Em **cada PC cliente**, copie o kit do app. Abra o PowerShell como Administrador, confira a impressão digital que saiu no build e siga o TXT do kit. O comando equivalente é:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\confiar-certificado-local.ps1 -CertificadoCer '.\NOME-DO-INSTALADOR.cer' -Thumbprint 'IMPRESSAO_DIGITAL_DO_BUILD'
```

Isso confia no certificado para todos os usuários daquele PC. Instale manualmente o primeiro NSIS assinado em cada balcão e totem; as próximas versões virão pelo HTTPS. Certificados autoassinados não têm reputação pública no SmartScreen, então o Windows ainda pode mostrar aviso. Nunca desative o SmartScreen para contornar isso. Se a chave privada for perdida, será preciso distribuir e confiar em outro certificado em cada PC antes de continuar as atualizações.

Guarde o instalador anterior. Se uma release apresentar problema, interrompa sua distribuição removendo o novo `latest.yml` do feed e publique uma correção com **versão maior**: máquinas já atualizadas não fazem downgrade automático. A instalação por usuário normalmente não exige administrador; uma instalação para todos os usuários pode pedir elevação do Windows.

## Verificação local

```powershell
node --test tests/atualizacao.test.js
node --check main.js
npm.cmd run build:release:win
```

O teste de instalação remota precisa de dois NSIS instalados em sequência numa máquina de teste; executar `electron .` não testa o feed de produção.
