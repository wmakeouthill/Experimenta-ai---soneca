function installTouchMode() {
  if (typeof window === 'undefined' || typeof document === 'undefined') {
    return;
  }

  window.addEventListener('DOMContentLoaded', () => {
    injectStyles();

    let dragState = null;
    let suppressClickUntil = 0;

    document.addEventListener('pointerdown', event => {
      if (!shouldStartDrag(event)) {
        return;
      }

      const scrollElement = findScrollableElement(event.target);
      dragState = {
        pointerId: event.pointerId,
        scrollElement,
        startX: event.clientX,
        startY: event.clientY,
        startScrollLeft: scrollElement.scrollLeft,
        startScrollTop: scrollElement.scrollTop,
        moved: false,
      };
    }, true);

    document.addEventListener('pointermove', event => {
      if (!dragState || dragState.pointerId !== event.pointerId) {
        return;
      }

      const deltaX = event.clientX - dragState.startX;
      const deltaY = event.clientY - dragState.startY;
      const movedEnough = Math.abs(deltaX) > 4 || Math.abs(deltaY) > 4;

      if (!movedEnough && !dragState.moved) {
        return;
      }

      dragState.moved = true;
      dragState.scrollElement.scrollTop = dragState.startScrollTop - deltaY;
      dragState.scrollElement.scrollLeft = dragState.startScrollLeft - deltaX;
      document.documentElement.classList.add('totem-touch-dragging');
      event.preventDefault();
    }, { capture: true, passive: false });

    document.addEventListener('pointerup', finishDrag, true);
    document.addEventListener('pointercancel', finishDrag, true);

    document.addEventListener('click', event => {
      if (Date.now() <= suppressClickUntil) {
        event.preventDefault();
        event.stopPropagation();
      }
    }, true);

    function finishDrag(event) {
      if (!dragState || dragState.pointerId !== event.pointerId) {
        return;
      }

      if (dragState.moved) {
        suppressClickUntil = Date.now() + 250;
        event.preventDefault();
      }

      dragState = null;
      document.documentElement.classList.remove('totem-touch-dragging');
    }
  });
}

function shouldStartDrag(event) {
  if (event.button !== 0 || event.pointerType === 'touch') {
    return false;
  }

  return !isTextInput(event.target) && !isInsideVirtualKeyboard(event.target);
}

function isTextInput(element) {
  const editableElement = element?.closest?.(
    'input, textarea, select, [contenteditable="true"], [contenteditable=""]'
  );

  return Boolean(editableElement);
}

function isInsideVirtualKeyboard(element) {
  return Boolean(element?.closest?.('.totem-virtual-keyboard'));
}

function findScrollableElement(startElement) {
  let current = startElement instanceof Element ? startElement : document.body;

  while (current && current !== document.body && current !== document.documentElement) {
    if (canScroll(current)) {
      return current;
    }
    current = current.parentElement;
  }

  return document.scrollingElement || document.documentElement;
}

function canScroll(element) {
  const style = window.getComputedStyle(element);
  const overflowY = style.overflowY;
  const overflowX = style.overflowX;
  const canScrollY =
    ['auto', 'scroll', 'overlay'].includes(overflowY) && element.scrollHeight > element.clientHeight;
  const canScrollX =
    ['auto', 'scroll', 'overlay'].includes(overflowX) && element.scrollWidth > element.clientWidth;

  return canScrollY || canScrollX;
}

function injectStyles() {
  const style = document.createElement('style');
  style.textContent = `
    html,
    body {
      overscroll-behavior: none !important;
      -webkit-text-size-adjust: 100% !important;
    }

    body,
    body *:not(input):not(textarea):not(select):not([contenteditable="true"]):not([contenteditable=""]) {
      -webkit-user-select: none !important;
      user-select: none !important;
      -webkit-touch-callout: none !important;
    }

    input,
    textarea,
    select,
    [contenteditable="true"],
    [contenteditable=""] {
      -webkit-user-select: text !important;
      user-select: text !important;
    }

    img,
    a {
      -webkit-user-drag: none !important;
      user-drag: none !important;
    }

    button,
    a,
    [role="button"],
    .produto-card,
    .produto-card-mini,
    .categoria-chip,
    .categoria-card,
    .pagamento-opcao {
      touch-action: manipulation !important;
      -webkit-tap-highlight-color: transparent !important;
    }

    .carrossel-horizontal,
    .produtos-container,
    .carrinho-items,
    .modal-produto,
    .pagamento-container,
    .confirmacao-container,
    .autoatendimento-container {
      -webkit-overflow-scrolling: touch !important;
      overscroll-behavior: contain !important;
    }

    .totem-touch-dragging,
    .totem-touch-dragging * {
      cursor: grabbing !important;
    }
  `;
  document.head.appendChild(style);
}

module.exports = { installTouchMode };
