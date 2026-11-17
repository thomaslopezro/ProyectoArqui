# Evidencias reales — Taller 3

Ejecución: 21 de octubre de 2026, entorno Docker local.

| Evidencia | Resultado |
|---|---|
| construccion.log y junit/ | Compilación y empaquetado correctos; 12 pruebas, 0 fallos, 0 errores, 0 omitidas |
| integracion.txt | Recorrido RabbitMQ → registro Jakarta → dos colas → SMTP; duplicados, validación y entrada REST aprobados |
| productor-consumidores-amqp.txt | Productor y dos consumidores independientes AMQP recibieron el mismo ID de desastre |
| recuperacion.txt | Recuperación tras caída de SMTP, reinicio de RabbitMQ y recepción mientras Jakarta estaba detenido |
| desastre-registrado.json | Exportación del registro real generado por el mensaje del broker |
| correo-smtp.json | Correo real recibido en Mailpit, exportado desde su API |
| payara.log | Despliegue y ejecución en Payara, incluidos fallos provocados deliberadamente durante la prueba de recuperación |
| contenedores.json | Los tres servicios iniciados con sus puertos |

La prueba de integración registra dos sistemas por ejecución, publica directamente en RabbitMQ, comprueba todos los campos y consume la copia de cada sistema. Confirma los destinatarios del correo en Mailpit. Reenvía el mismo ID y comprueba que no se duplique el registro, la notificación ni el correo en una reentrega normal. También verifica el aislamiento de mensajes inválidos y que las rutas del taller anterior estén ausentes.

La prueba de recuperación detiene Mailpit, comprueba que el registro y la mensajería siguen funcionando, vuelve a iniciarlo y espera el envío pendiente. Después reinicia RabbitMQ y comprueba la reconexión. Por último detiene Jakarta, publica un desastre, lo vuelve a iniciar y verifica el procesamiento pendiente y la conservación de los registros previos.

Estos resultados no establecen entrega exactamente una vez ni alta disponibilidad; esas limitaciones se explican en ARQUITECTURA.md.

Para reproducir desde la raíz del proyecto:

```sh
docker compose -p taller3-thomas up --build -d
python3 scripts/verificar_taller3.py
python3 scripts/verificar_recuperacion.py
```

Los productores y consumidores de demostración se ejecutan con los comandos del README principal. Las pruebas crean datos nuevos y no borran información existente. Las direcciones `example.test` se usan únicamente dentro del buzón local.
