// 1 MOA is 1/60 of a degree. At 100 yards (3600 inches) that subtends ~1.047".
const MOA_RADIANS = Math.PI / (180 * 60);
const INCHES_PER_YARD = 36;
const YARDS_PER_METRE = 1 / 0.9144;

export const MM_PER_INCH = 25.4;

export function toYards(distance, unit) {
  return unit === "m" ? distance * YARDS_PER_METRE : distance;
}

export function moaInInches(distanceYards) {
  return distanceYards * INCHES_PER_YARD * Math.tan(MOA_RADIANS);
}

export function clickInInches(clickMoa, distanceYards) {
  return clickMoa * moaInInches(distanceYards);
}

export function clicksForOffset(offsetInches, clickMoa, distanceYards) {
  const perClick = clickInInches(clickMoa, distanceYards);
  return perClick > 0 ? offsetInches / perClick : 0;
}
