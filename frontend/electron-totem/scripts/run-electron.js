const { existsSync } = require('fs');
const path = require('path');
const { spawn } = require('child_process');

const appRoot = path.resolve(__dirname, '..');
const bundledElectronCli = path.resolve(
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

if (existsSync(bundledElectronCli)) {
  command = process.execPath;
  commandArgs = [bundledElectronCli, ...args];
  options = { stdio: 'inherit' };
}

const child = spawn(command, commandArgs, options);

child.on('exit', code => {
  process.exit(code ?? 0);
});

child.on('error', error => {
  console.error('Nao foi possivel iniciar o Electron do totem.');
  console.error(error.message);
  console.error('Instale as dependencias em frontend/electron ou rode com electron disponivel no PATH.');
  process.exit(1);
});
