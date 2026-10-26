// SEC-07: lint rules that make XSS sinks a build failure, not a code review comment.
import js from '@eslint/js';
import tseslint from 'typescript-eslint';
import react from 'eslint-plugin-react';
import globals from 'globals';

export const xssRules = {
  'react/no-danger': 'error',
  'react/no-danger-with-children': 'error',
  'no-eval': 'error',
  'no-implied-eval': 'error',
  'no-new-func': 'error',
  'no-script-url': 'error',
  'no-restricted-syntax': [
    'error',
    {
      selector: "AssignmentExpression[left.property.name=/^(innerHTML|outerHTML)$/]",
      message: 'SEC-07: do not assign HTML strings. Render text with JSX instead.',
    },
    {
      selector: "CallExpression[callee.property.name='insertAdjacentHTML']",
      message: 'SEC-07: insertAdjacentHTML is an XSS sink.',
    },
    {
      selector: "CallExpression[callee.object.name='document'][callee.property.name='write']",
      message: 'SEC-07: document.write is an XSS sink.',
    },
  ],
};

export default tseslint.config(
  { ignores: ['dist', 'node_modules'] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ['**/*.{ts,tsx}'],
    plugins: { react },
    languageOptions: {
      globals: { ...globals.browser },
      parserOptions: { ecmaFeatures: { jsx: true } },
    },
    settings: { react: { version: '18.3' } },
    rules: xssRules,
  },
);
