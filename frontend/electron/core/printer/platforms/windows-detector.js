/**
 * Detector de Impressoras - Windows
 * Responsabilidade: Listar impressoras no Windows usando PowerShell
 */

const { executarComando } = require('../../../utils/exec-utils');

/**
 * Lista impressoras no Windows usando PowerShell
 * @returns {Promise<Array<{name: string, devicePath: string, status: string, padrao: boolean, tipo: string}>>}
 */
async function listarImpressorasWindows() {
  // Combina as consultas: o WMI pode listar algumas impressoras e omitir outras.
  const porNome = new Map();
  for (const consulta of ['Get-WmiObject -Class Win32_Printer', 'Get-Printer -ErrorAction Stop']) {
    try {
      const comando = `powershell -NoProfile -NonInteractive -Command "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8; ${consulta} | Select-Object Name, PortName, Default, Status, PrinterStatus | ConvertTo-Json -Compress"`;
      const { stdout } = await executarComando(comando, { timeout: 5000, maxBuffer: 1024 * 1024 });
      const impressoras = JSON.parse(stdout.trim() || '[]');
      const lista = Array.isArray(impressoras) ? impressoras : [impressoras];
      const validas = lista.filter(imp => imp && imp.Name);
      if (validas.length === 0) continue;

      for (const imp of validas) {
        const chave = imp.Name.toLowerCase();
        if (!porNome.has(chave)) {
          porNome.set(chave, {
            name: imp.Name,
            devicePath: imp.PortName || imp.Name,
            status: String(imp.Status || imp.PrinterStatus || 'Desconhecido'),
            padrao: imp.Default || false,
            tipo: 'windows'
          });
        }
      }
    } catch (error) {
      console.error(`❌ Erro ao listar impressoras Windows (${consulta}):`, error.message);
    }
  }
  return [...porNome.values()];
}

module.exports = {
  listarImpressorasWindows
};

