import time,urllib.request,urllib.error
# El módulo responde 401 sin sesión: eso ya indica que está arriba y validando contra el núcleo.
for _ in range(180):
 try:
  urllib.request.urlopen('http://127.0.0.1:18085/api/reconstruccion/desastres',timeout=3)
 except urllib.error.HTTPError as e:
  if e.code==401:print('Módulo de Reconstrucción disponible detrás de la entrada.');break
 except Exception:pass
 time.sleep(1)
else:raise SystemExit('Reconstrucción no estuvo disponible; revisa los logs de reconstruccion.')
