import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';

const BAD = [
  /-----BEGIN [A-Z ]*PRIVATE KEY-----/,
  /AKIA[0-9A-Z]{16}/,
  /(password|passwd|secret|api[_-]?key|token)\s*[=:]\s*["'][A-Za-z0-9+/=_-]{12,}["']/i,
  /VITE_[A-Z_]*(SECRET|PASSWORD|PRIVATE|KEY)[A-Z_]*\s*=/,
];

function walk(dir: string): string[] {
  return readdirSync(dir).flatMap((n) => {
    const p = join(dir, n);
    return statSync(p).isDirectory() ? walk(p) : [p];
  });
}

describe('no secrets in the frontend bundle sources', () => {
  it('has none', () => {
    const hits = walk('src')
      .filter((p) => /\.(ts|tsx|json|env)$/.test(p) && !/\.test\.tsx?$/.test(p))
      .filter((p) => BAD.some((re) => re.test(readFileSync(p, 'utf8'))));
    expect(hits).toEqual([]);
  });
});
