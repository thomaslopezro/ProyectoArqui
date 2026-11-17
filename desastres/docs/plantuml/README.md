# Diagramas PlantUML

Fuentes editables del diseño final del Taller 3:

1. [Componentes y dependencias](01-componentes.puml).
2. [Despliegue Docker y contenedor Jakarta](02-despliegue.puml).
3. [Secuencia de recepción, registro y notificación](03-secuencia-desastre.puml).
4. [Topología de mensajería y recuperación](04-topologia-mensajeria.puml).

Los nombres, puertos, confirmaciones y reintentos corresponden a la implementación entregada. Los diagramas distinguen el ACK del consumidor y el publisher confirm del broker. El temporizador consulta las colas; no se presenta como un MDB.

Cada fuente tiene una vista SVG con el mismo nombre:

- [Componentes](01-componentes.svg).
- [Despliegue](02-despliegue.svg).
- [Secuencia](03-secuencia-desastre.svg).
- [Mensajería](04-topologia-mensajeria.svg).

Se pueden abrir en un editor compatible con PlantUML. Con PlantUML instalado, generar imágenes SVG con:

```sh
java -jar plantuml.jar -tsvg docs/plantuml/*.puml
```
