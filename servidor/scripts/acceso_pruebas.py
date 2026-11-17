"""Sesión de laboratorio para las regresiones HTTP existentes."""
import atexit,json,os,urllib.request
base=os.environ.get('MAPAS_URL','http://127.0.0.1:18085').rstrip('/')
data=json.dumps({'usuario':os.environ.get('PGDR_BOOTSTRAP_USER','coordinador_demo'),'clave':os.environ.get('PGDR_BOOTSTRAP_PASSWORD','Arqui-Local-2026!')}).encode()
with urllib.request.urlopen(urllib.request.Request(base+'/api/auth/login',data,{'Content-Type':'application/json'})) as r:SESION=json.load(r)
HEADERS={'Content-Type':'application/json','Authorization':'Bearer '+SESION['accessToken']}
def cerrar():
 try:
  with urllib.request.urlopen(urllib.request.Request(base+'/api/auth/logout',b'{}',HEADERS),timeout=5):pass
 except Exception:pass
atexit.register(cerrar)
