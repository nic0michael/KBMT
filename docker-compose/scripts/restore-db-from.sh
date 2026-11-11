#!/usr/bin/env bash
set -e

DATABASE_NAME="n8n"
DATABASE_USER="myuser"
DATABASE_PASSWORD="ChangeThisToAStrongPassword"
BACKUP_DIRECTORY="/var/lib/postgresql/backup"

if [ "$#" -ne 1 ]; then
    echo "Usage: restore-db-from.sh <backup-filename>"
    exit 1
fi

backup_file="$1"
if [ "$(basename -- "$backup_file")" != "$backup_file" ] || [ ! -f "$BACKUP_DIRECTORY/$backup_file" ]; then
    echo "Backup file not found: $backup_file"
    exit 1
fi

read -r -p "Restore $backup_file to $DATABASE_NAME? This replaces the database. Type RESTORE to continue: " confirmation
if [ "$confirmation" != "RESTORE" ]; then
    echo "Restore cancelled"
    exit 0
fi

"$(dirname -- "$0")/make-db-dump.sh"

PGPASSWORD="$DATABASE_PASSWORD" pg_restore \
    -h 127.0.0.1 \
    -p 5432 \
    -U "$DATABASE_USER" \
    --clean \
    --if-exists \
    --exit-on-error \
    --single-transaction \
    -d "$DATABASE_NAME" \
    "$BACKUP_DIRECTORY/$backup_file"

echo "Database restored from: $backup_file"
