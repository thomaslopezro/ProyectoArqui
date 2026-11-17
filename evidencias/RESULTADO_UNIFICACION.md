# Resultado actual de la unificación

Se unieron la entrega persistencia/login de PROYECTO ARQUI y nuestra integración Mapas/DAL + Campo offline + Taller 3. Los originales de Documents/arquitectura y los archivos sources/ permanecen intactos. No se editaron SRS, SAD o ATAM.

## Comprobado

- Núcleo EJB/JPA/JTA compilado con 11 pruebas unitarias aprobadas, incluida la regresión de huellas con saltos de línea.
- Taller 3 compilado con sus pruebas y desplegado correctamente tras ajustar las vistas EJB del repositorio.
- .NET compilado y desplegado; migraciones V001–V005 ejecutadas en el nuevo volumen PostgreSQL desde cero.
- 6 pruebas de DAL, 4 de panorama y el flujo de campo inmediato aprobados con autenticación (unificacion-regresion.log).
- Prueba cruzada aprobada (unificacion-pruebas.log): login, roles, aislamiento de dos organismos, autoridad de identidad frente a datos falsos del cliente, catálogo, reintentos concurrentes, fotos protegidas, mapa/calor/GeoJSON, notificaciones por organismo, caída y recuperación de RabbitMQ, reinicio del núcleo y .NET, y revocación de tokens.
- Migración de los 8 desastres anteriores a la autoridad PostgreSQL; referencias y vínculos conservados. Destinatarios anteriores asignados explícitamente al organismo de laboratorio (unificacion-migracion.log).
- Comparación completa del JSON previo y actual de las 25 operaciones anteriores, incluidas las fotos: sin alteraciones (unificacion-conservacion.log).
- Prueba de interfaz: login de coordinador, desastre DEMO-UNIFICADO con correos confirmados 4/4, evaluación «Escuela · demostración unificada» asociada al mismo evento, evaluador tomado de la sesión. Evidencia unificacion-web-detalle.png.
- APK 0.3.0 compilado con cero errores/advertencias e instalado mediante actualización, sin desinstalar (unificacion-android-compilacion.log).

## Límite de verificación móvil

No se completó la interacción con el nuevo login/catálogo del APK desde la pantalla del emulador. El control de Android Studio continúa sin dirigir correctamente los clics al teléfono y la vista permanece en Updating después de la instalación. La instalación reportó Success; esto no se presenta como prueba interactiva de login, SQLite desconectado y sincronización del APK nuevo. Las evidencias Android de versiones anteriores se conservan como históricas. El contrato que consume el APK sí se probó contra las APIs reales.

## Alcance

El login ya está conectado al flujo web y móvil. El servicio del compañero conserva además sus APIs originales como parte de su fuente; la captura web/Android mantiene el almacenamiento .NET y sus evidencias, sin duplicar sus evaluaciones en Java. SOAP/RPC remoto y SAGA no se añadieron al flujo operativo. El entorno sigue siendo de demostración local, con credenciales configurables; no acredita producción, alta disponibilidad ni carga masiva. El resto de capacidades funcionales del proyecto sigue pendiente según README.

Los resultados RESULTADO_TALLER3.md y logs anteriores corresponden a la versión previa; este archivo y los prefijos unificacion-* describen la versión actual.
