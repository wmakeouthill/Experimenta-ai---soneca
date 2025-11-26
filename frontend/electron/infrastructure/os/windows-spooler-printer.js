/**
 * Impressora Windows - Spooler
 * Responsabilidade: Enviar dados RAW para spooler do Windows usando apenas Win32 API
 */

const path = require('path');
const fs = require('fs');
const { executarComando } = require('../../utils/exec-utils');
const { criarArquivoTemporario, removerArquivoTemporario, removerArquivoTemporarioComDelay } = require('../../utils/file-utils');
const { listarImpressorasDisponiveis } = require('../../core/printer/printer-detector');

/**
 * Código C# para enviar dados RAW via API Win32
 */
const CODIGO_CSHARP_RAW_PRINTER = `using System;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;
public class RawPrinter {
  [DllImport("winspool.drv", CharSet = CharSet.Auto, SetLastError = true, ExactSpelling = false, CallingConvention = CallingConvention.StdCall)]
  public static extern bool OpenPrinter([MarshalAs(UnmanagedType.LPTStr)] string szPrinter, out IntPtr hPrinter, IntPtr pd);
  [DllImport("winspool.drv", CharSet = CharSet.Auto, SetLastError = true, ExactSpelling = false, CallingConvention = CallingConvention.StdCall)]
  public static extern bool ClosePrinter(IntPtr hPrinter);
  [DllImport("winspool.drv", CharSet = CharSet.Auto, SetLastError = true, ExactSpelling = false, CallingConvention = CallingConvention.StdCall)]
  public static extern bool StartDocPrinter(IntPtr hPrinter, int level, [In, MarshalAs(UnmanagedType.LPStruct)] DOCINFOA di);
  [DllImport("winspool.drv", CharSet = CharSet.Auto, SetLastError = true, ExactSpelling = false, CallingConvention = CallingConvention.StdCall)]
  public static extern bool EndDocPrinter(IntPtr hPrinter);
  [DllImport("winspool.drv", CharSet = CharSet.Auto, SetLastError = true, ExactSpelling = false, CallingConvention = CallingConvention.StdCall)]
  public static extern bool StartPagePrinter(IntPtr hPrinter);
  [DllImport("winspool.drv", CharSet = CharSet.Auto, SetLastError = true, ExactSpelling = false, CallingConvention = CallingConvention.StdCall)]
  public static extern bool EndPagePrinter(IntPtr hPrinter);
  [DllImport("winspool.drv", CharSet = CharSet.Auto, SetLastError = true, ExactSpelling = false, CallingConvention = CallingConvention.StdCall)]
  public static extern bool WritePrinter(IntPtr hPrinter, IntPtr pBytes, int dwCount, out int dwWritten);
  [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Auto)]
  public class DOCINFOA {
    [MarshalAs(UnmanagedType.LPStr)]
    public string pDocName;
    [MarshalAs(UnmanagedType.LPStr)]
    public string pOutputFile;
    [MarshalAs(UnmanagedType.LPStr)]
    public string pDataType;
    public DOCINFOA(string docName, string dataType) {
      pDocName = docName;
      pOutputFile = null;
      pDataType = dataType;
    }
  }
  public static string SendBytesToPrinter(string szPrinterName, byte[] pBytes) {
    IntPtr hPrinter = IntPtr.Zero;
    DOCINFOA di = new DOCINFOA("Cupom Fiscal", "RAW");
    bool bSuccess = false;
    try {
      bSuccess = OpenPrinter(szPrinterName, out hPrinter, IntPtr.Zero);
      if (!bSuccess) {
        int error = Marshal.GetLastWin32Error();
        return $"ERRO:OpenPrinter falhou com código {error}. Impressora '{szPrinterName}' não encontrada ou inacessível.";
      }
      bSuccess = StartDocPrinter(hPrinter, 1, di);
      if (!bSuccess) {
        int error = Marshal.GetLastWin32Error();
        ClosePrinter(hPrinter);
        if (error == 1804) {
          return $"ERRO:StartDocPrinter falhou com código 1804 (ERROR_PRINTER_DRIVER_INCOMPATIBLE). O driver da impressora '{szPrinterName}' não suporta dados RAW ou não está configurado para aceitar RAW. SOLUÇÕES: 1) Instale o driver específico do fabricante (Diebold, Daruma ou Epson) - não use driver genérico, 2) Nas propriedades da impressora → Avançado, verifique se aceita 'RAW' como tipo de dados, 3) Tente usar 'Imprimir diretamente na impressora' ao invés de 'Spooling', 4) Se disponível, use porta COM direta (mais confiável para ESC/POS).";
        }
        return $"ERRO:StartDocPrinter falhou com código {error}.";
      }
      bSuccess = StartPagePrinter(hPrinter);
      if (!bSuccess) {
        int error = Marshal.GetLastWin32Error();
        EndDocPrinter(hPrinter);
        ClosePrinter(hPrinter);
        return $"ERRO:StartPagePrinter falhou com código {error}.";
      }
      int dwWritten = 0;
      bSuccess = WritePrinter(hPrinter, Marshal.UnsafeAddrOfPinnedArrayElement(pBytes, 0), pBytes.Length, out dwWritten);
      if (!bSuccess) {
        int error = Marshal.GetLastWin32Error();
        EndPagePrinter(hPrinter);
        EndDocPrinter(hPrinter);
        ClosePrinter(hPrinter);
        return $"ERRO:WritePrinter falhou com código {error}.";
      }
      if (dwWritten != pBytes.Length) {
        EndPagePrinter(hPrinter);
        EndDocPrinter(hPrinter);
        ClosePrinter(hPrinter);
        return $"ERRO:Nem todos os bytes foram escritos. Esperado: {pBytes.Length}, Escrito: {dwWritten}.";
      }
      EndPagePrinter(hPrinter);
      EndDocPrinter(hPrinter);
      ClosePrinter(hPrinter);
      return "SUCESSO: Dados enviados para spooler do Windows.";
    } catch (Exception ex) {
      if (hPrinter != IntPtr.Zero) {
        EndPagePrinter(hPrinter);
        EndDocPrinter(hPrinter);
        ClosePrinter(hPrinter);
      }
      return $"ERRO:Exceção ao enviar dados: {ex.Message}";
    }
  }
}`;

/**
 * Limpa a fila de impressão de uma impressora
 * @param {string} nomeImpressora - Nome da impressora
 */
async function limparFilaImpressao(nomeImpressora) {
  try {
    const nomePSEscapado = nomeImpressora.replace(/'/g, "''").replace(/"/g, '`"');
    const comando = `powershell -Command "Get-Printer -Name '${nomePSEscapado}' -ErrorAction SilentlyContinue | Get-PrintJob | Remove-PrintJob -ErrorAction SilentlyContinue"`;
    const { stdout } = await executarComando(comando, { timeout: 5000 });
    const jobs = (stdout || '').match(/PrintJobId/gi);
    const totalJobs = jobs ? jobs.length : 0;
    console.log(`🧹 Limpeza de fila: LIMPOU:${totalJobs} trabalhos`);
  } catch (error) {
    console.warn(`⚠️ Erro ao limpar fila: ${error.message}`);
  }
}

/**
 * Verifica se a impressora existe e está disponível
 * @param {string} nomeImpressora - Nome da impressora
 * @returns {Promise<{exists: boolean, online: boolean, status: string, jobs: number, driverName?: string, portName?: string}>}
 */
async function verificarImpressoraExiste(nomeImpressora) {
  try {
    const nomePSEscapado = nomeImpressora.replace(/'/g, "''").replace(/"/g, '`"');
    const comando = `powershell -Command "$printer = Get-Printer -Name '${nomePSEscapado}' -ErrorAction SilentlyContinue; if ($printer) { Write-Output 'EXISTS|' + $printer.PrinterStatus + '|' + $printer.DriverName + '|' + $printer.PortName; $jobs = Get-PrintJob -PrinterName '${nomePSEscapado}' -ErrorAction SilentlyContinue; Write-Output ('JOBS:' + $jobs.Count); Write-Output ('ONLINE:' + ($printer.PrinterStatus -eq 0)) } else { Write-Output 'NOT_FOUND' }"`;
    const { stdout } = await executarComando(comando, { timeout: 5000 });

    if (!stdout || stdout.includes('NOT_FOUND')) {
      return { exists: false, online: false, status: 'Não encontrada', jobs: 0 };
    }

    const linhas = stdout.trim().split('\n').filter(l => l.trim());
    let status = 'Desconhecido';
    let driverName = '';
    let portName = '';
    let jobs = 0;
    let online = false;

    for (const linha of linhas) {
      if (linha.startsWith('EXISTS|')) {
        const partes = linha.split('|');
        if (partes.length >= 4) {
          status = partes[1] || 'Desconhecido';
          driverName = partes[2] || '';
          portName = partes[3] || '';
        }
      } else if (linha.startsWith('JOBS:')) {
        jobs = parseInt(linha.split(':')[1]) || 0;
      } else if (linha.startsWith('ONLINE:')) {
        online = linha.split(':')[1] === 'True';
      }
    }

    return {
      exists: true,
      online: online || status === '0',
      status: status === '0' ? 'Normal' : status,
      jobs,
      driverName,
      portName
    };
  } catch (error) {
    console.warn(`⚠️ Erro ao verificar impressora: ${error.message}`);
    return { exists: false, online: false, status: 'Erro ao verificar', jobs: 0 };
  }
}

/**
 * Obtém o nome real da impressora
 * @param {string} devicePath - DevicePath fornecido
 * @param {string|null} nomeImpressora - Nome da impressora (opcional)
 * @returns {Promise<string>}
 */
async function obterNomeImpressora(devicePath, nomeImpressora = null) {
  if (nomeImpressora) {
    return nomeImpressora;
  }

  const impressoras = await listarImpressorasDisponiveis();
  for (const imp of impressoras) {
    const match = imp.match(/"([^"]+)"\s*\(([^)]+)\)/);
    if (match) {
      const nome = match[1];
      const path = match[2];
      if (path === devicePath || path.includes(devicePath) || devicePath.includes(path)) {
        return nome;
      }
    }
  }

  return devicePath;
}

/**
 * Envia dados RAW para spooler via Win32 API
 * @param {Buffer} dados - Dados ESC/POS binários
 * @param {string} nomeImpressora - Nome da impressora
 * @param {string} tipoDados - Tipo de dados ('RAW', 'TEXT', 'XPS_PASS', 'EMF')
 * @returns {Promise<{sucesso: boolean}>}
 */
async function enviarRawParaSpooler(dados, nomeImpressora, tipoDados = 'RAW') {
  const arquivoTemp = criarArquivoTemporario(dados, 'cupom', '.prn');
  console.log(`💾 Arquivo temporário criado: ${arquivoTemp} (${dados.length} bytes)`);

  const codigoCSharpComTipo = CODIGO_CSHARP_RAW_PRINTER.replace(
    'new DOCINFOA("Cupom Fiscal", "RAW");',
    `new DOCINFOA("Cupom Fiscal", "${tipoDados}");`
  );

  const arquivoCSharp = path.join(require('os').tmpdir(), `rawprinter_${Date.now()}.cs`);
  fs.writeFileSync(arquivoCSharp, codigoCSharpComTipo);

  try {
    const nomePSEscapado = nomeImpressora.replace(/'/g, "''").replace(/"/g, '`"');
    const arquivoTempEscapado = arquivoTemp.replace(/\\/g, '\\\\').replace(/'/g, "''");
    const arquivoCSharpEscapado = arquivoCSharp.replace(/\\/g, '\\\\').replace(/'/g, "''");

    const comandoPSRaw = `powershell -Command "$arquivo = '${arquivoTempEscapado}'; $printerName = '${nomePSEscapado}'; $csFile = '${arquivoCSharpEscapado}'; $bytes = [System.IO.File]::ReadAllBytes($arquivo); if ($bytes.Length -eq 0) { Write-Error 'Arquivo está vazio'; exit 1 }; $csCode = [System.IO.File]::ReadAllText($csFile); Add-Type -TypeDefinition $csCode; $resultado = [RawPrinter]::SendBytesToPrinter($printerName, $bytes); Write-Output $resultado; if (-not $resultado.StartsWith('SUCESSO')) { Write-Error $resultado; exit 1 }"`;

    const { stdout } = await executarComando(comandoPSRaw, { timeout: 20000 });

    if (stdout && stdout.includes('SUCESSO')) {
      console.log(`✅ Dados enviados para spooler do Windows com sucesso (tipo: ${tipoDados})`);
      return { sucesso: true };
    }

    throw new Error(stdout || 'Erro desconhecido');
  } catch (error) {
    if (error.message.includes('1804') || error.message.includes('ERROR_PRINTER_DRIVER_INCOMPATIBLE')) {
      throw new Error(`ERROR_1804:${error.message}`);
    }
    throw error;
  } finally {
    setTimeout(() => {
      removerArquivoTemporario(arquivoCSharp);
    }, 5000);
  }
}

/**
 * Envia dados para spooler do Windows (método principal)
 * @param {Buffer} dados - Dados ESC/POS binários
 * @param {string} devicePath - DevicePath de referência
 * @param {string|null} nomeImpressora - Nome da impressora (opcional)
 * @returns {Promise<{sucesso: boolean, erro?: string}>}
 */
async function enviarParaSpooler(dados, devicePath, nomeImpressora = null) {
  const nomeImpressoraReal = await obterNomeImpressora(devicePath, nomeImpressora);

  console.log(`🧹 Limpando trabalhos de impressão pendentes...`);
  await limparFilaImpressao(nomeImpressoraReal);
  await new Promise(resolve => setTimeout(resolve, 500));

  const arquivoTemp = criarArquivoTemporario(dados, 'cupom', '.prn');
  console.log(`💾 Arquivo temporário criado: ${arquivoTemp} (${dados.length} bytes)`);

  try {
    const verificacao = await verificarImpressoraExiste(nomeImpressoraReal);
    if (!verificacao.exists) {
      throw new Error(`Impressora '${nomeImpressoraReal}' não encontrada.`);
    }

    console.log(`🖨️ Tentando enviar dados RAW via spooler do Windows...`);

    // Tenta RAW primeiro
    try {
      return await enviarRawParaSpooler(dados, nomeImpressoraReal, 'RAW');
    } catch (error) {
      // Se erro 1804, tenta tipos alternativos
      if (error.message.includes('ERROR_1804:') || error.message.includes('1804')) {
        console.warn(`⚠️ Erro 1804: Driver não suporta RAW. Tentando tipos alternativos...`);

        const tiposAlternativos = ['TEXT', 'XPS_PASS', 'EMF'];
        for (const tipoAlternativo of tiposAlternativos) {
          console.log(`🔄 Tentando tipo de dados: ${tipoAlternativo}...`);
          try {
            return await enviarRawParaSpooler(dados, nomeImpressoraReal, tipoAlternativo);
          } catch (tipoError) {
            console.warn(`⚠️ Tipo ${tipoAlternativo} também falhou: ${tipoError.message}`);
          }
        }

        throw new Error(
          `Falha ao imprimir: Driver não suporta dados RAW (Erro 1804). ` +
          `Todos os tipos foram tentados (RAW, TEXT, XPS_PASS, EMF). ` +
          `SOLUÇÕES: 1) Nas Propriedades da impressora → Avançado, configure para aceitar 'RAW'; ` +
          `2) Configure para 'Imprimir diretamente na impressora' (sem spooling); ` +
          `3) Reinstale o driver específico do fabricante.`
        );
      }
      throw error;
    }
  } catch (error) {
    console.error(`❌ Erro ao executar impressão:`, error.message);
    throw error;
  } finally {
    removerArquivoTemporarioComDelay(arquivoTemp, 10000);
  }
}

module.exports = {
  enviarParaSpooler,
  verificarImpressoraExiste
};
