#!/usr/bin/env bash
set -euo pipefail

# --- Functions (sourced by tests) ---

resolve_name() { :; }
resolve_version() { :; }
detect_required_vars() { :; }
validate_vars() { :; }
resolve_url() { :; }
resolve_sha256() { :; }
resolve_license() { :; }
compute_class_name() { :; }
generate_formula() { :; }
push_to_tap() { :; }

# --- Main (skipped when sourced) ---

main() {
  echo "TODO: implement"
}

if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  main "$@"
fi
