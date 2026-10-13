import { h } from './dom.js';

const container = document.getElementById('toasts');

/** Mostra um aviso curto no rodapé da tela. */
export function toast(message, { error = false, duration = 4000 } = {}) {
  const item = h('div', { class: error ? 'toast toast--error' : 'toast' }, message);
  container.append(item);
  setTimeout(() => item.remove(), duration);
}
