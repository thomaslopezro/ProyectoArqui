"""Migración explícita e idempotente de los datos de laboratorio de la integración anterior.
No importa bases de otros talleres ni decide la propiedad de datos de producción.
El archivo de entrada es el respaldo de nuestro registro previo de notificaciones.
"""
import os,sys,json,uuid,subprocess,urllib.request,urllib.error,time
from pathlib import Path
BASE='http://127.0.0.1:18092/plataforma-desastres/api/integracion/'
KEY=os.environ.get('INTEGRACION_KEY','arqui-interna-local-cambiar')
COMPOSE=['docker','compose','-f',str(Path(__file__).resolve().parents[1]/'compose.yaml')]
def req(path,data=None):
 h={'X-Integracion-Key':KEY,'Content-Type':'application/json'}
 with urllib.request.urlopen(urllib.request.Request(BASE+path,None if data is None else json.dumps(data).encode(),h),timeout=15) as r:return json.load(r)
org=str(uuid.UUID(req('organizacion-legado')['organizacionId']))
source=Path(sys.argv[1]);rows=json.loads(source.read_text())['registros']
for r in rows:
 try:req('importar',r['desastre'])
 except urllib.error.HTTPError as e:
  if e.code!=409:raise
  raise SystemExit('Conflicto al importar '+r['desastre']['id']+'; no se modificó la captura previa.')
# Adopt only unassigned local laboratory rows. Keep immutable operation JSON unchanged.
sql=f'''BEGIN;
CREATE TABLE IF NOT EXISTS integracion_migraciones (nombre text PRIMARY KEY,organizacion_id text NOT NULL,ejecutada_en timestamptz DEFAULT now());
UPDATE campo_operaciones SET organizacion_id='{org}',usuario='legado-laboratorio' WHERE organizacion_id IS NULL;
UPDATE "Afectaciones" SET "OrganizacionId"='{org}' WHERE "OrganizacionId" IS NULL;
INSERT INTO integracion_migraciones(nombre,organizacion_id) VALUES ('legado-laboratorio-v1','{org}') ON CONFLICT DO NOTHING;
COMMIT;'''
subprocess.run(COMPOSE+['exec','-T','db','psql','-v','ON_ERROR_STOP=1','-U','mapas','-d','mapas'],input=sql.encode(),check=True)
# Explicit coordinator action: adopt only unassigned notification receipts and subscribers.
url='http://127.0.0.1:18085/api/'
body=json.dumps({'usuario':os.environ.get('PGDR_BOOTSTRAP_USER','coordinador_demo'),'clave':os.environ.get('PGDR_BOOTSTRAP_PASSWORD','Arqui-Local-2026!')}).encode()
with urllib.request.urlopen(urllib.request.Request(url+'auth/login',body,{'Content-Type':'application/json'})) as r:token=json.load(r)['accessToken']
try:
 with urllib.request.urlopen(urllib.request.Request(url+'administracion/migrar-notificaciones',b'{}',{'Content-Type':'application/json','Authorization':'Bearer '+token})) as r:assert r.status==204
finally:
 with urllib.request.urlopen(urllib.request.Request(url+'auth/logout',b'{}',{'Content-Type':'application/json','Authorization':'Bearer '+token})) as r:assert r.status==204
print(f'Migrados {len(rows)} desastres; capturas y afectaciones anteriores conservadas en el organismo de laboratorio {org}.')
