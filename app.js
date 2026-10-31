import { toYards, moaInInches, clickInInches, clicksForOffset, MM_PER_INCH } from "./moa.js";

const STORAGE_KEY = "moa-calculator";
const defaults = { click: "0.25", unit: "yd", distance: "100", offset: "" };

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

function renderControls() {
  for (const group of ["click", "unit"]) {
    for (const btn of $(group).querySelectorAll("button")) {
      btn.setAttribute("aria-checked", String(btn.dataset.value === state[group]));
    }
  }
  $("distance").value = state.distance;
  $("offset").value = state.offset;
}

function renderResults() {
  const metric = state.unit === "m";
  const clickMoa = Number(state.click);
  const yards = toYards(Number(state.distance) || 0, state.unit);
  const clickIn = clickInInches(clickMoa, yards);
  const moaIn = moaInInches(yards);

  $("click-primary").textContent = metric ? `${fmt(clickIn * MM_PER_INCH / 10, 2)} cm` : `${fmt(clickIn, 3)}″`;
  $("click-secondary").textContent = metric ? `${fmt(clickIn, 3)}″` : `${fmt(clickIn * MM_PER_INCH, 1)} mm`;
  $("one-moa").textContent = `1 MOA = ${metric ? `${fmt(moaIn * MM_PER_INCH / 10, 2)} cm` : `${fmt(moaIn, 3)}″`}`;

  $("offset-unit").textContent = metric ? "cm" : "in";
  const offset = Number(state.offset) || 0;
  const offsetIn = metric ? offset * 10 / MM_PER_INCH : offset;
  const clicks = clicksForOffset(offsetIn, clickMoa, yards);
  const rounded = Math.round(clicks);
  $("clicks").innerHTML = offset > 0 && clicks > 0
    ? `<strong>${rounded} click${rounded === 1 ? "" : "s"}</strong> <span class="secondary">(${fmt(clicks, 1)})</span>`
    : "";
}

function update(changes, { controls = true } = {}) {
  Object.assign(state, changes);
  save();
  if (controls) renderControls();
  renderResults();
}

$("click").addEventListener("click", (e) => {
  const value = e.target.closest("button")?.dataset.value;
  if (value) update({ click: value });
});

$("unit").addEventListener("click", (e) => {
  const value = e.target.closest("button")?.dataset.value;
  if (value) update({ unit: value });
});

$("presets").addEventListener("click", (e) => {
  const value = e.target.closest("button")?.dataset.value;
  if (value) update({ distance: value });
});

// Leave the inputs alone while typing: rewriting their value would clobber partial entries like "1.".
$("distance").addEventListener("input", (e) => update({ distance: e.target.value }, { controls: false }));
$("offset").addEventListener("input", (e) => update({ offset: e.target.value }, { controls: false }));

renderControls();
renderResults();

if ("serviceWorker" in navigator) {
  navigator.serviceWorker.register("sw.js");
}
