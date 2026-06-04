const WIKI_BASE    = '/src/main/resources/assets/universalkeyboard/wiki/';
const TEXTURE_BASE = '/src/main/resources/assets/universalkeyboard/';

let pages = [];
let currentIdx = 0;
let lbEntry = null;   // the WikiEntry currently shown in the lightbox

// ── Boot ─────────────────────────────────────────────────────────────────────

async function init() {
  const lang = navigator.language.toLowerCase().replace('-', '_');

  let manifest;
  try {
    manifest = await fetch(WIKI_BASE + 'manifest.json').then(r => r.json());
  } catch (e) {
    document.getElementById('content').textContent = 'Could not load manifest.json';
    return;
  }

  for (const id of manifest) {
    // Try browser locale first, fall back to en_us — same logic as in-game
    let page = null;
    for (const locale of [lang, 'en_us']) {
      try {
        page = await fetch(WIKI_BASE + locale + '/' + id + '.json').then(r => r.json());
        break;
      } catch (e) { /* try next */ }
    }
    if (page) pages.push(page);
    else console.warn('Could not load page:', id);
  }

  if (pages.length === 0) {
    document.getElementById('content').textContent = 'No pages found.';
    return;
  }

  renderSidebar();
  renderPage(0);

  document.addEventListener('keydown', e => {
    if (e.key === 'Escape' || e.key === 'Tab') {
      e.preventDefault();
      closeLightbox();
    }
  });
}

// ── Sidebar ───────────────────────────────────────────────────────────────────

function renderSidebar() {
  const sidebar = document.getElementById('sidebar');
  sidebar.innerHTML = '';
  pages.forEach((page, i) => {
    const row = document.createElement('div');
    row.className = 'sidebar-row' + (i === currentIdx ? ' active' : '');
    row.textContent = page.label || page.id || String(i);
    row.addEventListener('click', () => {
      currentIdx = i;
      renderSidebar();
      renderPage(i);
    });
    sidebar.appendChild(row);
  });
}

// ── Page content ──────────────────────────────────────────────────────────────

function renderPage(idx) {
  const page = pages[idx];
  const content = document.getElementById('content');
  content.innerHTML = '';
  content.scrollTop = 0;

  for (const entry of (page.entries || [])) {
    if      (entry.type === 'heading') content.appendChild(buildHeading(entry));
    else if (entry.type === 'text')    content.appendChild(buildText(entry));
    else if (entry.type === 'image')   content.appendChild(buildImage(entry));
  }
}

function buildHeading(entry) {
  const frag = document.createDocumentFragment();
  const h = document.createElement('div');
  h.className = 'wiki-heading';
  h.textContent = entry.text;
  frag.appendChild(h);
  const hr = document.createElement('hr');
  hr.className = 'wiki-heading-rule';
  frag.appendChild(hr);
  return frag;
}

function buildText(entry) {
  const p = document.createElement('p');
  p.className = 'wiki-text';
  if (entry.scale && entry.scale !== 1) p.style.fontSize = entry.scale + 'em';
  p.innerHTML = mcFormat(entry.body || '');
  return p;
}

function buildImage(entry) {
  const wrap = document.createElement('div');
  wrap.className = 'wiki-image-wrap';

  const img = document.createElement('img');
  img.src = TEXTURE_BASE + entry.texture;
  img.alt = '';
  img.draggable = false;
  wrap.appendChild(img);

  // Hotspot overlays (positioned as % of image)
  for (const spot of (entry.hotspots || [])) {
    const div = buildHotspotDiv(spot, entry.width, entry.height);
    wrap.appendChild(div);
  }

  // Click anywhere on image (including hotspots) opens lightbox
  wrap.addEventListener('click', () => openLightbox(entry));

  return wrap;
}

function buildHotspotDiv(spot, imgW, imgH) {
  const div = document.createElement('div');
  div.className = 'hotspot';
  div.style.left   = (spot.px0 / imgW * 100) + '%';
  div.style.top    = (spot.py0 / imgH * 100) + '%';
  div.style.width  = ((spot.px1 - spot.px0) / imgW * 100) + '%';
  div.style.height = ((spot.py1 - spot.py0) / imgH * 100) + '%';

  div.addEventListener('mouseenter', e => { showTooltip(spot); moveTooltip(e); });
  div.addEventListener('mousemove',  moveTooltip);
  div.addEventListener('mouseleave', hideTooltip);

  return div;
}

// ── Lightbox ──────────────────────────────────────────────────────────────────

function openLightbox(entry) {
  lbEntry = entry;
  const lb    = document.getElementById('lightbox');
  const wrap  = document.getElementById('lb-image-wrap');
  const img   = document.getElementById('lb-img');

  img.src = TEXTURE_BASE + entry.texture;

  // Remove any previous hotspot overlays
  wrap.querySelectorAll('.lb-hotspot').forEach(el => el.remove());

  // Add hotspot overlays sized to the image element (percentage-based, same approach)
  for (const spot of (entry.hotspots || [])) {
    const div = document.createElement('div');
    div.className = 'lb-hotspot';
    div.style.left   = (spot.px0 / entry.width  * 100) + '%';
    div.style.top    = (spot.py0 / entry.height * 100) + '%';
    div.style.width  = ((spot.px1 - spot.px0) / entry.width  * 100) + '%';
    div.style.height = ((spot.py1 - spot.py0) / entry.height * 100) + '%';

    div.addEventListener('mouseenter', e => { showTooltip(spot); moveTooltip(e); e.stopPropagation(); });
    div.addEventListener('mousemove',  e => { moveTooltip(e);                    e.stopPropagation(); });
    div.addEventListener('mouseleave', hideTooltip);
    div.addEventListener('click',      e => e.stopPropagation()); // don't close on hotspot click

    wrap.appendChild(div);
  }

  lb.classList.remove('hidden');
}

function closeLightbox() {
  document.getElementById('lightbox').classList.add('hidden');
  hideTooltip();
  lbEntry = null;
}

// ── Tooltip ───────────────────────────────────────────────────────────────────

const tooltip = document.getElementById('tooltip');

function showTooltip(spot) {
  document.getElementById('tooltip-title').textContent = spot.title || '';
  document.getElementById('tooltip-body').textContent  = spot.body  || '';
  tooltip.classList.remove('hidden');
}

function moveTooltip(e) {
  const ttW  = tooltip.offsetWidth;
  const ttH  = tooltip.offsetHeight;
  const half = window.innerWidth / 2;
  let x = e.clientX > half ? e.clientX - ttW - 14 : e.clientX + 14;
  let y = e.clientY - ttH / 2;
  x = Math.max(4, Math.min(x, window.innerWidth  - ttW - 4));
  y = Math.max(4, Math.min(y, window.innerHeight - ttH - 4));
  tooltip.style.left = x + 'px';
  tooltip.style.top  = y + 'px';
}

function hideTooltip() {
  tooltip.classList.add('hidden');
}

// ── Minecraft § formatting ────────────────────────────────────────────────────

function mcFormat(text) {
  // Split on § codes and wrap in spans
  const parts = text.split(/§([0-9a-flonmr])/i);
  let html = '';
  let openTags = 0;
  for (let i = 0; i < parts.length; i++) {
    if (i % 2 === 0) {
      html += escHtml(parts[i]);
    } else {
      const code = parts[i].toLowerCase();
      if (code === 'r') {
        html += '</span>'.repeat(openTags);
        openTags = 0;
      } else {
        html += `<span class="mc-${code}">`;
        openTags++;
      }
    }
  }
  html += '</span>'.repeat(openTags);
  return html;
}

function escHtml(str) {
  return str.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;')
            .replace(/\n/g,'<br>');
}

// ── Start ─────────────────────────────────────────────────────────────────────

window.closeLightbox = closeLightbox; // expose for onclick in HTML
init();
