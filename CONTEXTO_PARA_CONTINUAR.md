# Contexto para continuar el proyecto

Pega este documento completo al inicio de una conversación con un asistente (Claude, ChatGPT u otro) y dale acceso al repositorio `thomaslopezro/ProyectoArqui` o a su zip. Está escrito para que quien lo lea, persona o asistente, pueda seguir sin haber estado en las sesiones anteriores.

Fecha de corte: 14 de noviembre de 2026.

---

## 1. Qué es el proyecto

Curso de Arquitectura de Software, Pontificia Universidad Javeriana. Proyecto del semestre: **Plataforma para la Gestión de Desastres y Reconstrucción**.

- **Primera entrega** (ya calificada): SRS, SAD y ATAM. Notas bajas. El profesor anotó "Mejorar" sobre los diagramas de componentes y de despliegue del SAD. La copia calificada está en `Documentos/arquitectura/archivosProyecto/`.
- **Segunda entrega** (en curso): 50 % es la documentación corregida (SRS, SAD, ATAM) y 50 % es una implementación funcionando. El profesor no ha dicho qué partes exige implementadas. Durante el semestre dejó ejercicios sobre partes del proyecto; el equipo los unió en una sola plataforma, que es la implementación de este repositorio.
- El enunciado pide además un análisis de una solución existente (Sahana Eden) y diapositivas para una sustentación de 15 a 20 minutos, con defensa individual.
- El enunciado dice que el objetivo no es desarrollar un sistema completo.

## 2. Qué hay en el repositorio

```
  documentacion/     SRS, SAD y ATAM en PDF; fuentes .md del SAD y del ATAM; matriz de cobertura; manifiesto
  nucleo/            Java 21, Jakarta EE (EJB, JPA, JTA), WildFly, PostgreSQL: identidad, desastres, recursos, auditoría
  servidor/          ASP.NET Core 10, PostgreSQL: evaluaciones de campo, mapas, fachada de la API, web y Nginx de entrada
  desastres/         Java 17, Payara, RabbitMQ, correo: notificaciones
  campo/             .NET MAUI Android con SQLite: captura sin conexión
  interop/           prueba del inicio de sesión por EJB remoto
  reconstruccion/    Spring Boot 3.3, gRPC, React: módulo de Reconstrucción
  evidencias/        registros y capturas de las pruebas del equipo
  iniciar.sh         construye y levanta todo con Docker
  construir.command  lanzador de construcciones (ver sección 6)
  README.md          el proyecto, su arquitectura, su estado y cómo ejecutarlo
  docs/              notas técnicas de operación: rutas, permisos, pruebas y Android
```

La versión de Reconstrucción que se entregó al profesor como ejercicio independiente está aparte, en el computador de Thomas (`Documentos/arquitectura/reconstruccion/`), y **no se modificó**. En el computador de Thomas este repositorio corresponde a la carpeta `integracion`.

## 3. La arquitectura (referencia CA-2026-11-12d)

Los tres documentos describen **una sola arquitectura: la del código**, extendida a lo que el enunciado pide y aún no tiene código. No hay sección de "objetivo frente a implementado".

- **Estilo:** servicios de grano grueso. Doce componentes lógicos en ocho unidades de despliegue, cada una con su base de datos.
- **Unidades:** UD-01 Entrada (Nginx), UD-02 Núcleo, UD-03 Campo y mapas (`servidor`), UD-04 Notificaciones (`desastres`), UD-05 Personas afectadas, UD-06 Reconstrucción, UD-07 Consulta pública, UD-08 Integración. Más RabbitMQ y monitoreo.
- **Comunicación:** REST de `servidor` al núcleo. Colas solo para avisos: el núcleo guarda el cambio y su aviso en la misma transacción (bandeja de salida) y lo entrega a notificaciones, que lo publica en una cola por suscriptor y por correo.
- **Sesión:** token de 8 horas validado contra el núcleo en cada solicitud. Tres roles por organismo: COORDINADOR, EVALUADOR y CONSULTA.
- **Campo:** la app captura sin conexión, sincroniza por lotes y un conflicto de versión se muestra, no se sobrescribe.
- **Si cae el núcleo:** `servidor` mantiene consultas del propio organismo y recibe capturas por 15 minutos (modo degradado, sin código todavía). Registrar desastres, solicitar recursos e iniciar sesión se detienen.
- **Despliegue diseñado:** tres centros de datos, PostgreSQL con primario y dos réplicas síncronas, RabbitMQ en tres nodos. **Despliegue entregado:** Docker Compose en una máquina.
- **22 decisiones (ADR-01 a ADR-22)** en el SAD. El ATAM tiene 10 análisis, 21 riesgos y un plan de verificación (§8).

Reservas declaradas, que no deben presentarse como cumplidas: estimación simple de impacto y costo (E-18); disponibilidad del 99,95 % calculada pero no demostrada (E-31); capacidad por instancia supuesta (E-32); percentil 95, topes y carga de 10 alertas simultáneas son propuesta del equipo por validar con el profesor (E-33); contratos con sistemas externos sin verificar (E-36).

## 4. Estado real del código

| Parte | Estado |
| --- | --- |
| Núcleo, `servidor`, notificaciones, app de campo | Funcionan. Probados el 11 de noviembre: 46 comprobaciones de API correctas, en local y con poca carga |
| Personas afectadas, consulta pública, integración con sistemas externos | Sin código |
| Reconstrucción como módulo independiente | Funciona: 25 pruebas del dominio y 29 comprobaciones de la API. gRPC compila pero no se ha invocado con un cliente |
| Reconstrucción integrada a la plataforma | **Código escrito el 14 de noviembre, nunca compilado ni probado** |
| Modo degradado de `servidor`, consumo continuo de alertas | Diseñados en el SAD, sin código |

Cobertura aproximada antes de integrar Reconstrucción: 4 de 8 unidades y 11 de 61 requisitos funcionales implementados (12 más de forma parcial).

## 5. La integración de Reconstrucción (lo último que se hizo)

Decisiones tomadas:

- **Sesión:** el módulo valida el token contra el núcleo (`/auth/me`), en REST con un filtro y en gRPC con un interceptor. Las cabeceras `X-Usuario`, `X-Rol` y `X-Organizacion` del cliente se reemplazan por los valores validados.
- **Roles:** COORDINADOR actúa como autoridad, EVALUADOR como responsable de proyecto y CONSULTA como auditor. No se crearon roles nuevos.
- **Fase del desastre:** el núcleo es su dueño. Usa los cuatro estados que dibujó el profesor: Registro, Caracterización, Reconstrucción y Cierre. Ruta nueva `PATCH /api/desastres/{id}/fase`, con versión base, historial y repetición segura.
- **Evaluaciones:** se leen de `servidor`. Cuenta como validada la evaluación vinculada a un desastre registrado, porque `servidor` no tiene un estado "validada". El nivel de afectación de 1 a 5 se traduce a las cinco categorías de daño.
- **Avisos:** la bandeja de salida del módulo se despacha a RabbitMQ, intercambio `reconstruccion.eventos` y cola `integracion.reconstruccion`.
- **Entrada:** interfaz en `/reconstruccion/` y API en `/api/reconstruccion/`, detrás del Nginx de la plataforma y con el mismo inicio de sesión.
- **Modo independiente:** se conserva. Con `reconstruccion.integracion.habilitada=false` el módulo corre solo, como se entregó al profesor.

Archivos nuevos o cambiados:

- `reconstruccion/backend/.../adaptadores/plataforma/` (sesión y cliente HTTP), `adaptadores/entrada/rest/FiltroSesion.java` y `ManejadorPlataforma.java`, `adaptadores/entrada/grpc/InterceptorSesion.java`, `adaptadores/salida/plataforma/DesastresNucleo.java` y `EvaluacionesServidor.java`, `adaptadores/salida/mensajeria/`. También `pom.xml`, `application.properties` y `Dockerfile`.
- `reconstruccion/web/`: `src/api.js`, `src/App.jsx`, `vite.config.js`, `Dockerfile`, `nginx.integrado.conf`.
- `nucleo/.../RegistroIntegrado.java` y `EvolucionDesastresResource.java` (fase del desastre).
- `servidor/src/Mapas.Api/Infrastructure/DesastresCatalogo.cs` y `Application/EvolucionEndpoints.cs` (dejan pasar la fase).
- `servidor/web/desastres.js`, `desastres.html`, `index.html` (fase y enlace a Reconstrucción).
- `servidor/nginx.conf`, `servidor/compose.yaml`, `iniciar.sh`.
- `servidor/scripts/esperar_reconstruccion.py` y `verificar_reconstruccion.py` (prueba de punta a punta del escenario E, unas 50 comprobaciones).

## 6. Dónde quedó trabado y cómo seguir

La construcción falló tres veces porque Docker Desktop no resolvía `auth.docker.io` ("no such host"). Es un problema de red del equipo, no del código. La plataforma anterior seguía corriendo con la versión vieja.

Pasos, en orden:

1. Con internet y Docker Desktop funcionando, ejecutar `./iniciar.sh` desde la raíz del repositorio.
2. **Esperar errores de compilación.** Hay unos diez archivos Java nuevos y ediciones en el núcleo y en `servidor` que nunca se compilaron. Corregirlos hasta que levante.
3. Ejecutar `python3 servidor/scripts/verificar_reconstruccion.py` y corregir hasta que pase completo.
4. Probar gRPC con un cliente (el comando `grpcurl` está en `reconstruccion/README.md`; en modo integrado hay que enviar el token en el metadato `authorization`).
5. Si la integración no se logra a tiempo, se puede volver atrás: revertir `servidor/nginx.conf` y `servidor/compose.yaml`, y entregar Reconstrucción como módulo independiente.

Advertencia: `servidor/nginx.conf` ya apunta al módulo nuevo. Si la plataforma se reinicia sin que Reconstrucción esté construida, la entrada puede no levantar.

`construir.command` espera pedidos escritos en `.construir/pedido` (`iniciar`, `verificar`, `estado`, `logs:servicio`) y deja el resultado en `.construir/salida.log`. Sirve para lanzar construcciones sin escribir en la terminal. No es necesario usarlo.

## 7. Qué falta para la entrega

1. **Código:** terminar la integración de Reconstrucción (sección 6).
2. **Documentación:** SRS, SAD y ATAM **todavía no reflejan la integración de Reconstrucción**. Hay que actualizar, cuando la prueba pase:
   - UD-06: tecnología (Spring Boot, gRPC, React), construida.
   - ADR-17 o un ADR nuevo con la comparación de tecnologías del módulo (`reconstruccion/DECISIONES_TECNOLOGIA.md`).
   - Estados del desastre: los cuatro del profesor en lugar de los ocho actuales.
   - Estados del proyecto como en el código: termina en Finalizado y se cancela desde cualquier estado no terminal.
   - Presupuesto simplificado: un monto que se modifica con motivo. Sale el esquema de versiones y conciliación con el sistema financiero.
   - Roles de Reconstrucción y reglas propias del módulo (tipo de proyecto, sobrecosto, evaluación ya ocupada).
   - Rutas `/api/reconstruccion/...`, alcance implementado en el SRS, evidencia en el ATAM, filas de la matriz y manifiesto nuevo.
3. **Diagramas del SAD:** redibujar contenedores y despliegue. Hoy los genera Mermaid solo: quedan enredados, con letra pequeña, no distinguen lo construido de lo diseñado y falta un diagrama del ambiente entregado (Docker Compose). Es justo lo que el profesor marcó.
4. **Sahana Eden:** verificar el análisis del SRS §8 contra las fuentes.
5. **Diapositivas** para la sustentación. No existen.
6. **Recorte del SAD** (66 páginas): quitar repeticiones entre escenarios, §3.6 y los ADR, sin borrar decisiones, razones ni límites.
7. **Preguntar al profesor** si acepta el percentil 95 y la carga de 10 alertas simultáneas, y qué partes exige implementadas.
8. Pendientes de código ya diseñados: modo degradado de `servidor` y consumo continuo de alertas.

## 8. Reglas de trabajo que el equipo ha seguido

- La arquitectura documentada debe cuadrar con el código. Si no cuadra, se cambia una de las dos; no se describe un objetivo aparte.
- Todo lo que no tiene código o no se ha medido se declara así. No se afirma cumplimiento sin evidencia.
- En los documentos para entregar va solo lo que pide el profesor, sin relleno.
- Antes de cambiar algo que ya existe, mostrar el cambio propuesto. No borrar ni mover archivos sin autorización.
- Las credenciales de laboratorio están en `.env.ejemplo`. No copiarlas a documentos ni a conversaciones.
- Las fuentes vigentes del SAD y del ATAM son `documentacion/SAD_fuente.md` y `ATAM_fuente.md`. El SRS se edita en un documento de Claude y de ahí se exporta el PDF.
- Cada cambio a la documentación congelada genera una referencia nueva con su manifiesto de huellas SHA-256 (la vigente es CA-2026-11-12d).

## 9. Lo que se le pide al asistente

Lee primero `README.md`, `documentacion/SAD_fuente.md` y este documento. Luego continúa en el orden de la sección 7, empezando por la sección 6. Antes de modificar documentos, di qué vas a cambiar. Cuando reportes, distingue lo que probaste de lo que solo escribiste.
