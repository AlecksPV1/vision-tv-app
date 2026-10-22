# Vision (Android TV / Fire TV)

Reproductor IPTV para Fire Stick y Android TV con **perfiles**, **favoritos** y **recientes** que se
sincronizan entre todos tus dispositivos usando Firebase (cuenta con correo y contraseña).

- Fuentes: **Xtream Codes** (URL + usuario + contraseña) y listas **M3U/M3U8**.
- Reproductor: Media3/ExoPlayer (HLS y TS).
- Interfaz: Jetpack Compose con estilo **Liquid Glass Cinematic**, pensada para control remoto (D-pad).
- Sincronización: Firebase Auth + Firestore (con copia local, funciona con mala conexión).

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
   Esas reglas hacen que cada cuenta solo lea y escriba sus propios datos.

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

## 5. Cómo se usa

1. Inicia sesión o crea tu cuenta (la misma en todos los dispositivos).
2. Crea perfiles (tú, tu pareja…) con nombre, color y PIN opcional.
3. Añade tu fuente IPTV (**Fuentes IPTV**): Xtream (URL, usuario, contraseña) o enlace M3U.
   Con Xtream se valida al guardar.
4. Elige tu perfil → canales por categoría, búsqueda y reproducción.

Controles: **OK** reproduce · **mantener OK** en un canal lo agrega a favoritos ·
dentro del video **Arriba/Abajo** cambia de canal, **Menú** marca favorito, **Atrás** vuelve.

## 6. Diseño

Sistema **Liquid Glass Cinematic** (colores, tipografía y componentes de tus parámetros):

- **Paleta:** lienzo `#060810`, acentos cian `#00F0FF`, azul `#007AFF` y amatista `#8A2BE2`.
- **Tipografía:** Inter (Regular, SemiBold, Bold y ExtraBold) incluida en `app/src/main/assets/fonts`,
  con cifras tabulares.
- **Escala 1080p:** todas las medidas (márgenes de 64 px, radios, tamaños de texto) están definidas
  en píxeles de una pantalla de 1920 px y se adaptan solas a cada densidad (`Int.px` en `Theme.kt`).
- **Vidrio:** `GlassSurface` (`Glass.kt`) con relleno translúcido, luz cenital, borde especular y
  línea de brillo interior.
- **Motor de foco:** `Focusable` escala 1.05x en 240 ms con la curva `cubic-bezier(0.2, 0.9, 0.4, 1.1)`,
  intensifica el vidrio y proyecta el halo cian/azul.
- **Componentes:** tarjetas de perfil, cápsula de navegación, insignias EN VIVO / conexiones /
  vencimiento, y HUD flotante del reproductor que se oculta a los 4 segundos.

**Limitación honesta:** Android no ofrece desenfoque real del fondo (*backdrop blur*) en Fire OS 5/7
y en Fire Sticks antiguos sería muy pesado. El vidrio se simula con capas translúcidas sobre un
fondo con luces ambientales; se ve esmerilado, pero no desenfoca el video. Tampoco se implementó la
inclinación 3D (*tilt*) con el trackpad, que solo existe en el mando de Apple TV.

### Icono

Fire TV muestra un **banner rectangular** (16:9) en lugar de un icono cuadrado:

- `drawable-*/banner.png` (320×180 dp; se incluyen mdpi, hdpi, xhdpi y xxhdpi) con el lente de vidrio
  y el nombre Vision. El de xhdpi (640×360) es el que usa la mayoría de Fire Stick.
- `drawable-*/ic_launcher.png`: versión cuadrada de respaldo (ajustes, otros Android).

## 7. Sobre los perfiles y las sesiones simultáneas

- Los perfiles, favoritos y recientes son de la app y se sincronizan por Firebase.
- El límite de **conexiones simultáneas** lo define tu proveedor IPTV, no la app. Con Xtream la app
  muestra *conexiones en uso / máximas* y avisa cuando se alcanza el límite. Todos los perfiles
  comparten ese cupo.

## 8. Lo que falta / siguientes pasos

- **Login por código/QR** (hoy se escribe correo y contraseña con el control remoto). Requiere un
  paso de vinculación desde el celular (por ejemplo con Cloud Functions).
- **Guía de programación (EPG)**, películas y series (Xtream los ofrece; aquí solo hay TV en vivo).
- **Contraseñas de IPTV**: se guardan en tu documento privado de Firestore, protegido por las reglas
  y cifrado en reposo por Google, pero **no** cifradas por la app. Si piensas compartir la app con
  más gente, conviene cifrarlas del lado del cliente.
- **Aviso de privacidad** si otras personas van a crear cuenta con sus datos.
- Fire Sticks muy viejos (Fire OS 5) pueden ir lentos con listas muy grandes.

## 9. Estructura

```
app/src/main/java/com/vision/tv/
  MainActivity.kt         punto de entrada
  AppViewModel.kt         navegación, cuenta, perfiles, fuentes, favoritos
  data/Models.kt          Profile, Source, Channel…
  data/Net.kt             cliente Xtream y parser M3U
  data/CloudRepo.kt       lectura/escritura en Firestore
  ui/Theme.kt             paleta, escala 1080p, tipografía Inter
  ui/Glass.kt             GlassSurface (vidrio) y Focusable (motor de foco)
  ui/Components.kt        fondo, marca, botones, campos, insignias, avatares
  ui/Screens.kt           acceso, perfiles, fuentes
  ui/ChannelsScreen.kt    categorías y canales
  ui/PlayerScreen.kt      reproductor ExoPlayer con HUD flotante
```
