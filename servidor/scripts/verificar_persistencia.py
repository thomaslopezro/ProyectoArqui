#!/usr/bin/env python3
"""Reinicia solo api y db de ESTE taller. Conserva volúmenes y elimina su registro al final."""
import subprocess
import time
import uuid
from pathlib import Path
from verificar_api import request

root = Path(__file__).resolve().parents[1]
status, item = request('POST','/afectaciones',dict(titulo='PRUEBA-PERSISTENCIA-'+uuid.uuid4().hex[:8],tipo='OTRO',severidad='BAJA',descripcion='Registro temporal de prueba',latitud=0,longitud=0))
assert status == 201, (status,item)
try:
    subprocess.run(['docker','compose','restart','api','db'],cwd=root,check=True)
    deadline=time.monotonic()+90
    while True:
        try:
            status,health=request('GET','/salud')
            if status==200: break
        except OSError:
            pass
        if time.monotonic()>deadline: raise RuntimeError('Servicios no listos después de 90 segundos')
        time.sleep(1)
    status, found=request('GET','/afectaciones/'+item['id'])
    assert status==200 and found==item, (status,found)
    print('OK: registro y versión conservados tras reiniciar API y PostgreSQL.')
finally:
    try: request('DELETE',f"/afectaciones/{item['id']}?version={item['version']}")
    except OSError: print('No fue posible limpiar el registro temporal: '+item['id'])
