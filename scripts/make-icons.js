// Draws the crosshair app icons as PNGs with no dependencies. Run: npm run icons
import { deflateSync } from "node:zlib";
import { mkdirSync, writeFileSync } from "node:fs";

const BG = [0x14, 0x17, 0x1a];
const FG = [0xf2, 0xa5, 0x41];

const CRC_TABLE = Array.from({ length: 256 }, (_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c >>> 0;
});

function crc32(buf) {
  let c = 0xffffffff;
  for (const b of buf) c = CRC_TABLE[(c ^ b) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type, "ascii"), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(body));
  return Buffer.concat([len, body, crc]);
}

function png(size, pixel) {
  const raw = Buffer.alloc(size * (size * 3 + 1));
  for (let y = 0; y < size; y++) {
    const row = y * (size * 3 + 1);
    for (let x = 0; x < size; x++) raw.set(pixel(x + 0.5, y + 0.5), row + 1 + x * 3);
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(size, 0);
  ihdr.writeUInt32BE(size, 4);
  ihdr[8] = 8; // bit depth
  ihdr[9] = 2; // RGB
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk("IHDR", ihdr),
    chunk("IDAT", deflateSync(raw)),
    chunk("IEND", Buffer.alloc(0)),
  ]);
}

// `scale` shrinks the design so maskable icons keep it inside the safe zone.
function crosshair(size, scale) {
  const c = size / 2;
  const r = size * 0.34 * scale;
  const stroke = size * 0.035 * scale;
  const gap = size * 0.07 * scale;
  const arm = size * 0.44 * scale;
  const dot = size * 0.035 * scale;

  return (x, y) => {
    const dx = x - c;
    const dy = y - c;
    const d = Math.hypot(dx, dy);
    const ring = Math.abs(d - r) - stroke / 2;
    const hLine = Math.max(Math.abs(dy) - stroke / 2, gap - Math.abs(dx), Math.abs(dx) - arm);
    const vLine = Math.max(Math.abs(dx) - stroke / 2, gap - Math.abs(dy), Math.abs(dy) - arm);
    const centre = d - dot;
    const dist = Math.min(ring, hLine, vLine, centre);
    const a = Math.min(1, Math.max(0, 0.5 - dist));
    return BG.map((b, i) => Math.round(b + (FG[i] - b) * a));
  };
}

const out = new URL("../icons/", import.meta.url);
mkdirSync(out, { recursive: true });
for (const [name, size, scale] of [
  ["icon-192.png", 192, 1],
  ["icon-512.png", 512, 1],
  ["icon-maskable-512.png", 512, 0.75],
  ["apple-touch-icon.png", 180, 0.9],
]) {
  writeFileSync(new URL(name, out), png(size, crosshair(size, scale)));
}
