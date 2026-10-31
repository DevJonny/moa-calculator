import { toYards, moaInInches, clickInInches, clicksForOffset, offsetForClicks, MM_PER_INCH } from "./moa.js";

const STORAGE_KEY = "moa-calculator";
const defaults = { click: "0.25", unit: "yd", distance: "100", mode: "offset", offset: "", clicks: "" };

const $ = (id) => document.getElementById(id);
const state = { ...defaults, ...load() };

function load() {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY)) ?? {};
  } catch {
    return {};
  }
}

function save() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  } catch {}
}

const fmt = (n, dp) => n.toFixed(dp);
const length = (inches, metric) => (metric ? `${fmt(inches * MM_PER_INCH / 10, 2)} cm` : `${fmt(inches, 3)}″`);
const altLength = (inches, metric) => (metric ? `${fmt(inches, 3)}″` : `${fmt(inches * MM_PER_INCH, 1)} mm`);
const plural = (n, word) => `${n} ${word}${n === 1 ? "" : "s"}`;

function renderControls() {
  for (const group of ["click", "unit", "mode"]) {
    for (const btn of $(group).querySelectorAll("button")) {
      btn.setAttribute("aria-checked", String(btn.dataset.value === state[group]));
    }
  }
  const lengthUnit = state.unit === "m" ? "cm" : "in";
  const clicksMode = state.mode === "clicks";
  $("mode").querySelector('[data-value="offset"]').textContent = `${lengthUnit} → clicks`;
  $("mode").querySelector('[data-value="clicks"]').textContent = `clicks → ${lengthUnit}`;
  $("amount-unit").textContent = clicksMode ? "clicks" : lengthUnit;
  $("amount").step = clicksMode ? "1" : "any";
  $("amount").inputMode = clicksMode ? "numeric" : "decimal";
  $("distance").value = state.distance;
  $("amount").value = state[state.mode];
}

function renderResults() {
  const metric = state.unit === "m";
  const clickMoa = Number(state.click);
  const yards = toYards(Number(state.distance) || 0, state.unit);
  const clickIn = clickInInches(clickMoa, yards);

  $("click-primary").textContent = length(clickIn, metric);
  $("click-secondary").textContent = altLength(clickIn, metric);
  $("one-moa").textContent = `1 MOA = ${length(moaInInches(yards), metric)}`;

  const amount = Number(state[state.mode]) || 0;
  let html = "";
  if (amount > 0 && clickIn > 0) {
    if (state.mode === "clicks") {
      const moved = offsetForClicks(amount, clickMoa, yards);
      html = `Moves impact <strong>${length(moved, metric)}</strong> <span class="secondary">(${altLength(moved, metric)})</span>`;
    } else {
      const clicks = clicksForOffset(metric ? amount * 10 / MM_PER_INCH : amount, clickMoa, yards);
      html = `<strong>${plural(Math.round(clicks), "click")}</strong> <span class="secondary">(${fmt(clicks, 1)})</span>`;
    }
  }
  $("converted").innerHTML = html;
}

function update(changes, { controls = true } = {}) {
  Object.assign(state, changes);
  save();
  if (controls) renderControls();
  renderResults();
}

for (const group of ["click", "unit", "mode"]) {
  $(group).addEventListener("click", (e) => {
    const value = e.target.closest("button")?.dataset.value;
    if (value) update({ [group]: value });
  });
}

$("presets").addEventListener("click", (e) => {
  const value = e.target.closest("button")?.dataset.value;
  if (value) update({ distance: value });
});

// Leave the inputs alone while typing: rewriting their value would clobber partial entries like "1.".
$("distance").addEventListener("input", (e) => update({ distance: e.target.value }, { controls: false }));
$("amount").addEventListener("input", (e) => update({ [state.mode]: e.target.value }, { controls: false }));

renderControls();
renderResults();

if ("serviceWorker" in navigator) {
  navigator.serviceWorker.register("sw.js");
}
