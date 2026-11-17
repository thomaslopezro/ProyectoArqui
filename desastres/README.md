# Gestión de Emergencias — Taller 3

Recibe desastres de forma asincrónica mediante RabbitMQ, registra la información y notifica a cada sistema registrado por mensajería y correo electrónico.

Arquitectura por capas en un contenedor Jakarta EE: Java 17, Jakarta EE 10, EJB y Payara 6. Docker Compose inicia Payara, RabbitMQ y Mailpit. Se escogieron RabbitMQ y correo (Jakarta Mail, antes JavaMail) entre las opciones del tablero.

## Iniciar

Requisito: Docker Desktop iniciado. Desde esta carpeta:

```sh
docker compose -p taller3-thomas up --build -d
```

El primer inicio descarga imágenes y dependencias. Esperar hasta que `GET http://localhost:8081/gestion-emergencias/api/salud` devuelva `{"estado":"LISTO"}`.

| Componente | Dirección |
|---|---|
| API | http://localhost:8081/gestion-emergencias/api |
| RabbitMQ | http://localhost:15673 (usuario `taller`, clave `taller-local`) |
| Buzón de correos Mailpit | http://localhost:8026 |
| AMQP para un sistema externo | localhost:5673 |

Los puertos se pueden cambiar con APP_PORT, RABBIT_UI_PORT, MAIL_UI_PORT y AMQP_PORT. La aplicación anterior del puerto 8080 es independiente.

## Demostración completa

### 1. Registrar dos sistemas

```sh
curl -X POST http://localhost:8081/gestion-emergencias/api/sistemas -H 'Content-Type: application/json' -d '{"id":"bomberos","nombre":"Bomberos","correo":"bomberos@example.test"}'
curl -X POST http://localhost:8081/gestion-emergencias/api/sistemas -H 'Content-Type: application/json' -d '{"id":"defensa","nombre":"Defensa Civil","correo":"defensa@example.test"}'
```

Cada sistema obtiene una cola durable propia: `sistema.bomberos` y `sistema.defensa`. Registrar antes de publicar los desastres. Repetir un registro idéntico es seguro; cambiar los datos del mismo ID devuelve 409.

### 2. Publicar un desastre directamente por AMQP

```sh
docker compose -p taller3-thomas --profile demo run --build --rm demo publicar --id desastre-demo-001 --tipo INUNDACION --ubicacion 'Bogota' --descripcion 'Desbordamiento del rio' --severidad ALTA
```

Este productor independiente publica en RabbitMQ y termina cuando el broker confirma. No llama a un método de registro de negocio ni espera a los destinatarios.

### 3. Consultar el registro

```sh
curl http://localhost:8081/gestion-emergencias/api/desastres/desastre-demo-001
```

Puede devolver 404 durante el breve intervalo anterior al consumo. Reintentar la consulta después de unos segundos. La respuesta contiene el desastre, fecha de registro, destinatarios, estado de publicación y correos confirmados por SMTP.

### 4. Consumir la copia de cada sistema

Ejecutar en dos terminales:

```sh
docker compose -p taller3-thomas --profile demo run --build --rm demo consumir --sistema bomberos
```

```sh
docker compose -p taller3-thomas --profile demo run --rm demo consumir --sistema defensa
```

Cada consumidor recibe su propia copia y confirma con ACK después de imprimirla. También recibe mensajes que se publicaron mientras estaba desconectado. Detener con Ctrl+C.

### 5. Ver los correos

Abrir http://localhost:8026. Debe aparecer un correo por cada sistema registrado para el desastre. Se envían realmente mediante SMTP a Mailpit; Mailpit es el buzón local de demostración y **no entrega estos correos a Gmail ni a destinatarios de Internet**. La alternativa elegida es correo, por lo que no se implementa SMS.

## API auxiliar

| Método | Ruta bajo `/api` | Resultado |
|---|---|---|
| GET | `/salud` | Comprueba aplicación, registro y broker |
| POST | `/sistemas` | Registra suscriptor, correo y cola; 201 |
| GET | `/sistemas` | Lista sistemas registrados |
| POST | `/desastres` | Publica en RabbitMQ; 202, todavía no confirma el registro de negocio |
| GET | `/desastres` | Lista registros |
| GET | `/desastres/{id}` | Consulta registro; 404 si aún no existe |

La entrada REST es una comodidad para pruebas. La recepción asincrónica está implementada en el consumidor de RabbitMQ y funciona con el productor AMQP independiente.

Ejemplo JSON del mensaje:

```json
{
  "id": "desastre-demo-002",
  "tipo": "INUNDACION",
  "ubicacion": "Bogota",
  "descripcion": "Desbordamiento del rio",
  "severidad": "ALTA",
  "fecha": "2026-10-21T16:00:00Z"
}
```

Tipos: INUNDACION, TERREMOTO, INCENDIO, DESLIZAMIENTO u OTRO. Severidades: BAJA, MEDIA, ALTA o CRITICA. Todos los campos son obligatorios. El ID lo genera el productor y se conserva en los reintentos del mismo evento. Una reentrega con ese ID se considera duplicada; para corregir o comunicar otro evento se utiliza un ID nuevo.

## Pruebas

```sh
docker compose -p taller3-thomas --progress=plain build
python3 scripts/verificar_taller3.py
```

La construcción ejecuta JUnit. El script usa Python 3 sin paquetes adicionales y comprueba el recorrido real, dos suscriptores desconectados, SMTP, duplicados, entrada REST y mensajes inválidos. Crea sistemas con IDs únicos; no elimina información existente.

Para comprobar caídas y reinicios en un entorno sin demostraciones concurrentes:

```sh
python3 scripts/verificar_recuperacion.py
```

Este segundo script detiene y vuelve a iniciar únicamente los tres servicios del proyecto, sin borrar sus volúmenes.

Consultar las evidencias reales en [docs/evidencias](docs/evidencias/README.md) y la explicación en [docs/ARQUITECTURA.md](docs/ARQUITECTURA.md).

## Diagramas PlantUML

Ver [fuentes editables y vistas SVG](docs/plantuml/README.md): componentes, despliegue, secuencia y topología de mensajería.

## Datos, fallos y alcance

- El registro y las notificaciones pendientes se guardan juntos en un archivo JSON dentro de un volumen Docker. Sobreviven al reinicio de la aplicación. Los mensajes y el buzón también usan volúmenes.
- El ACK de entrada ocurre después de registrar el desastre y su salida pendiente. La salida se marca después del confirm del broker.
- Hay reintentos de procesamiento cada 5 segundos, hasta 5 intentos. Los mensajes inválidos van directamente a `desastres.registro.errores`; los fallos agotados quedan en esa cola o en `desastres.correo.errores`, disponibles para revisión y reenvío manual.
- Entrega al menos una vez: un fallo entre publicar y guardar la marca puede repetir una notificación. Los sistemas receptores deben deduplicar por ID. SMTP también puede duplicar un correo si la aplicación cae después de enviarlo y antes de guardar su confirmación. No se promete entrega exactamente una vez.
- El archivo y el bloqueo EJB están diseñados para una sola instancia Jakarta. Es una entrega académica local.
- La entrega se limita a recepción, registro y notificaciones. No incluye inventario, atención de recursos, SMS, microservicios, Kubernetes ni una interfaz gráfica de gestión.

Para detener sin borrar los datos:

```sh
docker compose -p taller3-thomas down
```

`down -v` borra los volúmenes y los datos; usarlo únicamente si se quiere reiniciar la demostración desde cero.
