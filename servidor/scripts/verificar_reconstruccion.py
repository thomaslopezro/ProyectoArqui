"""Prueba real del módulo de Reconstrucción integrado: sesión del núcleo, fase del desastre en el
núcleo, evaluaciones de servidor, proyectos y avisos en RabbitMQ. Crea datos sintéticos."""
import os,json,uuid,time,base64,urllib.request,urllib.error,subprocess
from pathlib import Path
BASE=os.environ.get('MAPAS_URL','http://127.0.0.1:18085')
C=['docker','compose','-f',str(Path(__file__).resolve().parents[1]/'compose.yaml')]
def uid():return str(uuid.uuid4())
def req(method,path,body=None,token=None,extra=None):
 h={'Content-Type':'application/json'}
 if token:h['Authorization']='Bearer '+token
 if extra:h.update(extra)
 q=urllib.request.Request(BASE+'/api'+path,None if body is None else json.dumps(body).encode(),h,method=method)
 try:
  with urllib.request.urlopen(q,timeout=30) as r:s,b=r.status,r.read()
 except urllib.error.HTTPError as r:s,b=r.code,r.read()
 try:b=json.loads(b)
 except ValueError:pass
 return s,b
total=0
def check(ok,msg,detail=None):
 global total
 if not ok:raise AssertionError(msg+': '+str(detail))
 total+=1;print('OK: '+msg,flush=True)
def login(name,pw):
 s,b=req('POST','/auth/login',{'usuario':name,'clave':pw});check(s==200,'login '+name,(s,b));return b
R='/reconstruccion'
name=os.environ.get('PGDR_BOOTSTRAP_USER','coordinador_demo');pw=os.environ.get('PGDR_BOOTSTRAP_PASSWORD','Arqui-Local-2026!')
root=login(name,pw);t=root['accessToken'];tag=uuid.uuid4().hex[:10]
# --- Sesión ---
check(req('GET',R+'/desastres')[0]==401,'sin sesión responde 401')
check(req('GET',R+'/desastres',extra={'X-Usuario':'intruso','X-Rol':'AUTORIDAD','X-Organizacion':root['organizacionId']})[0]==401,'las cabeceras de identidad sin token no sirven')
check(req('GET',R+'/desastres',token='token-inventado')[0]==401,'un token inventado responde 401')
roles={}
for rol in ['EVALUADOR','CONSULTA']:
 user='rec_'+rol.lower()+'_'+tag
 s,b=req('POST','/auth/usuarios',{'usuario':user,'clave':'Reconstruccion-Clave-2026!','rol':rol},t);check(s==201,'crear usuario '+rol,(s,b))
 roles[rol]=login(user,'Reconstruccion-Clave-2026!')['accessToken']
resp,cons=roles['EVALUADOR'],roles['CONSULTA']
# --- Desastre y evaluaciones en sus unidades dueñas ---
d={'id':'RECON-'+tag,'tipo':'TERREMOTO','ubicacion':'Zona sintética '+tag,'descripcion':'Prueba de reconstrucción','severidad':'ALTA','fecha':'2026-11-14T12:00:00Z'}
s,b=req('POST','/desastres',d,t);check(s==201,'desastre registrado en el núcleo',(s,b))
ev={}
for clave,nivel,activo in [('puente',4,'Puente'),('escuela',5,'Escuela'),('leve',2,'Vivienda')]:
 op={'operacionId':uid(),'evaluacionId':uid(),'eventoId':d['id'],'nivelAfectacion':nivel,'latitud':4.6,'longitud':-74.1,'capturadaEnUtc':'2026-11-14T12:05:00Z','descripcion':'Evaluación '+clave,'activo':activo}
 s,b=req('POST','/campo/lotes',{'operaciones':[op]},resp);check(s in [200,202],'evaluación '+clave+' capturada en servidor',(s,b));ev[clave]=op['evaluacionId']
def desastre_rec(token=t):
 s,b=req('GET',R+'/desastres',token=token);check(s==200,'el módulo lista los desastres del núcleo',(s,b))
 propios=[x for x in b if tag in x['nombre']]
 check(len(propios)==1,'el desastre aparece una vez en el módulo',b);return propios[0]
def catalogo():
 s,b=req('GET','/desastres',token=t);check(s==200,'catálogo de la plataforma',(s,b))
 return [x['desastre'] for x in b['desastres'] if x['desastre']['id']==d['id']][0]
dr=desastre_rec();did=dr['id']
check(dr['estado']=='REGISTRO','el desastre nace en fase Registro',dr)
s,b=req('POST',R+f'/desastres/{did}/iniciar-reconstruccion',token=t);check(s==422 and b.get('regla')=='RN-01','no se inicia reconstrucción desde Registro (RN-01)',(s,b))
# --- Fase en el núcleo ---
c=catalogo();F=f"/desastres/{d['id']}/fase"
s,b=req('PATCH',F,{'cambioId':uid(),'versionBase':c['version'],'fase':'RECONSTRUCCION','motivo':'Salto no permitido'},t);check(s==400,'el núcleo rechaza saltar de Registro a Reconstrucción',(s,b))
s,b=req('PATCH',F,{'cambioId':uid(),'versionBase':c['version'],'fase':'CARACTERIZACION','motivo':'Inicia la evaluación de daños'},resp);check(s==403,'solo un coordinador cambia la fase',(s,b))
cuerpo={'cambioId':uid(),'versionBase':c['version'],'fase':'CARACTERIZACION','motivo':'Inicia la evaluación de daños'}
s,b=req('PATCH',F,cuerpo,t);check(s==200 and b['fase']=='CARACTERIZACION','el núcleo pasa el desastre a Caracterización',(s,b))
s,b=req('PATCH',F,cuerpo,t);check(s==200 and b['fase']=='CARACTERIZACION','repetir el mismo cambio no lo aplica dos veces',(s,b))
s,b=req('PATCH',F,{'cambioId':uid(),'versionBase':c['version'],'fase':'CIERRE','motivo':'Versión vieja'},t);check(s==409,'una versión base vieja responde 409',(s,b))
check(desastre_rec()['estado']=='CARACTERIZACION','el módulo ve la fase nueva del núcleo')
# --- CU-01 ---
I=R+f'/desastres/{did}/iniciar-reconstruccion'
s,b=req('POST',I,token=cons);check(s==403,'consulta no inicia la reconstrucción',(s,b))
s,b=req('POST',I,token=cons,extra={'X-Rol':'AUTORIDAD','X-Usuario':'falso'});check(s==403,'falsificar el rol en una cabecera no da permisos',(s,b))
s,b=req('POST',I,token=t);check(s==200 and b['estado']=='RECONSTRUCCION','la autoridad inicia la reconstrucción (CU-01)',(s,b))
check(catalogo().get('fase')=='RECONSTRUCCION','la fase quedó guardada en el núcleo')
s,b=req('GET',f"/desastres/{d['id']}/historial",token=t);check(s==200 and [x['nuevo'].get('fase') for x in b][-2:]==['CARACTERIZACION','RECONSTRUCCION'],'el historial del núcleo conserva los cambios de fase',(s,b))
# --- CU-02 ---
s,b=req('GET',R+f'/desastres/{did}/evaluaciones',token=t);check(s==200 and {x['id'] for x in b}>=set(ev.values()),'el módulo lee las evaluaciones de servidor',(s,b))
niveles={x['id']:x['nivel'] for x in b}
check(niveles[ev['puente']]=='GRAVE' and niveles[ev['escuela']]=='DESTRUCCION_TOTAL' and niveles[ev['leve']]=='LEVE','el nivel de afectación se traduce a categoría de daño',niveles)
lote={'proyectos':[{'nombre':'Puente '+tag,'tipo':'REPARACION','evaluaciones':[ev['puente']]},{'nombre':'Vivienda '+tag,'tipo':'REPARACION','evaluaciones':[ev['leve']]},{'nombre':'Escuela '+tag,'tipo':'RECONSTRUCCION','evaluaciones':[ev['escuela']]},{'nombre':'Fantasma '+tag,'tipo':'REPARACION','evaluaciones':[uid()]}]}
L=R+f'/desastres/{did}/proyectos'
s,b=req('POST',L,lote,resp);check(s==403,'el responsable no crea proyectos',(s,b))
s,b=req('POST',L,lote,t);check(s==200 and [x['creado'] for x in b]==[True,False,True,False],'el lote crea los válidos y reporta los demás (CU-02, RN-18)',(s,b))
check(b[1]['regla']=='RN-02' and b[3]['regla']=='RN-07','daño leve se rechaza con RN-02 y evaluación inexistente con RN-07',b)
puente,escuela=b[0]['proyectoId'],b[2]['proyectoId']
s,b=req('POST',L,{'proyectos':[{'nombre':'Repetido','tipo':'REPARACION','evaluaciones':[ev['puente']]}]},t);check(s==200 and b[0]['regla']=='RN-09','una evaluación no origina dos proyectos activos (RN-09)',(s,b))
# --- CU-03 a CU-07 ---
P=R+'/proyectos/'+puente;inicio={'contratista':'Consorcio Vial','inicio':'2026-12-01','fin':'2027-06-30'}
s,b=req('POST',P+'/aprobar',{'presupuesto':500000000,'fuenteFinanciacion':'Fondo de reconstrucción','responsable':'rec_evaluador_'+tag},t);check(s==200 and b['estado']=='APROBADO','la autoridad aprueba el proyecto (CU-03)',(s,b))
s,b=req('POST',P+'/iniciar',inicio,cons);check(s==403,'consulta no inicia la ejecución',(s,b))
s,b=req('POST',P+'/iniciar',inicio,resp);check(s==200 and b['estado']=='EN_EJECUCION','el responsable inicia la ejecución (CU-04)',(s,b))
s,b=req('POST',P+'/avance',{'porcentaje':60,'descripcion':'Pilas terminadas'},resp);check(s==200,'el responsable registra avance (CU-05)',(s,b))
s,b=req('POST',P+'/costo',{'valor':600000000,'descripcion':'Acta 1'},resp);check(s==200,'un costo mayor que el presupuesto se registra y marca sobrecosto (RN-14)',(s,b))
s,b=req('POST',P+'/presupuesto',{'presupuesto':650000000,'motivo':''},t);check(s==422 and b.get('regla')=='RN-12','el presupuesto no cambia sin motivo (RN-12)',(s,b))
s,b=req('POST',P+'/presupuesto',{'presupuesto':650000000,'motivo':'Mayor cantidad de obra'},t);check(s==200,'la autoridad modifica el presupuesto con motivo (CU-06)',(s,b))
s,b=req('POST',P+'/estado',{'estado':'FINALIZADO','motivo':''},resp);check(s==422,'no se finaliza con avance menor que 100 (RN-15)',(s,b))
s,b=req('POST',P+'/avance',{'porcentaje':100,'descripcion':'Obra entregada'},resp);check(s==200,'avance al 100 %',(s,b))
s,b=req('POST',P+'/estado',{'estado':'FINALIZADO','motivo':''},resp);check(s==200 and b['estado']=='FINALIZADO','el responsable finaliza el proyecto (CU-07)',(s,b))
# --- CU-08 ---
s,b=req('GET',P,token=cons);check(s==200 and b['estado']=='FINALIZADO','consulta ve el proyecto de su organismo (CU-08)',(s,b))
s,b=req('GET',P+'/auditoria',token=t);check(s==200 and any(x.get('resultado')=='RECHAZADO' for x in b) and any(x.get('resultado')=='EXITOSO' for x in b),'la auditoría guarda los cambios y los intentos rechazados (RN-17)',(s,b))
s,b=req('POST',P+'/aprobar',{'presupuesto':1,'fuenteFinanciacion':'x','responsable':'y'},cons);check(s==403,'consulta no modifica',(s,b))
# --- CU-09 ---
K=R+f'/desastres/{did}/cerrar'
s,b=req('POST',K,token=t);check(s==422 and b.get('regla')=='RN-04','no se cierra con proyectos sin terminar (RN-04)',(s,b))
s,b=req('POST',R+'/proyectos/'+escuela+'/estado',{'estado':'CANCELADO','motivo':'Se atenderá con otro programa'},t);check(s==200 and b['estado']=='CANCELADO','la autoridad cancela un proyecto propuesto',(s,b))
s,b=req('POST',K,token=t);check(s==200 and b['estado']=='CIERRE','la autoridad cierra el desastre (CU-09)',(s,b))
check(catalogo().get('fase')=='CIERRE','el cierre quedó guardado en el núcleo')
# --- Avisos en RabbitMQ ---
def pendientes():return int(subprocess.check_output(C+['exec','-T','reconstruccion-db','psql','-At','-U','reconstruccion','-d','reconstruccion','-c','SELECT count(*) FROM outbox_eventos WHERE publicado_en IS NULL'],text=True).strip())
for _ in range(20):
 if pendientes()==0:break
 time.sleep(1)
check(pendientes()==0,'la bandeja de salida quedó despachada')
q=urllib.request.Request('http://127.0.0.1:15684/api/queues/%2F/integracion.reconstruccion',headers={'Authorization':'Basic '+base64.b64encode(b'taller:taller-local').decode()})
for _ in range(15):
 with urllib.request.urlopen(q,timeout=10) as r:cola=json.loads(r.read())
 if cola.get('messages',0)>0:break
 time.sleep(1)
check(cola.get('messages',0)>0,'los avisos llegaron a la cola de integración en RabbitMQ',cola.get('messages'))
print(f'\nReconstrucción integrada: {total} comprobaciones correctas.')
