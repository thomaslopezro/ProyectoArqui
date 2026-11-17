import json,os,time,urllib.request
base='http://127.0.0.1:18085/api/'
token=None
for _ in range(120):
 try:
  if token is None:
   body=json.dumps({'usuario':os.environ.get('PGDR_BOOTSTRAP_USER','coordinador_demo'),'clave':os.environ.get('PGDR_BOOTSTRAP_PASSWORD','Arqui-Local-2026!')}).encode()
   with urllib.request.urlopen(urllib.request.Request(base+'auth/login',body,{'Content-Type':'application/json'}),timeout=5) as r:token=json.load(r)['accessToken']
  with urllib.request.urlopen(urllib.request.Request(base+'sistemas',headers={'Authorization':'Bearer '+token}),timeout=5) as r:
   if r.status==200:print('Notificaciones disponibles.');break
 except Exception:pass
 time.sleep(1)
else:raise SystemExit('No se pudo validar notificaciones: revisa credenciales de laboratorio y logs.')
with urllib.request.urlopen(urllib.request.Request(base+'auth/logout',b'{}',{'Content-Type':'application/json','Authorization':'Bearer '+token}),timeout=5):pass
