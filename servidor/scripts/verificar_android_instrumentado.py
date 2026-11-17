"""Pruebas de negocio del APK en el emulador: sin pulsar ni inspeccionar la interfaz.
Instala previamente la variante -p:PruebasCampo=true (no es el APK de entrega).
Detiene/restaura brevemente la API de esta integración. Usa SQLite aislado y restaura la sesión previa. No ejecutar durante una demostración concurrente.
"""
import subprocess,os,sys,shutil
from pathlib import Path
COMPOSE=["docker","compose","-f",str(Path(__file__).resolve().parents[1]/"compose.yaml")]
ADB=os.environ.get('ADB') or shutil.which('adb') or str(Path.home()/'Library/Android/sdk/platform-tools/adb')
DEVICE=os.environ.get('ANDROID_SERIAL','emulator-5554')
base=[ADB,'-s',DEVICE]
def red(quitar=False):subprocess.run(base+['reverse']+(['--remove','tcp:8084'] if quitar else ['tcp:8084','tcp:18085']),check=True,stdout=subprocess.DEVNULL)
red();cmd=base+['shell','am','instrument','-w','-r','-e','usuario',os.environ.get('PGDR_BOOTSTRAP_USER','coordinador_demo'),'-e','clave',os.environ.get('PGDR_BOOTSTRAP_PASSWORD','Arqui-Local-2026!'),'co.edu.arqui.campo/co.edu.arqui.campo.PruebasCampo']
ok=False
try:
 p=subprocess.Popen(cmd,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,bufsize=1)
 for line in p.stdout:
  print(line.rstrip(),flush=True)
  if 'fase=DETENER_API' in line:subprocess.run(COMPOSE+['stop','api'],check=True,stdout=subprocess.DEVNULL)
  if 'fase=RESTAURAR_API' in line:subprocess.run(COMPOSE+['start','api'],check=True,stdout=subprocess.DEVNULL)
  if 'APROBADO: SQLite' in line:ok=True
 rc=p.wait(timeout=150)
 if rc or not ok:raise SystemExit('No se completó la prueba instrumentada; revisa la salida anterior.')
finally:
 subprocess.run(COMPOSE+['start','api'],check=True,stdout=subprocess.DEVNULL)
 red()
print('RESULTADO ANDROID: APROBADO',flush=True)
