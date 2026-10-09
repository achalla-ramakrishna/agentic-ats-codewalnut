#!/usr/bin/env bash
# Run on a separate trusted backup host; dump travels over SSH directly into age encryption.
# Requires the forced-command backup account documented in the runbook; no Docker-group access.
# Age public recipient is not a secret. Private age key stays offline.
set -euo pipefail
umask 077
if [[ $# != 3 ]]; then
  echo 'Usage: backup.sh ssh-host age1recipient /offserver/backup.sql.gz.age' >&2
  exit 2
fi
host=$1
recipient=$2
destination=$3
[[ "$host" != -* && "$recipient" == age1* && "$destination" == *.sql.gz.age ]]
[[ ! -e "$destination" ]] || { echo 'Refusing to replace a backup' >&2; exit 1; }
temporary=$(mktemp "${destination}.incomplete.XXXXXX")
trap 'rm -f "$temporary"' EXIT
ssh -o BatchMode=yes "$host" ats-database-backup \
  | gzip | age -r "$recipient" > "$temporary"
ln "$temporary" "$destination"
rm "$temporary"
echo 'Encrypted backup completed on the off-server host. Verify and apply retention there.'
