# Comprobación de la API en ejecución — 11 de noviembre de 2026

Ejecutada contra `http://127.0.0.1:18085` (ambiente Docker Compose en una máquina), con datos sintéticos, antes de reconstruir con el ciclo de avisos de 1 s. Resultado: 46 de 46 comprobaciones correctas.

## Alcance

Una máquina, poca carga, un solo organismo. No cubre la app Android, el inicio de sesión por EJB remoto, la caída de RabbitMQ, los reinicios ni la separación entre dos organismos; esos casos están en los demás registros de esta carpeta.

## Comprobaciones

- Sesión: sin token se rechaza (401); clave incorrecta rechazada; inicio de sesión del coordinador; consulta de la sesión; vigencia de 8 horas; cierre de sesión; un token cerrado deja de servir.
- Roles: el usuario de consulta lee, y no puede registrar desastres, cambiar el nivel, corregir evaluaciones ni sincronizar lotes (403).
- Desastres: registrar (201); repetir el mismo no duplica (200); mismo identificador con otro contenido (409); cinco reintentos simultáneos del cambio de nivel dan un solo cambio en el historial; versión base obsoleta (409).
- Avisos: suscripción de un sistema; aviso publicado y correo confirmado al registrar (2,7 s) y al cambiar el nivel (3,2 s).
- Campo: lote con evaluación y fotografía; evaluación preliminar sin desastre y vínculo posterior; corrección que crea la versión 1; reintento que no duplica; propuesta sobre versión vieja (409) sin sobrescribir; reenvío sobre la versión revisada; historial con dos correcciones; fotografía con token (200) y sin token (401); mapa de calor, panorama y exportación GeoJSON.
- Solicitudes de recursos: creación desde una evaluación, con el origen puesto por el servidor; reintento que no duplica; cantidad 0 rechazada; solicitud directa; pendientes; tres atenciones simultáneas (una correcta, dos con 409); historial.
- Otros: sonda de salud, catálogos y WSDL del inicio de sesión por SOAP.

## Tiempos observados

Estos tiempos se midieron con el cliente en la misma máquina que el servidor, sin red real, con un solo usuario salvo donde se indica, y con una fotografía de prueba de un píxel (menos de 100 bytes). Muestran que el flujo funciona. No demuestran el rendimiento con red móvil, con fotografías reales ni con la carga del enunciado.

| Operación | Tiempo |
| --- | --- |
| Consulta de desastres, 20 repeticiones | 4 a 8 ms |
| Consulta con 6 solicitudes simultáneas, 600 en total | mediana 17 ms, percentil 95 de 24 a 30 ms |
| Registro de desastre | 22 ms |
| Lote de campo con la fotografía de un píxel; no incluye transferencia real | 12 ms |
| Solicitud de recursos | 57 ms |

## Datos que quedaron en la base de laboratorio

Un desastre `CLAUDE-PRUEBA-…`, un sistema suscrito `claude-…`, un usuario `claude_consulta_…`, dos evaluaciones y dos solicitudes.
