#!/bin/sh
set -eu
cd "$(dirname "$0")"
docker compose -f servidor/compose.yaml up -d --wait database
for archivo in nucleo/migraciones/V*.sql; do
  docker compose -f servidor/compose.yaml exec -T database psql -v ON_ERROR_STOP=1 -U pgdr -d pgdrdb < "$archivo"
done
docker compose -f servidor/compose.yaml up -d --build
python3 servidor/scripts/esperar_api.py
python3 servidor/scripts/esperar_nucleo.py
python3 servidor/scripts/esperar_taller3.py
python3 servidor/scripts/esperar_reconstruccion.py
printf '\nSolución disponible: http://127.0.0.1:18085\n'
