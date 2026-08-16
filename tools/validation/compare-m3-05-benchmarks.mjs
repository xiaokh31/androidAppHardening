#!/usr/bin/env node
import { readFileSync } from "node:fs";

if (process.argv.length !== 4) throw new Error("usage: compare-m3-05-benchmarks.mjs first.json second.json");
const first = JSON.parse(readFileSync(process.argv[2], "utf8"));
const second = JSON.parse(readFileSync(process.argv[3], "utf8"));
const key = row => `${row.fixtureId}|${row.metric}`;
const secondRows = new Map(second.results.map(row => [key(row), row]));
if (first.results.length !== second.results.length) throw new Error("benchmark result count changed");
for (const row of first.results) {
  const other = secondRows.get(key(row));
  if (!other) throw new Error(`missing repeated benchmark ${key(row)}`);
  for (const field of ["p50", "p95"]) {
    const denominator = Math.max(1, Math.min(row[field], other[field]));
    const variation = Math.abs(row[field] - other[field]) / denominator;
    if (variation > 0.10) throw new Error(`${key(row)} ${field} variation ${(variation * 100).toFixed(2)}% exceeds 10%`);
  }
}
console.log(`M3-05 repeatability PASS rows=${first.results.length}`);
