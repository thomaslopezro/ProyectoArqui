# Resultado de integración del Taller 3

## Verificado

- Compilación e instalación del APK 0.2.0: correctas; formulario actualizado visible en Android Studio.
- Regresión de mapas, captura y panorama: aprobada (taller3-regresion-final.log).
- Flujo .NET → Java → RabbitMQ → persistencia → catálogo → notificaciones AMQP y Mailpit: aprobado (taller3-integrado-pruebas.log).
- Vinculación de evaluación, mapa de calor y GeoJSON con el mismo ID; asociación posterior e idempotencia: aprobadas.
- Referencia pendiente vinculada automáticamente al llegar el desastre; ID de 100 caracteres: aprobados.
- Caída de Java y reinicio de .NET con catálogo persistente y captura disponible: aprobados.
- Catálogo web: desastre DEMO-T3-INTEGRADO visible y correos confirmados 3/3 (taller3-catalogo-web.png).

## Pendiente concreto

Completar en la pantalla del nuevo APK: actualizar catálogo, retirar túnel, seleccionar un evento descargado, guardar, reabrir y sincronizar tras restablecerlo. El control automático del emulador falla al dirigir clics a la ventana incluso después de traer Android Studio al frente. Esto limita la evidencia interactiva móvil; no invalida las pruebas cruzadas del servidor. Los resultados Android anteriores prueban el flujo previo, no el catálogo recién agregado.

SRS, SAD y ATAM siguen sin modificar. Login permanece separado.
