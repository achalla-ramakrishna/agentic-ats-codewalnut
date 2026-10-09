#!/bin/sh
# Install root-owned, mode 0755 at /usr/local/libexec/ats-backup-source.
# The dedicated SSH backup user may sudo ONLY this immutable, argument-free command.
# No caller-provided command, environment, path, arguments or database name is evaluated.
set -eu
if [ "$#" -ne 0 ]; then
    echo 'No arguments accepted' >&2
    exit 2
fi
exec /usr/bin/env -i PATH=/usr/bin:/bin /usr/bin/docker exec ats-mysql-1 \
    mysqldump --defaults-extra-file=/run/secrets/ats-backup.cnf \
    --single-transaction --quick --hex-blob --routines --triggers --events \
    --no-tablespaces --set-gtid-purged=OFF --default-character-set=utf8mb4 \
    --max-allowed-packet=128M ats
