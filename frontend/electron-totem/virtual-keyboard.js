function installVirtualKeyboard() {
  if (typeof window === 'undefined' || typeof document === 'undefined') {
    return;
  }

  window.addEventListener('DOMContentLoaded', () => {
    let activeElement = null;
    let shiftEnabled = false;
    let keyboard = null;

    injectStyles();

    document.addEventListener('focusin', event => {
      const target = event.target;
      if (!isEditable(target)) {
        return;
      }

      activeElement = target;
      renderKeyboard(getLayoutType(target));
      setTimeout(() => target.scrollIntoView({ block: 'center', behavior: 'smooth' }), 100);
    });

    document.addEventListener('pointerdown', event => {
      if (!keyboard || keyboard.contains(event.target)) {
        return;
      }

      if (!isEditable(event.target)) {
        hideKeyboard();
      }
    });

    function renderKeyboard(layoutType) {
      if (!keyboard) {
        keyboard = document.createElement('section');
        keyboard.className = 'totem-virtual-keyboard';
        keyboard.setAttribute('aria-label', 'Teclado virtual do totem');
        keyboard.addEventListener('pointerdown', event => event.preventDefault());
        document.body.appendChild(keyboard);
      }

      keyboard.innerHTML = '';
      keyboard.classList.add('totem-virtual-keyboard--visible');
      document.body.classList.add('totem-keyboard-open');

      const rows = layoutType === 'number' ? numericRows() : textRows();
      for (const row of rows) {
        const rowElement = document.createElement('div');
        rowElement.className = 'totem-keyboard-row';

        for (const key of row) {
          const button = document.createElement('button');
          button.type = 'button';
          button.className = `totem-key ${key.action ? `totem-key--${key.action}` : ''}`;
          button.textContent = key.label;
          button.addEventListener('click', () => handleKey(key));
          rowElement.appendChild(button);
        }

        keyboard.appendChild(rowElement);
      }
    }

    function handleKey(key) {
      if (!activeElement) {
        return;
      }

      if (key.action === 'close') {
        hideKeyboard();
        activeElement.blur();
        return;
      }

      if (key.action === 'enter') {
        dispatchInputEvents(activeElement);
        activeElement.blur();
        hideKeyboard();
        return;
      }

      if (key.action === 'backspace') {
        removePreviousCharacter(activeElement);
        return;
      }

      if (key.action === 'clear') {
        updateValue(activeElement, '');
        return;
      }

      if (key.action === 'shift') {
        shiftEnabled = !shiftEnabled;
        renderKeyboard(getLayoutType(activeElement));
        return;
      }

      const value = key.value === ' ' ? ' ' : shiftEnabled ? key.value.toUpperCase() : key.value;
      insertText(activeElement, value);
    }

    function hideKeyboard() {
      if (!keyboard) {
        return;
      }

      keyboard.classList.remove('totem-virtual-keyboard--visible');
      document.body.classList.remove('totem-keyboard-open');
    }

    function textRows() {
      const letters = shiftEnabled ? 'QWERTYUIOPASDFGHJKLZXCVBNM' : 'qwertyuiopasdfghjklzxcvbnm';
      return [
        '1234567890'.split('').map(key),
        letters
          .slice(0, 10)
          .split('')
          .map(key),
        letters
          .slice(10, 19)
          .split('')
          .map(key),
        [{ label: shiftEnabled ? 'abc' : 'ABC', action: 'shift' }, ...letters.slice(19).split('').map(key), { label: 'Apagar', action: 'backspace' }],
        [
          { label: 'Limpar', action: 'clear' },
          { label: 'Espaco', value: ' ', wide: true },
          { label: 'Enter', action: 'enter' },
          { label: 'Fechar', action: 'close' },
        ],
      ];
    }

    function numericRows() {
      return [
        ['1', '2', '3'].map(key),
        ['4', '5', '6'].map(key),
        ['7', '8', '9'].map(key),
        [key('0'), key('.'), { label: 'Apagar', action: 'backspace' }],
        [
          { label: 'Limpar', action: 'clear' },
          { label: 'Enter', action: 'enter' },
          { label: 'Fechar', action: 'close' },
        ],
      ];
    }

    function key(value) {
      return { label: value, value };
    }
  });
}

function isEditable(element) {
  if (!element || element.disabled || element.readOnly) {
    return false;
  }

  if (element.tagName === 'TEXTAREA') {
    return true;
  }

  if (element.tagName !== 'INPUT') {
    return false;
  }

  const type = (element.getAttribute('type') || 'text').toLowerCase();
  return ['email', 'number', 'password', 'search', 'tel', 'text'].includes(type);
}

function getLayoutType(element) {
  const type = (element.getAttribute('type') || 'text').toLowerCase();
  return ['number', 'tel'].includes(type) ? 'number' : 'text';
}

function insertText(element, text) {
  const oldValue = String(element.value || '');
  const maxLength = Number(element.getAttribute('maxlength') || -1);
  const start = getSelectionStart(element, oldValue.length);
  const end = getSelectionEnd(element, start);
  const nextValue = `${oldValue.slice(0, start)}${text}${oldValue.slice(end)}`;

  if (maxLength > -1 && nextValue.length > maxLength) {
    return;
  }

  if (element.type === 'number' && text === '.' && oldValue.includes('.')) {
    return;
  }

  updateValue(element, nextValue, start + text.length);
}

function removePreviousCharacter(element) {
  const oldValue = String(element.value || '');
  const start = getSelectionStart(element, oldValue.length);
  const end = getSelectionEnd(element, start);

  if (start === 0 && end === 0) {
    return;
  }

  const removeFrom = start === end ? Math.max(0, start - 1) : start;
  const nextValue = `${oldValue.slice(0, removeFrom)}${oldValue.slice(end)}`;
  updateValue(element, nextValue, removeFrom);
}

function updateValue(element, value, cursorPosition) {
  element.value = value;
  dispatchInputEvents(element);

  if (cursorPosition === undefined) {
    return;
  }

  try {
    element.setSelectionRange(cursorPosition, cursorPosition);
  } catch (_error) {
    // Inputs numericos nao suportam selecao em alguns navegadores.
  }
}

function dispatchInputEvents(element) {
  element.dispatchEvent(new Event('input', { bubbles: true }));
  element.dispatchEvent(new Event('change', { bubbles: true }));
}

function getSelectionStart(element, fallback) {
  return typeof element.selectionStart === 'number' ? element.selectionStart : fallback;
}

function getSelectionEnd(element, fallback) {
  return typeof element.selectionEnd === 'number' ? element.selectionEnd : fallback;
}

function injectStyles() {
  const style = document.createElement('style');
  style.textContent = `
    body.totem-keyboard-open {
      padding-bottom: min(46vh, 430px) !important;
    }

    .totem-virtual-keyboard {
      position: fixed;
      left: 0;
      right: 0;
      bottom: 0;
      z-index: 2147483647;
      display: none;
      box-sizing: border-box;
      width: 100%;
      max-height: 48vh;
      padding: 12px;
      overflow: auto;
      background: #111827;
      border-top: 1px solid #374151;
      box-shadow: 0 -10px 30px rgba(0, 0, 0, 0.25);
    }

    .totem-virtual-keyboard--visible {
      display: block;
    }

    .totem-keyboard-row {
      display: grid;
      grid-auto-flow: column;
      grid-auto-columns: minmax(0, 1fr);
      gap: 8px;
      margin-bottom: 8px;
    }

    .totem-keyboard-row:last-child {
      margin-bottom: 0;
    }

    .totem-key {
      min-height: 54px;
      border: 1px solid #4b5563;
      border-radius: 8px;
      color: #f9fafb;
      background: #1f2937;
      font: 600 20px/1.2 system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      letter-spacing: 0;
      touch-action: manipulation;
    }

    .totem-key:active {
      background: #2563eb;
    }

    .totem-key--backspace,
    .totem-key--clear,
    .totem-key--enter,
    .totem-key--close,
    .totem-key--shift {
      background: #374151;
      font-size: 16px;
    }

    .totem-key--enter {
      background: #166534;
    }

    .totem-key--close {
      background: #7f1d1d;
    }

    @media (max-height: 720px) {
      .totem-virtual-keyboard {
        max-height: 56vh;
      }

      .totem-key {
        min-height: 46px;
        font-size: 18px;
      }
    }
  `;
  document.head.appendChild(style);
}

module.exports = { installVirtualKeyboard };
