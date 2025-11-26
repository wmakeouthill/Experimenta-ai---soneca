/**
 * Impressora Windows - Spooler
 * Responsabilidade: Enviar dados RAW para spooler do Windows via Win32 API
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
public class RawPrinter {
  [DllImport("winspool.drv", CharSet = CharSet.Auto, SetLastError = true)]
  public static extern bool OpenPrinter([MarshalAs(UnmanagedType.LPTStr)] string szPrinter, out IntPtr hPrinter, IntPtr pd);
  [DllImport("winspool.drv")]
  public static extern bool ClosePrinter(IntPtr hPrinter);
  [DllImport("winspool.drv")]
  public static extern bool StartDocPrinter(IntPtr hPrinter, int level, [In, MarshalAs(UnmanagedType.LPStruct)] DOCINFOA di);
  [DllImport("winspool.drv")]
  public static extern bool EndDocPrinter(IntPtr hPrinter);
  [DllImport("winspool.drv")]
  public static extern bool StartPagePrinter(IntPtr hPrinter);
  [DllImport("winspool.drv")]
  public static extern bool EndPagePrinter(IntPtr hPrinter);
  [DllImport("winspool.drv")]
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
    try {
      if (!OpenPrinter(szPrinterName, out hPrinter, IntPtr.Zero)) {
        int error = Marshal.GetLastWin32Error();
        return "ERRO:OpenPrinter falhou com código " + error + ". Impressora '" + szPrinterName + "' não encontrada.";
      }
      if (!StartDocPrinter(hPrinter, 1, di)) {
        int error = Marshal.GetLastWin32Error();
        ClosePrinter(hPrinter);
        return "ERRO:StartDocPrinter falhou com código " + error + ".";
      }
      if (!StartPagePrinter(hPrinter)) {
        int error = Marshal.GetLastWin32Error();
        EndDocPrinter(hPrinter);
        ClosePrinter(hPrinter);
        return "ERRO:StartPagePrinter falhou com código " + error + ".";
      }
      int dwWritten = 0;
      if (!WritePrinter(hPrinter, Marshal.UnsafeAddrOfPinnedArrayElement(pBytes, 0), pBytes.Length, out dwWritten)) {
        int error = Marshal.GetLastWin32Error();
        EndPagePrinter(hPrinter);
        EndDocPrinter(hPrinter);
        ClosePrinter(hPrinter);
        return "ERRO:WritePrinter falhou com código " + error + ".";
      }
      if (dwWritten != pBytes.Length) {
        EndPagePrinter(hPrinter);
        EndDocPrinter(hPrinter);
        ClosePrinter(hPrinter);
        return "ERRO:Nem todos os bytes foram escritos. Esperado: " + pBytes.Length + ", Escrito: " + dwWritten + ".";
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
      return "ERRO:Exceção ao enviar dados: " + ex.Message;
    }
  }
}`;

/**
 * Obtém o nome real da impressora a partir do devicePath
 *
 * IMPORTANTE: Win32 API OpenPrinter() requer o NOME EXATO da impressora,
 * não apenas o devicePath. O Windows usa o nome para identificar a impressora
 * no spooler. O devicePath (COM3, USB001, etc.) não é suficiente.
 *
 * @param {string} devicePath - DevicePath de referência (ex: USB001, COM3, ou nome da impressora)
 * @param {string|null} nomeImpressora - Nome da impressora (opcional, já fornecido e priorizado)
 * @returns {Promise<string>} - Nome real da impressora como registrado no Windows
 */
async function obterNomeImpressora(devicePath, nomeImpressora = null) {
  // Se o nome já foi fornecido, usa ele (mais confiável)
  if (nomeImpressora && nomeImpressora.trim().length > 0) {
    console.log(`✅ Usando nome de impressora fornecido: "${nomeImpressora}"`);
    return nomeImpressora;
  }

  // Busca lista de impressoras disponíveis no sistema
  const impressoras = await listarImpressorasDisponiveis();

  if (impressoras.length === 0) {
    console.warn(`⚠️ Nenhuma impressora encontrada no sistema. Usando devicePath como fallback: "${devicePath}"`);
    return devicePath;
  }

  // Tenta encontrar por devicePath exato
  let impressoraEncontrada = impressoras.find(imp =>
    imp.devicePath === devicePath || imp.devicePath === devicePath.toUpperCase()
  );

  // Se não encontrou, tenta encontrar por nome (devicePath pode ser o nome da impressora)
  if (!impressoraEncontrada) {
    impressoraEncontrada = impressoras.find(imp =>
      imp.name === devicePath || imp.name.toLowerCase() === devicePath.toLowerCase()
    );
  }

  // Se não encontrou, tenta encontrar por devicePath parcial (contém)
  if (!impressoraEncontrada) {
    impressoraEncontrada = impressoras.find(imp =>
      (imp.devicePath && imp.devicePath.includes(devicePath)) ||
      (imp.devicePath && devicePath.includes(imp.devicePath))
    );
  }

  // Se encontrou, retorna o nome
  if (impressoraEncontrada) {
    console.log(`✅ Impressora encontrada: "${impressoraEncontrada.name}" (devicePath: ${impressoraEncontrada.devicePath})`);
    return impressoraEncontrada.name;
  }

  // Fallback: usa o devicePath como nome (pode funcionar se for o nome exato)
  console.warn(`⚠️ Impressora não encontrada na lista. Usando devicePath como nome: "${devicePath}"`);
  console.log(`📋 Impressoras disponíveis:`, impressoras.map(imp => `"${imp.name}" (${imp.devicePath})`).join(', '));
  return devicePath;
}

/**
 * Verifica se a impressora existe no sistema
 * @param {string} nomeImpressora - Nome da impressora
 * @returns {Promise<boolean>} - true se existe, false caso contrário
 */
async function verificarImpressoraExiste(nomeImpressora) {
  try {
    const nomePSEscapado = nomeImpressora.replace(/'/g, "''").replace(/"/g, '`"');
    const comando = `powershell -Command "$printer = Get-Printer -Name '${nomePSEscapado}' -ErrorAction SilentlyContinue; if ($printer) { Write-Output 'EXISTS' } else { Write-Output 'NOT_FOUND' }"`;
    const { stdout } = await executarComando(comando, { timeout: 5000 });
    return stdout && stdout.includes('EXISTS');
  } catch (error) {
    console.warn(`⚠️ Erro ao verificar impressora: ${error.message || 'erro desconhecido'}`);
    return false;
  }
}

/**
 * Envia dados RAW para spooler do Windows via Win32 API
 *
 * Fluxo de execução:
 * 1. Obtém nome real da impressora
 * 2. Cria arquivo temporário com dados ESC/POS
 * 3. Cria arquivo C# com código de impressão
 * 4. Verifica se impressora existe
 * 5. Envia dados via Win32 API
 * 6. Limpa arquivos temporários
 *
 * @param {Buffer} dados - Dados ESC/POS binários já formatados
 * @param {string} devicePath - DevicePath de referência (ex: USB001)
 * @param {string|null} nomeImpressora - Nome real da impressora (opcional)
 * @returns {Promise<{sucesso: boolean}>}
 */
async function enviarParaSpooler(dados, devicePath, nomeImpressora = null) {
  // 1. Obtém nome real da impressora
  const nomeImpressoraReal = await obterNomeImpressora(devicePath, nomeImpressora);

  // 2. Cria arquivos temporários
  const arquivoTemp = criarArquivoTemporario(dados, 'cupom', '.prn');
  const arquivoCSharp = path.join(require('os').tmpdir(), `rawprinter_${Date.now()}.cs`);

  console.log(`💾 Arquivo temporário criado: ${arquivoTemp} (${dados.length} bytes)`);
  fs.writeFileSync(arquivoCSharp, CODIGO_CSHARP_RAW_PRINTER);

  try {
    // 3. Verifica se impressora existe
    if (!await verificarImpressoraExiste(nomeImpressoraReal)) {
      throw new Error(`Impressora '${nomeImpressoraReal}' não encontrada.`);
    }

    // 4. Prepara comando PowerShell
    const nomePSEscapado = nomeImpressoraReal.replace(/'/g, "''").replace(/"/g, '`"');
    const arquivoTempEscapado = arquivoTemp.replace(/\\/g, '\\\\').replace(/'/g, "''");
    const arquivoCSharpEscapado = arquivoCSharp.replace(/\\/g, '\\\\').replace(/'/g, "''");

    const comando = `powershell -Command "$arquivo = '${arquivoTempEscapado}'; $printerName = '${nomePSEscapado}'; $csFile = '${arquivoCSharpEscapado}'; $bytes = [System.IO.File]::ReadAllBytes($arquivo); if ($bytes.Length -eq 0) { Write-Error 'Arquivo está vazio'; exit 1 }; $csCode = [System.IO.File]::ReadAllText($csFile); Add-Type -TypeDefinition $csCode; $resultado = [RawPrinter]::SendBytesToPrinter($printerName, $bytes); Write-Output $resultado; if (-not $resultado.StartsWith('SUCESSO')) { Write-Error $resultado; exit 1 }"`;

    // 5. Envia dados via Win32 API
    console.log(`🖨️ Enviando dados RAW via spooler do Windows para "${nomeImpressoraReal}"...`);
    const { stdout, stderr } = await executarComando(comando, { timeout: 20000 });

    if (stdout && stdout.includes('SUCESSO')) {
      console.log(`✅ Dados enviados para spooler do Windows com sucesso`);
      return { sucesso: true };
    }

    const erro = stdout || stderr || 'Erro desconhecido';
    throw new Error(erro);
  } catch (error) {
    const mensagemErro = error.message || 'Erro desconhecido';
    console.error(`❌ Erro ao imprimir: ${mensagemErro}`);
    throw new Error(`Falha ao imprimir: ${mensagemErro}`);
  } finally {
    // 6. Limpa arquivos temporários após delay (garante que spooler processou)
    // Delay de 10s para arquivo do cupom (pode ser grande)
    // Delay de 5s para arquivo C# (pequeno, pode ser removido mais rápido)
    removerArquivoTemporarioComDelay(arquivoTemp, 10000);
    setTimeout(() => removerArquivoTemporario(arquivoCSharp), 5000);
  }
}

module.exports = {
  enviarParaSpooler
};
