# Módulo de Reconstrucción

Implementación del módulo de Reconstrucción de la Plataforma para la Gestión de Desastres y Reconstrucción: casos de uso CU-01 a CU-09 y reglas RN-01 a RN-19, con arquitectura clean.

- Backend: Java 17, Spring Boot 3.3, gRPC, PostgreSQL.
- Interfaz: React 18 con Vite.
- La justificación de cada tecnología está en `DECISIONES_TECNOLOGIA.md`.

## Cómo ejecutarlo

Con Docker (recomendado):

```bash
docker compose up --build
```

| Qué | Dirección |
| --- | --- |
| Interfaz web | http://localhost:8087 |
| API REST | http://localhost:8086/api/reconstruccion |
| gRPC | localhost:9096 |

La primera construcción descarga las dependencias de Maven y de npm, y corre las 25 pruebas del backend.

Sin Docker (necesita Java 17 o superior, Maven y Node 20 o superior):

```bash
cd backend && mvn spring-boot:run      # usa H2 en memoria, no necesita PostgreSQL
cd web && npm install && npm run dev   # interfaz en http://localhost:5173
```

Solo las pruebas de las reglas:

```bash
cd backend && mvn test
```

## Recorrido de demostración (escenario E del enunciado)

La interfaz trae cuatro usuarios de ejemplo en el selector de arriba a la derecha y un desastre con ocho evaluaciones de daño.

1. Como **Carlos (autoridad)**, en «Sismo del Eje Cafetero 2026» pulse **Iniciar reconstrucción**. El desastre pasa de Caracterización a Reconstrucción (CU-01).
2. Marque varias evaluaciones, incluida «Acueducto veredal», que está sin validar, y pulse **Crear proyectos**. Se crean los válidos y el inválido se reporta con RN-02 (CU-02, RN-18).
3. Abra un proyecto y apruébelo con presupuesto, fuente de financiación y responsable (CU-03).
4. Cambie a **Ana (responsable)**, abra el proyecto e inicie la ejecución con contratista y fechas (CU-04).
5. Registre avance, un costo mayor que el presupuesto y una evidencia. El proyecto queda marcado con sobrecosto sin bloquear el registro (CU-05, RN-14).
6. Vuelva a **Carlos** y modifique el presupuesto sin motivo: se rechaza con RN-12 y el intento aparece en la auditoría como rechazado. Repítalo con motivo (CU-06, RN-17).
7. Como **Ana**, lleve el avance a 100 % y pulse **Finalizar** (CU-07, RN-15).
8. Como **Luisa (auditora)**, revise la auditoría del proyecto (CU-08).
9. Como **Carlos**, pulse **Cerrar desastre**: se rechaza con RN-04 mientras queden proyectos sin finalizar ni cancelar (CU-09).
10. Como **Pedro (otra organización)**, compruebe que no ve los proyectos de la UNGRD (RN-10).

## Estructura

```text
reconstruccion/
  backend/
    src/main/java/com/pgdr/reconstruccion/
      dominio/                    capa 1: entidades y reglas, sin framework
      aplicacion/casosdeuso/      capa 2: un caso de uso por clase
      aplicacion/puertos/         capa 2: interfaces hacia afuera
      adaptadores/entrada/grpc/   capa 3: servicio gRPC
      adaptadores/entrada/rest/   capa 3: API REST para la web
      adaptadores/salida/persistencia/   capa 3: PostgreSQL, auditoría, eventos
      infraestructura/            capa 4: configuración y datos de ejemplo
    src/main/proto/reconstruccion.proto   contrato gRPC
    src/main/resources/schema.sql         tablas
    src/test/java/...                     pruebas de las reglas y dobles en memoria
  web/                            interfaz React
  compose.yaml
```

Regla de dependencia: `dominio` no importa nada de las otras carpetas; `aplicacion` solo importa `dominio`; los adaptadores importan `aplicacion` y `dominio`. Ninguna clase de `dominio` ni de `aplicacion` importa Spring, gRPC ni JDBC.

## Dónde está cada regla

| Regla | Clase |
| --- | --- |
| RN-01, RN-02 | `IniciarReconstruccion`, `EstadoDesastre`, `EvaluacionDano` |
| RN-03 a RN-05 | `CerrarDesastre`, `EstadoDesastre` |
| RN-06, RN-09, RN-18 | `CrearProyectos` |
| RN-07, RN-08 | `Proyecto.proponer` |
| RN-10 | `CasoDeUso.autorizar` y `CasoDeUso.cargarProyecto` |
| RN-11 a RN-16 | `Proyecto`, `EstadoProyecto` |
| RN-17 | `CasoDeUso.ejecutarAuditado`, `UnidadDeTrabajoSpring`, `AuditoriaJdbc` |
| RN-19 | El módulo no tiene operaciones de pago; solo publica eventos en `BandejaDeSalidaJdbc` |

## API REST

Todas las solicitudes llevan las cabeceras `X-Usuario`, `X-Rol` (`AUTORIDAD`, `RESPONSABLE` o `AUDITOR`) y `X-Organizacion`.

| Método y ruta (bajo `/api/reconstruccion`) | Caso de uso |
| --- | --- |
| `GET /desastres` | CU-08 |
| `POST /desastres/{id}/iniciar-reconstruccion` | CU-01 |
| `GET /desastres/{id}/evaluaciones` | CU-02 |
| `POST /desastres/{id}/proyectos` | CU-02 (uno o lote) |
| `POST /proyectos/{id}/aprobar` | CU-03 |
| `POST /proyectos/{id}/iniciar` | CU-04 |
| `POST /proyectos/{id}/avance`, `/costo`, `/evidencia` | CU-05 |
| `POST /proyectos/{id}/presupuesto` | CU-06 |
| `POST /proyectos/{id}/estado` | CU-07 |
| `GET /desastres/{id}/seguimiento`, `GET /proyectos/{id}`, `GET /proyectos/{id}/auditoria`, `GET /desastres/{id}/auditoria` | CU-08 |
| `POST /desastres/{id}/cerrar` | CU-09 |

Errores: 422 con el código de la regla (`RN-xx`), 403 por rol u organización, 404 si no existe, 409 por cambio simultáneo y 400 por solicitud mal formada.

## gRPC

El contrato está en `backend/src/main/proto/reconstruccion.proto`. Ejemplo con `grpcurl`:

```bash
grpcurl -plaintext -import-path backend/src/main/proto -proto reconstruccion.proto \
  -d '{"identidad":{"usuario":"carlos","rol":"AUTORIDAD","organizacion":"UNGRD"},"desastre_id":"11111111-1111-1111-1111-111111111111"}' \
  localhost:9096 pgdr.reconstruccion.v1.ReconstruccionService/ConsultarSeguimiento
```

Errores: `FAILED_PRECONDITION` para una regla de negocio, `PERMISSION_DENIED`, `NOT_FOUND`, `ABORTED` por cambio simultáneo e `INVALID_ARGUMENT`.

## Estado de verificación (13 de noviembre de 2026)

| Parte | Estado |
| --- | --- |
| `docker compose up --build` | Ejecutado: construye, corre las 25 pruebas del backend y levanta base de datos, backend e interfaz |
| Dominio y casos de uso (capas 1 y 2) | Las 25 pruebas pasan |
| API REST, persistencia en PostgreSQL y configuración Spring | Probadas contra el sistema corriendo: 29 comprobaciones de CU-01 a CU-08 y de las reglas RN-01, RN-02, RN-04 y RN-06 a RN-18, todas correctas |
| Interfaz React | Carga contra el backend real y muestra desastres, evaluaciones, proyectos, seguimiento y auditoría |
| Servicio gRPC | Compila y queda registrado en el puerto 9096; no se ha invocado con un cliente |
| Cierre exitoso del desastre (CU-09) | Probado solo el rechazo por RN-04; el cierre con todos los proyectos terminados no se ha ejecutado contra el sistema corriendo |

`iniciar.command` hace el arranque con doble clic y deja la salida en `arranque.log`.
