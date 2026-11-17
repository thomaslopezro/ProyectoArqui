"""Fallo controlado de outbox, limitado a un desastre sintético, para comprobar JTA."""
import os,json,uuid,urllib.request,urllib.error,subprocess
from pathlib import Path
BASE='http://127.0.0.1:18085/api';C=['docker','compose','-f',str(Path(__file__).resolve().parents[1]/'compose.yaml')]
def req(method,path,body=None,token=None):
 h={'Content-Type':'application/json'}
 if token:h['Authorization']='Bearer '+token
 q=urllib.request.Request(BASE+path,None if body is None else json.dumps(body).encode(),h,method=method)
 try:
  with urllib.request.urlopen(q,timeout=20) as r:return r.status,json.loads(r.read() or 'null')
 except urllib.error.HTTPError as r:return r.code,None

def sql(q):return subprocess.check_output(C+['exec','-T','database','psql','-At','-U','pgdr','-d','pgdrdb','-c',q],text=True).strip()
s,a=req('POST','/auth/login',{'usuario':os.environ.get('PGDR_BOOTSTRAP_USER','coordinador_demo'),'clave':os.environ.get('PGDR_BOOTSTRAP_PASSWORD','Arqui-Local-2026!')});assert s==200;t=a['accessToken']
ref='JTA-GRAVEDAD-'+uuid.uuid4().hex[:8]
d={'id':ref,'tipo':'INUNDACION','ubicacion':'Zona sintética','descripcion':'Fallo transaccional controlado','severidad':'BAJA','fecha':'2026-11-10T12:00:00Z'}
assert req('POST','/desastres',d,t)[0]==201
id=str(uuid.UUID(sql(f"SELECT desastre_id FROM desastres_integrados WHERE referencia='{ref}'")))
change={'cambioId':str(uuid.uuid4()),'versionBase':0,'severidad':'CRITICA','motivo':'Prueba transaccional'}
sql(f"CREATE FUNCTION prueba_gravedad_outbox() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.agregado_id='{id}'::uuid AND NEW.tipo='DESASTRE_GRAVEDAD_CAMBIADA' THEN RAISE EXCEPTION 'FALLO_SINTETICO_OUTBOX'; END IF; RETURN NEW; END $$; CREATE TRIGGER prueba_gravedad_outbox BEFORE INSERT ON outbox_eventos FOR EACH ROW EXECUTE FUNCTION prueba_gravedad_outbox();")
try:
 assert req('PATCH',f'/desastres/{ref}/gravedad',change,t)[0]==500
 assert sql(f"SELECT version FROM desastres WHERE id='{id}'")=='0'
 assert sql(f"SELECT count(*) FROM desastre_cambios WHERE desastre_id='{id}'")=='0'
 assert sql(f"SELECT count(*) FROM outbox_eventos WHERE agregado_id='{id}' AND tipo='DESASTRE_GRAVEDAD_CAMBIADA'")=='0'
 print('OK: fallo del aviso revierte gravedad, versión e historial sin confirmación parcial.',flush=True)
finally:sql('DROP TRIGGER prueba_gravedad_outbox ON outbox_eventos; DROP FUNCTION prueba_gravedad_outbox();')
assert req('PATCH',f'/desastres/{ref}/gravedad',change,t)[0]==200
assert len(req('GET',f'/desastres/{ref}/historial',token=t)[1])==1
req('POST','/auth/logout',{},t)
print('OK: el mismo cambio puede reintentarse tras el fallo y genera una sola versión.')
