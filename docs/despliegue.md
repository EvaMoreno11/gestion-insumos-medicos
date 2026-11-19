# Publicar la app en internet

La app se publica con dos servicios gratuitos:

| Servicio | Para qué | Plan gratuito |
|---|---|---|
| [Neon](https://neon.com) | Base de datos PostgreSQL | 1 GB y 100 horas de cómputo al mes. Se duerme tras 5 minutos sin uso y despierta sola. |
| [Render](https://render.com) | Servidor: entrega la app y la API en una sola dirección | 750 horas al mes. Se duerme tras 15 minutos sin uso y tarda alrededor de un minuto en despertar. |

El servidor se construye con el [`Dockerfile`](../Dockerfile) y se configura con [`render.yaml`](../render.yaml). Las claves y contraseñas solo existen en los paneles de Neon y Render, nunca en el repositorio.

## 1. Base de datos en Neon

1. Entra a [neon.com](https://neon.com) con tu cuenta de GitHub y crea un proyecto:
   - **Postgres version:** 17 (la misma que se usa en desarrollo y en las pruebas).
   - **Region:** AWS US East 1 (N. Virginia), cerca del servidor de Render.
2. En el panel del proyecto, toca **Connect** y **desactiva "Connection pooling"**. Flyway, que crea las tablas, necesita la conexión directa.
3. Anota tres datos de la cadena de conexión `postgresql://USUARIO:CONTRASEÑA@HOST/BASE?sslmode=require`:
   - **DB_URL** = `jdbc:postgresql://HOST/BASE?sslmode=require`
   - **DB_USER** = `USUARIO`
   - **DB_PASSWORD** = `CONTRASEÑA`

## 2. Servidor en Render

1. Entra a [render.com](https://render.com) con tu cuenta de GitHub.
2. **New → Blueprint** y elige el repositorio `gestion-insumos-medicos`. Render lee `render.yaml` y te pide:
   - `DB_URL`, `DB_USER` y `DB_PASSWORD`: los datos de Neon.
   - `APP_CODIGO_CONFIGURACION`: un código que tú inventes. Se pide una sola vez, al crear la primera cuenta, para que nadie se adelante.
   - `APP_CLAVE_RECORDAR` no se pide: Render la genera al azar.
3. Confirma. La primera publicación tarda varios minutos: construye la app, la API y crea las tablas en Neon.
4. Al terminar, Render muestra la dirección, por ejemplo `https://inventario-xxxx.onrender.com`. El nombre del servicio se puede cambiar en *Settings*.

Cada vez que se fusiona un PR en `main` y **pasan las pruebas de GitHub**, Render publica la versión nueva solo (`autoDeployTrigger: checksPass`). Los datos no se tocan: las migraciones de Flyway solo agregan lo nuevo.

## 3. Primera vez en la app

1. Abre la dirección. La app pide **crear la cuenta**: escribe el código de configuración, el usuario y la contraseña.
2. **Configuración → Datos del negocio:** nombre, dirección y teléfono.
3. **Configuración → Numeración:** serie `0002`, siguiente número `1` (la primera nota será 0002-000001).
4. **Configuración → Importar inventario o clientes**, mejor desde la computadora donde están los Excel:
   1. **Inventario:** elige `inventario.xlsx` → **Validar** → si no hay errores, **Importar**. Se importa **una sola vez**.
   2. **Clientes:** lo mismo con `clientes.xlsx`.

   Con el servidor gratuito, validar o importar un inventario de unas 700 filas tarda **unos 3 minutos** cada uno (los clientes, segundos). No cierres la página mientras tanto. Si la página se cerró durante la importación, **no vuelvas a importar**: primero revisa en *Consultar inventario* si los productos ya están, porque el servidor termina la importación aunque la página se haya cerrado.
5. Revisa algunos productos y clientes al azar.

## 4. Instalarla en el celular

En el iPhone, abre la dirección en **Safari** → botón **Compartir** → **Agregar a inicio**. La sesión se recuerda 30 días.

## 5. Que no se duerma en horario de trabajo (opcional)

En [cron-job.org](https://cron-job.org), una cuenta gratuita, crea una tarea:

- **URL:** `https://TU-DIRECCION.onrender.com/api/health` (no usa la base de datos, así que no gasta sus horas).
- **Cada:** 10 minutos.
- **Horario:** de 7:00 a 1:00, zona horaria `America/Lima`.

Son unas 560 horas al mes, dentro de las 750 gratuitas de Render. Fuera de ese horario, la primera vez que se abra tardará un minuto.

## 6. Copias de seguridad

Los datos del negocio solo están en Neon. Conviene hacer una copia **cada semana** y **antes de cada cambio grande**:

```powershell
.\scripts\copia-de-seguridad.ps1
```

El script pide la cadena de conexión de Neon (no la guarda) y deja un archivo `inventario-AAAA-MM-DD_HHMM.dump` en `Documentos\Copias inventario`. Necesita Docker Desktop abierto. Guarda las copias en un lugar privado, por ejemplo una carpeta de Google Drive.

**Restaurar una copia** reemplaza los datos actuales por los de la copia, así que se hace solo si algo salió mal:

```powershell
docker run --rm -v "RUTA\DE\LA\CARPETA:/copias" postgres:18 pg_restore --clean --if-exists --no-owner --dbname "CADENA_DE_CONEXION" /copias/ARCHIVO.dump
```

Neon además guarda un historial corto de la base, que permite volver a un momento reciente desde su panel (*Restore*).

## 7. Si algo falla

- **La app no abre o muestra un error:** en Render, servicio → **Logs**.
- **"Inicia sesión" a cada rato:** la cookie "Recordarme" depende de `APP_CLAVE_RECORDAR`. Si alguien la cambia, todos deben volver a entrar.
- **Se acabaron las horas gratuitas:** Neon o Render suspenden el servicio hasta el mes siguiente. Se resuelve pasando a un plan de pago (no hay que cambiar nada de la app).
