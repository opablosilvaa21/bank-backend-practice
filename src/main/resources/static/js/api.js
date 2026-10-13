/** Cliente HTTP da API do banco. Os erros seguem o formato {"error": "...", "fields": {...}}. */

export class ApiError extends Error {
  constructor(status, body) {
    super(body?.error ?? `A API respondeu com erro ${status}.`);
    this.status = status;
    this.fields = body?.fields ?? {};
  }
}

/**
 * Servida pela aplicação (porta 8080), a página chama a API no mesmo endereço.
 * Aberta por outro servidor, como o Live Server do VS Code, chama a API local na porta 8080.
 */
const API_URL = location.port === '8080' ? '/api' : 'http://localhost:8080/api';

async function request(path, { method = 'GET', body } = {}) {
  let response;
  try {
    response = await fetch(`${API_URL}${path}`, {
      method,
      headers: body ? { 'Content-Type': 'application/json' } : undefined,
      body: body ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiError(0, { error: 'Sem conexão com a API. Rode .\mvnw.cmd spring-boot:run e recarregue a página.' });
  }

  const data = await response.json().catch(() => null);
  if (!response.ok) throw new ApiError(response.status, data);
  return data;
}

function query(params) {
  const entries = Object.entries(params).filter(([, value]) => value !== undefined && value !== '');
  return entries.length ? `?${new URLSearchParams(entries)}` : '';
}

const post = (path, body) => request(path, { method: 'POST', body });

export const api = {
  customers: ({ name, page = 0, size = 20, sort = 'id,asc' } = {}) =>
    request(`/customers${query({ name, page, size, sort })}`),
  customer: (id) => request(`/customers/${id}`),
  accounts: () => request('/accounts'),

  pixKeys: (accountId) => request(`/accounts/${accountId}/pix-keys`),
  createPixKey: (accountId, body) => post(`/accounts/${accountId}/pix-keys`, body),
};
