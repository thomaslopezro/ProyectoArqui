"""Prueba cruzada con datos sintéticos. Detiene/reinicia solo servicios de arqui-integracion.
Requiere Docker y no debe ejecutarse durante una demostración concurrente.
"""
import os,json,uuid,time,base64,hashlib,subprocess,urllib.request,urllib.error,concurrent.futures
from pathlib import Path
BASE=os.environ.get('ARQUI_URL','http://127.0.0.1:18085')
C=['docker','compose','-f',str(Path(__file__).resolve().parents[1]/'compose.yaml')]
def req(method,path,data=None,t=None):
 h={'Content-Type':'application/json'}
 if t:h['Authorization']='Bearer '+t
 q=urllib.request.Request(BASE+path,None if data is None else json.dumps(data).encode(),h,method=method)
 try:
  with urllib.request.urlopen(q,timeout=25) as r:s,b=r.status,r.read()
 except urllib.error.HTTPError as r:s,b=r.code,r.read()
 try:b=json.loads(b)
 except (ValueError,UnicodeDecodeError):pass
 return s,b
def check(ok,label,detail=None):
 if not ok:raise AssertionError(label+': '+str(detail))
 print('OK: '+label,flush=True)
def uid():return str(uuid.uuid4())
def sql(service,db,q):return subprocess.check_output(C+['exec','-T',service,'psql','-At','-U','pgdr' if db=='pgdrdb' else 'mapas','-d',db,'-c',q],text=True).strip()
def login(user,pw):
 s,b=req('POST','/api/auth/login',{'usuario':user,'clave':pw});check(s==200,'login '+user,(s,b));return b
pw=os.environ.get('PGDR_BOOTSTRAP_PASSWORD','Arqui-Local-2026!');user=os.environ.get('PGDR_BOOTSTRAP_USER','coordinador_demo')
check(req('GET','/api/panorama')[0]==401,'mapas requieren sesión')
check(req('GET','/api/desastres')[0]==401,'catálogo requiere sesión')
check(req('POST','/api/auth/login',{'usuario':user,'clave':'mala'})[0]==401,'clave incorrecta rechazada')
actor=login(user,pw);t=actor['accessToken'];org=actor['organizacionId'];suffix=uuid.uuid4().hex[:10]
roles={}
for role in ['EVALUADOR','CONSULTA']:
 name=role.lower()+'_'+suffix
 s,b=req('POST','/api/auth/usuarios',{'usuario':name,'clave':'Revision-Clave-2026!','rol':role},t);check(s==201,'crear rol '+role,(s,b))
 roles[role]=login(name,'Revision-Clave-2026!')['accessToken']
sid='unificacion-'+suffix
s,b=req('POST','/api/sistemas',{'id':sid,'nombre':'Organismo de prueba','correo':sid+'@example.test','organizacionId':'falsa'},t);check(s==201,'suscripción del organismo',(s,b))
d={'id':'UNIFICADO-'+suffix,'tipo':'INUNDACION','ubicacion':'Zona sintética de integración','descripcion':'Prueba de talleres unidos','severidad':'ALTA','fecha':'2026-11-09T12:00:00Z'}
check(req('POST','/api/desastres',d,roles['EVALUADOR'])[0]==403,'evaluador no crea desastre')
check(req('POST','/api/desastres',d,roles['CONSULTA'])[0]==403,'consulta no crea desastre')
s,b=req('POST','/api/desastres',d,t);check(s==201,'desastre persistido en núcleo',(s,b))
with concurrent.futures.ThreadPoolExecutor(max_workers=10) as ex:res=list(ex.map(lambda _:req('POST','/api/desastres',d,t),range(10)))
check(all(s==200 for s,b in res),'diez reintentos conservan el registro',res)
check(req('POST','/api/desastres',dict(d,descripcion='Otro contenido'),t)[0]==409,'cambio de contenido devuelve conflicto')
for _ in range(45):
 s,b=req('GET','/api/desastres',t=t)
 registro=next((r for r in b.get('desastres',[]) if r['desastre']['id']==d['id']),None) if isinstance(b,dict) else None
 if registro and registro['notificacionPublicada'] and sid in registro['correosEnviados']:break
 time.sleep(1)
check(registro and registro['notificacionPublicada'] and sid in registro['correosEnviados'],'núcleo → outbox → Taller3 → RabbitMQ → correo confirmado',registro)
check(registro['organizacionId']==org,'catálogo conserva organismo')
foto=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=')
op={'operacionId':uid(),'evaluacionId':uid(),'eventoId':d['id'],'nivelAfectacion':4,'latitud':4.6,'longitud':-74.1,'capturadaEnUtc':'2026-11-09T12:01:00Z','descripcion':'Captura offline sintética','fuenteUbicacion':'MANUAL','evidenciaId':uid(),'fotoTipo':'image/png','fotoSha256':hashlib.sha256(foto).hexdigest(),'foto':base64.b64encode(foto).decode(),'evaluador':'suplantado','organizacion':'otra'}
check(req('POST','/api/campo/lotes',{'operaciones':[op]},roles['CONSULTA'])[0]==403,'consulta no sincroniza')
s,b=req('POST','/api/campo/lotes',{'operaciones':[op]},roles['EVALUADOR']);check(s in [200,202] and b['resultados'][0]['estado'] in ['incorporada','recibida'],'evaluador sincroniza foto y captura',(s,b))
for _ in range(15):
 s,detalle=req('GET','/api/campo/evaluaciones/'+op['evaluacionId'],t=t)
 if s==200:break
 time.sleep(1)
check(s==200 and detalle['estadoVinculo']=='VINCULADA','evaluación asociada al desastre del núcleo',(s,detalle))
check(detalle['evaluador']=='evaluador_'+suffix and detalle['organizacion']==org,'identidad procede del token, no del JSON',detalle)
check(req('GET','/api/campo/evaluaciones/'+op['evaluacionId']+'/foto',t=t)==(200,foto),'foto conservada y autorizada')
check(req('GET','/api/campo/evaluaciones/'+op['evaluacionId']+'/foto')[0]==401,'foto no es pública')
s,b=req('POST','/api/campo/lotes',{'operaciones':[op]},roles['EVALUADOR']);check(s==200 and b['resultados'][0]['estado']=='incorporada','reintento móvil idéntico no duplica',(s,b))
for path in ['/api/panorama?eventoId='+d['id'],'/api/campo/sitios?eventoId='+d['id'],'/api/panorama/geojson?eventoId='+d['id']]:
 s,b=req('GET',path,t=t);check(s==200 and op['evaluacionId'] in json.dumps(b),'mapa/calor/GeoJSON '+path,(s,b))
a={'titulo':'Punto DAL de prueba','tipo':'INUNDACION','severidad':'ALTA','descripcion':'Regresión del taller DAL','latitud':4.61,'longitud':-74.08}
s,b=req('POST','/api/afectaciones',a,t);check(s==201,'DAL original conserva creación',(s,b));aid=b['id']
check(req('POST','/api/afectaciones',a,roles['EVALUADOR'])[0]==403,'evaluador no modifica puntos de coordinación')
# A second organization is seeded only in this local test database, never through user-supplied identity.
otherorg=uid();name='otro_'+suffix
sql('database','pgdrdb',f"INSERT INTO organizaciones VALUES ('{otherorg}','Organismo sintético'); INSERT INTO usuarios SELECT '{uid()}','{name}',password_hash,password_salt,'COORDINADOR','{otherorg}',true FROM usuarios WHERE usuario='{user}';")
ot=login(name,pw)['accessToken']
for path in ['/api/campo/evaluaciones/'+op['evaluacionId'],'/api/campo/evaluaciones/'+op['evaluacionId']+'/foto','/api/afectaciones/'+aid]:check(req('GET',path,t=ot)[0]==404,'aislamiento de detalle '+path)
for path in ['/api/desastres','/api/panorama','/api/campo/sitios','/api/panorama/geojson','/api/sistemas']:
 s,b=req('GET',path,t=ot);check(s==200 and op['evaluacionId'] not in json.dumps(b) and d['id'] not in json.dumps(b) and sid not in json.dumps(b),'aislamiento de consulta '+path,(s,b))
check(req('PUT','/api/campo/evaluaciones/'+op['evaluacionId']+'/desastre',{'eventoId':d['id']},ot)[0]==404,'organismo ajeno no reasocia captura')
check(req('POST','/api/desastres',d,ot)[0]==409,'referencia existente no cambia de propietario')
check(sql('database','pgdrdb',f"SELECT count(*) FROM desastres WHERE organizacion_id='{otherorg}'")=='0','conflicto de propietario revierte inserción')
# Broker outage: registry remains writable and outbox retries without claiming delivered notifications.
try:
 subprocess.run(C+['stop','rabbitmq'],check=True,stdout=subprocess.DEVNULL)
 pending=dict(d,id='COLA-'+suffix)
 s,b=req('POST','/api/desastres',pending,t);check(s==201,'desastre durable aunque broker esté detenido',(s,b))
 check(sql('database','pgdrdb',"SELECT count(*) FROM outbox_eventos o JOIN desastres_integrados i ON i.desastre_id=o.agregado_id WHERE i.referencia='"+pending['id']+"' AND o.publicado_en IS NULL")=='1','notificación queda pendiente en outbox')
 check(req('GET','/api/panorama',t=t)[0]==200,'mapas siguen disponibles sin broker')
finally:subprocess.run(C+['start','rabbitmq'],check=True,stdout=subprocess.DEVNULL)
for _ in range(80):
 s,b=req('GET','/api/desastres',t=t)
 r=next((r for r in b.get('desastres',[]) if r['desastre']['id']==pending['id']),None) if isinstance(b,dict) else None
 if r and r['notificacionPublicada'] and sid in r['correosEnviados']:break
 time.sleep(1)
check(r and r['notificacionPublicada'] and sid in r['correosEnviados'],'notificación pendiente recuperada al volver broker',r)
# A restarted authority retains both login sessions and original registry.
subprocess.run(C+['restart','nucleo','api'],check=True,stdout=subprocess.DEVNULL)
for _ in range(75):
 try:
  s,b=req('GET','/api/desastres',t=t)
  if s==200 and any(r['desastre']['id']==d['id'] for r in b['desastres']):break
 except Exception:pass
 time.sleep(1)
check(s==200 and any(r['desastre']['id']==d['id'] for r in b['desastres']),'sesión y desastre sobreviven reinicio',(s,b))
check(req('GET','/api/campo/evaluaciones/'+op['evaluacionId'],t=t)[0]==200,'captura sobrevive reinicio')
for token in [t,ot,*roles.values()]:
 check(req('POST','/api/auth/logout',{},token)[0]==204,'logout integrado')
 check(req('GET','/api/panorama',t=token)[0]==401,'token revocado no accede a mapas')
print(json.dumps({'resultado':'APROBADO','desastre':d['id'],'evaluacion':op['evaluacionId'],'organizacion':org}),flush=True)
