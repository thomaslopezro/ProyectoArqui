import os,time
from urllib.request import urlopen
url=os.environ.get('MAPAS_URL','http://127.0.0.1:18085').rstrip('/')+'/api/salud'
for _ in range(60):
    try:
        with urlopen(url,timeout=2) as r:
            if r.status==200:break
    except OSError:pass
    time.sleep(1)
else:raise SystemExit('La API no quedó disponible. Revisa docker compose logs.')
