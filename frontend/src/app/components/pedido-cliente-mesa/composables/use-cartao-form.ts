import { computed, signal } from '@angular/core';

export interface CartaoPayload {
  numero: string;
  nomePortador: string;
  validadeMes: string;
  validadeAno: string;
  cvv: string;
  parcelas: number;
}

export function validarLuhn(numero: string): boolean {
  const digitos = numero.replace(/\D/g, '');
  if (digitos.length < 13 || digitos.length > 19) {
    return false;
  }
  let soma = 0;
  let alternar = false;
  for (let i = digitos.length - 1; i >= 0; i--) {
    let digito = Number(digitos[i]);
    if (alternar) {
      digito *= 2;
      if (digito > 9) {
        digito -= 9;
      }
    }
    soma += digito;
    alternar = !alternar;
  }
  return soma % 10 === 0;
}

export function useCartaoForm() {
  const numero = signal('');
  const nomePortador = signal('');
  const validadeMes = signal('');
  const validadeAno = signal('');
  const cvv = signal('');
  const parcelas = signal(1);

  const numeroValido = computed(() => validarLuhn(numero()));
  const cvvValido = computed(() => /^\d{3,4}$/.test(cvv()));
  const validadeValida = computed(() => {
    const mes = Number(validadeMes());
    const ano = Number(validadeAno());
    return mes >= 1 && mes <= 12 && ano >= 2024 && ano <= 2099;
  });
  const valido = computed(() =>
    numeroValido() && cvvValido() && validadeValida() && nomePortador().trim().length >= 3
  );

  function payload(): CartaoPayload {
    return {
      numero: numero().replace(/\D/g, ''),
      nomePortador: nomePortador().trim(),
      validadeMes: validadeMes().padStart(2, '0'),
      validadeAno: validadeAno(),
      cvv: cvv(),
      parcelas: parcelas(),
    };
  }

  function limpar(): void {
    numero.set('');
    nomePortador.set('');
    validadeMes.set('');
    validadeAno.set('');
    cvv.set('');
    parcelas.set(1);
  }

  return {
    numero,
    nomePortador,
    validadeMes,
    validadeAno,
    cvv,
    parcelas,
    numeroValido,
    cvvValido,
    validadeValida,
    valido,
    payload,
    limpar,
  };
}
