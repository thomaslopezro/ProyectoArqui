import time,urllib.request
for _ in range(120):
 try:
  with urllib.request.urlopen('http://127.0.0.1:18092/plataforma-desastres/api/health',timeout=3) as r:
   if r.status==200:print('Núcleo de identidad y persistencia disponible.');break
 except Exception:pass
 time.sleep(1)
else:raise SystemExit('El núcleo no estuvo disponible; revisa los logs de nucleo.')
