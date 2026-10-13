import { api, ApiError } from './api.js';
import { h, icon, ICONS } from './dom.js';
import { ACCOUNT_TYPE, PIX_KEY_TYPE } from './format.js';
import { toast } from './toast.js';

const dialog = document.getElementById('operation-dialog');

const PIX_KEY_CREATED = { CPF: 'Chave CPF', EMAIL: 'Chave e-mail', ALEATORIA: 'Chave aleatória' };

/* --------------------------------------------------------------------------
   Cadastro de chave Pix. Ao concluir, chama onDone().
   -------------------------------------------------------------------------- */

export function openPixKey(account, onDone) {
  const valueField = field({
    name: 'value', label: 'E-mail', type: 'email', placeholder: 'nome@gmail.com', hint: 'O e-mail passa a receber Pix nesta conta.',
  });

  const form = openForm({
    title: 'Cadastrar chave Pix',
    subtitle: describe(account),
    submitLabel: 'Cadastrar chave',
    fields: [
      field({
        name: 'type',
        label: 'Tipo de chave',
        options: [
          { value: 'EMAIL', label: PIX_KEY_TYPE.EMAIL },
          { value: 'ALEATORIA', label: PIX_KEY_TYPE.ALEATORIA },
          { value: 'CPF', label: 'CPF do titular' },
        ],
      }),
      valueField,
    ],
    async submit(values) {
      if (values.type === 'EMAIL' && !values.value?.trim()) throw invalid('value', 'Informe o e-mail.');

      const created = await api.createPixKey(account.id, {
        type: values.type,
        value: values.type === 'EMAIL' ? values.value.trim() : undefined,
      });
      form.close();
      toast(`${PIX_KEY_CREATED[created.type]} cadastrada.`);
      onDone();
    },
  });

  // Só a chave de e-mail precisa de valor: CPF vem do titular e a aleatória é gerada pelo banco.
  form.element.elements.type.addEventListener('change', (event) => {
    valueField.hidden = event.target.value !== 'EMAIL';
  });
}

/* --------------------------------------------------------------------------
   Formulário genérico dentro do <dialog>
   -------------------------------------------------------------------------- */

function openForm({ title, subtitle, fields, submitLabel, submit }) {
  const errorBox = h('p', { class: 'form-error', role: 'alert', hidden: true });
  const submitButton = h('button', { type: 'submit', class: 'button button--primary' }, submitLabel);
  const close = () => dialog.close();

  const element = h('form', { class: 'dialog__form', novalidate: true },
    h('div', { class: 'dialog__head' },
      h('div', {},
        h('h2', { class: 'dialog__title', id: 'dialog-title' }, title),
        h('p', { class: 'dialog__subtitle' }, subtitle),
      ),
      h('button', { type: 'button', class: 'icon-button', 'aria-label': 'Fechar', onClick: close }, icon(...ICONS.close)),
    ),
    errorBox,
    fields,
    h('div', { class: 'dialog__actions' },
      h('button', { type: 'button', class: 'button button--ghost', onClick: close }, 'Cancelar'),
      submitButton,
    ),
  );

  const controller = { element, close };

  element.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (submitButton.getAttribute('aria-busy') === 'true') return;

    clearErrors(element, errorBox);
    submitButton.setAttribute('aria-busy', 'true');
    try {
      await submit(Object.fromEntries(new FormData(element)), controller);
    } catch (error) {
      showErrors(element, errorBox, error);
    } finally {
      submitButton.removeAttribute('aria-busy');
    }
  });

  dialog.setAttribute('aria-labelledby', 'dialog-title');
  dialog.replaceChildren(element);
  dialog.showModal();
  element.querySelector('input, select')?.focus();
  return controller;
}

function field({ name, label, hint = '', type = 'text', options, placeholder }) {
  const id = `field-${name}`;
  const messageId = `${id}-message`;
  const control = options
    ? h('select', { id, name, 'aria-describedby': messageId },
        options.map((option) => h('option', { value: option.value }, option.label)))
    : h('input', { id, name, type, placeholder, autocomplete: 'off', 'aria-describedby': messageId });

  return h('div', { class: 'field', 'data-field': name },
    h('label', { class: 'field__label', for: id }, label),
    control,
    h('p', { class: 'field__hint', id: messageId, 'data-hint': hint }, hint),
  );
}

function describe(account) {
  return `${account.ownerName}, ${ACCOUNT_TYPE[account.type].toLowerCase()} ${account.accountNumber}`;
}

function invalid(fieldName, message) {
  return new ApiError(400, { error: 'Revise o campo destacado.', fields: { [fieldName]: message } });
}

function clearErrors(form, errorBox) {
  errorBox.hidden = true;
  for (const message of form.querySelectorAll('.field__error')) {
    message.className = 'field__hint';
    message.textContent = message.dataset.hint;
  }
  for (const control of form.querySelectorAll('[aria-invalid]')) control.removeAttribute('aria-invalid');
}

/** Erros de campo aparecem embaixo do campo; os demais, no topo do formulário. */
function showErrors(form, errorBox, error) {
  let firstInvalid = null;

  for (const [name, message] of Object.entries(error.fields ?? {})) {
    const wrapper = form.querySelector(`[data-field="${name}"]`);
    if (!wrapper || wrapper.hidden) continue;

    const control = wrapper.querySelector('input, select');
    const hint = wrapper.querySelector('.field__hint');
    control.setAttribute('aria-invalid', 'true');
    hint.className = 'field__error';
    hint.textContent = capitalize(message);
    firstInvalid ??= control;
  }

  if (firstInvalid) {
    firstInvalid.focus();
  } else {
    errorBox.textContent = error.message;
    errorBox.hidden = false;
  }
}

const capitalize = (text) => text.charAt(0).toUpperCase() + text.slice(1);
