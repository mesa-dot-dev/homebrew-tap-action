<div align="center">
  <h1>homebrew-tap-action</h1>
  <p><strong>Generate and push Homebrew formulae to a tap repository — from a template.</strong></p>
</div>

<div align="center">

[![CI](https://img.shields.io/github/actions/workflow/status/mesa-dot-dev/homebrew-tap-action/ci.yml?branch=main&label=CI)](https://github.com/mesa-dot-dev/homebrew-tap-action/actions/workflows/ci.yml)
[![License](https://img.shields.io/github/license/mesa-dot-dev/homebrew-tap-action)](https://github.com/mesa-dot-dev/homebrew-tap-action/blob/main/LICENSE)

</div>

---

## Quick Start

```yaml
- uses: mesa-dot-dev/homebrew-tap-action@v1
  with:
    tap: owner/homebrew-tap
    latest-path: Formula/my-app.rb
    template: formula.rb.tmpl
    tap-token: ${{ secrets.TAP_TOKEN }}
```

That's it. The action resolves the version from the current Git tag, discovers the release artifact URL, computes its SHA256, and pushes a generated formula to your tap.

## Usage

```yaml
- uses: mesa-dot-dev/homebrew-tap-action@v1
  with:
    # Project name (defaults to repository name).
    name: ''

    # URL to the artifact. Auto-discovered from the GitHub release if omitted.
    url: ''

    # SHA256 checksum of the artifact. Auto-computed from the URL if omitted.
    sha256: ''

    # Version string. Defaults to the current tag with the v prefix stripped.
    version: ''

    # Target tap repository, e.g. owner/homebrew-tap.
    # Required.
    tap: ''

    # Branch to push to in the tap repository.
    # Default: main
    tap-branch: ''

    # Path for a versioned formula in the tap, e.g. Formula/app@${VERSION}.rb.
    # ${VERSION} is replaced with the resolved version.
    versioned-path: ''

    # Path for the latest (unversioned) formula in the tap, e.g. Formula/app.rb.
    # Required.
    latest-path: ''

    # Path to the formula template file in the calling repository.
    # Required.
    template: ''

    # Commit message. ${VERSION} is replaced with the resolved version.
    # Default: Deploy Formula ${VERSION}
    commit-message: ''

    # PAT with push access to the tap repository.
    # Required.
    tap-token: ''

    # Git committer name.
    # Default: github-actions[bot]
    committer-name: ''

    # Git committer email.
    # Default: github-actions[bot]@users.noreply.github.com
    committer-email: ''
```

### Outputs

| Name | Description |
|------|-------------|
| `version` | The resolved version string |
| `sha256` | The resolved or computed SHA256 checksum |

## Template Variables

Templates use `${VAR}` placeholders. Only variables actually referenced in your template need to be resolvable — unused ones are ignored.

| Variable | Description |
|----------|-------------|
| `${VERSION}` | Resolved version string (tag with `v` prefix stripped, or explicit input) |
| `${NAME}` | Project name (explicit input or derived from repository name) |
| `${URL}` | Artifact download URL (explicit input or auto-discovered from GitHub release) |
| `${SHA256}` | SHA256 checksum of the artifact (explicit input or computed by downloading the URL) |
| `${LICENSE}` | SPDX license identifier detected from the repository via the GitHub API |
| `${FORMULA_CLASS_NAME}` | Homebrew formula class name (e.g. `MyApp`, `MyAppAT123`) |

### Example template

```ruby
class ${FORMULA_CLASS_NAME} < Formula
  desc "My application"
  homepage "https://github.com/owner/my-app"
  url "${URL}"
  sha256 "${SHA256}"
  license "${LICENSE}"
  version "${VERSION}"

  def install
    bin.install "my-app"
  end
end
```

## Scenarios

### Publish on every release

```yaml
on:
  release:
    types: [published]

jobs:
  homebrew:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: mesa-dot-dev/homebrew-tap-action@v1
        with:
          tap: owner/homebrew-tap
          latest-path: Formula/my-app.rb
          template: formula.rb.tmpl
          tap-token: ${{ secrets.TAP_TOKEN }}
```

### Keep versioned formulae alongside latest

```yaml
- uses: mesa-dot-dev/homebrew-tap-action@v1
  with:
    tap: owner/homebrew-tap
    latest-path: Formula/my-app.rb
    versioned-path: "Formula/my-app@${VERSION}.rb"
    template: formula.rb.tmpl
    tap-token: ${{ secrets.TAP_TOKEN }}
```

This produces both `Formula/my-app.rb` (latest) and `Formula/my-app@1.2.3.rb` (pinned), so users can `brew install owner/tap/my-app@1.2.3`.

### Explicit version and URL

```yaml
- uses: mesa-dot-dev/homebrew-tap-action@v1
  with:
    version: ${{ needs.build.outputs.version }}
    url: "https://example.com/my-app-${{ needs.build.outputs.version }}.tar.gz"
    tap: owner/homebrew-tap
    latest-path: Formula/my-app.rb
    template: formula.rb.tmpl
    tap-token: ${{ secrets.TAP_TOKEN }}
```

### Use the resolved outputs

```yaml
- uses: mesa-dot-dev/homebrew-tap-action@v1
  id: tap
  with:
    tap: owner/homebrew-tap
    latest-path: Formula/my-app.rb
    template: formula.rb.tmpl
    tap-token: ${{ secrets.TAP_TOKEN }}

- run: echo "Published version ${{ steps.tap.outputs.version }} (sha256 ${{ steps.tap.outputs.sha256 }})"
```

## How It Works

The action is implemented in [Babashka](https://babashka.org/) (a fast-starting Clojure scripting runtime). Babashka is installed automatically on the runner — no Homebrew or JVM required.

```
Runner                                        Tap repository
───────                                       ──────────────
1. Resolve version, name, URL, SHA256, license
2. Scan template for ${VAR} references
3. Validate all referenced vars are resolved
4. Substitute placeholders → formula files
5. Clone tap repo                       ──────>  git clone
6. Write formula files
7. Commit & push                        ──────>  Formula/app.rb updated
```

## Development

Requires [Babashka](https://babashka.org/) installed locally.

```bash
bb test          # run tests
bb lint          # lint with clj-kondo
bb fmt-check     # check formatting
bb fmt           # fix formatting
```

## License

[MIT](LICENSE)
