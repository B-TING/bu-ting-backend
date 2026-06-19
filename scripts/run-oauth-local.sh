#!/usr/bin/env bash
set -euo pipefail

if [[ ! -f .env ]]; then
  echo ".env file not found. Copy .env.example to .env and fill in OAuth2 credentials first." >&2
  exit 1
fi

set -a
source .env
set +a

./gradlew bootRun
