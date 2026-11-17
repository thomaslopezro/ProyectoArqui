#!/usr/bin/env python3
"""Pruebas reales a través de Nginx → .NET → PostgreSQL. Solo elimina sus propios registros."""
from acceso_pruebas import HEADERS, SESION
import concurrent.futures
import json
import os
import unittest
import urllib.error
import urllib.request
import uuid

BASE = os.environ.get('MAPAS_URL', 'http://127.0.0.1:18085').rstrip('/')

def request(method, path, body=None):
    raw = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(BASE + '/api' + path, data=raw, method=method,
                                 headers=HEADERS)
    try:
        response = urllib.request.urlopen(req, timeout=20)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        text = response.read()
        try:
            body = json.loads(text) if text else None
        except json.JSONDecodeError:
            body = {'title': 'Respuesta no JSON del servidor', 'status': response.status}
        return response.status, body

class ApiTests(unittest.TestCase):
    def setUp(self):
        self.created = set()
        self.prefix = 'PRUEBA-' + uuid.uuid4().hex[:10]
        self.input = dict(titulo=self.prefix, tipo='INUNDACION', severidad='ALTA',
                          descripcion='Dato sintético de verificación', latitud=4.60971, longitud=-74.08175)

    def tearDown(self):
        # Incluye los creados por la importación con el prefijo exclusivo de esta prueba.
        status, items = request('GET', '/afectaciones')
        if status == 200:
            for item in items:
                if item['id'] in self.created or item['titulo'].startswith(self.prefix):
                    request('DELETE', f"/afectaciones/{item['id']}?version={item['version']}")

    def create(self, **changes):
        status, item = request('POST', '/afectaciones', self.input | changes)
        self.assertEqual(status, 201, item)
        self.created.add(item['id'])
        return item

    def feature(self, **changes):
        props = self.input | changes
        return dict(type='Feature', geometry=dict(type='Point', coordinates=[props.pop('longitud'), props.pop('latitud')]), properties=props)

    def test_01_salud_catalogos(self):
        self.assertEqual(request('GET', '/salud'), (200, {'estado':'LISTO'}))
        status, body = request('GET', '/catalogos')
        self.assertEqual(status, 200)
        self.assertIn('INUNDACION', body['tipos'])
        self.assertIn('CRITICA', body['severidades'])

    def test_02_crud_filtros_version(self):
        item = self.create()
        self.assertEqual(request('GET', '/afectaciones/' + item['id'])[1], item)
        status, items = request('GET', '/afectaciones?tipo=INUNDACION&severidad=ALTA')
        self.assertEqual(status, 200)
        self.assertIn(item['id'], [x['id'] for x in items])
        self.assertTrue(all(x['tipo']=='INUNDACION' and x['severidad']=='ALTA' for x in items))
        status, changed = request('PUT', '/afectaciones/' + item['id'], self.input | dict(severidad='MEDIA',version=item['version']))
        self.assertEqual(status, 200)
        self.assertNotEqual(item['version'], changed['version'])
        self.assertEqual(item['registradaEn'], changed['registradaEn'])
        self.assertEqual(request('PUT', '/afectaciones/' + item['id'], self.input | dict(version=item['version']))[0], 409)
        self.assertEqual(request('DELETE', f"/afectaciones/{item['id']}?version={item['version']}")[0], 409)
        self.assertEqual(request('DELETE', f"/afectaciones/{item['id']}?version={changed['version']}")[0], 204)
        self.assertEqual(request('GET', '/afectaciones/' + item['id'])[0], 404)

    def test_03_validacion_limites_y_ausencias(self):
        for changes in [dict(latitud=91),dict(latitud=-91),dict(longitud=181),dict(longitud=-181),dict(latitud=None),dict(longitud=None),dict(tipo='NO_EXISTE'),dict(severidad='URGENTE'),dict(titulo=' '),dict(titulo='x'*121),dict(descripcion='x'*2001)]:
            with self.subTest(changes=changes):
                self.assertEqual(request('POST', '/afectaciones', self.input | changes)[0], 400)
        for coords in [(0,0),(-90,-180),(90,180)]:
            item=self.create(latitud=coords[0],longitud=coords[1]);self.assertEqual(item['latitud'],coords[0]);self.assertEqual(item['longitud'],coords[1])
        body=dict(self.input);del body['latitud']
        self.assertEqual(request('POST','/afectaciones',body)[0],400)
        self.assertEqual(request('GET', '/afectaciones?tipo=INVALIDO')[0], 400)
        self.assertEqual(request('GET', '/afectaciones/' + str(uuid.uuid4()))[0], 404)

    def test_04_geojson_orden_y_atomicidad(self):
        item=self.create()
        status, body=request('GET','/geojson?tipo=INUNDACION&severidad=ALTA')
        self.assertEqual(status,200)
        feature=next(x for x in body['features'] if x['id']==item['id'])
        self.assertEqual(feature['geometry']['coordinates'],[-74.08175,4.60971])
        batch=dict(type='FeatureCollection',features=[self.feature(titulo=self.prefix+'-importado'),self.feature(titulo=self.prefix+'-invalido',latitud=200)])
        self.assertEqual(request('POST','/importaciones/geojson',batch)[0],400)
        items=request('GET','/afectaciones')[1]
        self.assertFalse(any(x['titulo']==self.prefix+'-importado' for x in items))
        batch['features'][1]=self.feature(titulo=self.prefix+'-segundo',latitud=5,longitud=-73)
        self.assertEqual(request('POST','/importaciones/geojson',batch),(200,{'importadas':2}))
        items=request('GET','/afectaciones')[1]
        self.assertEqual(sum(x['titulo'].startswith(self.prefix) for x in items),3)
        # Exportar e importar deben compartir exactamente el mismo contrato.
        feature['properties']['titulo']=self.prefix+'-roundtrip'
        self.assertEqual(request('POST','/importaciones/geojson',dict(type='FeatureCollection',features=[feature]))[0],200)

    def test_05_geojson_malformado(self):
        for body in [None,{},dict(type='FeatureCollection',features=[]),dict(type='FeatureCollection',features=[dict(type='Feature',geometry=dict(type='Polygon',coordinates=[]),properties={})]),dict(type='FeatureCollection',features=[self.feature()]*501)]:
            with self.subTest(body_type=type(body).__name__):
                self.assertEqual(request('POST','/importaciones/geojson',body)[0],400)

    def test_06_actualizacion_concurrente(self):
        item=self.create()
        def update(severity):
            return request('PUT','/afectaciones/'+item['id'],self.input|dict(severidad=severity,version=item['version']))[0]
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            statuses=list(pool.map(update,['MEDIA','CRITICA']))
        self.assertEqual(sorted(statuses),[200,409])

if __name__=='__main__':
    unittest.main(verbosity=2)
