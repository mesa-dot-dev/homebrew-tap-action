# homebrew-tap-push

A GitHub Action that generates Homebrew formulae from a template and pushes them to a tap repository.

## Usage

```yaml
- name: Push To Tap
  uses: mesa-dev/homebrew-tap-push@v1
  with:
    version: ${{ needs.build.outputs.version }}
    tap: owner/homebrew-tap
    tap-branch: main
    versioned-path: "Formula/my-app@${VERSION}.rb"
    latest-path: Formula/my-app.rb
    template: formula.rb.tmpl
    tap-token: ${{ secrets.TAP_TOKEN }}
```

## Inputs

| Name | Required | Default | Description |
|------|----------|---------|-------------|
| `name` | No | Repository name | Project name (defaults to repository name) |
| `url` | No | Auto-discovered | URL to the artifact (auto-discovered from GitHub release if omitted) |
| `sha256` | No | Auto-computed | SHA256 checksum of the artifact (auto-computed from url if omitted) |
| `version` | No | Current tag | Version string (defaults to current tag with v prefix stripped) |
| `tap` | **Yes** | | Target tap repository, e.g. `owner/homebrew-tap` |
| `tap-branch` | No | `main` | Branch to push to in the tap repository |
| `versioned-path` | No | | Path for versioned formula in the tap, e.g. `Formula/app@${VERSION}.rb` |
| `latest-path` | **Yes** | | Path for latest formula in the tap, e.g. `Formula/app.rb` |
| `template` | **Yes** | | Path to the formula template file in the calling repository |
| `commit-message` | No | `Deploy Formula ${VERSION}` | Commit message (`${VERSION}` is replaced) |
| `tap-token` | **Yes** | | PAT with push access to the tap repository |
| `committer-name` | No | `github-actions[bot]` | Git committer name |
| `committer-email` | No | `github-actions[bot]@users.noreply.github.com` | Git committer email |

## Template Variables

Templates use `${VAR}` placeholders. Only variables actually referenced in your template are resolved, so unused optional inputs are never required.

| Variable | Description |
|----------|-------------|
| `${VERSION}` | Resolved version string (tag with v prefix stripped, or explicit input) |
| `${NAME}` | Project name (explicit input or derived from repository name) |
| `${URL}` | Artifact download URL (explicit input or auto-discovered from GitHub release) |
| `${SHA256}` | SHA256 checksum of the artifact (explicit input or computed by downloading the URL) |
| `${LICENSE}` | SPDX license identifier detected from the repository via the GitHub API |
| `${FORMULA_CLASS_NAME}` | Homebrew formula class name computed in pure Clojure (e.g. `GitFs`, `GitFsAT123`) |

## How It Works

The action is implemented in [Babashka](https://babashka.org/) (a fast-starting Clojure scripting runtime). Babashka is installed automatically on the runner -- no Homebrew installation is needed.

- **Resolves inputs**: version, name, URL, SHA256, and license are resolved from explicit inputs, the current Git tag, the GitHub release, or the GitHub API as needed.
- **Scans the template**: the template file is scanned for `${VAR}` references so only the variables your template actually uses need to be resolvable.
- **Validates requirements**: before generating anything, the action verifies that every referenced variable has a non-empty value, failing early with a clear error if not.
- **Computes formula class names**: Homebrew's `Formulary.class_s` algorithm is reimplemented in pure Clojure, so no Homebrew installation is required on the runner.
- **Generates formulae**: `${VAR}` placeholders are substituted with the resolved values, producing both a latest formula and an optional versioned formula (e.g. `app@1.2.3.rb`).
- **Pushes to the tap**: the tap repository is cloned, the generated formula files are committed, and the result is pushed to the configured branch.

## Development

Requires [Babashka](https://babashka.org/) installed locally.

Run the tests:

```bash
bb test
```

## License

MIT
