#!/usr/bin/env bash
set -euo pipefail

# Resolve the project directory even when called from elsewhere or a path with spaces.
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"

# Git Bash may inherit a JAVA_HOME left behind by an uninstalled Windows JDK.
if command -v cygpath >/dev/null 2>&1; then
  java_home_path="${JAVA_HOME:-}"
  if [[ -n "$java_home_path" ]]; then
    java_home_path="$(cygpath -u "$java_home_path")"
  fi
  if [[ -z "$java_home_path" || ! -x "$java_home_path/bin/java" || ! -x "$java_home_path/bin/javac" ]]; then
    for java_version in 17 21 20; do
      for jdk_path in "/c/Program Files/Java/jdk-$java_version"* "/c/Program Files/Eclipse Adoptium/jdk-$java_version"*; do
        if [[ -x "$jdk_path/bin/java" && -x "$jdk_path/bin/javac" ]]; then
          export JAVA_HOME="$jdk_path"
          printf 'Using JDK: %s\n' "$JAVA_HOME" >&2
          break 2
        fi
      done
    done
    if [[ -z "${JAVA_HOME:-}" || ! -x "${JAVA_HOME}/bin/java" || ! -x "${JAVA_HOME}/bin/javac" ]]; then
      printf 'Set JAVA_HOME to an installed JDK (17 recommended), then run this script again.\n' >&2
      exit 1
    fi
  fi
fi

exec sh ./mvnw spring-boot:run "$@"
