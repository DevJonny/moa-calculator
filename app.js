import { toYards, moaInInches, clickInInches, clicksForOffset, offsetForClicks, MM_PER_INCH } from "./moa.js";
import { holdovers, reticleDots, FPS_TO_MPS, INCH_TO_M, YARD_TO_M } from "./ballistics.js";

const STORAGE_KEY = "moa-calculator";
// Rifle setup defaults suit a sub-12 ft-lb .177 PCP (e.g. Gamo Phox) with an 8.4 gr pellet and a 3-9x SFP scope.
const defaults = {
  tab: "moa", click: "0.25", unit: "yd", distance: "100", mode: "offset", offset: "", clicks: "",
  mv: "790", bc: "0.021", sight: "1.5", zero: "25", trueMag: "9", mag: "9",
};
const SETUP_FIELDS = ["mv", "bc", "sight", "zero", "trueMag", "mag"];
const TABLE_RANGES = [10, 15, 20, 25, 30, 35, 40, 45, 50, 55];

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
const roundTo1 = (n) => String(Math.round(n * 10) / 10);

function renderControls() {
  for (const group of ["tab", "click", "unit", "mode"]) {
    for (const btn of $(group).querySelectorAll("button")) {
      btn.setAttribute("aria-checked", String(btn.dataset.value === state[group]));
    }
  }
  for (const el of document.querySelectorAll("[data-tab]")) el.hidden = el.dataset.tab !== state.tab;

  const metric = state.unit === "m";
  const lengthUnit = metric ? "cm" : "in";
  const clicksMode = state.mode === "clicks";
  $("mode").querySelector('[data-value="offset"]').textContent = `${lengthUnit} → clicks`;
  $("mode").querySelector('[data-value="clicks"]').textContent = `clicks → ${lengthUnit}`;
  $("amount-unit").textContent = clicksMode ? "clicks" : lengthUnit;
  $("amount").step = clicksMode ? "1" : "any";
  $("amount").inputMode = clicksMode ? "numeric" : "decimal";
  $("distance").value = state.distance;
  $("amount").value = state[state.mode];

  $("sight-unit").textContent = metric ? "mm" : "in";
  $("zero-unit").textContent = state.unit;
  $("table-unit").textContent = state.unit;
  for (const field of SETUP_FIELDS) $(field).value = state[field];
}

function renderClicks(metric) {
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

// Signed dots with an arrow: ↑ hold over, ↓ hold under.
const holdLabel = (dots) => (Math.abs(dots) < 0.05 ? "0.0" : `${dots > 0 ? "↑" : "↓"}${fmt(Math.abs(dots), 1)}`);
const impactLabel = (heightM, metric) => {
  if (Math.abs(heightM) < 0.0005) return "on aim";
  const size = metric ? `${fmt(Math.abs(heightM) * 100, 1)} cm` : `${fmt(Math.abs(heightM) / INCH_TO_M, 2)}″`;
  return `${size} ${heightM < 0 ? "low" : "high"}`;
};

function renderHoldover(metric) {
  const toM = (d) => (metric ? d : d * YARD_TO_M);
  const setup = {
    muzzleVelocityMps: Number(state.mv) * FPS_TO_MPS,
    bc: Number(state.bc),
    sightHeightM: metric ? Number(state.sight) / 1000 : Number(state.sight) * INCH_TO_M,
    zeroM: toM(Number(state.zero)),
  };
  const trueMag = Number(state.trueMag);
  const mag = Number(state.mag);
  const magsValid = trueMag > 0 && mag > 0;
  const dotsFor = (mils) => (magsValid ? reticleDots(mils, mag, trueMag) : mils);

  const distance = Number(state.distance) || 0;
  const ranges = [...new Set([...TABLE_RANGES, distance])].filter((d) => d > 0).sort((a, b) => a - b);
  const results = holdovers(setup, ranges.map(toM));

  const rows = results
    ? TABLE_RANGES.map((d) => {
        const h = results[ranges.indexOf(d)];
        const cells = Number.isNaN(h.heightM)
          ? `<td>–</td><td>out of range</td>`
          : `<td>${holdLabel(dotsFor(h.mils))}</td><td>${impactLabel(h.heightM, metric)}</td>`;
        return `<tr${d === distance ? ' class="current"' : ""}><td>${d}</td>${cells}</tr>`;
      })
    : [];
  $("hold-table").innerHTML = rows.join("");

  const current = results && distance > 0 ? results[ranges.indexOf(distance)] : null;
  if (!current) {
    $("hold-caption").textContent = "Hold";
    $("hold-primary").textContent = "–";
    $("hold-secondary").textContent = results ? "Enter a distance" : "Fill in the rifle setup";
    $("hold-dots").textContent = "";
    return;
  }
  if (Number.isNaN(current.heightM)) {
    $("hold-caption").textContent = "Hold";
    $("hold-primary").textContent = "–";
    $("hold-secondary").textContent = "Pellet doesn't reach this far";
    $("hold-dots").textContent = "";
    return;
  }
  const dots = dotsFor(current.mils);
  const dead = Math.abs(dots) < 0.05;
  $("hold-caption").textContent = dead ? "Dead on" : dots > 0 ? "Hold over" : "Hold under";
  $("hold-primary").textContent = `${fmt(Math.abs(dots), 1)} ${Math.abs(dots) === 1 ? "dot" : "dots"}`;
  $("hold-secondary").textContent = `${impactLabel(current.heightM, metric)} with no holdover`;
  $("hold-dots").textContent = magsValid && mag !== trueMag
    ? `${fmt(Math.abs(current.mils), 1)} true mil, reticle at ${mag}× (true at ${trueMag}×)`
    : "";
}

function renderResults() {
  const metric = state.unit === "m";
  renderClicks(metric);
  renderHoldover(metric);
}

function update(changes, { controls = true } = {}) {
  Object.assign(state, changes);
  save();
  if (controls) renderControls();
  renderResults();
}

for (const group of ["tab", "click", "mode"]) {
  $(group).addEventListener("click", (e) => {
    const value = e.target.closest("button")?.dataset.value;
    if (value) update({ [group]: value });
  });
}

// Sight height and zero describe the rifle, so convert them with the unit rather than reinterpret the number.
$("unit").addEventListener("click", (e) => {
  const value = e.target.closest("button")?.dataset.value;
  if (!value || value === state.unit) return;
  const toMetric = value === "m";
  const sight = Number(state.sight);
  const zero = Number(state.zero);
  update({
    unit: value,
    sight: state.sight === "" ? "" : roundTo1(toMetric ? sight * MM_PER_INCH : sight / MM_PER_INCH),
    zero: state.zero === "" ? "" : roundTo1(toMetric ? zero * YARD_TO_M : zero / YARD_TO_M),
  });
});

for (const id of ["presets", "mil-presets"]) {
  $(id).addEventListener("click", (e) => {
    const value = e.target.closest("button")?.dataset.value;
    if (value) update({ distance: value });
  });
}

// Leave the inputs alone while typing: rewriting their value would clobber partial entries like "1.".
$("distance").addEventListener("input", (e) => update({ distance: e.target.value }, { controls: false }));
$("amount").addEventListener("input", (e) => update({ [state.mode]: e.target.value }, { controls: false }));
for (const field of SETUP_FIELDS) {
  $(field).addEventListener("input", (e) => update({ [field]: e.target.value }, { controls: false }));
}

renderControls();
renderResults();

if ("serviceWorker" in navigator) {
  navigator.serviceWorker.register("sw.js");
}
