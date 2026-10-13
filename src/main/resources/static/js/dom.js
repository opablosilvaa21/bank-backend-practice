/**
 * Cria um elemento a partir de tag, atributos e filhos.
 * Texto entra sempre como nó de texto, nunca como HTML, então dados da API não injetam marcação.
 *
 *   h('button', { class: 'button', onClick: save }, 'Salvar')
 */
export function h(tag, props = {}, ...children) {
  const el = document.createElement(tag);

  for (const [key, value] of Object.entries(props ?? {})) {
    if (value == null || value === false) continue;

    if (key === 'class') el.className = value;
    else if (key === 'style') el.style.cssText = value;
    else if (key.startsWith('on')) el.addEventListener(key.slice(2).toLowerCase(), value);
    else if (value === true) el.setAttribute(key, '');
    else el.setAttribute(key, String(value));
  }

  el.append(...clean(children));
  return el;
}

/** Troca o conteúdo de um elemento. Aceita listas aninhadas e ignora null/false, como h(). */
export function fill(el, ...children) {
  el.replaceChildren(...clean(children));
}

const clean = (children) => children.flat(Infinity).filter((child) => child != null && child !== false);

/** Ícone SVG de traço a partir de uma lista de paths (24×24). */
export function icon(...paths) {
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('viewBox', '0 0 24 24');
  svg.setAttribute('aria-hidden', 'true');
  for (const d of paths) {
    const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    path.setAttribute('d', d);
    svg.append(path);
  }
  return svg;
}

export const ICONS = {
  close: ['M6 6l12 12', 'M18 6 6 18'],
  plus: ['M12 5v14', 'M5 12h14'],
};

/** Agrupa itens por chave, preservando a ordem. Equivale a Map.groupBy, que navegadores antigos não têm. */
export function groupBy(items, keyOf) {
  const groups = new Map();
  for (const item of items) {
    const key = keyOf(item);
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(item);
  }
  return groups;
}

export function debounce(fn, wait) {
  let timer;
  return (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), wait);
  };
}
