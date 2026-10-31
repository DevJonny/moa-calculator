import { test } from "node:test";
import assert from "node:assert/strict";
import { toYards, moaInInches, clickInInches, clicksForOffset } from "./moa.js";

const close = (actual, expected) => assert.ok(Math.abs(actual - expected) < 0.001, `${actual} != ${expected}`);

test("1 MOA at 100 yards is 1.047 inches", () => close(moaInInches(100), 1.047));

test("1/4 MOA click at 100 yards is 0.262 inches", () => close(clickInInches(0.25, 100), 0.262));

test("1/4 MOA click at 50 yards is 0.131 inches", () => close(clickInInches(0.25, 50), 0.131));

test("100 metres is 109.36 yards", () => close(toYards(100, "m"), 109.361));

test("2 inches at 100 yards with 1/4 MOA clicks is ~7.6 clicks", () => close(clicksForOffset(2, 0.25, 100), 7.639));

test("zero distance gives zero clicks rather than infinity", () => assert.equal(clicksForOffset(2, 0.25, 0), 0));
