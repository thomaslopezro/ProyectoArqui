# Alcance validado de la integración

Pruebas realizadas el 7 de noviembre de 2026 en el emulador Medium Phone API 37.0, abierto y operado dentro de Android Studio. APK original de la entrega; servidor integrado local en el puerto 18085.

## Flujo comprobado

1. Instalación y apertura del APK.
2. Captura de evaluación sin conexión al servidor, con nivel y coordenadas manuales.
3. Persistencia tras cierre forzado y reapertura.
4. Reintento y sincronización sin duplicar la evaluación.
5. Lectura de GPS con ubicación sintética: 4.6097, -74.0817; origen DISPOSITIVO. La longitud reportada fue -74.08169833333334 por la representación del emulador.
6. Captura de imagen con cámara virtual y almacenamiento conjunto con la evaluación.
7. Conservación de foto y coordenadas tras cierre forzado y reapertura, todavía sin conexión al servidor.
8. Sincronización de la evaluación y evidencia al restablecer el túnel local.
9. Consulta única desde panorama, calor y GeoJSON.
10. Apertura visual de la fotografía desde el detalle web: JPEG de 63.927 bytes, 1440 × 1920.

Se simuló la falta de acceso al servidor retirando el túnel ADB; no se desactivó toda la conectividad del emulador. La ubicación y la fotografía fueron sintéticas. No se usaron datos personales ni una cámara física.

## Qué queda implementado

Consulta común de afectaciones y evaluaciones, registro web de evaluaciones por el mismo flujo que Android, mapa de calor de evaluaciones, detalle y fotografía web, filtros y exportación geográfica que conserva identidades y clases. Se preservan CRUD de afectaciones, validación, concurrencia, importación original de afectaciones e idempotencia de campo.

## Límites para la documentación posterior

La prueba no acredita precisión de GPS o compatibilidad de cámaras en teléfonos físicos, funcionamiento en todos los Android, carga masiva ni alta disponibilidad. Los talleres no implementan toda la plataforma del enunciado: faltan gestión formal de desastres, catálogos configurables, autorización por organización y edición/historial/reconciliación de evaluaciones. El APK original no captura activo, evaluador y organización; el formulario web sí. Las fotografías siguen viajando con la evaluación. La sincronización automática se ejecuta en la pantalla principal, no mientras se consulta el detalle.

SRS, SAD y ATAM originales permanecen sin cambios; deben actualizarse a partir de este alcance comprobado y registrar explícitamente lo pendiente.
