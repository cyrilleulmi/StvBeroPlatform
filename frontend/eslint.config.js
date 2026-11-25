// @ts-check
const eslint = require('@eslint/js');
const { defineConfig } = require('eslint/config');
const tseslint = require('typescript-eslint');
const angular = require('angular-eslint');
const manifest = require('../modules.json');

// --- Module boundaries ------------------------------------------------------
// Source of truth: ../modules.json (also used by the backend ArchUnit test and
// scripts/test-affected.sh). Explained in docs/architecture.md.
// Modules without a frontend (e.g. the test support of the backend) have no folders here
const modules = Object.entries(manifest.modules).filter(([, module]) => module.frontend);

/** Import patterns (gitignore style, imports are relative) that point into a module's folders/files. */
const patternsOf = (module) =>
  module.frontend.paths.flatMap((path) => {
    const base = path.replace(/\.ts$/, '');
    return path.endsWith('.ts') ? [`**/${base}`] : [`**/${base}/**`];
  });

const boundaryBlocks = modules.map(([name, module]) => ({
  files: module.frontend.paths.map((path) =>
    path.endsWith('.ts') ? `src/app/${path}` : `src/app/${path}/**/*.ts`,
  ),
  rules: {
    'no-restricted-imports': [
      'error',
      {
        patterns: modules
          .filter(([other]) => other !== name && !module.frontend.dependsOn.includes(other))
          .map(([other, otherModule]) => ({
            group: patternsOf(otherModule),
            message:
              `Module boundary: «${name}» (${module.frontend.paths.join(', ')}) must not import from ` +
              `«${other}» (${otherModule.frontend.paths.join(', ')}). Allowed: ` +
              `${module.frontend.dependsOn.join(', ') || 'nothing'}. See docs/architecture.md.`,
          })),
      },
    ],
  },
}));

// --- Size limits --------------------------------------------------------------
const MAX_LINES = 500;
const MAX_COMPLEXITY = 20;
const maxLines = (max) => ['error', { max, skipBlankLines: true, skipComments: true }];

// FROZEN LIST: files that were already above the limits when the limits were introduced.
// Value = current size (max-lines rounded up to the next 10, complexity as is), so these files
// cannot grow. Entries may only be lowered or removed (never raised, never added);
// new files must respect the general limits above.
const FROZEN_MAX_LINES = {
  'attendance/event-form.ts': 650,
  'attendance/event-variants.ts': 630,
  'drivers/driver-list.ts': 570,
  'drivers/driver-plan.ts': 930,
  'pages/administration/membership-table.ts': 890,
  'pages/administration/team-wizard.ts': 530,
};
const FROZEN_COMPLEXITY = {
  'attendance/event-form.ts': 34,
  'attendance/event-variants.ts': 23,
};

module.exports = defineConfig([
  // Generated from docs/api/openapi.yaml (npm run api-types)
  { ignores: ['src/app/api/schema.d.ts'] },
  {
    files: ['**/*.ts'],
    extends: [
      eslint.configs.recommended,
      tseslint.configs.recommended,
      tseslint.configs.stylistic,
      angular.configs.tsRecommended,
    ],
    processor: angular.processInlineTemplates,
    rules: {
      '@angular-eslint/directive-selector': [
        'error',
        {
          type: 'attribute',
          prefix: 'app',
          style: 'camelCase',
        },
      ],
      '@angular-eslint/component-selector': [
        'error',
        {
          type: 'element',
          prefix: 'app',
          style: 'kebab-case',
        },
      ],
    },
  },
  ...boundaryBlocks,
  {
    files: ['src/app/**/*.ts'],
    rules: {
      'max-lines': maxLines(MAX_LINES),
      complexity: ['error', { max: MAX_COMPLEXITY }],
    },
  },
  // Dialogs open through core/dialog.ts, which renders their content first (see there).
  {
    files: ['src/app/**/*.ts'],
    ignores: ['src/app/core/dialog.ts', 'src/**/*.spec.ts'],
    rules: {
      'no-restricted-syntax': [
        'error',
        {
          selector: "CallExpression[callee.property.name='showModal']",
          message:
            'Open dialogs with showModal(dialog, changes) from core/dialog.ts: it renders the content first, ' +
            'so that no field is still empty when the dialog shows.',
        },
      ],
    },
  },
  // Exempt from max-lines: the central UI texts and spec files.
  {
    files: ['src/app/core/texts.ts', 'src/**/*.spec.ts'],
    rules: { 'max-lines': 'off' },
  },
  ...Object.entries(FROZEN_MAX_LINES).map(([file, max]) => ({
    files: [`src/app/${file}`],
    rules: { 'max-lines': maxLines(max) },
  })),
  ...Object.entries(FROZEN_COMPLEXITY).map(([file, max]) => ({
    files: [`src/app/${file}`],
    rules: { complexity: ['error', { max }] },
  })),
  {
    files: ['**/*.html'],
    extends: [angular.configs.templateRecommended, angular.configs.templateAccessibility],
    rules: {},
  },
]);
