// @vitest-environment node
import { ESLint } from 'eslint';

/** SEC-07: "ESLint fails on dangerouslySetInnerHTML", checked with the project's own config. */
const eslint = new ESLint({ cwd: process.cwd() });

async function lint(code: string) {
  const [result] = await eslint.lintText(code, { filePath: 'src/components/Probe.tsx' });
  return result.messages.filter((m) => m.severity === 2).map((m) => m.ruleId);
}

describe('SEC-07 lint rules', () => {
  it('fails on dangerouslySetInnerHTML', async () => {
    const errors = await lint(
      `export function Bad({ name }: { name: string }) { return <div dangerouslySetInnerHTML={{ __html: name }} />; }`,
    );
    expect(errors).toContain('react/no-danger');
  });

  it('fails on innerHTML assignment', async () => {
    const errors = await lint(`export function bad(el: HTMLElement, s: string) { el.innerHTML = s; }`);
    expect(errors).toContain('no-restricted-syntax');
  });

  it('fails on eval', async () => {
    const errors = await lint(`export function bad(s: string) { return eval(s); }`);
    expect(errors).toContain('no-eval');
  });

  it('passes safe JSX', async () => {
    const errors = await lint(`export function Good({ name }: { name: string }) { return <div>{name}</div>; }`);
    expect(errors).toEqual([]);
  });
});
