# Spec: atualização remota dos Electron do balcão e totem

## 1. Objetivo

Permitir que os Electron instalados no balcão e no totem encontrem novas versões pela KingHost via HTTPS, baixem seus instaladores e ofereçam reinício para aplicar a atualização sem reinstalação manual no cliente.

## 2. Escopo

- Inclui os apps Windows instalados por NSIS em `frontend/electron` e `frontend/electron-totem`, com feeds independentes.
- Inclui “Verificar atualizações” no menu do balcão e atalho de manutenção `Ctrl+Shift+U` no totem, além da verificação automática em segundo plano.
- Inclui hospedagem estática persistente dos artefatos de release no Nginx atual, independente do build do frontend.
- Inclui um procedimento explícito de build e publicação: binário e blockmap primeiro, `latest.yml` por último.
- Não inclui o executável portable, macOS nem Linux.
- Não inclui release automática em todo deploy do site. Mudanças só no Angular/backend continuam no deploy normal.

## 3. Requisitos funcionais

- RF1: o app instalado consulta `https://experimentaaisoneca.app/updates/balcao/` sem bloquear a tela ou a impressão.
- RF2: no menu, o operador consegue verificar atualizações e ver o resultado: indisponível, disponível, erro ou pronta para instalar.
- RF3: uma versão baixada pode ser instalada após confirmação do operador ou automaticamente no encerramento normal do app; o servidor local de impressão é encerrado antes da instalação.
- RF4: a versão instalada fica no `package.json`; a publicação exige versão maior e preserva releases anteriores para recuperação.
- RF5: o servidor retorna 404 para artefatos inexistentes e não serve o `index.html` da SPA no lugar deles. Metadados de atualização não são armazenados em cache.
- RF6: o totem consulta `/updates/totem/`, baixa em segundo plano e instala no encerramento normal diário ou por confirmação do operador entre vendas; o instalador contém TEF e o core de impressão compartilhado.

## 4. Critérios de aceite

- [ ] Dado um NSIS instalado sem release nova, quando o operador usa “Verificar atualizações”, então o app informa que está atualizado.
- [ ] Dado um NSIS instalado e uma release de versão maior publicada, quando o app consulta o feed, então baixa a release e oferece “Reiniciar e instalar” ou “Depois”.
- [ ] Dada uma release baixada, quando o download termina, então cada app avisa visualmente a versão pronta; na próxima abertura após instalar, confirma a nova versão em uso.
- [ ] Dada uma release baixada, quando o operador escolhe “Depois”, então o app continua atendendo, o menu ainda permite instalar ao retornar e o encerramento normal instala automaticamente.
- [ ] Dada uma release baixada, quando o operador confirma o reinício, então o servidor de impressão é encerrado antes de chamar a instalação.
- [ ] Dada uma falha de rede ou feed inválido, quando ocorre a checagem, então o app continua funcionando e mostra erro somente se a checagem foi manual.
- [ ] Dado um caminho inexistente em `/updates/balcao/`, quando requisitado via HTTPS, então responde 404 sem fallback da SPA.
- [ ] Dado um release pronto, quando publicado, então `latest.yml` é atualizado apenas depois de o instalador e blockmap estarem disponíveis.
- [ ] Dado um NSIS do totem instalado, quando o operador usa `Ctrl+Shift+U`, então vê o estado da atualização e pode instalar a versão baixada depois de encerrar as vendas.
- [ ] Dado o instalador do totem, quando aberto, então TEF, impressão e configuração externa funcionam sem depender de arquivos do balcão na máquina cliente.

## 5. Restrições técnicas e segurança

- Reutilizar o NSIS do balcão e criar o NSIS do totem. `electron-updater` deve ser dependência de produção em ambos.
- Manter o feed sob HTTPS no domínio atual, sem token embutido no cliente nem endpoint Java novo.
- Preservar a verificação de hash do `electron-updater`. A publicação exige instalador com assinatura Windows válida e certificado cujo publicador corresponda ao configurado no pacote.
- Usar um certificado autoassinado para os dois apps: PFX e senha somente na máquina de build, CER público confiado manualmente em cada PC; nunca desativar a verificação de assinatura para contornar a falta de certificado.
- A primeira versão que contém o atualizador precisa ser instalada manualmente uma vez nas máquinas que executam a versão antiga.
- Atualização nativa não deve reiniciar automaticamente durante uma venda. Publicar só depois de testar uma máquina instalada e manter o instalador anterior para recuperação.

## 6. Contratos

- Feeds: `GET /updates/balcao/latest.yml` e `GET /updates/totem/latest.yml`.
- Artefatos: `GET /updates/<app>/<instalador>.exe` e `<instalador>.exe.blockmap`.
- Balcão: menu “Verificar atualizações”; totem: `Ctrl+Shift+U`; estado pronto oferece confirmação para reiniciar e instalar.
- Publicação é uma ação explícita separada de `deploy-vps.sh atualizar` e `atualizar-frontend`.

## 7. Casos de erro e borda

- Sem internet, feed ausente ou corrompido: registrar erro, preservar o app em execução.
- Duas checagens simultâneas: manter uma única operação e informar andamento na checagem manual.
- Executável em desenvolvimento ou portable: não executar auto atualização.
- Aplicação encerrada durante download: voltar a checar na próxima abertura.

## 8. Validação e publicação

- Testar os estados do atualizador com eventos simulados e verificar sintaxe do processo principal.
- Validar a configuração Nginx e o feed publicado antes de distribuir o primeiro instalador com atualizador.
- Publicar uma versão de teste para uma única máquina instalada antes das demais.
