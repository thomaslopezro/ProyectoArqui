# Módulo de Reconstrucción — tecnología asociada al diseño y su justificación

12 de noviembre de 2026 · Thomas López Rojas · Nicolas Parra Santos

El módulo de Reconstrucción se implementa con **Java 17 y Spring Boot 3** en el backend, **gRPC** como contrato con los demás módulos, **PostgreSQL** como base de datos y **React** en la interfaz. La elección sale de los atributos de calidad que el enunciado prioriza para este módulo: trazabilidad de presupuestos y estados, interoperabilidad, modificabilidad y disponibilidad.

## 1. Tecnología asociada a cada elemento del diseño

El diseño es el de la entrega anterior: arquitectura clean en cuatro capas, casos de uso CU-01 a CU-09 y reglas RN-01 a RN-19.

| Elemento del diseño | Tecnología | Dónde está en el código |
| --- | --- | --- |
| Capa 1. Entidades y reglas | Java 17 sin framework: clases, `record`, `enum` y `BigDecimal` para el dinero | `backend/.../dominio` |
| Capa 2. Casos de uso y puertos | Java 17 sin framework; los puertos son interfaces | `backend/.../aplicacion` |
| Capa 3. Adaptador de entrada entre módulos | gRPC con Protocol Buffers 3 | `backend/src/main/proto`, `.../adaptadores/entrada/grpc` |
| Capa 3. Adaptador de entrada para la web | REST con JSON sobre Spring Web | `.../adaptadores/entrada/rest` |
| Capa 3. Persistencia y auditoría | PostgreSQL 16 con Spring JDBC (SQL directo) | `.../adaptadores/salida/persistencia` |
| Capa 3. Transacción única (RN-17) | `TransactionTemplate` de Spring detrás del puerto `UnidadDeTrabajo` | `UnidadDeTrabajoSpring` |
| Capa 3. Publicación de eventos | Bandeja de salida en PostgreSQL, para despachar a la mensajería de la plataforma | `BandejaDeSalidaJdbc` |
| Capa 4. Arranque y configuración | Spring Boot 3.3 | `.../infraestructura` |
| Interfaz de usuario | React 18 con Vite, servida por Nginx | `web/` |
| Pruebas de las reglas | JUnit 5, con los puertos sustituidos por dobles en memoria | `backend/src/test` |
| Despliegue | Docker y Docker Compose | `compose.yaml` |

## 2. Decisión del backend: Java con Spring Boot

Las tres opciones del taller se compararon contra los atributos de calidad del enunciado (§6) que afectan a este módulo. La escala es de 1 a 5 y es una valoración del equipo, no una medición.

| Criterio | Qué exige el módulo | Java + Spring Boot | .NET | Node.js |
| --- | --- | --- | --- | --- |
| Trazabilidad e integridad | El cambio de presupuesto o de estado y su auditoría se confirman juntos (escenario E) | 5 | 5 | 3 |
| Interoperabilidad | gRPC con los otros módulos, REST con la web y eventos a la mensajería | 5 | 5 | 4 |
| Modificabilidad | Reglas aisladas del framework, probadas sin servidor ni base de datos | 5 | 5 | 3 |
| Disponibilidad y escalabilidad | Instancias sin estado que se replican detrás del balanceador | 4 | 4 | 4 |
| Rendimiento | Consultas en menos de 2 s y registros en menos de 3 s | 4 | 5 | 4 |
| Seguridad | Control de acceso por rol y por organización, validación de entradas | 5 | 5 | 4 |
| Coherencia con la plataforma | El núcleo ya es Java sobre PostgreSQL; el equipo hizo los talleres en Java | 5 | 3 | 2 |
| **Total** | | **33** | **32** | **24** |

Razones de la elección:

1. **Trazabilidad.** El enunciado exige que los cambios de presupuesto y de estado queden registrados para auditoría. Spring ofrece transacciones declaradas en un solo punto sobre PostgreSQL, y `BigDecimal` evita errores de redondeo en el dinero. El proyecto, su registro de auditoría y su evento se guardan en una sola transacción.
2. **Modificabilidad.** Spring Boot permite dejar las capas 1 y 2 en Java puro y conectar todo en una sola clase de configuración. Cambiar gRPC, la base de datos o un sistema externo no toca las reglas. Las 24 pruebas de reglas corren sin Spring.
3. **Interoperabilidad.** gRPC tiene soporte maduro en Java y el contrato `.proto` sirve para generar clientes en cualquier lenguaje, incluido el servidor de la plataforma, que está en .NET.
4. **Coherencia con la plataforma.** El núcleo de la plataforma ya corre en Java sobre PostgreSQL. Usar el mismo lenguaje reduce lo que el equipo debe aprender y mantener.

Por qué no las otras:

- **.NET** cumple igual de bien en lo técnico y arranca más rápido. Se descarta por coherencia: obligaría a mantener el dominio de negocio en dos lenguajes y el equipo tiene menos práctica modelando reglas en C#.
- **Node.js** resuelve bien la entrada y salida concurrente, pero no tiene un tipo decimal nativo para el dinero, su tipado es opcional y las transacciones dependen de la disciplina de cada desarrollador. Para un módulo cuyo riesgo principal es la integridad del presupuesto, es la opción más débil.

Costo aceptado: la máquina virtual de Java consume más memoria y tarda más en arrancar que Node.js. Para un módulo de uso prolongado y carga moderada, como la reconstrucción, ese costo no afecta los tiempos que pide el enunciado.

## 3. Decisiones dentro del backend

| Decisión | Alternativa descartada | Justificación |
| --- | --- | --- |
| gRPC entre módulos | Solo REST | Contrato tipado y versionado en un archivo `.proto`, mensajes binarios más pequeños y una operación por caso de uso. Es la tecnología que pidió el taller. |
| REST para la interfaz web | gRPC-Web | El navegador no habla gRPC sin un proxy adicional. Un segundo adaptador de entrada sobre los mismos casos de uso lo resuelve sin duplicar reglas. |
| Spring JDBC con SQL directo | JPA / Hibernate | Mantiene el dominio sin anotaciones de persistencia y hace visible cada sentencia, incluido el control de versión del proyecto. El costo es escribir el mapeo a mano. |
| PostgreSQL | Base de datos documental | El módulo necesita transacciones entre varias tablas (proyecto, auditoría, eventos) y es la base que ya usa la plataforma. |
| Auditoría en la misma base de datos | Servicio de auditoría separado | Solo así el cambio y su registro comparten transacción (RN-17). Un intento rechazado se audita aparte, porque su transacción se deshace. |
| Bandeja de salida para eventos | Publicar directo a la mensajería | El evento se guarda en la transacción del cambio; si la mensajería o un sistema externo no responde, el módulo sigue operando (escenario C). |
| Control optimista con número de versión | Bloqueo de filas | Dos cambios simultáneos sobre el mismo proyecto no se pisan: el segundo se rechaza y se reintenta. No retiene conexiones. |
| Instancias sin estado | Sesión en el servidor | La identidad viaja en cada solicitud, así que se pueden levantar varias instancias (disponibilidad y escalabilidad). |

## 4. Decisión de la interfaz: React

| Criterio | React | Angular |
| --- | --- | --- |
| Tamaño de la interfaz | Una pantalla con cinco secciones: basta una biblioteca de componentes | Marco completo, pensado para aplicaciones con muchas pantallas |
| Código de arranque | Mínimo con Vite | Mayor: módulos, servicios e inyección de dependencias |
| Despliegue | Archivos estáticos detrás de Nginx | Igual |

Se elige **React** porque la interfaz del módulo es pequeña y lo que debe mostrar es el efecto de las reglas: qué acciones permite cada estado y qué regla rechazó una operación. Angular aportaría estructura que esta pantalla no necesita. La interfaz no contiene reglas de negocio; solo llama a la API y muestra la respuesta.

## 5. Límites de esta implementación

- **Datos de otros módulos.** El estado del desastre y las evaluaciones de daño se leen de tablas locales de referencia con datos de ejemplo. En la plataforma integrada se cambian esos dos adaptadores por clientes de Gestión de desastres y Evaluación de daños; los casos de uso no cambian.
- **Identidad.** El usuario, el rol y la organización llegan en cada solicitud y el módulo confía en ellos. En la plataforma integrada los fija la fachada después de validar la sesión.
- **Eventos.** Quedan guardados en la bandeja de salida. Falta el despachador que los envía a la mensajería.
- **Documentos y evidencias.** Se registra su descripción o referencia, no el archivo.
