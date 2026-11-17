#!/bin/sh
set -eu
cd "$(dirname "$0")"
python3 servidor/scripts/esperar_api.py
python3 servidor/scripts/verificar_api.py
python3 servidor/scripts/verificar_integracion.py
if [ "${CAMPO_DIFERIDA:-false}" = "true" ]; then
    python3 servidor/scripts/verificar_campo.py --url "${MAPAS_URL:-http://127.0.0.1:18085}" --diferida
else
    python3 servidor/scripts/verificar_campo.py --url "${MAPAS_URL:-http://127.0.0.1:18085}"
fi
