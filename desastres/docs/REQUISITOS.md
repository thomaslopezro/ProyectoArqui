# Correspondencia con el tablero del profesor

Fuente: fotografía del Taller No. 3 aportada en la conversación. Thomas aclaró que las tecnologías mostradas son opciones y que se debe respetar el despliegue asociado con la arquitectura elegida.

| Pedido del profesor | Implementación |
|---|---|
| Recibir asincrónicamente información de un desastre usando mensajería | Productor AMQP → RabbitMQ → consumidor en el contenedor Jakarta |
| Registrar la información | Registro persistente JSON con ID y campos del desastre |
| Notificar a sistemas conectados o registrados por mensajería | Alta de sistemas y cola durable independiente para cada uno; recepción incluso si estaba desconectado |
| Correo electrónico o SMS | Se seleccionó correo con Jakarta Mail y servidor SMTP Mailpit |
| Tecnologías seleccionables | RabbitMQ y Jakarta Mail/JavaMail; no se seleccionaron Kafka, MDB/JMS ni SMS |
| Tiers → contenedor Jakarta | Dominio, aplicación e infraestructura en un WAR desplegado en Payara |
| Docker | Dockerfile multietapa y Docker Compose |
| Código | src/, pom.xml, Dockerfiles, compose.yaml y scripts de demostración |
| Diseño A.S. | ARQUITECTURA.md y arquitectura.svg |

## Alcance de la entrega

Se retiraron las rutas, clases y pruebas de atención de solicitudes de recursos del taller anterior, así como guías históricas y evidencias que describían ese alcance. Se conserva su respaldo fuera de la entrega, en el ZIP previo.

Los scripts de productor, consumidor y verificación sirven para demostrar los requisitos. ACK, persistencia de la salida pendiente, deduplicación, reintentos y colas de errores son mecanismos de soporte del flujo de mensajería; no agregan funcionalidades de negocio.

Se conserva la estructura del proyecto, los paquetes por capas, Java 17, Jakarta EE 10, Maven, EJB y Payara. El registro ahora usa un volumen persistente para que recibir y confirmar mensajes no deje la información solamente en memoria.

Mailpit demuestra el envío SMTP real en un entorno local; no es una entrega a correo público. No se implementaron pantallas de gestión, inventario, microservicios ni Kubernetes.
