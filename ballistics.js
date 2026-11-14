// Point-mass trajectory with the G1 drag model, for working out holdover from a known zero.
// Standard atmosphere, no wind. Pellet BCs vary a lot, so treat results as a starting point to check on paper.

const G = 9.80665; // m/s²
const AIR_DENSITY = 1.225; // kg/m³ at sea level, 15°C
const SPEED_OF_SOUND = 340.29; // m/s
const KG_PER_M2_PER_LB_PER_IN2 = 703.0696;
const TIME_STEP = 0.0001; // s
const MAX_FLIGHT_TIME = 5; // s

export const FPS_TO_MPS = 0.3048;
export const INCH_TO_M = 0.0254;
export const YARD_TO_M = 0.9144;

// G1 drag coefficient by Mach number. Air rifles stay well under Mach 1, so the table stops there.
const G1 = [
  [0.0, 0.2629], [0.05, 0.2558], [0.1, 0.2487], [0.15, 0.2413], [0.2, 0.2344], [0.25, 0.2278],
  [0.3, 0.2214], [0.35, 0.2155], [0.4, 0.2104], [0.45, 0.2061], [0.5, 0.2032], [0.55, 0.202],
  [0.6, 0.2034], [0.7, 0.2165], [0.725, 0.223], [0.75, 0.2313], [0.775, 0.2417], [0.8, 0.2546],
  [0.825, 0.2706], [0.85, 0.2901], [0.875, 0.3136], [0.9, 0.3415], [0.925, 0.3734], [0.95, 0.4084],
  [0.975, 0.4448], [1.0, 0.4805],
];

function g1DragCoefficient(mach) {
  if (mach <= G1[0][0]) return G1[0][1];
  for (let i = 1; i < G1.length; i++) {
    const [m1, cd1] = G1[i];
    if (mach <= m1) {
      const [m0, cd0] = G1[i - 1];
      return cd0 + ((cd1 - cd0) * (mach - m0)) / (m1 - m0);
    }
  }
  return G1[G1.length - 1][1];
}

// Height of the projectile relative to the line of sight (m, negative = below) at each range in `rangesM`,
// which must be ascending. NaN for any range the projectile doesn't reach.
function flightPath(angle, muzzleVelocity, bcKgPerM2, sightHeight, rangesM) {
  let x = 0;
  let y = -sightHeight;
  let vx = muzzleVelocity * Math.cos(angle);
  let vy = muzzleVelocity * Math.sin(angle);
  const heights = [];

  for (let t = 0; heights.length < rangesM.length && t < MAX_FLIGHT_TIME; t += TIME_STEP) {
    const v = Math.hypot(vx, vy);
    const drag = (AIR_DENSITY * v * v * g1DragCoefficient(v / SPEED_OF_SOUND) * Math.PI) / (8 * bcKgPerM2);
    const nx = x + vx * TIME_STEP;
    const ny = y + vy * TIME_STEP;
    vx -= ((drag * vx) / v) * TIME_STEP;
    vy -= ((drag * vy) / v + G) * TIME_STEP;
    while (heights.length < rangesM.length && nx >= rangesM[heights.length]) {
      const f = (rangesM[heights.length] - x) / (nx - x);
      heights.push(y + (ny - y) * f);
    }
    x = nx;
    y = ny;
  }
  while (heights.length < rangesM.length) heights.push(NaN);
  return heights;
}

// Bore angle that puts the projectile back on the line of sight at the zero range.
function zeroAngle(muzzleVelocity, bcKgPerM2, sightHeight, zeroM) {
  let lo = -0.05;
  let hi = 0.1;
  for (let i = 0; i < 60; i++) {
    const mid = (lo + hi) / 2;
    const [height] = flightPath(mid, muzzleVelocity, bcKgPerM2, sightHeight, [zeroM]);
    if (Number.isNaN(height) || height < 0) lo = mid;
    else hi = mid;
  }
  return (lo + hi) / 2;
}

/**
 * Holdover at each range for a rifle zeroed at `zeroM`.
 * Returns null when the setup can't be solved (missing or non-positive inputs).
 * Each entry: { rangeM, heightM (relative to line of sight), mils (positive = hold over) }.
 */
export function holdovers({ muzzleVelocityMps, bc, sightHeightM, zeroM }, rangesM) {
  if (!(muzzleVelocityMps > 0 && bc > 0 && sightHeightM >= 0 && zeroM > 0)) return null;
  const bcKgPerM2 = bc * KG_PER_M2_PER_LB_PER_IN2;
  const angle = zeroAngle(muzzleVelocityMps, bcKgPerM2, sightHeightM, zeroM);
  const heights = flightPath(angle, muzzleVelocityMps, bcKgPerM2, sightHeightM, rangesM);
  return rangesM.map((rangeM, i) => ({
    rangeM,
    heightM: heights[i],
    mils: (-Math.atan2(heights[i], rangeM)) * 1000,
  }));
}

// A second focal plane mil-dot reticle only reads true at one magnification; elsewhere each dot covers more or less.
export function reticleDots(mils, magnification, trueAtMagnification) {
  return (mils * magnification) / trueAtMagnification;
}
