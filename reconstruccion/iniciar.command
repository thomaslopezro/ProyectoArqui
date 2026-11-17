#!/bin/bash
# Construye y levanta el módulo de Reconstrucción. Doble clic en Finder o: ./iniciar.command
# Deja la salida también en arranque.log, en esta misma carpeta.
cd "$(dirname "$0")" || exit 1
if ! docker info >/dev/null 2>&1; then
  echo "Docker no está corriendo. Abra Docker Desktop, espere a que arranque y vuelva a ejecutar este archivo." | tee arranque.log
  read -r -p "Enter para cerrar..." _
  exit 1
fi
docker compose --progress plain up --build 2>&1 | tee arranque.log
