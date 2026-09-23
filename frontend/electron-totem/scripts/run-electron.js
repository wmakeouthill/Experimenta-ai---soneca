const { existsSync } = require('fs');
const path = require('path');
const { spawn } = require('child_process');

const appRoot = path.resolve(__dirname, '..');
const localElectronCli = path.join(appRoot, 'node_modules', 'electron', 'cli.js');
const siblingElectronCli = path.resolve(
  __dirname,
  '..',
  '..',
  'electron',
  'node_modules',
  'electron',
  'cli.js'
);

const args = [appRoot, ...process.argv.slice(2)];

let command = 'electron';
let commandArgs = args;
let options = { stdio: 'inherit', shell: process.platform === 'win32' };

const electronCli = existsSync(localElectronCli) ? localElectronCli : siblingElectronCli;
if (existsSync(electronCli)) {
  command = process.execPath;
  commandArgs = [electronCli, ...args];
  options = { stdio: 'inherit' };
}

const child = spawn(command, commandArgs, options);

child.on('exit', code => {
  process.exit(code ?? 0);
});

child.on('error', error => {
  console.error('Nao foi possivel iniciar o Electron do totem.');
  console.error(error.message);
  console.error('Instale as dependencias em frontend/electron-totem ou rode com electron disponivel no PATH.');
  process.exit(1);
});
