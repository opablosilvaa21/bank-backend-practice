import { api } from './api.js';
import { debounce, fill, h } from './dom.js';
import { ACCOUNT_STATUS, ACCOUNT_TYPE_SHORT, formatBirthDate, formatInteger, initials } from './format.js';

const PAGE_SIZE = 20;
const COLUMNS = 5;

/**
 * Tabela paginada de clientes com busca por nome e ordenação.
 * As contas de cada cliente vêm da lista de contas, agrupada em main.js.
 */
export function createRegistry({ onSelect }) {
  const rows = document.getElementById('registry-rows');
  const filters = document.getElementById('registry-filters');
  const range = document.getElementById('pager-range');
  const pageLabel = document.getElementById('pager-page');
  const prev = document.getElementById('pager-prev');
  const next = document.getElementById('pager-next');

  const query = { name: '', sort: 'id,asc', page: 0, size: PAGE_SIZE };
  let page = null;
  let accountsByCustomer = new Map();
  let selectedId = null;
  let latestRequest = 0;

  async function load() {
    const requestId = ++latestRequest;
    if (!page) renderLoading();

    try {
      const result = await api.customers(query);
      if (requestId !== latestRequest) return;
      page = result;
      render();
    } catch (error) {
      if (requestId !== latestRequest) return;
      page = null;
      renderMessage('Não foi possível carregar os clientes.', error.message);
      updatePager();
    }
  }

  function render() {
    if (page.content.length > 0) {
      fill(rows, page.content.map(renderRow));
    } else if (query.name) {
      renderMessage(`Nenhum cliente encontrado para “${query.name}”.`, 'Confira a grafia ou busque só pelo primeiro nome.');
    } else {
      renderMessage('Nenhum cliente cadastrado.', 'A base de demonstração é criada quando a aplicação sobe.');
    }
    updatePager();
  }

  function renderRow(customer) {
    const accounts = accountsByCustomer.get(customer.id) ?? [];
    const select = () => onSelect(customer.id);

    return h('tr', {
      class: 'registry__row',
      'aria-current': customer.id === selectedId ? 'true' : 'false',
      'data-id': customer.id,
      onClick: select,
    },
      h('td', { 'data-cell': 'name' },
        h('div', { class: 'customer-cell' },
          h('span', { class: 'avatar', 'aria-hidden': 'true' }, initials(customer.fullName)),
          h('button', {
            type: 'button',
            class: 'customer-cell__name',
            onClick: (event) => { event.stopPropagation(); select(); },
          }, customer.fullName),
        ),
      ),
      h('td', { 'data-cell': 'cpf' }, h('span', { class: 'document' }, customer.cpf)),
      h('td', { 'data-cell': 'email' }, h('span', { class: 'email' }, customer.email)),
      h('td', { 'data-cell': 'birth' }, formatBirthDate(customer.birthDate)),
      h('td', { 'data-cell': 'accounts' },
        h('div', { class: 'account-tags' },
          accounts.map((account) => h('span', {
            class: `tag tag--${account.status}`,
            title: `${ACCOUNT_TYPE_SHORT[account.type]} ${account.accountNumber}: ${ACCOUNT_STATUS[account.status].toLowerCase()}`,
          }, ACCOUNT_TYPE_SHORT[account.type])),
        ),
      ),
    );
  }

  function renderLoading() {
    fill(rows, Array.from({ length: 6 }, () =>
      h('tr', { 'aria-hidden': 'true' },
        Array.from({ length: COLUMNS }, (_, index) =>
          h('td', {}, h('span', { class: 'skeleton', style: `width: ${index === 0 ? 70 : 55}%` }))),
      )));
  }

  function renderMessage(title, detail) {
    fill(rows,
      h('tr', { class: 'registry__state' },
        h('td', { colspan: COLUMNS }, h('strong', {}, title), detail),
      ),
    );
  }

  function updatePager() {
    const total = page?.page.totalElements ?? 0;
    const totalPages = page?.page.totalPages ?? 0;
    const start = total === 0 ? 0 : query.page * query.size + 1;
    const end = Math.min((query.page + 1) * query.size, total);

    range.textContent = total === 0 ? '' : `${formatInteger(start)} a ${formatInteger(end)} de ${formatInteger(total)} clientes`;
    pageLabel.textContent = totalPages > 1 ? `Página ${query.page + 1} de ${totalPages}` : '';
    prev.disabled = query.page === 0;
    next.disabled = !page || query.page + 1 >= totalPages;
  }

  function goTo(pageNumber) {
    query.page = pageNumber;
    load();
  }

  const searchByName = debounce((name) => {
    query.name = name;
    goTo(0);
  }, 250);

  filters.addEventListener('submit', (event) => event.preventDefault());
  filters.elements.name.addEventListener('input', (event) => searchByName(event.target.value.trim()));
  filters.elements.sort.addEventListener('change', (event) => {
    query.sort = event.target.value;
    goTo(0);
  });
  prev.addEventListener('click', () => goTo(query.page - 1));
  next.addEventListener('click', () => goTo(query.page + 1));

  return {
    load,
    setAccounts(map) {
      accountsByCustomer = map;
      if (page) render();
    },
    setSelected(id) {
      selectedId = id;
      for (const row of rows.querySelectorAll('.registry__row')) {
        row.setAttribute('aria-current', String(Number(row.dataset.id) === id));
      }
    },
  };
}
