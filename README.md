# Vision (Android TV / Fire TV)

Reproductor IPTV para Fire Stick y Android TV con **perfiles**, **favoritos**, **Mi lista** y
**continuar viendo** que se sincronizan entre todos tus dispositivos usando Firebase (cuenta con
correo y contraseña).

- Fuentes: **Xtream Codes** (URL + usuario + contraseña) y listas **M3U/M3U8**.
- Contenido: **TV en vivo**, **Películas** y **Series** (películas y series solo con Xtream).
- Reproductor: Media3/ExoPlayer (HLS, TS, MP4 y MKV).
- Interfaz: Jetpack Compose con estilo **Liquid Glass Cinematic**, pensada para control remoto (D-pad).
- Sincronización: Firebase Auth + Firestore (con copia local, funciona con mala conexión).
- Red: si el DNS de tu router no resuelve el servidor, Vision consulta 1.1.1.1 y 8.8.8.8 por su cuenta.

> Este proyecto es solo el reproductor. No incluye canales ni listas: cada persona agrega las de su
> propio proveedor.

## 1. Requisitos

- Android Studio (versión reciente) con JDK 17.
- Una cuenta de Google para Firebase (el plan gratuito alcanza para uso personal).

## 2. Configurar Firebase (una sola vez)

1. Entra a https://console.firebase.google.com y crea un proyecto.
2. **Agregar app Android** con el nombre de paquete **`com.vision.tv`**.
3. Descarga `google-services.json` y colócalo en `Vision/app/google-services.json`.
4. **Authentication → Método de acceso →** activa **Correo electrónico/contraseña**.
5. **Firestore Database → Crear base de datos** (modo producción).
6. En Firestore → pestaña **Reglas**, pega el contenido de `firestore.rules` y publica.
   Esas reglas hacen que cada cuenta solo lea y escriba sus propios datos. Ya cubren las
   colecciones nuevas (progreso y Mi lista), así que no hay que cambiarlas.

Sin `google-services.json` la app compila pero se cierra al abrir.

## 3. Compilar el APK

1. Abre la carpeta `Vision` en Android Studio y espera a que termine el *Gradle Sync*.
2. Crea tu llave de firma (una sola vez, guárdala bien: la necesitas para cada actualización):

   ```
   keytool -genkeypair -v -keystore vision.jks -alias vision -keyalg RSA -keysize 2048 -validity 10000
   ```
3. Copia `keystore.properties.example` a `keystore.properties` y llénalo con tus datos.
4. Genera el APK: **Build → Generate App Bundles or APKs → Generate APKs**
   (o `./gradlew assembleRelease` si ya tienes el wrapper).
5. El archivo queda en `app/build/outputs/apk/release/app-release.apk`.

## 4. Instalarlo con Downloader

1. Sube el APK a un lugar con enlace directo de descarga (GitHub Releases, tu propio hosting, etc.).
2. En el Fire Stick: **Ajustes → Mi Fire TV → Opciones para desarrolladores → Instalar apps
   desconocidas** → activa **Downloader**.
3. Abre Downloader, escribe la URL del APK y pulsa Ir → Instalar.
   (Si quieres un código corto en lugar de la URL, puedes solicitarlo en AFTVnews.)

### Actualizaciones desde la app (a partir de la 1.2.0)

Al abrir, Vision consulta la última release de este repositorio en GitHub. Si hay una versión más
nueva, muestra un aviso con las novedades y el botón **Actualizar ahora**: descarga el APK y abre el
instalador. También hay un botón **Buscar actualizaciones** en la pantalla de perfiles.

- La primera vez, Fire OS pide autorizar a Vision para instalar apps (la app lleva a ese ajuste).
- Para publicar una versión nueva: sube `versionCode` y `versionName` en `app/build.gradle.kts`,
  compila con la **misma llave** (`vision.jks`) y crea una release en GitHub con etiqueta `vX.Y.Z`
  y el `.apk` adjunto. Si la llave cambia, Android no deja instalar encima.

### Si la app se cierra

Vision guarda el detalle del error y lo muestra la siguiente vez que se abre
(«Vision se cerró inesperadamente»). Una foto de esa pantalla basta para saber qué falló.

## 5. Cómo se usa

1. Inicia sesión o crea tu cuenta (la misma en todos los dispositivos).
2. Crea perfiles (tú, tu pareja…) con nombre, **emoji o inicial como icono**, color y PIN opcional.
3. Añade tu fuente IPTV (**Fuentes IPTV**): Xtream (URL, usuario, contraseña) o enlace M3U.
   Con Xtream se valida al guardar. Escribe la URL como `http://servidor.com:puerto`.
4. Elige tu perfil. Llegas al **Inicio**, con menú superior y filas al estilo Netflix.

### Menú y Inicio (estilo Netflix)

- Barra superior: **Inicio · TV en vivo · Películas · Series · Mi lista** (Películas y Series solo con
  Xtream), más tu fuente y tu perfil a la derecha. Con flecha arriba desde cualquier fila llegas a ella.
- **Inicio**: banner con lo último que veías (o una película destacada) y filas: Continuar viendo,
  Mi lista, Tus favoritos, Canales recientes, Películas y Series populares.

### Catálogos

- Una fila por categoría; "Ver todo" abre la categoría en cuadrícula. Atrás vuelve a las filas.
- Filas personales arriba: Continuar viendo, Mi lista, Favoritos y, en TV en vivo, Vistos recientemente.
- Búsqueda arriba: muestra los resultados en cuadrícula.
- **Mantén OK** sobre un título para marcarlo como favorito.

### Cambio de perfil

Al cambiar de perfil se borra de pantalla todo lo del anterior antes de mostrar el nuevo
(favoritos, avance, búsquedas), así nunca se ve por un instante lo que veía otra persona.

### Ficha de película o serie

- Sinopsis, año, nota, duración y género.
- **Reanudar · 32:10** (o **Desde el inicio**) en películas; **Continuar T1 · E3** o **Siguiente:
  T1 · E4** en series.
- **Mi lista** (ver después) y **Favorito**.
- Series: temporadas y episodios, con **VISTO**, barra de avance y tiempo restante en cada uno.

### Reproductor

| Contenido | Control |
|---|---|
| TV en vivo | **Arriba/Abajo** cambia de canal · **Menú** favorito |
| Película / episodio | **Izq/Der** ±10 s · **Avance/Retroceso rápido** ±30 s · **OK** pausa |
| Episodios | **Siguiente/Anterior** cambia de episodio; al terminar pasa solo al siguiente |
| Todos | **Atrás** vuelve |

El avance se guarda cada 10 segundos, al pausar y al salir. Una película o episodio pasa a
**visto** al llegar al 95 %.

## 6. Qué se guarda por perfil

Cada perfil tiene lo suyo, sincronizado entre dispositivos (`users/{uid}/profiles/{id}/…`):

| Colección | Contenido |
|---|---|
| `favorites` | Canales, películas y series favoritos |
| `watchlist` | Mi lista (ver después) |
| `recents` | Últimos canales en vivo |
| `progress` | Dónde te quedaste en películas y episodios |

Si tienes varias fuentes, favoritos, lista y avance se muestran solo para la fuente activa.

## 7. Diseño

Sistema **Liquid Glass Cinematic**:

- **Paleta:** lienzo `#060810`, acentos cian `#00F0FF`, azul `#007AFF` y amatista `#8A2BE2`.
- **Tipografía:** Inter (Regular, SemiBold, Bold y ExtraBold) incluida en `app/src/main/assets/fonts`,
  con cifras tabulares.
- **Escala 1080p:** todas las medidas están definidas en píxeles de una pantalla de 1920 px y se
  adaptan solas a cada densidad (`Int.px` en `Theme.kt`).
- **Vidrio:** `GlassSurface` (`Glass.kt`) con relleno translúcido, luz cenital, borde especular y
  línea de brillo interior.
- **Motor de foco:** `Focusable` escala 1.05x en 240 ms con la curva `cubic-bezier(0.2, 0.9, 0.4, 1.1)`,
  intensifica el vidrio y proyecta el halo cian/azul.

**Limitación honesta:** Android no ofrece desenfoque real del fondo (*backdrop blur*) en Fire OS 5/7
y en Fire Sticks antiguos sería muy pesado. El vidrio se simula con capas translúcidas sobre un
fondo con luces ambientales. Tampoco se implementó la inclinación 3D (*tilt*) con el trackpad, que
solo existe en el mando de Apple TV.

### Icono

Fire TV muestra un **banner rectangular** (16:9) en lugar de un icono cuadrado:

- `drawable-*/banner.png` (320×180 dp; mdpi, hdpi, xhdpi y xxhdpi).
- `drawable-*/ic_launcher.png`: versión cuadrada de respaldo.

## 8. Sobre los perfiles y las sesiones simultáneas

- Los perfiles, favoritos, listas y avance son de la app y se sincronizan por Firebase.
- El límite de **conexiones simultáneas** lo define tu proveedor IPTV, no la app. Con Xtream la app
  muestra *conexiones en uso / máximas* y avisa cuando se alcanza el límite. Todos los perfiles
  comparten ese cupo.

## 9. Lo que falta / siguientes pasos

- **Login por código/QR** (hoy se escribe correo y contraseña con el control remoto). Requiere un
  paso de vinculación desde el celular (por ejemplo con Cloud Functions).
- **Guía de programación (EPG)** de los canales en vivo.
- **Subtítulos y selección de audio** en películas y series.
- **Contraseñas de IPTV**: se guardan en tu documento privado de Firestore, protegido por las reglas
  y cifrado en reposo por Google, pero **no** cifradas por la app. Si piensas compartir la app con
  más gente, conviene cifrarlas del lado del cliente.
- **Aviso de privacidad** si otras personas van a crear cuenta con sus datos.
- Catálogos muy grandes (decenas de miles de películas) pueden tardar en la primera carga en
  Fire Sticks antiguos.

## 10. Estructura

```
app/src/main/java/com/vision/tv/
  VisionApp.kt             arranque: registro de errores y cargador de imágenes
  MainActivity.kt          punto de entrada
  AppViewModel.kt          navegación, cuenta, perfiles, catálogos, progreso, favoritos y lista
  data/Models.kt           Profile, Source, Media, Progress…
  data/Net.kt              cliente Xtream (vivo, películas, series), parser M3U y DNS de respaldo
  data/CloudRepo.kt        lectura/escritura en Firestore
  data/Updater.kt          busca, descarga e instala versiones nuevas desde GitHub
  ui/Theme.kt              paleta, escala 1080p, tipografía Inter
  ui/Glass.kt              GlassSurface (vidrio) y Focusable (motor de foco)
  ui/Components.kt         fondo, marca, botones, campos, insignias, avatares
  ui/MediaComponents.kt    pósters, tarjetas de continuar, barra de avance
  ui/Screens.kt            acceso, perfiles, fuentes
  ui/NavAndShelves.kt      barra superior, filas y tarjetas
  ui/HomeScreen.kt         inicio: banner y filas
  ui/CatalogScreen.kt      catálogos por filas y cuadrícula
  ui/MyListScreen.kt       Mi lista y favoritos
  ui/DetailScreen.kt       ficha con temporadas y episodios
  ui/PlayerScreen.kt       reproductor con reanudación y guardado de avance
  ui/AppDialogs.kt         avisos de actualización y de cierre inesperado
  ui/Format.kt             formato de tiempos
```
