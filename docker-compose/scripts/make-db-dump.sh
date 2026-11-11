#!/usr/bin/env bash
set -e

# Keep these connection values aligned with the server's Compose configuration.
DATABASE_NAME="n8n"
DATABASE_USER="myuser"
DATABASE_HOST="127.0.0.1"
DATABASE_PORT="5432"
DATABASE_PASSWORD="ChangeThisToAStrongPassword"
BACKUP_DIRECTORY="/var/lib/postgresql/backup"

timestamp="$(date '+%Y%d%m%H%M%S')"
backup_file="kbmt_${DATABASE_NAME}_${timestamp}.dump"
backup_path="$BACKUP_DIRECTORY/$backup_file"

if ! PGPASSWORD="$DATABASE_PASSWORD" pg_dump \
    -h 127.0.0.1 \
    -p 5432 \
    -U "$DATABASE_USER" \
    -Fc \
    "$DATABASE_NAME" \
    -f "$backup_path"; then
    rm -f "$backup_path"
    echo "Backup failed" >&2
    exit 1
fi

if [ ! -s "$backup_path" ]; then
    rm -f "$backup_path"
    echo "Backup file is empty" >&2
    exit 1
fi

echo "Backup created: $backup_file"
