from acceso_pruebas import HEADERS, SESION
"""Prueba HTTP de sincronización. Usa un desastre ficticio único por ejecución."""
import argparse
import base64
import hashlib
import json
import time
import uuid
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from urllib.request import Request, urlopen


def api(path, payload=None):
    data = None if payload is None else json.dumps(payload).encode()
    req = Request(args.url + "/api/campo/" + path, data, HEADERS)
    with urlopen(req, timeout=8) as response:
        return response.status, json.load(response)


parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--url", default="http://127.0.0.1:18085")
parser.add_argument("--diferida", action="store_true")
args = parser.parse_args()
evento = "PRUEBA-" + uuid.uuid4().hex


def operacion(nivel, latitud):
    return {
        "operacionId": str(uuid.uuid4()), "evaluacionId": str(uuid.uuid4()),
        "eventoId": evento, "latitud": latitud, "longitud": -74.0817,
        "nivelAfectacion": nivel, "capturadaEnUtc": datetime.now(timezone.utc).isoformat(),
    }


def enviar(*operaciones):
    codigo, respuesta = api("lotes", {"operaciones": operaciones})
    return codigo, [resultado["estado"] for resultado in respuesta["resultados"]]


# PNG de 1 x 1 con hash del archivo.
png = base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aC1sAAAAASUVORK5CYII=")
alta = operacion(5, 4.61) | {
    "foto": base64.b64encode(png).decode(), "evidenciaId": str(uuid.uuid4()),
    "fotoTipo": "image/png", "fotoSha256": hashlib.sha256(png).hexdigest(),
}
baja = operacion(1, 4.60)
invalida = operacion(6, 4.59)
sin_latitud = operacion(2, None)
hash_incorrecto = operacion(4, 4.62) | {
    "foto": alta["foto"], "evidenciaId": str(uuid.uuid4()),
    "fotoTipo": "image/png", "fotoSha256": "0000",
}
inicio = time.monotonic()
codigo, estados = enviar(alta, baja, invalida, sin_latitud, hash_incorrecto)
acuse = time.monotonic() - inicio
assert codigo == (202 if args.diferida else 200), (codigo, estados)
assert estados[:2] == [("recibida" if args.diferida else "incorporada")] * 2, estados
assert estados[2:] == ["rechazada"] * 3, estados
assert enviar(alta | {"nivelAfectacion": 4}) == (200, ["conflicto"])
assert enviar(alta | {"operacionId": str(uuid.uuid4())}) == (200, ["conflicto"])

limite = time.monotonic() + 12
while True:
    _, sitios = api("sitios?eventoId=" + evento)
    if len(sitios) == 2:
        break
    assert time.monotonic() < limite, sitios
    time.sleep(0.25)

assert {(s["nivelAfectacion"], s["latitud"], s["longitud"]) for s in sitios} == {
    (5, 4.61, -74.0817), (1, 4.60, -74.0817)
}
visible = time.monotonic() - inicio
with ThreadPoolExecutor(max_workers=2) as executor:
    repetidos = list(executor.map(lambda _: enviar(alta, baja), range(2)))
assert repetidos == [(200, ["incorporada", "incorporada"])] * 2
assert len(api("sitios?eventoId=" + evento)[1]) == 2
assert api("sitios?eventoId=SIN-SITIOS-" + uuid.uuid4().hex)[1] == []
print(f"OK modo {'diferido' if args.diferida else 'inmediato'}: lote mixto, foto, conflictos, reintento y consulta.")
print(f"Observación local: acuse {acuse * 1000:.0f} ms, consulta completa {visible * 1000:.0f} ms.")
