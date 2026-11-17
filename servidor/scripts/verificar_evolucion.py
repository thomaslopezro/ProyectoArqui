"""Conexiones de talleres: gravedad/avisos, versiones/conflictos y origen de solicitudes."""
import os,json,uuid,time,subprocess,urllib.request,urllib.error,concurrent.futures,xml.etree.ElementTree as ET,base64,hashlib
from pathlib import Path
BASE='http://127.0.0.1:18085';C=['docker','compose','-f',str(Path(__file__).resolve().parents[1]/'compose.yaml')]
def uid():return str(uuid.uuid4())
def req(method,path,b=None,t=None):
 h={'Content-Type':'application/json'}
 if t:h['Authorization']='Bearer '+t
 q=urllib.request.Request(BASE+'/api'+path,None if b is None else json.dumps(b).encode(),h,method=method)
 try:
  with urllib.request.urlopen(q,timeout=25) as r:s,b=r.status,r.read()
 except urllib.error.HTTPError as r:s,b=r.code,r.read()
 try:b=json.loads(b)
 except ValueError:pass
 return s,b
def check(ok,msg,extra=None):
 if not ok:raise AssertionError(msg+': '+str(extra))
 print('OK: '+msg,flush=True)
def esperar(fn,segundos=90):
 for _ in range(segundos):
  result=fn()
  if result:return result
  time.sleep(1)
 raise AssertionError('Condición no alcanzada')
def sql(servicio,db,q):return subprocess.check_output(C+['exec','-T',servicio,'psql','-At','-U','pgdr' if db=='pgdrdb' else 'mapas','-d',db,'-c',q],text=True).strip()
name=os.environ.get('PGDR_BOOTSTRAP_USER','coordinador_demo');pw=os.environ.get('PGDR_BOOTSTRAP_PASSWORD','Arqui-Local-2026!')
s,actor=req('POST','/auth/login',{'usuario':name,'clave':pw});check(s==200,'login único');t=actor['accessToken'];tag=uuid.uuid4().hex[:8]
d={'id':'EVOLUCION-'+tag,'tipo':'INUNDACION','ubicacion':'Zona sintética','descripcion':'Prueba de evolución de talleres','severidad':'BAJA','fecha':'2026-11-10T12:00:00Z'}
s,b=req('POST','/desastres',d,t);check(s==201,'registrar desastre',(s,b))
s,b=req('POST','/sistemas',{'id':'evolucion-'+tag,'nombre':'Equipo de prueba','correo':'evolucion-'+tag+'@example.test'},t);check(s==201,'destinatario de avisos',(s,b))
change={'cambioId':uid(),'versionBase':0,'severidad':'ALTA','motivo':'Nuevas evaluaciones amplían el impacto'}
with concurrent.futures.ThreadPoolExecutor(max_workers=5) as ex:res=list(ex.map(lambda _:req('PATCH','/desastres/'+d['id']+'/gravedad',change,t),range(5)))
check(all(s==200 for s,b in res),'reintentos de cambio de gravedad idempotentes',res)
check(req('PATCH','/desastres/'+d['id']+'/gravedad',change|{'cambioId':uid(),'severidad':'CRITICA'},t)[0]==409,'versión de desastre obsoleta rechazada')
s,h=req('GET','/desastres/'+d['id']+'/historial',t=t);check(s==200 and len(h)==1 and h[0]['anterior']['severidad']=='BAJA' and h[0]['nuevo']['severidad']=='ALTA','historial del cambio conserva antes/después',(s,h))
def notificado():
 s,b=req('GET','/desastres',t=t)
 r=next((r for r in b.get('desastres',[]) if r['desastre']['id']==d['id']),None) if isinstance(b,dict) else None
 return r if r and r['desastre']['version']==1 and r['notificacionPublicada'] and 'evolucion-'+tag in r['correosEnviados'] else None
check(bool(esperar(notificado)),'aviso de nueva gravedad confirmado por Taller 3/SMTP')
check(sql('database','pgdrdb',"SELECT count(*) FROM outbox_eventos o JOIN desastres_integrados i ON i.desastre_id=o.agregado_id WHERE i.referencia='"+d['id']+"' AND o.tipo='DESASTRE_GRAVEDAD_CAMBIADA'")=='1','un solo evento de gravedad para cinco reintentos')
op={'operacionId':uid(),'evaluacionId':uid(),'eventoId':d['id'],'nivelAfectacion':2,'latitud':4.6,'longitud':-74.1,'capturadaEnUtc':'2026-11-10T12:05:00Z','descripcion':'Versión capturada','activo':'Escuela'}
foto=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=');op.update(foto=base64.b64encode(foto).decode(),fotoSha256=hashlib.sha256(foto).hexdigest(),fotoTipo='image/png',evidenciaId=uid())
s,b=req('POST','/campo/lotes',{'operaciones':[op]},t);check(s in [200,202],'captura original aceptada',(s,b));eid=op['evaluacionId'];path='/campo/evaluaciones/'+eid
esperar(lambda:req('GET',path,t=t)[0]==200)
patch={'operacionId':uid(),'versionBase':0,'nivelAfectacion':4,'descripcion':'Daño verificado en nueva visita','activo':'Escuela','latitud':4.61,'longitud':-74.11,'motivo':'Nueva inspección'}
s,b=req('PUT',path,patch,t);check(s==200 and b['version']==1,'corrección crea versión 1',(s,b))
check(req('PUT',path,patch,t)==(200,b),'reintento de corrección no duplica')
local=patch|{'operacionId':uid(),'descripcion':'Propuesta offline anterior','nivelAfectacion':5}
s,b=req('PUT',path,local,t);check(s==409 and b['versionActual']==1,'propuesta offline obsoleta queda en conflicto',(s,b))
s,actual=req('GET',path,t=t);check(s==200 and actual['version']==1 and actual['descripcion']==patch['descripcion'],'conflicto no sobrescribe servidor',(s,actual))
resolved=local|{'operacionId':uid(),'versionBase':actual['version'],'motivo':'Propuesta local revisada contra el servidor'}
s,b=req('PUT',path,resolved,t);check(s==200 and b['version']==2,'resolución explícita permite reenviar sobre versión revisada',(s,b))
s,h=req('GET',path+'/historial',t=t);check(s==200 and len(h)==2 and h[0]['anterior']['descripcion']==op['descripcion'],'historial de evaluaciones conserva original y correcciones',(s,h))
check(sql('db','mapas',f"SELECT datos->>'descripcion' FROM campo_operaciones WHERE evaluacion_id='{eid}'")==op['descripcion'],'operación original permanece inmutable')
s,b=req('POST','/campo/lotes',{'operaciones':[op]},t);check(s==200 and b['resultados'][0]['estado']=='incorporada','reenvío original no revierte correcciones',(s,b))
check(req('GET',path,t=t)[1]['version']==2,'versión actual no vuelve a cero')
check(req('GET',path+'/foto',t=t)==(200,foto),'foto original permanece disponible tras correcciones')
check(req('GET','/campo/sitios?eventoId='+d['id'],t=t)[1][0]['nivelAfectacion']==5,'calor lee versión actual')
request={'solicitudId':uid(),'versionBase':2,'tipoRecurso':'Botiquines','cantidad':8,'prioridad':'ALTA','eventoId':'falso','evaluacionVersion':99}
s,b=req('POST',path+'/solicitudes',request,t);check(s==201,'solicitud nace de evaluación existente',(s,b));rid=b['id']
check(b['evaluacionId']==eid and b['evaluacionVersion']==2 and b['eventoId']==d['id'] and json.loads(b['evaluacionResumen'])['nivelAfectacion']==5,'origen y versión provienen del servidor, no del cliente',b)
check(req('POST',path+'/solicitudes',request,t)[0]==200,'reintento de solicitud derivada no duplica')
check(req('POST',path+'/solicitudes',request|{'solicitudId':uid(),'versionBase':0},t)[0]==409,'no deriva solicitud de una versión obsoleta sin revisar')
check(req('POST','/solicitudes/desde-evaluacion',request,t)[0]==403,'cliente externo no puede falsificar atestación de evaluación')
s,b=req('PATCH',f'/solicitudes/{rid}/atender',t=t);check(s==200 and b['evaluacionVersion']==2,'atención conserva origen de daños',(s,b))
# A later correction must not rewrite the historical source of a resource request.
s,b=req('PUT',path,resolved|{'operacionId':uid(),'versionBase':2,'nivelAfectacion':3,'motivo':'Nueva inspección posterior a la solicitud'},t);check(s==200 and b['version']==3,'nueva corrección posterior a la solicitud',(s,b))
s,b=req('GET',f'/solicitudes/{rid}',t=t);check(s==200 and b['evaluacionVersion']==2 and json.loads(b['evaluacionResumen'])['nivelAfectacion']==5,'solicitud conserva instantánea de origen aunque cambie la evaluación',(s,b))
s,b=req('POST',path+'/solicitudes',request,t);check(s==200 and b['id']==rid and b['evaluacionVersion']==2,'reintento posterior a otra corrección recupera solicitud original sin duplicar',(s,b))
# Read-only and foreign-organization boundaries apply to the new APIs too.
reader='evol_consulta_'+tag
req('POST','/auth/usuarios',{'usuario':reader,'clave':'Evolucion-Clave-2026!','rol':'CONSULTA'},t)
rt=req('POST','/auth/login',{'usuario':reader,'clave':'Evolucion-Clave-2026!'})[1]['accessToken']
check(req('PUT',path,resolved,rt)[0]==403,'consulta no corrige evaluaciones')
check(req('PATCH','/desastres/'+d['id']+'/gravedad',change,rt)[0]==403,'consulta no cambia gravedad')
org=uid();other='evol_otro_'+tag
sql('database','pgdrdb',f"INSERT INTO organizaciones VALUES ('{org}','Otro organismo sintético'); INSERT INTO usuarios SELECT '{uid()}','{other}',password_hash,password_salt,'COORDINADOR','{org}',true FROM usuarios WHERE usuario='{name}';")
ot=req('POST','/auth/login',{'usuario':other,'clave':pw})[1]['accessToken']
for method,payload,p in [('GET',None,path+'/historial'),('PUT',resolved,path),('POST',request,path+'/solicitudes'),('GET',None,'/desastres/'+d['id']+'/historial')]:check(req(method,p,payload,ot)[0]==404,'aislamiento nuevo flujo '+method+' '+p)
# SOAP adapter must issue the same REST session and observe revocation.
soap='http://127.0.0.1:18092/plataforma-desastres/AutenticacionProyectoService/AutenticacionSoap'
def soapcall(op,**values):
 env=ET.Element('{http://schemas.xmlsoap.org/soap/envelope/}Envelope');body=ET.SubElement(env,'{http://schemas.xmlsoap.org/soap/envelope/}Body');call=ET.SubElement(body,'{urn:arqui:identidad}'+op)
 for k,v in values.items():ET.SubElement(call,k).text=v
 with urllib.request.urlopen(urllib.request.Request(soap,ET.tostring(env),{'Content-Type':'text/xml; charset=utf-8','SOAPAction':'""'}),timeout=20) as response:
  root=ET.fromstring(response.read());texts=[e.text for e in root.iter() if e.tag.split('}')[-1]=='return'];return json.loads(texts[0]) if texts else None
check('error' in soapcall('autenticar',usuario=name,clave='incorrecta'),'SOAP rechaza credenciales inválidas')
sesion=soapcall('autenticar',usuario=name,clave=pw);st=sesion['accessToken'];check(req('GET','/panorama',t=st)[0]==200,'token SOAP funciona en mapas REST')
soapcall('cerrar',token=st);check(req('GET','/panorama',t=st)[0]==401,'logout SOAP revoca sesión REST')
for token in [t,rt,ot]:req('POST','/auth/logout',{},token)
print(json.dumps({'resultado':'APROBADO','desastre':d['id'],'evaluacion':eid,'solicitud':rid}),flush=True)
