# Contrato HTTP

Base local: `http://localhost:8084/api`. JSON UTF-8. Coordenadas WGS 84, grados decimales. Sin autenticación en la demo local.

| Método | Ruta | Resultado normal |
|---|---|---|
| GET | `/salud` | 200, `{"estado":"LISTO"}` si se conecta a DB; 503 si no |
| GET | `/catalogos` | 200, tipos y severidades |
| GET | `/afectaciones?tipo=INUNDACION&severidad=ALTA` | 200, array; filtros opcionales combinados con AND |
| GET | `/afectaciones/{id}` | 200 o 404 |
| POST | `/afectaciones` | 201 + Location + entidad |
| PUT | `/afectaciones/{id}` | 200 + entidad actualizada, 404 o 409 |
| DELETE | `/afectaciones/{id}?version={version}` | 204, 404 o 409 |
| GET | `/geojson` | 200, FeatureCollection; admite los mismos filtros |
| POST | `/importaciones/geojson` | 200, `{"importadas":3}` |

POST recibe:

```json
{
  "titulo": "DEMO · Inundación",
  "tipo": "INUNDACION",
  "severidad": "ALTA",
  "descripcion": "Dato ficticio para el taller",
  "latitud": 4.60971,
  "longitud": -74.08175
}
```

La respuesta agrega `id`, `registradaEn` y `version`. PUT recibe los mismos campos del POST más `version` de la última lectura; reemplaza los campos editables completos. DELETE requiere esa versión en la consulta.

Los filtros deben usar exactamente los valores mayúsculos del catálogo. Los campos tipo y severidad en escritura se normalizan a mayúsculas. El título se recorta. Se rechazan coordenadas ausentes, nulas o fuera de rango. Los IDs deben ser Guid; una ruta con ID de formato inválido no coincide y devuelve 404. Una versión ausente o mal formada en DELETE produce 400.

Ejemplo de registro:

```sh
curl -i http://localhost:8084/api/afectaciones \
  -H 'Content-Type: application/json' \
  -d '{"titulo":"DEMO · Inundación","tipo":"INUNDACION","severidad":"ALTA","latitud":4.60971,"longitud":-74.08175}'
```

Importación desde el ejemplo incluido (desde la raíz del taller):

```sh
curl -i http://localhost:8084/api/importaciones/geojson \
  -H 'Content-Type: application/json' --data-binary @docs/ejemplo.geojson
```

Formato de importación:

```json
{
  "type": "FeatureCollection",
  "features": [{
    "type": "Feature",
    "geometry": {"type":"Point","coordinates":[-74.08175,4.60971]},
    "properties": {
      "titulo":"DEMO · Inundación",
      "tipo":"INUNDACION",
      "severidad":"ALTA",
      "descripcion":"Ejemplo ficticio"
    }
  }]
}
```

Solo se aceptan Points bidimensionales, entre 1 y 500. Los IDs del archivo no se usan: el servidor genera nuevos. No se admite un CRS alternativo ni polígonos. Los datos deben estar ya expresados en WGS 84. Toda la petición debe ser de hasta 2 MiB. La importación devuelve 400 si falla la estructura o una regla, sin guardar partes del lote. Una petición excesiva puede recibir 413 desde Nginx o Kestrel.

Errores de validación usan `application/problem+json` con un diccionario `errors`. Conflictos de versión devuelven 409. Errores inesperados devuelven un problema HTTP 500 sin detalles internos en el entorno Production del contenedor.
