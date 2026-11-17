"""Compatibilidad: la prueba vigente del Taller 3 incluye ahora identidad y núcleo persistente."""
from pathlib import Path
import runpy
runpy.run_path(str(Path(__file__).with_name('verificar_unificacion.py')),run_name='__main__')
