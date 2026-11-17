# Taller 2 de lógica de negocio integrado

## Fuente revisada

El ZIP /Users/thomaslopezrojas/Documents/arquitectura/gestion-emergencias 2.zip contiene el taller completado de solicitudes de recursos (registro, consulta, pendientes y atención atómica). La carpeta descomprimida del mismo nombre contiene en cambio el Taller 3 de notificaciones ya integrado. Se tomó el código del ZIP, sin modificar los originales. Se conservan sin cambios su dominio, puertos, excepciones y EJB SolicitudServicio; solicitudes-procedencia.json permite comprobarlo. Sus 19 pruebas se integraron en el núcleo, incluido el repositorio en memoria solo como soporte de pruebas.

## Unión implementada

- El EJB original sigue aplicando las reglas de registro, cantidad positiva, prioridad obligatoria, pendientes y atención única.
- El adaptador de ejecución usa JPA/PostgreSQL y JTA dentro del núcleo existente. V006 añade solicitudes_recursos y solicitudes_historial; no altera las capturas anteriores.
- Desastre y organismo se resuelven en la autoridad actual. Estado inicial, creador y responsable de atención se toman del negocio y del token; no del JSON del cliente.
- COORDINADOR y EVALUADOR solicitan; solo COORDINADOR atiende; CONSULTA lee. La lectura, atención y auditoría se filtran por organismo también entrando directamente a Java.
- solicitudId estable evita duplicados en reintentos concurrentes. Un contenido diferente con la misma clave devuelve 409.
- La transición PENDIENTE → ATENDIDA usa comparación atómica en PostgreSQL. Solicitud e historial se guardan en la misma transacción.
- La web ofrece registro, filtro por evento/pendientes, atención e historial. Se llega desde mapas, desastres y el detalle de evaluaciones vinculadas. El formulario conserva el intento sin confirmar en la misma sesión del navegador.

## Evidencias actuales

- solicitudes-compilacion-final.log: 30 pruebas unitarias aprobadas, incluidas las 19 del taller original.
- solicitudes-pruebas.log: integración HTTP aprobada; validaciones, tres roles, autoridad de los datos, diez reintentos sin duplicar, diez atenciones con un solo ganador y nueve conflictos, aislamiento de dos organismos, auditoría, rollback JTA por fallo sintético e información conservada tras reiniciar núcleo/API. El trigger de fallo de prueba fue retirado al terminar.
- solicitudes-regresion.log: 6 pruebas del DAL, 4 de panorama y flujo de campo inmediato aprobados.
- solicitudes-web-historial.png: prueba de pantalla. Se creó «Botiquines · demostración del Taller 2», cantidad 12, para DEMO-UNIFICADO y se atendió como coordinador_demo. El historial conserva registro y atención.

## Alcance

Es gestión de solicitudes, no inventario, reserva o asignación automática de existencias. No se añadieron notificaciones de recursos al contrato de desastres. El APK 0.3.0 no cambió; la gestión de solicitudes está disponible en la web/API. Se conservaron la captura offline y las notificaciones anteriores. No se editaron SRS, SAD o ATAM.

Los respaldos de base previos a la migración permanecen en respaldo-previo/ únicamente en este equipo y se excluyen del ZIP.
