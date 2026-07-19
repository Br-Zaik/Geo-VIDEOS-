# Validacion Geo Susu v173

Cambios limitados a memoria y recursos:

- `android:largeHeap="true"` en la aplicacion.
- Version 173 / 17.3.0.
- Un ejecutor reutilizable para busqueda JSON y otro para NET.
- Cancelacion de consultas anteriores y cierre de ejecutores en `onDestroy`.
- Liberacion de la cache reconstruible del JSON en avisos de memoria baja.
- Contexto de aplicacion en repositorios y ajustes del servicio.

No se modificaron los tiempos ni la logica del reconocimiento Google interno, seleccion de idioma, coincidencias JSON, AUTO, proveedores NET, prueba de APIs, Vosk o interfaz.

Limitacion honesta: `largeHeap` aumenta el margen permitido por Android, pero no puede garantizar que el sistema nunca cierre la app. La estabilidad de seis horas debe comprobarse en el telefono real.
