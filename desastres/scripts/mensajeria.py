"""Productor/consumidor AMQP de demostración. Ejecutar con el perfil demo de Compose."""
import argparse
import json
import os
import uuid
from datetime import datetime, timezone
import pika

p = argparse.ArgumentParser()
p.add_argument("accion", choices=["publicar", "consumir"])
p.add_argument("--sistema", help="ID exacto de un sistema previamente registrado")
p.add_argument("--tipo", default="INUNDACION")
p.add_argument("--ubicacion", default="Bogota")
p.add_argument("--descripcion", default="Desbordamiento del rio")
p.add_argument("--severidad", default="ALTA")
p.add_argument("--id", default=None, help="Conservar este ID si se reintenta el mismo evento")
a = p.parse_args()
f = pika.ConnectionParameters(os.getenv("RABBIT_HOST", "rabbitmq"),
    credentials=pika.PlainCredentials("taller", "taller-local"))
c = pika.BlockingConnection(f)
canal = c.channel()
try:
    if a.accion == "publicar":
        canal.exchange_declare(exchange="desastres.entrada", exchange_type="topic", durable=True)
        canal.queue_declare(queue="desastres.registro", durable=True)
        canal.queue_bind(queue="desastres.registro", exchange="desastres.entrada", routing_key="desastre.reportado")
        evento = {"id": a.id or str(uuid.uuid4()), "tipo": a.tipo, "ubicacion": a.ubicacion,
            "descripcion": a.descripcion, "severidad": a.severidad,
            "fecha": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")}
        canal.confirm_delivery()
        canal.basic_publish(exchange="desastres.entrada", routing_key="desastre.reportado", mandatory=True,
            body=json.dumps(evento), properties=pika.BasicProperties(delivery_mode=2, content_type="application/json", message_id=evento["id"]))
        print("Mensaje confirmado por RabbitMQ:", json.dumps(evento, ensure_ascii=False), flush=True)
    else:
        if not a.sistema:
            p.error("consumir requiere --sistema registrado")
        cola = "sistema." + a.sistema
        canal.queue_declare(queue=cola, passive=True)
        canal.basic_qos(prefetch_count=1)
        def recibido(ch, metodo, propiedades, cuerpo):
            print("Desastre recibido:", json.dumps(json.loads(cuerpo), ensure_ascii=False), flush=True)
            # Confirmar después del procesamiento. Un sistema real debe deduplicar por el ID del evento.
            ch.basic_ack(delivery_tag=metodo.delivery_tag)
        canal.basic_consume(queue=cola, on_message_callback=recibido, auto_ack=False)
        print("Esperando mensajes en", cola, flush=True)
        canal.start_consuming()
finally:
    if c.is_open:
        c.close()
