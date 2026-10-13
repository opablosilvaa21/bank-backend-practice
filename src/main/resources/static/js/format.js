/** Formatação no padrão brasileiro e rótulos legíveis para os enums da API. */

const integer = new Intl.NumberFormat('pt-BR');
const shortDate = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: '2-digit', year: 'numeric' });

export const formatInteger = (value) => integer.format(value);

/** Instantes da API (ISO com horário), como data de cadastro: "07/08/2026". */
export const formatDate = (iso) => shortDate.format(new Date(iso));

/** Datas sem horário (AAAA-MM-DD) são lidas como data local, para não voltar um dia por causa do fuso. */
function localDate(isoDate) {
  const [year, month, day] = isoDate.split('-').map(Number);
  return new Date(year, month - 1, day);
}

export const formatBirthDate = (isoDate) => shortDate.format(localDate(isoDate));

export function initials(fullName) {
  const parts = fullName.trim().split(/\s+/);
  const first = parts[0]?.[0] ?? '';
  const last = parts.length > 1 ? parts.at(-1)[0] : '';
  return (first + last).toUpperCase();
}

export const ACCOUNT_TYPE = { CORRENTE: 'Conta corrente', POUPANCA: 'Poupança' };
export const ACCOUNT_TYPE_SHORT = { CORRENTE: 'Corrente', POUPANCA: 'Poupança' };
export const ACCOUNT_STATUS = { ATIVA: 'Ativa', BLOQUEADA: 'Bloqueada', ENCERRADA: 'Encerrada' };
export const PIX_KEY_TYPE = { CPF: 'CPF', EMAIL: 'E-mail', ALEATORIA: 'Chave aleatória' };
