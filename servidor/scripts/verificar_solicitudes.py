"""Prueba real del Taller 2 unido al núcleo y los clientes. Crea datos sintéticos.
Reinicia únicamente nucleo/api de arqui-integracion para validar persistencia.
"""
import os,json,uuid,time,urllib.request,urllib.error,subprocess,concurrent.futures
from pathlib import Path
BASE=os.environ.get('MAPAS_URL','http://127.0.0.1:18085')
C=['docker','compose','-f',str(Path(__file__).resolve().parents[1]/'compose.yaml')]
def uid():return str(uuid.uuid4())
def req(method,path,body=None,token=None):
 h={'Content-Type':'application/json'}
 if token:h['Authorization']='Bearer '+token
 q=urllib.request.Request(BASE+'/api'+path,None if body is None else json.dumps(body).encode(),h,method=method)
 try:
  with urllib.request.urlopen(q,timeout=25) as r:s,b=r.status,r.read()
 except urllib.error.HTTPError as r:s,b=r.code,r.read()
 try:b=json.loads(b)
 except ValueError:pass
 return s,b
def check(ok,msg,detail=None):
 if not ok:raise AssertionError(msg+': '+str(detail))
 print('OK: '+msg,flush=True)
def sql(q):return subprocess.check_output(C+['exec','-T','database','psql','-At','-U','pgdr','-d','pgdrdb','-c',q],text=True).strip()
def login(name,pw):
 s,b=req('POST','/auth/login',{'usuario':name,'clave':pw});check(s==200,'login '+name,(s,b));return b
name=os.environ.get('PGDR_BOOTSTRAP_USER','coordinador_demo');pw=os.environ.get('PGDR_BOOTSTRAP_PASSWORD','Arqui-Local-2026!')
root=login(name,pw);t=root['accessToken'];org=root['organizacionId'];tag=uuid.uuid4().hex[:10]
check(req('GET','/solicitudes')[0]==401,'solicitudes requieren sesión')
roles={}
for rol in ['EVALUADOR','CONSULTA']:
 user='sol_'+rol.lower()+'_'+tag
 s,b=req('POST','/auth/usuarios',{'usuario':user,'clave':'Solicitudes-Clave-2026!','rol':rol},t);check(s==201,'crear rol '+rol,(s,b))
 roles[rol]=login(user,'Solicitudes-Clave-2026!')['accessToken']
d={'id':'SOLICITUDES-'+tag,'tipo':'INUNDACION','ubicacion':'Zona sintética de recursos','descripcion':'Prueba Taller 2','severidad':'ALTA','fecha':'2026-11-09T12:00:00Z'}
s,b=req('POST','/desastres',d,t);check(s==201,'desastre en autoridad única',(s,b))
r={'solicitudId':uid(),'eventoId':d['id'],'tipoRecurso':'Agua potable','cantidad':50,'prioridad':'ALTA'}
for change in [{'cantidad':0},{'cantidad':-1},{'cantidad':None},{'tipoRecurso':'  '},{'tipoRecurso':'x'*121},{'prioridad':None},{'prioridad':'CRITICA'},{'solicitudId':None},{'eventoId':None}]:
 s,b=req('POST','/solicitudes',r|change,t);check(s==400,'rechazar datos inválidos '+str(change),(s,b))
check(req('POST','/solicitudes',r,roles['CONSULTA'])[0]==403,'consulta no registra solicitudes')
check(req('POST','/solicitudes',r|{'eventoId':'evento-inexistente'},t)[0]==404,'no inventa desastres para registrar')
s,b=req('POST','/solicitudes',r|{'organizacionId':'falsa','estado':'ATENDIDA','descripcionDesastre':'falsa'},roles['EVALUADOR']);check(s==201,'evaluador registra solicitud',(s,b));sid=b['id']
check(b['estado']=='PENDIENTE' and b['eventoId']==d['id'] and b['descripcionDesastre']!='falsa','estado y desastre provienen del negocio',b)
check(sql(f"SELECT organizacion_id::text FROM solicitudes_recursos WHERE id={sid}")==org,'organismo procede de sesión')
with concurrent.futures.ThreadPoolExecutor(max_workers=10) as pool:res=list(pool.map(lambda _:req('POST','/solicitudes',r,roles['EVALUADOR']),range(10)))
check(all(s==200 and b['id']==sid for s,b in res),'diez reintentos de registro no duplican',res)
check(req('POST','/solicitudes',r|{'cantidad':51},t)[0]==409,'misma solicitudId con otros datos produce conflicto')
check(sql(f"SELECT count(*) FROM solicitudes_historial WHERE solicitud_id={sid}")=='1','una auditoría de registro')
for path in ['/solicitudes','/solicitudes/pendientes','/solicitudes?eventoId='+d['id']]:
 s,b=req('GET',path,token=roles['CONSULTA']);check(s==200 and any(x['id']==sid for x in b),'lectura autorizada '+path,(s,b))
for ident in ['0','-1','abc','99999999999999999999999999999999999999']:
 check(req('GET','/solicitudes/'+ident,token=t)[0]==400,'ID inválido '+ident)
check(req('GET','/solicitudes/9223372036854775807',token=t)[0]==404,'ID inexistente devuelve 404')
check(req('PATCH',f'/solicitudes/{sid}/atender',token=roles['EVALUADOR'])[0]==403,'evaluador no atiende solicitudes')
check(req('PATCH',f'/solicitudes/{sid}/atender',token=roles['CONSULTA'])[0]==403,'consulta no atiende solicitudes')
with concurrent.futures.ThreadPoolExecutor(max_workers=10) as pool:res=list(pool.map(lambda _:req('PATCH',f'/solicitudes/{sid}/atender',token=t),range(10)))
check(sum(s==200 for s,b in res)==1 and sum(s==409 for s,b in res)==9,'una sola atención gana la carrera concurrente',res)
s,b=req('GET',f'/solicitudes/{sid}',token=t);check(s==200 and b['estado']=='ATENDIDA' and b['cantidad']==50 and b['atendidaEn'] and b['atendidaPor'],'atención conserva datos y responsable',(s,b))
check(req('PATCH',f'/solicitudes/{sid}/atender',token=t)[0]==409,'atención repetida devuelve conflicto')
s,b=req('GET','/solicitudes/pendientes',token=t);check(s==200 and not any(x['id']==sid for x in b),'atendida desaparece de pendientes')
s,b=req('GET',f'/solicitudes/{sid}/historial',token=t);check(s==200 and [x[0] for x in b]==['SOLICITUD_REGISTRADA','SOLICITUD_ATENDIDA'],'historial completo sin duplicados',(s,b))
# Isolation: a test-only coordinator in another organization cannot access or mutate this request.
org2=uid();other='sol_otro_'+tag
sql(f"INSERT INTO organizaciones VALUES ('{org2}','Organismo de prueba solicitudes'); INSERT INTO usuarios SELECT '{uid()}','{other}',password_hash,password_salt,'COORDINADOR','{org2}',true FROM usuarios WHERE usuario='{name}';")
ot=login(other,pw)['accessToken']
for method,path,body in [('GET',f'/solicitudes/{sid}',None),('GET',f'/solicitudes/{sid}/historial',None),('PATCH',f'/solicitudes/{sid}/atender',None),('POST','/solicitudes',r|{'solicitudId':uid()})]:
 check(req(method,path,body,ot)[0]==404,'aislamiento entre organismos '+method+' '+path)
check(req('GET','/solicitudes',token=ot)==(200,[]),'lista ajena vacía')
# Atomic registration and audit: a synthetic audit failure must leave no request behind.
rollback=uid()
sql("CREATE FUNCTION solicitudes_falla_auditoria() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'FALLO_SINTETICO_SOLICITUDES'; END $$; CREATE TRIGGER solicitudes_falla_auditoria BEFORE INSERT ON solicitudes_historial FOR EACH ROW EXECUTE FUNCTION solicitudes_falla_auditoria();")
try:
 s,b=req('POST','/solicitudes',r|{'solicitudId':rollback},t);check(s==500,'fallo sintético de auditoría comunicado')
 check(sql(f"SELECT count(*) FROM solicitudes_recursos WHERE solicitud_id='{rollback}'")=='0','JTA revierte solicitud si falla auditoría')
finally:sql('DROP TRIGGER solicitudes_falla_auditoria ON solicitudes_historial; DROP FUNCTION solicitudes_falla_auditoria();')
# Persist one pending request, restart, then verify both states and audit.
s,b=req('POST','/solicitudes',r|{'solicitudId':uid(),'tipoRecurso':'Botiquines','cantidad':10},t);check(s==201,'nueva solicitud pendiente',(s,b));pending=b['id']
subprocess.run(C+['restart','nucleo','api'],check=True,stdout=subprocess.DEVNULL)
for _ in range(90):
 try:
  if req('GET',f'/solicitudes/{sid}',token=t)[0]==200:break
 except Exception:pass
 time.sleep(1)
check(req('GET',f'/solicitudes/{sid}',token=t)[1]['estado']=='ATENDIDA','atención persiste tras reinicio')
check(req('GET',f'/solicitudes/{pending}',token=t)[1]['estado']=='PENDIENTE','pendiente persiste tras reinicio')
check(req('GET',f'/solicitudes/{sid}/historial',token=t)[0]==200,'historial persiste tras reinicio')
check(req('GET','/panorama',token=t)[0]==200,'mapas anteriores siguen disponibles')
for token in [t,ot,*roles.values()]:
 req('POST','/auth/logout',{},token)
 check(req('GET','/solicitudes',token=token)[0]==401,'sesión revocada no accede a solicitudes')
print(json.dumps({'resultado':'APROBADO','solicitudAtendida':sid,'solicitudPendiente':pending,'desastre':d['id']}),flush=True)
