# Matriz de cobertura del enunciado

Versión 5, del 12 de noviembre de 2026 (referencia CA-2026-11-12c). Estado de la arquitectura documentada frente a cada exigencia del enunciado, después de corregir lo que encontraron las revisiones de las versiones 2 y 3. Evalúa el diseño, no el código: que algo no tenga código no es una reserva de esta matriz.

## Criterio de cierre

Un ítem está **completo** cuando cumple cinco condiciones: tiene un requisito trazable en el SRS; su diseño en el SAD es suficiente para resolver sus decisiones arquitectónicas; remite a la decisión pertinente, sin exigir un ADR propio; no contradice a otra sección; y tiene definido cómo se verificará, aunque la verificación no se haya ejecutado. No se exige código.

**Completo con reserva** significa que cumple las cinco condiciones pero tiene un punto que conviene que otra persona revise; la columna final dice cuál. **Parcial** significa que falta una condición. **Falta** significa que no existe.

Estos estados son la propuesta de quien hizo la pasada. No son un cierre: cada fila debe auditarse contra los documentos, en especial las que la última columna marca como corregidas. El conteo tampoco es un porcentaje de cumplimiento: las filas no pesan lo mismo.

## Resumen

| Bloque del enunciado | Ítems | Completos | Con reserva | Parciales | Faltan |
| --- | --- | --- | --- | --- | --- |
| Capacidades funcionales (§4) | 25 | 24 | 1 | 0 | 0 |
| Temas transversales | 6 | 6 | 0 | 0 | 0 |
| Escenarios de funcionamiento (§5) | 5 | 5 | 0 | 0 | 0 |
| Atributos de calidad (§6) | 9 | 5 | 4 | 0 | 0 |
| Restricciones (§8) | 9 | 9 | 0 | 0 | 0 |
| Solución existente (§9) | 1 | 0 | 0 | 1 | 0 |
| Entregables (§10) | 12 | 10 | 0 | 1 | 1 |
| **Total** | **67** | **59** | **5** | **2** | **1** |

## Capacidades funcionales

| ID | Lo que pide el enunciado | SRS | SAD | Decisión | Verificación | Estado propuesto | Reserva o vacío |
| --- | --- | --- | --- | --- | --- | --- | --- |
| E-01 | Registrar un desastre manualmente | RF-GD-01 | §8.2 | ADR-02 | SAD §15.2 | Completo | — |
| E-02 | Crear el desastre automáticamente desde sistemas externos | RF-GD-02 | §5.1 y sus fallos, §7.6 | ADR-07 | SAD §15.2 | Completo | — |
| E-03 | Doce datos mínimos del evento | RF-GD-03 | §3.6 Gestión de desastres | — | SAD §15.2 | Completo | — |
| E-04 | Tipos de desastre no fijos en el código | RF-GD-04 | §3.6 Catálogos | ADR-08 | ATAM §8 | Completo | Corregido en v4: la interfaz construye los campos de los atributos adicionales desde la definición del tipo |
| E-05 | Ciclo de vida con estados que varían según el desastre | RF-GD-05, 15 | §3.6 | ADR-08 | SAD §15.2 | Completo | Corregido en v3: se distingue la activación explícita de la que resulta de subir a crítico |
| E-06 | Clasificación en cuatro niveles; reclasificar actualiza estado, notifica y deja traza | RF-GD-06, 07, 13, 17 | §3.6, §8.2 | ADR-10, ADR-15 | SAD §15.2 | Completo | — |
| E-07 | Delimitación geográfica y su evolución | RF-GD-08, 09, 14 | §3.6 | ADR-06 | SAD §15.2 | Completo | Corregido en v3: la consulta histórica distingue lo vigente de lo conocido. Corregido en v4: un cambio posterior del catálogo del GIS no altera las versiones guardadas |
| E-08 | Fuentes de información y consolidación de varios reportes | RF-GD-10, 11, 16 | §3.6 | ADR-05 | SAD §15.2, ATAM §8 | Completo | Corregido en v3: consolidación por alias sin mover registros entre unidades, y reversión que conserva lo posterior. Corregido en v4: reglas de integridad de los alias |
| E-09 | Organismos responsables y visualizar su responsabilidad | RF-GD-12, RF-CO-01 | §3.6 Coordinación | ADR-19 | SAD §15.2 | Completo | — |
| E-10 | Priorización de tareas y alertas | RF-CO-03, RF-GD-13 | §3.6, §5.1 | ADR-15 | SAD §15.2 | Completo | La evolución del desastre se evalúa en E-07 |
| E-11 | Registrar daños con fotografías, documentos y coordenadas | RF-ED-01, 03, 07 | §3.6 Evidencias, §5.4, §8.3 | ADR-13 | SAD §15.2 | Completo | Corregido en v3: tipo, tamaño máximo, número y forma de envío de los documentos |
| E-12 | Actualizar una evaluación conservando lo anterior | RF-ED-08 | §8.3 | ADR-04 | SAD §15.2 | Completo | — |
| E-13 | Evaluación asociada a un desastre, o preliminar | RF-ED-01, 02 | §3.6, §8.3 | ADR-04 | SAD §15.2 | Completo | — |
| E-14 | Tipos de activo y categorías de daño configurables, con criterios por infraestructura | RF-ED-04, 05, 06 | §3.6 Catálogos | ADR-08 | ATAM §8 | Completo | — |
| E-15 | Mostrar los daños en un mapa | RF-ED-14 | §8.3 | — | SAD §15.2 | Completo | — |
| E-16 | Trabajo sin conexión | RF-ED-11, 12, 13 | §5.4, §9.9 | ADR-04 | SAD §15.2 | Completo | — |
| E-17 | Conflictos de sincronización | RF-ED-13 | §8.3 | ADR-04 | SAD §15.2 | Completo | — |
| E-18 | Validar, gestionar duplicados, priorizar y estimar impacto y costos | RF-ED-09, 10, 15, 17 | §3.6 Evaluación de daños | ADR-05 | SAD §15.2 | Con reserva | La estimación de costo y la regla de prioridad son deliberadamente simples; conviene confirmar que bastan |
| E-19 | Integración con GIS, generación de eventos y enlace con otras fases | RF-ED-16 | §3.6, §7.6, §8.5 | ADR-07 | SAD §15.2 | Completo | Corregido en v3: las evaluaciones no cambian de evento al consolidar |
| E-20 | Personas, familias, necesidades, ubicación, refugios, atención y solicitudes de ayuda | RF-PA-01 a 05 | §3.6 | ADR-01 | SAD §15.2 | Completo | — |
| E-21 | Protección y control de acceso de esa información | RF-PA-06, 07 | §3.6, §9.4, §9.6, §9.9 | ADR-19, ADR-20 | SAD §15.2, ATAM §8 | Completo | Corregido en v3: §9.4 y §15.2 usan el ámbito de caso y exceptúan al organismo que registra |
| E-22 | Visión consolidada de nueve tipos de recursos | RF-GR-01, 04, 06 | §3.6 Recursos | ADR-21 | SAD §15.2 | Completo | — |
| E-23 | Solicitar y asignar recursos | RF-GR-02, 03, 05, 07 | §3.6, §8.4 | ADR-21 | SAD §15.2 | Completo | Corregido en v3: una asignación sin respuesta queda En conciliación y no libera lo apartado. Corregido en v4: solo un resultado definitivo libera; "no encontrada" no lo es |
| E-24 | Coordinación entre organismos | RF-CO-01 a 07 | §3.6, §9.3, §9.4 | ADR-19 | SAD §15.2 | Completo | — |
| E-25 | Proyecto de reconstrucción desde una evaluación | RF-RE-01 a 06 | §3.6, §5.5 | ADR-20, ADR-21 | SAD §15.2 | Completo | Corregido en v3: presupuesto por versiones; un rechazo anula solo su versión y el silencio no revierte nada |

## Temas transversales

| ID | Tema | Dónde queda resuelto | Decisión | Verificación | Estado propuesto | Reserva o vacío |
| --- | --- | --- | --- | --- | --- | --- |
| T-01 | Evolución espacial y conservación del historial | SAD §3.6 Gestión de desastres | ADR-06 | SAD §15.2 | Completo | — |
| T-02 | Consolidación con procedencia | SAD §3.6 | ADR-05 | SAD §15.2, ATAM §8 | Completo | Corregido en v3: alias con vigencia; cada registro conserva su evento de origen. Corregido en v4: sin ciclos ni cadenas; concurrencia por versión |
| T-03 | Trabajo sin conexión y resolución de conflictos | SAD §5.4, §8.3, §9.9 | ADR-04 | SAD §15.2 | Completo | — |
| T-04 | Colaboración entre organismos con acceso controlado | SAD §3.6 Reglas comunes, §9.3, §9.4 | ADR-19 | ATAM §8 | Completo | — |
| T-05 | Vínculo de la evaluación con recursos y con reconstrucción | SAD §3.6: solicitud desde evaluación, aviso de evaluación validada, copia de origen en el proyecto | ADR-20 | SAD §15.2 | Completo | Corregido en v3: se retira la reserva, que era por falta de código y no de diseño |
| T-06 | Auditoría de las operaciones críticas | SAD §3.6 Reglas comunes, §9.7 | ADR-20 | ATAM §8 | Completo | — |

## Escenarios de funcionamiento

| ID | Escenario | SRS | SAD | ATAM | Verificación | Estado propuesto |
| --- | --- | --- | --- | --- | --- | --- |
| E-26 | A: inicio de una emergencia | §7.1 | §5.1 con ocho fallos y alternativas | AN-05 | ATAM §8 | Completo |
| E-27 | B: aumento repentino de usuarios | §7.2 | §5.2 con seis fallos; §11.3 | AN-03 | ATAM §8 | Completo |
| E-28 | C: falla de un sistema externo | §7.3 | §5.3 con ocho fallos; §7.6 | AN-07 | ATAM §8 | Completo |
| E-29 | D: evaluación distribuida de daños | §7.4 | §5.4 con ocho fallos | AN-04, AN-06 | ATAM §8 | Completo |
| E-30 | E: reconstrucción | §7.5 | §5.5 con diez fallos | AN-09 | SAD §15.2 | Completo |

## Atributos de calidad

| ID | Atributo | SRS | SAD | Decisión | ATAM | Verificación | Estado propuesto | Reserva o vacío |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| E-31 | Disponibilidad: 99,95 % en lo crítico | QA-DIS-01 | §6, §10 | ADR-11, 12, 22 | AN-01 | ATAM §8 | Con reserva | El cálculo es ilustrativo; el diseño del coordinador de PostgreSQL debe revisarlo alguien con experiencia. Corregido en v3: se define cómo cuenta la sonda la indisponibilidad |
| E-32 | Escalabilidad | QA-ESC-01, 02 | §11 | ADR-01, 03, 14, 18 | AN-03, AN-04 | ATAM §8 | Con reserva | El modelo de capacidad parte de un supuesto de 100 operaciones por segundo por instancia |
| E-33 | Rendimiento | QA-REN-01 a 03 | §1.4, §5.1, §8.6, §11.4, §11.5 | ADR-02, 15 | AN-03, AN-05 | ATAM §8 | Con reserva | El percentil 95, los topes, la tasa de error y la carga de diseño de 10 alertas simultáneas son propuesta del equipo (PREM-06), con su justificación en SAD §8.6. El enunciado solo da los tiempos: hay que validarlos con el profesor |
| E-34 | Resiliencia: siete mecanismos | QA-RES-01, 02 | §10.1, §10.2 | ADR-07, 21 | AN-07 | ATAM §8 | Completo | Corregido en v3: el plan incluye los umbrales de detección y recuperación y la caída larga |
| E-35 | Recuperación ante una falla grave | QA-DIS-02 | §10.3 a §10.8 | ADR-09, 22 | AN-02 | ATAM §8 | Completo | Corregido en v3: el plan incluye los plazos de funciones esenciales y degradables |
| E-36 | Interoperabilidad | QA-INT-01, 02 | §7, §7.6 | ADR-07 | AN-07 | ATAM §8 | Con reserva | El contrato con cada sistema es lógico: los campos exactos, y que cada sistema permita consultar el resultado de una operación por su identificador, dependen de sistemas a los que no se tiene acceso |
| E-37 | Seguridad: ocho aspectos | QA-SEG-01, 02 | §9 | ADR-10, 19 | AN-08 | ATAM §8 | Completo | Cambiado en v5: modo degradado de `servidor` ante la caída del núcleo; los 15 minutos son política propuesta |
| E-38 | Trazabilidad | QA-TRZ-01, 02 | §3.6, §9.7 | ADR-06, 20 | AN-09 | ATAM §8 | Completo | — |
| E-39 | Modificabilidad | QA-MOD-01, 02; QA-EXT-01 | §3.6 Catálogos, §7.5 | ADR-07, 08 | AN-10 | ATAM §8 | Completo | Corregido en v3: el plan incluye las metas de 1 hora y 5 días-persona |

## Restricciones

| ID | Restricción del enunciado | SRS | Cómo la atiende el SAD | Estado propuesto |
| --- | --- | --- | --- | --- |
| R-1 | No reemplaza los sistemas existentes | CON-01 | Un adaptador por sistema; la plataforma solo guarda copias (§7) | Completo |
| R-2 | Integrarse con tecnologías diferentes | CON-02, CON-03 | Traductor a un modelo común; accesos REST, SOAP y EJB remoto (§7, ADR-17) | Completo |
| R-3 | Los sistemas externos pueden no estar disponibles | CON-04 | Corte de circuito, cola de pendientes y operaciones Por confirmar (§7.3, ADR-21) | Completo |
| R-4 | Los usuarios pueden aumentar abruptamente | SUP-01, QA-ESC-01 | Unidades que crecen por separado y modelo de capacidad (§11) | Completo |
| R-5 | Algunas operaciones requieren respuesta inmediata | CON-05 | REST con el resultado en la misma llamada (ADR-02) | Completo |
| R-6 | Otras pueden procesarse de manera asíncrona | CON-06 | Avisos por cola, incorporación diferida (ADR-02, ADR-18) | Completo |
| R-7 | La información debe mantenerse disponible durante fallos parciales | QA-DIS-01, QA-RES-02 | Copias de lectura, captura sin conexión, degradación controlada (§10.2) y modo degradado de `servidor` si cae el núcleo (§9.2) | Completo |
| R-8 | Las decisiones se justifican con atributos de calidad | — | Cada ADR indica los requisitos que lo originan (§14) | Completo |
| R-9 | Tecnología definida en la segunda entrega | CON-07 | ADR-17 | Completo |

## Solución existente y entregables

| ID | Lo que pide el enunciado | Dónde está | Estado propuesto | Vacío |
| --- | --- | --- | --- | --- |
| S-1 | Análisis de Sahana Eden y propuesta alternativa | SRS §8; SAD §12 | Parcial | El análisis y tres referencias no se han verificado contra las fuentes |
| D-01 | Requisitos funcionales y atributos de calidad | SRS §3, §5 | Completo | — |
| D-02 | Análisis de una solución existente | SRS §8 | Parcial | Está presente, pero su contenido no se ha verificado contra las fuentes (S-1) |
| D-03 | Diagrama de contexto | SRS §2 | Completo | — |
| D-04 | Diagrama 4+1 o de contenedores y componentes más despliegue | SAD §3 a §6 | Completo | — |
| D-05 | Modelo de integración con sistemas externos | SAD §7 | Completo | — |
| D-06 | Diseño de las principales APIs y eventos | SAD §8 | Completo | — |
| D-07 | Decisiones arquitectónicas documentadas | SAD §14 | Completo | — |
| D-08 | Estrategia de seguridad | SAD §9 | Completo | — |
| D-09 | Estrategia de resiliencia y recuperación | SAD §10 | Completo | — |
| D-10 | Estrategia de escalabilidad | SAD §11 | Completo | — |
| D-11 | Validación de los atributos de calidad | ATAM | Completo | Corregido en v3: los criterios de ATAM §8 ya no son más débiles que las metas del SRS |
| D-12 | Diapositivas para la sustentación | — | Falta | No existen |

## Qué cerró cada grupo de trabajo

| Grupo | Qué se hizo | Filas que cierra | Evidencia en el documento |
| --- | --- | --- | --- |
| G1 | Modelo de datos del evento, delimitación versionada, fuentes y consolidación | E-03, E-07, E-08, T-01, T-02 | SAD §3.6 Gestión de desastres |
| G2 | Ficha de evaluación de daños: estados, validación, duplicados, evidencias, estimación y aviso | E-11, E-18, E-19, T-05 | SAD §3.6 Evaluación de daños |
| G3 | Catálogos configurables y política de ciclo de vida con versión | E-04, E-05, E-14, E-39 | SAD §3.6 Catálogos |
| G4 | Tabla de fallos y alternativas en cada escenario | E-02, E-26 a E-30 | SAD §5.1 a §5.5 |
| G5 | Persistencia de notificaciones, contrato lógico por sistema, identidad de recursos externos y confirmación de asignaciones | E-22, E-23, E-31, E-35, E-36 | SAD §7.6, §10.4, ADR-21, ADR-22 |
| G6 | Tres decisiones transversales y requisitos de origen en cada ADR | E-06, E-21, E-24, E-25, T-04, T-06, R-8 | SAD §3.6 Reglas comunes, ADR-19 a ADR-21, §14 |
| G7 | Plan de verificación por atributo y criterios de aceptación por capacidad | Condición de verificación de todas las filas | ATAM §8, SAD §15.2 |
| G8 | Trazabilidad desde el enunciado | Cierre | SAD §13.1 |

## Qué corrigió la versión 3

| Hallazgo de la revisión | Corrección | Dónde | Filas |
| --- | --- | --- | --- |
| Una operación externa vencida se trataba como rechazada | Estado En conciliación: nada se libera ni se revierte sin rechazo confirmado; consulta por identificador; la respuesta tardía se aplica a su versión; decide una persona si no se puede consultar | SAD §3.6 Reglas comunes, Recursos y Reconstrucción; §5.3; §5.5; §7.4; §7.6; ADR-21; ATAM AN-07, R-20 | E-23, E-25, E-28, E-30, E-34, E-36 |
| La consolidación movía registros entre dos bases | Evento canónico y alias con vigencia en el núcleo; nada se mueve; `servidor` lo recibe al actualizar su copia del catálogo; la reversión cierra el alias y conserva lo posterior | SAD §3.6 Gestión de desastres; §8.2; ADR-05; §15.2 | E-08, E-19, T-02 |
| La consulta histórica no distinguía dos tiempos | Dos instantes: vigente en y conocido en | SAD §3.6; §8.2; ATAM AN-09 | E-07, T-01 |
| Criterios de verificación más débiles que el SRS | Percentil, tope y tasa de error en el SRS; carga de diseño de alertas; umbrales de QA-RES-02, QA-RES-01, QA-DIS-02, QA-MOD, QA-EXT y QA-INT; conteo de la sonda de disponibilidad | SRS QA-REN-01 a 03 y QA-DIS-01; ATAM §8; SAD §8.6 | E-31, E-33, E-34, E-35, E-39, D-11 |
| Acceso a personas afectadas limitado a "su organismo" | Ámbito de caso, con el organismo que registra exceptuado de la denegación | SAD §9.4; §15.2 | E-21 |
| "Activar sin responsable se rechaza" contradecía la reclasificación | Se distingue la activación explícita de la que resulta de subir a crítico | SAD §15.2 | E-05 |
| Límites de los documentos adjuntos sin definir | PDF de hasta 5 MB, hasta 5 evidencias por evaluación, envío aparte y con conexión | SAD §3.6 Evidencias; §8.3; §11.2 | E-11 |
| Reservas por falta de código | Retiradas en T-05 y E-23 | Esta matriz | T-05, E-23 |
| D-02 figuraba completo sin verificar | Pasa a parcial | Esta matriz | D-02 |

## Qué corrigió la versión 4

| Hallazgo de la revisión | Corrección | Dónde | Filas |
| --- | --- | --- | --- |
| El percentil y las diez alertas son propuestas, no cumplimiento automático | Se justifica la carga de diseño con el modelo de carga, se agrega un tope a la alerta y queda como reserva por validar con el profesor | SRS QA-REN-01 a 03; SAD §1.4, §8.6; ATAM §7.4, §8 | E-33 |
| "No encontrada" se trataba como rechazo | Solo un resultado definitivo libera lo apartado: rechazo, cancelación confirmada o garantía de que no se ejecutará. El contrato debe ofrecer consulta, cancelación o plazo de validez | SAD §3.6 Reglas comunes y Recursos; §5.3; §5.5; §7.6; ADR-21; §15.2; §16; ATAM AN-07, R-20, §8 | E-23, E-25, E-28, E-30, E-36 |
| Faltaban reglas de integridad de los alias | Sin consolidación consigo mismo; el alias apunta siempre a un evento abierto y no absorbido; un alias vigente por evento; reapuntado en la misma transacción; concurrencia por versión con 409; orden de reversión | SAD §3.6 Gestión de desastres; §8.2; ADR-05; §15.2; ATAM §8 | E-08, T-02 |
| La propagación del alias a `servidor` usaba un aviso que `servidor` no consume | El alias viaja como dato del evento en el catálogo de desastres que `servidor` ya lee | SAD §3.6; ADR-05 | E-08, E-19 |
| Reservas de E-04 y E-07 | Se resolvieron en el diseño | SAD §3.6 Catálogos y Gestión de desastres | E-04, E-07 |

## Qué cambió la versión 5

| Motivo | Cambio | Dónde | Filas |
| --- | --- | --- | --- |
| La caída del núcleo hacía que `servidor` rechazara todo acceso | Modo degradado de `servidor`: consultas locales del propio organismo con la última validación, por 15 minutos; capturas recibidas y pendientes de revalidación; lo demás espera al núcleo. En operación normal se sigue validando cada solicitud | SRS QA-SEG-01; SAD §9.2, §10.3, ADR-10, §15.2; ATAM R-13, TO-04, NR-08, §8 | E-37, R-7 |

Sigue dependiendo del núcleo registrar desastres, solicitar recursos e iniciar sesión. El modo no tiene código.

Un punto queda dicho sin resolver en el código: con la carga de diseño de 10 alertas simultáneas, el consumidor actual, que toma un mensaje por segundo, no cumple los 5 s. El diseño exige consumo continuo (SAD §8.6, ATAM R-16).

Quedan abiertos S-1 y D-02 (verificar Sahana Eden contra sus fuentes) y D-12 (diapositivas), además de las cinco reservas (E-18, E-31, E-32, E-33 y E-36), que son puntos por validar con el profesor o con alguien con experiencia, no vacíos del diseño.
