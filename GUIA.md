# Mi Reproductor: guía paso a paso

## Parte 1: crear tu copia y subir los cambios (una sola vez)
1. Crea una cuenta gratis en https://github.com e inicia sesión.
2. Abre https://github.com/fmhyplay/android y pulsa **Fork** (arriba a la derecha) y luego **Create fork**.
   Ahora tienes tu copia en `https://github.com/chapu21/android`.
3. En tu copia: **Add file → Upload files**. Arrastra el CONTENIDO de la carpeta `cambios`
   (las carpetas `src` y `assets`, y los archivos `AndroidManifest.xml` y `hoy.json`) a la ventana. Confirma los avisos de
   "reemplazar" si los hay y pulsa **Commit changes**.
4. Edita `src/me/champdev/weblock/SimpleHome.java` (lápiz) y cambia la línea `LIST_URL` por:
   `https://raw.githubusercontent.com/chapu21/android/main/hoy.json`
   (con tu usuario). Pulsa **Commit changes**.

## Parte 2: obtener el APK
1. En tu copia abre la pestaña **Actions** y pulsa el botón verde para activarlas si lo pide.
2. Cada vez que cambias código, GitHub compila solo. Entra en el último "Build", baja a **Artifacts** y descarga el APK.
   (Descarga el de Android para móvil/tablet y el de TV para Fire TV / Android TV.)
3. Instala el APK en el dispositivo de tus abuelos (en Fire TV, con la app "Downloader").

## Parte 3: cambiar lo que ven cada día
1. Abre `hoy.json` en tu copia de GitHub, pulsa el lápiz, cambia los elementos y **Commit changes**.
2. En unos segundos, al abrir la app, ya lo ven. No hay que reinstalar nada.

Formato de cada elemento (usa `url` o `embed`):
```json
{ "titulo": "Película", "subtitulo": "20:00", "url": "https://..." }
{ "titulo": "Partido", "embed": "<iframe src=\"https://...\"></iframe>", "imagen": "https://.../foto.jpg" }
```
- Cuida las comillas y las comas: un error hace que la app siga con la última lista buena.
- Puedes comprobar el archivo en https://jsonlint.com antes de guardar.
