# Frontend dependency baseline

This directory is a supply-chain fixture for P01, not the Teyru website implementation.

- Node: `22.22.2`
- npm: `10.9.7`
- Install: `npm ci --ignore-scripts --audit=false --fund=false`
- Direct dependencies: `astro@7.3.2`, `@astrojs/starlight@0.42.0`

The committed lock is npm lockfileVersion 3. Lifecycle scripts stay disabled during dependency installation. Do not commit `node_modules`.

Regenerate the repository-level component manifest deterministically from the lock:

```bash
jq -S -f config/frontend/generate-components.jq \
  config/frontend/package-lock.json > third-party/frontend-components.json
```

Verify that the committed manifest is current without overwriting it:

```bash
tmp_file="$(mktemp)"
jq -S -f config/frontend/generate-components.jq \
  config/frontend/package-lock.json > "$tmp_file"
cmp "$tmp_file" third-party/frontend-components.json
rm "$tmp_file"
```

Every dependency update must regenerate both files, pass a clean `npm ci`, inventory all licenses, and receive independent review. Package-lock integrity values lock registry bytes; P51 still reviews what the generated static site actually bundles and distributes.
