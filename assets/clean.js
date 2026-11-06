// Corre en cada marco de la página. Quita dos tipos de anuncio que el bloqueo por dominio no puede parar:
//   A) capas invisibles de pantalla completa que se "comen" los toques (hay que tocar varias veces para dar al play)
//   B) globos o banners flotantes con una X para cerrar
// Es prudente: nunca toca nada que contenga o esté dentro de un reproductor de vídeo.
(() => {
  if (window.__fpc) return;
  window.__fpc = 1;
  const PLAYER = 'video,audio,.jwplayer,.video-js,.plyr,[class*="player" i],[id*="player" i]';
  const closeText = /^\s*(x|×|✕|✖|close|cerrar|fechar)\s*$/i;
  const closeClass = /close|dismiss|cerrar/i;

  const playerish = e => !!(e.querySelector('video,audio') || e.closest(PLAYER));
  const hasClose = e => {
    const all = e.querySelectorAll('*');
    for (let i = 0; i < all.length && i < 80; i++) {
      const n = all[i];
      if (n.children.length === 0 && closeText.test(n.textContent || '')) return true;
      if (closeClass.test(String((n.getAttribute && n.getAttribute('class')) || ''))) return true;
    }
    return false;
  };

  const check = e => {
    if (!e || e === document.body || e === document.documentElement || e.nodeType !== 1) return;
    const cs = getComputedStyle(e);
    const pos = cs.position;
    if (pos !== 'fixed' && pos !== 'absolute' && pos !== 'sticky') return;
    const z = parseInt(cs.zIndex, 10);
    if (!(z >= 100)) return;
    if (playerish(e)) return;
    const r = e.getBoundingClientRect();
    if (r.width < 20 || r.height < 20) return;
    const vw = innerWidth || 1, vh = innerHeight || 1;
    const txt = (e.innerText || '').trim();
    const clear = cs.backgroundColor === 'rgba(0, 0, 0, 0)' || cs.backgroundColor === 'transparent' || parseFloat(cs.opacity) < 0.05;
    // A) capa invisible que tapa casi toda la pantalla
    if (r.width >= 0.8 * vw && r.height >= 0.8 * vh && clear && txt.length < 3 && !e.querySelector('input,button,video,iframe')) {
      e.remove();
      return;
    }
    // B) globo / banner flotante con botón de cerrar, que ocupa menos de la mitad de la pantalla
    if (pos === 'fixed' && (r.width * r.height) / (vw * vh) < 0.5 && hasClose(e)) e.remove();
  };

  const scan = () => {
    const b = document.body;
    if (!b) return;
    for (const c of [...b.children]) {
      check(c);
      if (c.isConnected) for (const d of [...c.children]) check(d);
    }
  };

  let timer = 0;
  const later = () => { if (!timer) timer = setTimeout(() => { timer = 0; try { scan(); } catch (e) { } }, 300); };
  setInterval(later, 2000);
  new MutationObserver(later).observe(document, { childList: true, subtree: true });
})();
