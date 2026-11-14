import { test } from "node:test";
import assert from "node:assert/strict";
import { holdovers, reticleDots, FPS_TO_MPS, INCH_TO_M, YARD_TO_M } from "./ballistics.js";

const airRifle = {
  muzzleVelocityMps: 790 * FPS_TO_MPS,
  bc: 0.021,
  sightHeightM: 1.5 * INCH_TO_M,
  zeroM: 25 * YARD_TO_M,
};
const yards = (...ys) => ys.map((y) => y * YARD_TO_M);

test("holdover is zero at the zero range", () => {
  const [atZero] = holdovers(airRifle, yards(25));
  assert.ok(Math.abs(atZero.heightM) < 0.0005, `${atZero.heightM} m off line of sight`);
});

test("holdover grows past the zero", () => {
  const [at35, at45, at55] = holdovers(airRifle, yards(35, 45, 55)).map((h) => h.mils);
  assert.ok(0 < at35 && at35 < at45 && at45 < at55, `${at35}, ${at45}, ${at55}`);
});

test("close in, the pellet is still below the scope so you hold over", () => {
  const [at5] = holdovers(airRifle, yards(5));
  assert.ok(at5.mils > 0, `${at5.mils}`);
});

test("with negligible drag the path matches the vacuum formula", () => {
  const setup = { muzzleVelocityMps: 300, bc: 1e9, sightHeightM: 0.04, zeroM: 30 };
  const [at60] = holdovers(setup, [60]);

  // Vacuum: y(x) = -h + x·tanθ - g·x² / (2v²cos²θ), with θ chosen so y(zero) = 0.
  const { muzzleVelocityMps: v, sightHeightM: h, zeroM: z } = setup;
  const y = (theta, x) => -h + x * Math.tan(theta) - (9.80665 * x * x) / (2 * v * v * Math.cos(theta) ** 2);
  let theta = 0;
  for (let i = 0; i < 50; i++) theta = Math.atan((h + (9.80665 * z * z) / (2 * v * v * Math.cos(theta) ** 2)) / z);

  assert.ok(Math.abs(at60.heightM - y(theta, 60)) < 0.001, `${at60.heightM} vs ${y(theta, 60)}`);
});

test("missing inputs give no result", () => assert.equal(holdovers({ ...airRifle, bc: 0 }, [10]), null));

test("a reticle true at 10x reads half the dots at 5x", () => assert.equal(reticleDots(1, 5, 10), 0.5));
