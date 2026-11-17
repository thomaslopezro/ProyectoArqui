# Cómo entender y sustentar el taller

## Qué resolvimos

«Inundación en Bogotá» no determina un punto único. La latitud indica posición norte/sur y la longitud este/oeste. Con ambos valores ubicamos la afectación; el tipo y la descripción explican qué representa ese lugar. El mapa es una vista de registros persistidos, no la base de datos.

## Por qué esta arquitectura

La interfaz permite interactuar; el servidor decide qué datos son válidos; la base de datos los conserva. Esa distribución permite cambiar la presentación sin reescribir las reglas y usar la misma API desde otro sistema. Separar tres niveles añade despliegue y comunicación, pero en este taller hace visible la arquitectura que aparece en el tablero.

El navegador no conoce la contraseña de PostgreSQL ni envía SQL. Hace una petición HTTP a la API. La API valida y utiliza EF Core para comunicarse con PostgreSQL.

## Qué significa cada tecnología

- **ASP.NET Core:** recibe las peticiones HTTP y produce respuestas.
- **REST:** organiza operaciones sobre recursos usando GET, POST, PUT y DELETE.
- **Entity Framework Core:** convierte las entidades y operaciones del programa en acceso a datos.
- **LINQ:** permite expresar consultas como «afectaciones de tipo inundación y severidad alta». EF las traduce y PostgreSQL filtra.
- **Npgsql / ADO.NET:** proveedor y base de acceso a PostgreSQL sobre los que funciona el ORM.
- **Leaflet:** dibuja el mapa y los marcadores en el navegador.
- **OpenStreetMap:** suministra el mapa base; no contiene nuestros registros.
- **GeoJSON:** permite intercambiar datos geográficos; sus coordenadas Point son `[longitud, latitud]`.
- **Docker Compose:** inicia y conecta los tres servicios para reproducir la demostración.

## Preguntas de sustentación resueltas

**¿Por qué REST y no gRPC?** El principal consumidor es un navegador y las operaciones son solicitudes y respuestas sencillas. REST permite consumir JSON directamente y demostrar los contratos con facilidad. No hay una necesidad de streaming que justifique gRPC aquí.

**¿Tres carpetas equivalen a tres niveles?** No. Las carpetas separan código. En esta implementación web, API y base de datos se ejecutan como servicios separados. Dentro de la API también organizamos dominio, aplicación e infraestructura.

**¿Por qué validar también en el servidor si el formulario ya valida?** Otro consumidor puede llamar a la API directamente y saltarse el formulario. Las reglas de negocio deben aplicarse en la API para todos los clientes.

**¿Un clic en el mapa guarda datos?** No. Solo completa coordenadas y muestra una selección provisional. El registro ocurre al enviar el formulario y recibir confirmación del servidor.

**¿Cómo sabemos que los datos no están solo en memoria?** La API usa PostgreSQL con un volumen. La prueba de persistencia crea un registro, reinicia los servicios y vuelve a consultarlo.

**¿Qué pasa si una coordenada del archivo es inválida?** Se rechaza todo el lote. El usuario corrige el archivo y lo importa otra vez; no queda una mitad cargada.

**¿Qué pasa si dos usuarios editan el mismo punto?** Ambos leen una versión. La primera edición la cambia; la segunda, que todavía envía la versión anterior, recibe 409 y debe recargar. Así se evita sobrescribir cambios silenciosamente.

**¿Por qué no hay RabbitMQ como en otro taller?** Este flujo necesita confirmar un guardado y mostrarlo al usuario. La mensajería no es requisito del tablero y agregaría coordinación y fallos que este caso no necesita.

**¿Por qué no dibujar áreas?** El enunciado acepta al menos ubicar coordenadas. Un marcador representa una ubicación reportada, no toda la extensión del daño. Para áreas reales se necesitarían polígonos y validación geográfica adicional.

**¿Esto es alta disponibilidad?** No. Hay separación de niveles, pero una instancia por servicio y un único equipo. Los datos sobreviven a reinicios; eso no equivale a resistir la pérdida de la máquina.

## Guion breve

1. Presentar el objetivo: registrar y visualizar ubicaciones de afectaciones.
2. Mostrar los tres niveles y explicar sus responsabilidades.
3. Importar el archivo de tres ejemplos ficticios y ubicar un marcador.
4. Crear un punto desde el mapa y cambiar su severidad.
5. Explicar el contrato REST y el orden de coordenadas de GeoJSON.
6. Mostrar los resultados de pruebas y los límites del prototipo.

La defensa debe conectar cada decisión con una necesidad: REST con integración, transacciones con integridad, versión con concurrencia y volumen con persistencia. No afirmar capacidades que no se probaron.
