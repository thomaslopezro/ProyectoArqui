from acceso_pruebas import HEADERS, SESION
"""Pruebas de integración: flujo de campo, panorama, calor, detalle y GeoJSON."""
import base64
import hashlib
import json
import os
import time
import unittest
import uuid
from datetime import datetime, timezone
from urllib.error import HTTPError
from urllib.request import Request, urlopen

BASE=os.environ.get('MAPAS_URL','http://127.0.0.1:18085').rstrip('/')
def request(path,body=None,method=None):
    req=Request(BASE+'/api'+path,data=None if body is None else json.dumps(body).encode(),method=method,headers=HEADERS)
    try:r=urlopen(req,timeout=15)
    except HTTPError as e:r=e
    with r:
        raw=r.read()
        return r.status,json.loads(raw) if raw and 'json' in r.headers.get('Content-Type','') else raw

class IntegracionTests(unittest.TestCase):
    def setUp(self):
        self.evento='PRUEBA-INTEGRACION-'+uuid.uuid4().hex
        self.op=dict(operacionId=str(uuid.uuid4()),evaluacionId=str(uuid.uuid4()),eventoId=self.evento,
                     nivelAfectacion=4,latitud=4.6097,longitud=-74.0817,
                     capturadaEnUtc=datetime.now(timezone.utc).isoformat(),descripcion='Datos sintéticos')
    def enviar(self,op):
        status,body=request('/campo/lotes',{'operaciones':[op]})
        self.assertIn(status,[200,202],body)
        return body['resultados'][0]
    def incorporar(self,op):
        self.assertIn(self.enviar(op)['estado'],['recibida','incorporada'])
        for _ in range(60):
            status,puntos=request('/panorama?clase=EVALUACION&eventoId='+str(op.get('eventoId') or ''))
            self.assertEqual(status,200,puntos)
            punto=next((p for p in puntos if p['id']==op['evaluacionId']),None)
            if punto:return punto
            time.sleep(.1)
        self.fail('La evaluación no llegó al panorama')
    def test_01_campo_en_panorama_calor_geojson_y_detalle(self):
        op=self.op|dict(evaluador='Persona de prueba',organizacion='Organización de prueba',activo='Escuela')
        punto=self.incorporar(op)
        self.assertEqual(punto['clase'],'EVALUACION')
        self.assertEqual(punto['nivelAfectacion'],4)
        self.assertIsNone(punto['severidad'])
        self.assertEqual(punto['activo'],'Escuela')
        self.assertNotIn('foto',punto)
        sitios=request('/campo/sitios?eventoId='+self.evento)[1]
        self.assertIn(op['evaluacionId'],[s['evaluacionId'] for s in sitios])
        geo=request('/panorama/geojson?eventoId='+self.evento)[1]
        feature=next(f for f in geo['features'] if f['id']==op['evaluacionId'])
        self.assertEqual(feature['geometry']['coordinates'],[-74.0817,4.6097])
        self.assertEqual(feature['properties']['nivelAfectacion'],4)
        self.assertEqual(feature['properties']['evaluador'],SESION['usuario'])
        self.assertEqual(request('/campo/evaluaciones/'+op['evaluacionId'])[1]['organizacion'],SESION['organizacionId'])
        self.assertEqual(self.enviar(op)['estado'],'incorporada')
        self.assertEqual(self.enviar(op|{'nivelAfectacion':3})['estado'],'conflicto')
        self.assertEqual(len(request('/panorama?eventoId='+self.evento)[1]),1)
    def test_02_afectacion_original_y_filtros(self):
        status,item=request('/afectaciones',dict(titulo=self.evento,tipo='INUNDACION',severidad='ALTA',latitud=1,longitud=2))
        self.assertEqual(status,201,item)
        try:
            self.incorporar(self.op)
            puntos=request('/panorama')[1]
            self.assertTrue({item['id'],self.op['evaluacionId']} <= {p['id'] for p in puntos})
            self.assertIn(item['id'],[p['id'] for p in request('/panorama?tipo=INUNDACION&severidad=ALTA')[1]])
            self.assertTrue(all(p['clase']=='AFECTACION' for p in request('/panorama?tipo=INUNDACION')[1]))
            self.assertEqual(request('/panorama?eventoId='+self.evento+'&nivel=1')[1],[])
            self.assertTrue(all(p['clase']=='EVALUACION' for p in request('/panorama?nivel=4')[1]))
            self.assertTrue(all(p['clase']=='AFECTACION' for p in request('/panorama?clase=AFECTACION')[1]))
            for query in ['clase=OTRO','nivel=6','tipo=INVALIDO','severidad=URGENTE','eventoId='+'x'*101]:
                self.assertEqual(request('/panorama?'+query)[0],400)
        finally:request('/afectaciones/'+item['id']+'?version='+item['version'],method='DELETE')
    def test_03_preliminar_y_foto(self):
        foto=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aC1sAAAAASUVORK5CYII=')
        op=self.op|dict(eventoId=None,foto=base64.b64encode(foto).decode(),fotoTipo='image/png',evidenciaId=str(uuid.uuid4()),fotoSha256=hashlib.sha256(foto).hexdigest())
        punto=self.incorporar(op)
        self.assertIsNone(punto['eventoId'])
        self.assertEqual(punto['evidenciaId'],op['evidenciaId'])
        self.assertEqual(request('/campo/evaluaciones/'+op['evaluacionId']+'/foto'),(200,foto))
        self.assertTrue(any(s['evaluacionId']==op['evaluacionId'] for s in request('/campo/sitios')[1]))
        self.assertEqual(request('/campo/evaluaciones/'+str(uuid.uuid4()))[0],404)
        self.assertEqual(request('/campo/evaluaciones/'+str(uuid.uuid4())+'/foto')[0],404)
    def test_04_importacion_no_pierde_evaluaciones(self):
        self.incorporar(self.op)
        geo=request('/panorama/geojson?eventoId='+self.evento)[1]
        status,problem=request('/importaciones/geojson',geo)
        self.assertEqual(status,400,problem)
        self.assertIn('evaluaciones',problem['errors']['geojson'][0])

if __name__=='__main__':unittest.main(verbosity=2)
