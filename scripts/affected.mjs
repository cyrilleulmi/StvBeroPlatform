#!/usr/bin/env node
// Which tests does a change need? Reads modules.json (docs/architecture.md) and the changed files, and prints
// the plan. scripts/test-affected.sh runs it; the CI uses it to skip jobs a change cannot touch.
//
//   node scripts/affected.mjs                      # compared with origin/develop, plus uncommitted work
//   node scripts/affected.mjs --base HEAD          # uncommitted work only
//   node scripts/affected.mjs --files a.ts b.java  # these files instead of asking git
//   node scripts/affected.mjs --json               # the plan as JSON
//   node scripts/affected.mjs --check              # only checks that modules.json is complete
//
// How a change spreads:
//   backend file of module M   → backend tests of M and of every module whose backend depends on M
//   frontend file of module M  → frontend checks, E2E specs of M and of every module whose frontend depends on M
//   E2E spec                   → that spec
//   seed and test endpoints, migrations, build files, modules.json, unknown files → everything
//   documents, skills, deployment → nothing
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, readdirSync, appendFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const BACKEND = 'backend/src/';
const JAVA = /^backend\/src\/(main|test)\/java\/ch\/stvbero\/platform\/([^/]+)\//;
const FRONTEND_APP = 'frontend/src/app/';

const args = process.argv.slice(2);
const flag = (name) => args.includes(name);
const option = (name) => (args.includes(name) ? args[args.indexOf(name) + 1] : undefined);

const manifest = JSON.parse(readFileSync(join(ROOT, 'modules.json'), 'utf8')).modules;
const names = Object.keys(manifest);
const specsOnDisk = readdirSync(join(ROOT, 'e2e/tests'))
  .filter((file) => file.endsWith('.spec.ts'))
  .map((file) => file.replace(/\.spec\.ts$/, ''));

// ---------------------------------------------------------------- modules.json is complete
function check() {
  const problems = [];
  const listed = new Set(names.flatMap((name) => manifest[name].e2e ?? []));
  for (const spec of specsOnDisk) {
    if (!listed.has(spec)) problems.push(`e2e/tests/${spec}.spec.ts belongs to no module: add it to "e2e" of its module in modules.json`);
  }
  for (const spec of listed) {
    if (!specsOnDisk.includes(spec)) problems.push(`modules.json lists the E2E spec «${spec}», which does not exist`);
  }
  for (const name of names) {
    for (const side of ['backend', 'frontend']) {
      for (const dependency of manifest[name][side]?.dependsOn ?? []) {
        if (!manifest[dependency]) problems.push(`modules.json: ${name}.${side}.dependsOn names the unknown module «${dependency}»`);
      }
    }
    for (const path of manifest[name].frontend?.paths ?? []) {
      if (!existsSync(join(ROOT, FRONTEND_APP, path))) problems.push(`modules.json: ${name}.frontend.paths names «${path}», which does not exist`);
    }
    const pkg = manifest[name].backend?.package;
    if (pkg && !existsSync(join(ROOT, BACKEND, 'main/java/ch/stvbero/platform', pkg))) {
      problems.push(`modules.json: ${name}.backend.package names «${pkg}», which does not exist`);
    }
  }
  return problems;
}

// ---------------------------------------------------------------- changed files
function git(...parameters) {
  return execFileSync('git', parameters, { cwd: ROOT, encoding: 'utf8' }).split('\n').filter(Boolean);
}

function changedFiles() {
  if (flag('--files')) {
    return args.slice(args.indexOf('--files') + 1).filter((value) => !value.startsWith('--'));
  }
  let base = option('--base');
  if (!base) {
    base = ['origin/develop', 'origin/main'].find((ref) => {
      try {
        git('rev-parse', '--verify', '--quiet', ref);
        return true;
      } catch {
        return false;
      }
    });
    if (!base) throw new Error('Neither origin/develop nor origin/main is known here: pass --base <ref>');
  }
  let from = base;
  try {
    from = git('merge-base', base, 'HEAD')[0];
  } catch {
    // no common ancestor in a shallow clone: compare with the base itself
  }
  const committedAndLocal = git('diff', '--name-only', from);
  const untracked = git('ls-files', '--others', '--exclude-standard');
  return [...new Set([...committedAndLocal, ...untracked])].sort();
}

// ---------------------------------------------------------------- from files to modules
/** Everything whose `side` depends on one of the modules, the modules included. */
function withDependents(modules, side) {
  const result = new Set(modules);
  let grew = true;
  while (grew) {
    grew = false;
    for (const name of names) {
      if (!result.has(name) && (manifest[name][side]?.dependsOn ?? []).some((dependency) => result.has(dependency))) {
        result.add(name);
        grew = true;
      }
    }
  }
  return result;
}

function frontendModuleOf(pathInApp) {
  let best;
  for (const name of names) {
    for (const path of manifest[name].frontend?.paths ?? []) {
      const matches = pathInApp === path || pathInApp.startsWith(`${path}/`);
      if (matches && (!best || path.length > best.path.length)) best = { name, path };
    }
  }
  return best?.name;
}

function plan(files) {
  const backend = new Set(); // modules with a changed backend
  const frontend = new Set(); // modules with a changed frontend
  const specs = new Set(); // changed spec files
  const reasons = []; // why everything runs
  let allBackend = false;
  let allFrontend = false;
  let allE2e = false;
  let contract = false;
  const ignored = [];

  for (const file of files) {
    const java = JAVA.exec(file);
    if (java) {
      const pkg = java[2];
      if (pkg === 'architecture') continue; // the architecture tests always run with the backend
      const module = names.find((name) => manifest[name].backend?.package === pkg);
      if (!module) {
        allBackend = allE2e = true;
        reasons.push(`${file}: package «${pkg}» is no module of modules.json`);
      } else if (manifest[module].backend.affectsAll && java[1] === 'main') {
        allBackend = allE2e = true;
        reasons.push(`${file}: seed and test endpoints are used by every test`);
      } else {
        backend.add(module);
      }
    } else if (file.startsWith('backend/')) {
      if (file === 'backend/README.md') ignored.push(file);
      else {
        allBackend = allE2e = true;
        reasons.push(`${file}: build, configuration or migration of the backend`);
      }
    } else if (file.startsWith(FRONTEND_APP)) {
      const module = frontendModuleOf(file.slice(FRONTEND_APP.length));
      if (module) frontend.add(module);
      else {
        allFrontend = allE2e = true;
        reasons.push(`${file}: belongs to no module of modules.json`);
      }
    } else if (file.startsWith('frontend/')) {
      if (file === 'frontend/README.md' || file === 'frontend/.gitattributes') ignored.push(file);
      else {
        allFrontend = allE2e = true;
        reasons.push(`${file}: build, configuration or global styles of the frontend`);
      }
    } else if (/^e2e\/tests\/[^/]+\.spec\.ts$/.test(file)) {
      specs.add(file.replace(/^e2e\/tests\//, '').replace(/\.spec\.ts$/, ''));
    } else if (file.startsWith('e2e/plans/')) {
      ignored.push(file);
    } else if (file.startsWith('e2e/')) {
      allE2e = true;
      reasons.push(`${file}: shared by all E2E tests`);
    } else if (file === 'docs/api/openapi.yaml') {
      contract = true;
    } else if (file === 'modules.json' || file === 'scripts/affected.mjs') {
      allBackend = allFrontend = allE2e = true;
      reasons.push(`${file}: decides what is tested`);
    } else if (file === 'scripts/e2e.sh') {
      allE2e = true;
      reasons.push(`${file}: starts the E2E tests`);
    } else {
      // documents, skills, status page, deployment (checked by the job «Container image»), other scripts
      ignored.push(file);
    }
  }

  const affectedBackend = allBackend ? new Set(names.filter((name) => manifest[name].backend)) : withDependents(backend, 'backend');
  const affectedFrontend = allFrontend ? new Set(names.filter((name) => manifest[name].frontend)) : withDependents(frontend, 'frontend');
  // The contract concerns every module: its test runs with the backend, the types are built with the frontend
  const runBackend = allBackend || backend.size > 0 || contract;
  const runFrontend = allFrontend || frontend.size > 0 || contract;

  const e2e = new Set(specs);
  if (allE2e) {
    specsOnDisk.forEach((spec) => e2e.add(spec));
  } else {
    for (const name of new Set([...affectedBackend, ...affectedFrontend])) {
      (manifest[name].e2e ?? []).forEach((spec) => e2e.add(spec));
    }
  }
  const everySpec = specsOnDisk.every((spec) => e2e.has(spec));
  const everyBackend = allBackend || names.filter((name) => manifest[name].backend).every((name) => affectedBackend.has(name));

  return {
    files: files.length,
    ignored: ignored.length,
    everything: reasons,
    changed: { backend: [...backend].sort(), frontend: [...frontend].sort(), contract },
    backend: {
      run: runBackend,
      all: runBackend && everyBackend,
      modules: [...affectedBackend].sort(),
      // -Dtest= for Maven: the tests of the affected modules, the architecture tests and the API contract
      tests: runBackend && !everyBackend
        ? [
            ...[...affectedBackend].sort().map((name) => `ch/stvbero/platform/${manifest[name].backend.package}/**/*Test`),
            'ch/stvbero/platform/architecture/*Test',
            'OpenApiContractTest',
          ].join(',')
        : '',
    },
    frontend: { run: runFrontend, modules: [...affectedFrontend].sort() },
    e2e: {
      run: e2e.size > 0,
      all: everySpec,
      specs: everySpec ? [] : [...e2e].sort().map((spec) => `tests/${spec}.spec.ts`),
      count: e2e.size,
      of: specsOnDisk.length,
    },
  };
}

// ---------------------------------------------------------------- output
const problems = check();
if (problems.length) {
  problems.forEach((problem) => console.error(problem));
  process.exit(2);
}
if (flag('--check')) {
  console.log(`modules.json: ${names.length} modules, ${specsOnDisk.length} E2E specs, all assigned.`);
  process.exit(0);
}

const result = plan(changedFiles());

if (process.env.GITHUB_OUTPUT && flag('--github-output')) {
  appendFileSync(
    process.env.GITHUB_OUTPUT,
    `backend=${result.backend.run}\nfrontend=${result.frontend.run}\ne2e=${result.e2e.run}\n`,
  );
}

if (flag('--json')) {
  console.log(JSON.stringify(result, null, 2));
} else {
  const list = (values) => (values.length ? values.join(', ') : '–');
  console.log(`Changed files: ${result.files} (${result.ignored} without tests: documents, skills, deployment)`);
  console.log(`Changed modules: backend ${list(result.changed.backend)} · frontend ${list(result.changed.frontend)}${result.changed.contract ? ' · API contract' : ''}`);
  result.everything.slice(0, 5).forEach((reason) => console.log(`  everything, because ${reason}`));
  if (result.everything.length > 5) console.log(`  … and ${result.everything.length - 5} more`);
  console.log(`Backend:  ${result.backend.run ? (result.backend.all ? 'all tests' : `tests of ${list(result.backend.modules)}`) : 'nothing'}`);
  console.log(`Frontend: ${result.frontend.run ? 'lint, unit tests, build' : 'nothing'}`);
  console.log(`E2E:      ${result.e2e.run ? (result.e2e.all ? `all ${result.e2e.of} specs` : `${result.e2e.count} of ${result.e2e.of} specs`) : 'nothing'}`);
}
