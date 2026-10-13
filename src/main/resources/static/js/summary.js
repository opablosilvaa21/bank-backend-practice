import { formatInteger } from './format.js';

const $ = (id) => document.getElementById(id);

/** Preenche os números do topo: clientes e contas por situação. */
export function renderSummary(accounts, customerCount) {
  const countStatus = (status) => accounts.filter((account) => account.status === status).length;

  $('summary-customers').textContent = formatInteger(customerCount);
  $('summary-active').textContent = formatInteger(countStatus('ATIVA'));
  $('summary-blocked').textContent = formatInteger(countStatus('BLOQUEADA'));
  $('summary-closed').textContent = formatInteger(countStatus('ENCERRADA'));
}

export function setApiStatus(state) {
  const labels = { loading: 'Conectando', online: 'Sistema disponível', offline: 'Sistema indisponível' };
  const status = $('api-status');
  status.dataset.state = state;
  status.textContent = labels[state];
}
