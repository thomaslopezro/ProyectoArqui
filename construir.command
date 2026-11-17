#!/bin/bash
# Deja esta ventana abierta. Ejecuta, cuando se le pide, solo estas acciones fijas del proyecto:
#   iniciar     -> ./iniciar.sh (construye y levanta la plataforma con Docker)
#   verificar   -> python3 servidor/scripts/verificar_reconstruccion.py
#   estado      -> docker compose ps
#   logs:NOMBRE -> últimas líneas de un servicio de esta plataforma
# Se le pide escribiendo la acción en .construir/pedido; el resultado queda en .construir/salida.log
cd "$(dirname "$0")" || exit 1
export PATH="/usr/local/bin:/opt/homebrew/bin:/Applications/Docker.app/Contents/Resources/bin:$PATH"
mkdir -p .construir
rm -f .construir/pedido
echo "listo $(date)" > .construir/estado
echo "Esperando pedidos de construcción. Cierra esta ventana para detenerlo."
while true; do
  if [ -f .construir/pedido ]; then
    accion="$(head -c 60 .construir/pedido | tr -d '\r\n')"
    rm -f .construir/pedido
    echo "ejecutando $accion $(date)" > .construir/estado
    echo "== $accion · $(date)"
    case "$accion" in
      iniciar) ./iniciar.sh > .construir/salida.log 2>&1; codigo=$? ;;
      verificar) python3 servidor/scripts/verificar_reconstruccion.py > .construir/salida.log 2>&1; codigo=$? ;;
      estado) docker compose -f servidor/compose.yaml ps -a > .construir/salida.log 2>&1; codigo=$? ;;
      logs:reconstruccion|logs:reconstruccion-web|logs:reconstruccion-db|logs:nucleo|logs:api|logs:web|logs:desastres|logs:rabbitmq)
        docker compose -f servidor/compose.yaml logs --no-color --tail 150 "${accion#logs:}" > .construir/salida.log 2>&1; codigo=$? ;;
      *) echo "Acción no permitida: $accion" > .construir/salida.log; codigo=64 ;;
    esac
    echo "terminado $accion codigo=$codigo $(date)" > .construir/estado
    echo "   terminó con código $codigo"
  fi
  sleep 2
done
