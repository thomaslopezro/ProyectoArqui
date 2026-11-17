"""Prueba real contra Payara, RabbitMQ y Mailpit. Crea dos sistemas y eventos de prueba.
No borra datos. Usa las APIs locales de administración para inspeccionar las colas.
"""
import base64
import json
import os
import time
import uuid
from datetime import datetime, timezone
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError
from urllib.parse import quote

APP = os.getenv("API_URL", "http://127.0.0.1:8081/gestion-emergencias/api")
RABBIT = os.getenv("RABBIT_API", "http://127.0.0.1:15673/api")
MAIL = os.getenv("MAIL_API", "http://127.0.0.1:8026/api/v1")
AUTH = base64.b64encode(b"taller:taller-local").decode()


def peticion(base, metodo, ruta, datos=None, esperado=200):
    cabeceras = {"Content-Type": "application/json"}
    if base == RABBIT:
        cabeceras["Authorization"] = "Basic " + AUTH
    req = Request(base + ruta, data=None if datos is None else json.dumps(datos).encode(),
                  headers=cabeceras, method=metodo)
    try:
        respuesta = urlopen(req, timeout=15)
    except HTTPError as error:
        respuesta = error
    with respuesta:
        estado = respuesta.status
        texto = respuesta.read().decode()
    if estado != esperado:
        raise AssertionError((metodo, ruta, estado, texto, esperado))
    return json.loads(texto) if texto else None


def esperar(funcion, segundos=100):
    limite = time.monotonic() + segundos
    ultimo = None
    while time.monotonic() < limite:
        try:
            resultado = funcion()
            if resultado:
                return resultado
        except (AssertionError, URLError, OSError) as error:
            ultimo = error
        time.sleep(1)
    raise AssertionError("Tiempo agotado: " + str(ultimo))


def publicar_broker(evento):
    resultado = peticion(RABBIT, "POST", "/exchanges/%2F/desastres.entrada/publish", {
        "properties": {"delivery_mode": 2, "content_type": "application/json", "message_id": evento.get("id", "invalido")},
        "routing_key": "desastre.reportado", "payload": json.dumps(evento), "payload_encoding": "string"})
    assert resultado["routed"] is True


def leer_cola(cola):
    return peticion(RABBIT, "POST", "/queues/%2F/" + quote(cola, safe="") + "/get", {
        "count": 100, "ackmode": "ack_requeue_false", "encoding": "auto"})


def evento(identificador):
    return {"id": identificador, "tipo": "INUNDACION", "ubicacion": "Bogota - zona de demostracion",
            "descripcion": "Desbordamiento reportado por el productor de prueba", "severidad": "ALTA",
            "fecha": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")}


def correos_del_evento(identificador):
    mensajes = peticion(MAIL, "GET", "/messages")["messages"]
    return [m for m in mensajes if identificador in m.get("Subject", "")]


def prueba():
    esperar(lambda: peticion(APP, "GET", "/salud"))
    print("OK: Payara, registro y RabbitMQ listos", flush=True)
    sufijo = uuid.uuid4().hex[:10]
    sistemas = []
    for nombre in ["bomberos", "defensa"]:
        s = {"id": nombre + "-" + sufijo, "nombre": nombre, "correo": nombre + "-" + sufijo + "@example.test"}
        resultado = peticion(APP, "POST", "/sistemas", s, 201)
        assert resultado["cola"] == "sistema." + s["id"]
        sistemas.append(s)
    print("OK: dos sistemas registrados con colas durables independientes", flush=True)
    d = evento("broker-" + sufijo)
    publicar_broker(d)
    print("OK: productor publico directamente en RabbitMQ, sin invocar el endpoint de registro", flush=True)
    r = esperar(lambda: peticion(APP, "GET", "/desastres/" + d["id"]))
    assert r["desastre"] == d
    esperar(lambda: peticion(APP, "GET", "/desastres/" + d["id"])["notificacionPublicada"])
    print("OK: consumidor Jakarta registro todos los campos del desastre y publico las notificaciones", flush=True)
    for s in sistemas:
        recibidos = []
        def consumir():
            recibidos.extend(leer_cola("sistema." + s["id"]))
            return any(json.loads(m["payload"])["id"] == d["id"] for m in recibidos)
        esperar(consumir)
        assert any(m["properties"]["delivery_mode"] == 2 for m in recibidos)
    print("OK: ambos sistemas recibieron su copia aun sin estar conectados al publicarse", flush=True)
    esperar(lambda: all(s["id"] in peticion(APP, "GET", "/desastres/" + d["id"])["correosEnviados"] for s in sistemas))
    destinatarios = {s["correo"] for s in sistemas}
    def correos_confirmados():
        recibidos = {r["Address"] for m in correos_del_evento(d["id"]) for r in m.get("To", [])}
        return destinatarios <= recibidos
    esperar(correos_confirmados)
    print("OK: correos enviados por Jakarta Mail y recibidos por SMTP en Mailpit", flush=True)
    cantidad_antes = len(correos_del_evento(d["id"]))
    publicar_broker(d)
    time.sleep(4)
    assert sum(r["desastre"]["id"] == d["id"] for r in peticion(APP, "GET", "/desastres")) == 1
    assert len(correos_del_evento(d["id"])) == cantidad_antes
    for s in sistemas:
        assert leer_cola("sistema." + s["id"]) == []
    print("OK: reentrega con el mismo ID no duplico el registro, las notificaciones ni los correos", flush=True)
    segundo = evento("rest-" + sufijo)
    aceptado = peticion(APP, "POST", "/desastres", segundo, 202)
    assert aceptado["estado"] == "RECIBIDO_EN_COLA"
    esperar(lambda: peticion(APP, "GET", "/desastres/" + segundo["id"]))
    print("OK: entrada REST auxiliar devuelve 202 y pasa por RabbitMQ", flush=True)
    invalido = evento("invalido-" + sufijo)
    invalido["descripcion"] = " "
    peticion(APP, "POST", "/desastres", invalido, 400)
    publicar_broker(invalido)
    errores = []
    def error_en_cola():
        errores.extend(leer_cola("desastres.registro.errores"))
        return any(json.loads(m["payload"]).get("id") == invalido["id"] for m in errores)
    esperar(error_en_cola)
    peticion(APP, "GET", "/desastres/" + invalido["id"], esperado=404)
    print("OK: mensaje invalido aislado en cola de errores; no se registro ni detuvo el procesamiento", flush=True)
    peticion(APP, "POST", "/sistemas", {"id": "x", "nombre": "x", "correo": "sin-arroba"}, 400)
    peticion(APP, "GET", "/solicitudes", esperado=404)
    print("OK: validacion de sistemas y eliminacion de las rutas del taller anterior", flush=True)
    resultado = {"eventoBroker": d["id"], "eventoRest": segundo["id"], "sistemas": sistemas,
                 "fecha": datetime.now(timezone.utc).isoformat(), "resultado": "APROBADO"}
    print(json.dumps(resultado, ensure_ascii=False), flush=True)
    return resultado

if __name__ == "__main__":
    prueba()
