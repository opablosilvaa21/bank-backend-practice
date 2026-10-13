import { api } from './api.js';
import { fill, h, icon, ICONS } from './dom.js';
import {
  ACCOUNT_STATUS, ACCOUNT_TYPE, PIX_KEY_TYPE,
  formatBirthDate, formatDate, initials,
} from './format.js';
import { openPixKey } from './operations.js';

const PIX_KEY_LIMIT = 5;

/**
 * Painel lateral com o cadastro de um cliente: dados pessoais, documentos, contas e chaves Pix.
 * Não exibe saldos nem movimentações.
 */
export function createDrawer({ onChange, onClose }) {
  const root = document.getElementById('drawer');
  const backdrop = document.getElementById('drawer-backdrop');
  const dialog = document.getElementById('operation-dialog');
  const pageRegions = document.querySelectorAll('body > header, body > section, body > main, body > footer');

  let customerId = null;
  let returnFocus = null;
  let latestRequest = 0;

  /* Abrir e fechar ------------------------------------------------------- */

  async function open(id) {
    if (root.hidden) {
      returnFocus = document.activeElement;
      root.hidden = false;
      backdrop.hidden = false;
      document.body.classList.add('is-locked');
      pageRegions.forEach((region) => { region.inert = true; });
    }
    customerId = id;
    fill(root, topBar(), h('div', { class: 'drawer__section' }, h('span', { class: 'skeleton', style: 'width: 60%' })));
    root.focus();
    await load();
  }

  function close() {
    if (root.hidden) return;
    latestRequest++;
    root.hidden = true;
    backdrop.hidden = true;
    document.body.classList.remove('is-locked');
    pageRegions.forEach((region) => { region.inert = false; });
    customerId = null;
    returnFocus?.focus();
    onClose();
  }

  /** Recarrega o cliente aberto depois de uma alteração, como uma chave Pix nova. */
  async function refresh() {
    if (customerId) await load();
  }

  async function load() {
    const requestId = ++latestRequest;
    try {
      const { customer, accounts } = await api.customer(customerId);
      const pixKeys = await Promise.all(accounts.map((account) => api.pixKeys(account.id)));
      if (requestId !== latestRequest) return;
      render(customer, accounts.map((account, index) => ({ ...account, pixKeys: pixKeys[index] })));
    } catch (error) {
      if (requestId !== latestRequest) return;
      fill(root, topBar(), h('div', { class: 'drawer__section' }, h('p', { class: 'empty-note' }, error.message)));
    }
  }

  /* Conteúdo --------------------------------------------------------------- */

  function topBar() {
    return h('div', { class: 'drawer__top' },
      h('span', { class: 'drawer__label' }, 'Ficha cadastral'),
      h('button', { type: 'button', class: 'icon-button', 'aria-label': 'Fechar ficha do cliente', onClick: close },
        icon(...ICONS.close)),
    );
  }

  function render(customer, accounts) {
    fill(root,
      topBar(),
      h('section', { class: 'drawer__section' },
        h('div', { class: 'profile' },
          h('span', { class: 'avatar avatar--large', 'aria-hidden': 'true' }, initials(customer.fullName)),
          h('div', {},
            h('h2', { class: 'profile__name', id: 'drawer-title' }, customer.fullName),
            h('p', { class: 'profile__since' }, `Cliente desde ${formatDate(customer.createdAt)}`),
          ),
        ),
      ),
      h('section', { class: 'drawer__section', 'aria-labelledby': 'personal-title' },
        h('h3', { class: 'section-title', id: 'personal-title' }, 'Dados pessoais'),
        h('dl', { class: 'facts' },
          fact('Nome completo', customer.fullName, true),
          fact('CPF', customer.cpf),
          fact('Data de nascimento', formatBirthDate(customer.birthDate)),
          fact('E-mail', customer.email, true),
          fact('Código do cliente', String(customer.id).padStart(6, '0')),
        ),
      ),
      h('section', { class: 'drawer__section', 'aria-labelledby': 'accounts-title' },
        h('h3', { class: 'section-title', id: 'accounts-title' }, accounts.length === 1 ? 'Conta' : 'Contas'),
        accounts.length === 0
          ? h('p', { class: 'empty-note' }, 'Este cliente ainda não tem contas.')
          : accounts.map(accountCard),
      ),
    );
  }

  function fact(label, value, wide = false) {
    return h('div', { class: wide ? 'facts__item facts__item--wide' : 'facts__item' },
      h('dt', {}, label),
      h('dd', {}, value),
    );
  }

  function accountCard(account) {
    const canRegister = account.status === 'ATIVA' && account.pixKeys.length < PIX_KEY_LIMIT;

    return h('article', { class: 'account-card' },
      h('header', { class: 'account-card__head' },
        h('h4', { class: 'account-card__title' }, ACCOUNT_TYPE[account.type]),
        h('span', { class: `tag tag--${account.status}` }, ACCOUNT_STATUS[account.status]),
      ),
      h('dl', { class: 'facts facts--compact' },
        fact('Agência', account.agency),
        fact('Conta', account.accountNumber),
        fact('Abertura', formatDate(account.openedAt)),
      ),
      h('div', { class: 'pix' },
        h('p', { class: 'pix__title' }, `Chaves Pix (${account.pixKeys.length} de ${PIX_KEY_LIMIT})`),
        account.pixKeys.length === 0
          ? h('p', { class: 'muted-note' }, 'Nenhuma chave cadastrada.')
          : h('ul', { class: 'pix__list' }, account.pixKeys.map((key) =>
              h('li', { class: 'pix__item' },
                h('span', { class: 'pix__type' }, PIX_KEY_TYPE[key.type]),
                h('span', { class: 'pix__key' }, key.key),
              ))),
        canRegister
          ? h('button', { type: 'button', class: 'button button--text', onClick: () => openPixKey(account, onChange) },
              icon(...ICONS.plus), 'Cadastrar chave Pix')
          : null,
      ),
    );
  }

  /* Teclado ---------------------------------------------------------------- */

  backdrop.addEventListener('click', close);
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && !dialog.open) close();
  });

  return { open, close, refresh };
}
