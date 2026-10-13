import { api } from './api.js';
import { groupBy } from './dom.js';
import { createDrawer } from './drawer.js';
import { createRegistry } from './registry.js';
import { renderSummary, setApiStatus } from './summary.js';
import { toast } from './toast.js';

const registry = createRegistry({ onSelect: openCustomer });

const drawer = createDrawer({
  onChange: refreshAfterChange,
  onClose: () => {
    registry.setSelected(null);
    history.replaceState(null, '', location.pathname);
  },
});

function openCustomer(id) {
  registry.setSelected(id);
  history.replaceState(null, '', `#cliente-${id}`);
  drawer.open(id);
}

/** As contas alimentam os números do topo e as etiquetas de conta na tabela. */
async function loadAccounts() {
  const [accounts, customers] = await Promise.all([api.accounts(), api.customers({ size: 1 })]);
  renderSummary(accounts, customers.page.totalElements);
  registry.setAccounts(groupBy(accounts, (account) => account.customerId));
}

/** Depois de cadastrar uma chave Pix, atualiza a ficha aberta e os números do topo. */
async function refreshAfterChange() {
  try {
    await Promise.all([loadAccounts(), drawer.refresh()]);
  } catch (error) {
    toast(error.message, { error: true });
  }
}

/** Link direto para um cliente: /#cliente-12 abre o painel dele. */
function customerFromHash() {
  const match = location.hash.match(/^#cliente-(\d+)$/);
  return match ? Number(match[1]) : null;
}

async function start() {
  registry.load();
  try {
    await loadAccounts();
    setApiStatus('online');
  } catch (error) {
    setApiStatus('offline');
    toast(error.message, { error: true, duration: 8000 });
    return;
  }

  const linkedCustomer = customerFromHash();
  if (linkedCustomer) openCustomer(linkedCustomer);
}

document.documentElement.dataset.app = 'ready';
start();
