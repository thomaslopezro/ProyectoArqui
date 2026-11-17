# Territorio — taller de mapas de desastres

Aplicación académica para registrar afectaciones georreferenciadas, guardar los datos y visualizarlos como puntos en un mapa. Corresponde al taller del tablero: .NET, tres niveles, acceso a datos y comunicación distribuida. Es un proyecto nuevo e independiente de los talleres Java previos.

## Ejecutar

Requisito: Docker Desktop iniciado, Docker Compose y conexión a Internet en el primer inicio.

Desde esta carpeta:

```sh
docker compose up --build -d
```

Abrir **http://localhost:8084**. Esperar a que http://localhost:8084/api/salud responda `{"estado":"LISTO"}`. La primera construcción descarga imágenes y paquetes; puede tardar varios minutos.

Para detener conservando datos:

```sh
docker compose down
```

La base de datos está en el volumen `taller4-mapas_mapas-data`. Reiniciar o reconstruir no lo elimina. No usar `down -v` si se desea conservar la información.

## Demostración de cinco minutos

1. Abrir la aplicación. La base de datos empieza vacía.
2. Importar `docs/ejemplo.geojson`: contiene tres puntos ficticios, identificados como DEMO. No son reportes reales. Reimportar agrega duplicados intencionadamente; una importación no actualiza registros existentes.
3. Seleccionar un marcador para ver su descripción. Filtrar por tipo y severidad.
4. Hacer clic en el mapa, escribir nombre, tipo, severidad y registrar. También se pueden escribir las coordenadas sin usar el mapa.
5. Editar un registro, cambiar severidad y guardar. La columna Acciones también permite ubicarlo o eliminarlo con confirmación.
6. Exportar GeoJSON. La exportación respeta los filtros actuales.
7. Reiniciar con `docker compose restart api db`. Esperar a que `/api/salud` esté listo y actualizar la página: los puntos permanecen.

## Qué cumple

| Necesidad | Implementación |
|---|---|
| Gestionar datos de afectaciones | Alta, listado, consulta por ID, edición y eliminación |
| Recibir o leer coordenadas | Formulario, clic en mapa, API JSON e importación GeoJSON |
| Ubicar las coordenadas | Marcadores interactivos Leaflet sobre OpenStreetMap |
| .NET y tres niveles distribuidos | Nginx/web → API ASP.NET Core 10 → PostgreSQL 17, en contenedores separados |
| Acceso a datos | EF Core con proveedor Npgsql; consultas LINQ traducidas a SQL |
| Comunicación distribuida | REST/HTTP con JSON; contrato GeoJSON para intercambio geográfico |
| Conservar datos | Volumen PostgreSQL persistente |
| Evitar pérdida por edición simultánea | Token de versión y respuesta 409 |

ADO.NET, LINQ y Entity Framework aparecen como temas en el tablero; no son tres subsistemas que deban implementarse por separado. EF Core usa el proveedor Npgsql y este se apoya en ADO.NET. LINQ expresa las consultas. Se eligió REST entre las alternativas REST/gRPC.

## Pruebas

Con los servicios iniciados:

```sh
python3 scripts/verificar_api.py
```

Se verifican salud, catálogos, CRUD, filtros, límites y ausencia de coordenadas, orden de GeoJSON, importación atómica y edición concurrente. El script elimina únicamente sus propios registros de prueba. No necesita paquetes Python adicionales.

La prueba de persistencia se ejecuta por separado porque reinicia servicios:

```sh
python3 scripts/verificar_persistencia.py
```

Compilación fuera de Docker, si tienes SDK .NET 10 instalado:

```sh
dotnet build src/Mapas.Api/Mapas.Api.csproj -c Release
```

## Organización

- `src/Mapas.Api/Domain`: entidad de afectación.
- `src/Mapas.Api/Application`: reglas, casos de uso, contrato de persistencia y GeoJSON.
- `src/Mapas.Api/Infrastructure`: EF Core, mapeo y consultas LINQ.
- `src/Mapas.Api/Program.cs`: adaptador HTTP y composición de dependencias.
- `web`: presentación independiente en HTML, CSS y JavaScript.
- `docs/ARQUITECTURA.md`: decisiones, diagramas, escenarios de calidad y límites.
- `docs/API.md`: operaciones y contratos.
- `docs/GUIA_PARA_ENTENDER.md`: explicación y guion de sustentación.
- `docs/VERIFICACION.md`: resultados y límites de la comprobación.

## Límites de esta entrega

Prototipo local, accesible solo desde este equipo mediante el puerto 8084. Sin autenticación, autorización ni TLS; credenciales locales de demostración en Compose. No representa un sistema oficial de emergencias. Un despliegue compartido requiere esos controles y gestión externa de secretos.

Se muestran puntos, no polígonos ni estimaciones de área o población afectada. Las coordenadas son WGS 84 en grados decimales. No se deduce una ubicación a partir de texto y no se solicitan permisos de geolocalización al navegador.

Las teselas de OpenStreetMap, Leaflet y las fuentes tipográficas requieren conexión a Internet. Si no carga Leaflet, el formulario y la tabla siguen disponibles. No hay garantía de funcionamiento sin conexión ni servicio cartográfico con disponibilidad contratada. El mapa base recibe las solicitudes de teselas, no los títulos o descripciones registrados.

La importación admite 1–500 puntos y peticiones de hasta 2 MiB; es aditiva y transaccional. El listado no tiene paginación y está dimensionado para una demostración pequeña. El esquema inicial se crea con `EnsureCreated`, apropiado para esta entrega; cambios futuros requieren adoptar migraciones, respaldar y planificar la transición. No mezclar `EnsureCreated` y migraciones sobre una base existente sin un procedimiento de migración.
