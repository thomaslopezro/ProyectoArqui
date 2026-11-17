"""Pruebas de fallos de la entrega local. Detiene y arranca solo los servicios de este Compose.
Ejecutar sin demostraciones concurrentes: python3 scripts/verificar_recuperacion.py.
Requiere la prueba principal aprobada y Docker disponible; no borra volúmenes.
"""
import json
import os
import subprocess
import time
import uuid
from pathlib import Path
from verificar_taller3 import APP, RABBIT, evento, esperar, peticion, publicar_broker

BASE = Path(__file__).resolve().parents[1]
PROYECTO = os.getenv("COMPOSE_PROJECT_NAME", "taller3-thomas")


def compose(*args):
    subprocess.run(["docker", "compose", "-p", PROYECTO, *args], cwd=BASE, check=True,
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def prueba():
    esperar(lambda: peticion(APP, "GET", "/salud"))
    sistemas = peticion(APP, "GET", "/sistemas")
    assert sistemas, "Ejecute primero verificar_taller3.py para registrar destinatarios"
    anteriores = peticion(APP, "GET", "/desastres")
    assert anteriores
    sufijo = uuid.uuid4().hex[:10]
    smtp = evento("smtp-" + sufijo)
    try:
        compose("stop", "mailpit")
        publicar_broker(smtp)
        esperar(lambda: peticion(APP, "GET", "/desastres/" + smtp["id"])["notificacionPublicada"])
        time.sleep(3)
        r = peticion(APP, "GET", "/desastres/" + smtp["id"])
        assert r["correosEnviados"] == []
        print("OK: SMTP caido no impide registrar ni publicar a los sistemas", flush=True)
    finally:
        compose("start", "mailpit")
    esperar(lambda: len(peticion(APP, "GET", "/desastres/" + smtp["id"])["correosEnviados"]) == len(sistemas))
    print("OK: correos pendientes recuperados tras restablecer SMTP", flush=True)
    try:
        compose("stop", "rabbitmq")
        peticion(APP, "POST", "/desastres", evento("sin-broker-" + sufijo), esperado=503)
        print("OK: broker caido devuelve 503; no se informa una aceptacion falsa", flush=True)
    finally:
        compose("start", "rabbitmq")
    esperar(lambda: peticion(APP, "GET", "/salud"), 120)
    print("OK: aplicacion reconecto despues del reinicio de RabbitMQ", flush=True)
    pendiente = evento("reinicio-" + sufijo)
    try:
        compose("stop", "emergencias")
        publicar_broker(pendiente)
        print("OK: broker acepto el desastre mientras Jakarta estaba detenido", flush=True)
    finally:
        compose("start", "emergencias")
    esperar(lambda: peticion(APP, "GET", "/salud"), 120)
    esperar(lambda: peticion(APP, "GET", "/desastres/" + pendiente["id"])["notificacionPublicada"])
    for previo in anteriores:
        actual = peticion(APP, "GET", "/desastres/" + previo["desastre"]["id"])
        assert actual["desastre"] == previo["desastre"]
    assert len(peticion(APP, "GET", "/sistemas")) == len(sistemas)
    print("OK: tras reiniciar Jakarta se conservaron registros y sistemas y se proceso el mensaje pendiente", flush=True)
    print(json.dumps({"eventoSMTP": smtp["id"], "eventoReinicio": pendiente["id"], "resultado": "APROBADO"}), flush=True)

if __name__ == "__main__":
    prueba()
