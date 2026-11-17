# Arqui Campo · Entrega de arquitectura

App Android en .NET MAUI para capturar **latitud, longitud y nivel de afectación de 1 a 5** sin Internet. Conserva fotos opcionales y sincroniza los sitios con PostgreSQL. La visualización web presenta un mapa de calor por desastre.

[Diseño y comparación de alternativas](DISEÑO.md)

## Arrancar el sistema central y el mapa

Desde el directorio que contiene `desconectados` y `taller4-mapas`:

```sh
docker compose -f taller4-mapas/compose.yaml up --build -d
```

Abre [el mapa de calor local](http://localhost:8084/calor.html). El modo inmediato es el predeterminado. Para activar el diferido, ejecuta:

```sh
CAMPO_DIFERIDA=true docker compose -f taller4-mapas/compose.yaml up --build -d
```

## Instalar y conectar el APK

El ZIP de entrega incluye `Arqui-Campo.apk` en la raíz. En el espacio de trabajo también está en `output/Arqui-Campo.apk`.

En un teléfono Android con depuración USB o en un emulador, ejecuta `adb devices` para comprobar que aparece. Instala el APK firmado con `adb install -r Arqui-Campo.apk`. Usa `adb reverse tcp:8084 tcp:8084` y deja en la app la URL `http://127.0.0.1:8084`. El túnel USB conecta el Android al servicio local de tu computador. Para un emulador Android estándar también puedes usar `http://10.0.2.2:8084`.

Para compilar desde el código instala .NET SDK 10, el workload `maui-android`, el SDK Android API 36 y un JDK compatible. Desde la raíz de la entrega:

```sh
dotnet workload install maui-android
dotnet build desconectados/src/Arqui.Campo/Arqui.Campo.csproj -c Release
```

Usa el APK con sufijo `-Signed.apk` en la salida de la compilación.

## Demostración del caso del rescatista

1. Para simular desconexión real, elimina el túnel con `adb reverse --remove tcp:8084` si estaba configurado. Activa modo avión con Wi-Fi apagado.
2. Guarda dos sitios para el mismo desastre, uno de nivel 1 y otro de nivel 5. Usa las coordenadas de ejemplo `4.60, -74.0817` y `4.61, -74.0817`. Adjunta una foto a uno de los sitios.
3. Fuerza el cierre desde los ajustes de Android. Abre la app en modo avión y verifica los dos registros y la foto.
4. Recupera conexión con el servidor. Si usas USB, restablece el túnel. Pulsa **Sincronizar ahora** o espera el siguiente intento automático. En modo diferido la app puede mostrar **Recibida · en proceso** antes de **Sincronizada**.
5. En el visor, filtra por el mismo desastre. Comprueba que aparecen ambos niveles en la tabla y sus aportes a la capa de calor.

Para repetir la verificación HTTP del backend con datos ficticios:

```sh
python3 taller4-mapas/scripts/verificar_campo.py
# Agrega --diferida si activaste ese modo al arrancar.
```
