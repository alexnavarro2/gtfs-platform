# Desplegar GTFS Platform (piloto con usuarios reales)

Guía para poner la app en una VM gratuita de Oracle Cloud ("Always Free"),
accesible en tu dominio propio vía HTTPS. No necesitas experiencia previa con
Linux — copia y pega los comandos tal cual, en orden.

## 0. Antes de empezar

- Tu dominio (`alexnavarromx.com` en esta guía — cámbialo por el tuyo en todos
  los comandos) debe estar registrado y con DNS administrable (GoDaddy, en tu
  caso).
- Vas a necesitar subir este código a GitHub (repo privado está bien).

## 1. Sube el código a GitHub

Si aún no lo hiciste:

```bash
cd "/Users/AlexNavarro/Documents/GTFS-Platform:"
git remote add origin https://github.com/TU_USUARIO/gtfs-platform.git
git push -u origin main
```

## 2. Crea la cuenta y la VM en Oracle Cloud

1. Entra a [oracle.com/cloud/free](https://www.oracle.com/cloud/free/) y crea una
   cuenta (pide tarjeta para verificar identidad, pero los recursos "Always Free"
   nunca se cobran mientras te quedes dentro de esos límites).
2. Una vez dentro de la consola: **Compute → Instances → Create Instance**.
3. Configura:
   - **Name**: `gtfs-platform`
   - **Image**: Ubuntu (la versión LTS más reciente que ofrezca, ej. 24.04)
   - **Shape**: clic en "Change shape" → pestaña "Ampere" → `VM.Standard.A1.Flex`
     → 2 OCPUs / 12 GB RAM (dentro del límite "Always Free"; puedes subir hasta
     4/24 sin costo si lo necesitas después)
   - **Networking**: deja la VCN/subnet que te proponga por default
   - **Add SSH keys**: elige "Generate a key pair for me" y **descarga la llave
     privada** (`.key` o `.pem`) — la vas a necesitar para conectarte
4. Clic en **Create**. Espera 1-2 minutos a que el estado pase a "Running" y
   copia la **Public IP** que te asigna.
5. Abre los puertos 80 y 443 (por defecto Oracle solo deja abierto el 22 de
   SSH): en la página de la instancia, entra al link de tu **Subnet** →
   **Security Lists** → la lista default → **Add Ingress Rules** → agrega dos
   reglas, una para el puerto `80` y otra para el `443`, ambas con Source CIDR
   `0.0.0.0/0` y protocolo TCP.

## 3. Conéctate por SSH

```bash
chmod 600 ~/Downloads/tu-llave-descargada.key
ssh -i ~/Downloads/tu-llave-descargada.key ubuntu@LA_IP_PUBLICA
```

## 4. Instala Docker en la VM

Ya conectado por SSH, pega esto (instala Docker + el plugin de compose):

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
newgrp docker
```

## 5. Trae el código y configura los secretos

```bash
git clone https://github.com/TU_USUARIO/gtfs-platform.git app
cd app
cp .env.production.example .env
nano .env
```

Dentro de `nano`, cambia como mínimo:
- `APP_DOMAIN` → tu dominio real (ej. `alexnavarromx.com`)
- `POSTGRES_PASSWORD` → genera uno con `openssl rand -base64 24` (corre eso en
  otra terminal, pega el resultado)
- `GTFSPLATFORM_JWT_SECRET` → genera uno con `openssl rand -base64 48`

Guarda con `Ctrl+O`, `Enter`, y sal con `Ctrl+X`.

## 6. Apunta tu dominio a la VM (antes de levantar los contenedores)

En el panel de DNS de GoDaddy para el dominio que vayas a usar, agrega un
registro:

| Tipo | Nombre | Valor |
|---|---|---|
| A | `@` (o el subdominio, ej. `app`) | la IP pública de tu VM |

Espera unos minutos a que propague (puedes checar con `dig alexnavarromx.com`
desde tu Mac — cuando la IP que devuelve coincide con la de la VM, ya
propagó).

## 7. Levanta todo

De vuelta en la VM, por SSH:

```bash
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
```

La primera vez tarda varios minutos (construye las imágenes). Cuando termine:

```bash
docker compose -f docker-compose.prod.yml ps
```

Los tres servicios (`db`, `backend`, `frontend`) deben decir `Up`/`healthy`, y
`caddy` también — Caddy pide el certificado HTTPS solo en cuanto arranca (por
eso el DNS debe estar propagado antes del paso 7).

## 8. Verifica

Abre `https://alexnavarromx.com` (tu dominio) en el navegador — debería cargar
la pantalla de login con candado verde (HTTPS válido).

## Para actualizar la app después (cuando yo te dé cambios nuevos)

```bash
cd ~/app
git pull
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
```

## Notas de este piloto

- **Respaldo de datos**: los datos viven en un volumen de Docker en esta VM
  (`gtfsplatform-db-data`). Si la VM se borra, se pierden. Para un piloto está
  bien, pero antes de depender de esto en serio conviene automatizar un
  `pg_dump` periódico a otro lugar.
- **OSRM y geocoding**: usan servicios públicos gratuitos de terceros
  (`router.project-osrm.org`, ArcGIS/Esri) sin garantía de servicio — bien para
  un piloto chico, pero si el uso crece y empiezan a fallar/tardar, hay que
  migrar a una instancia propia (ver comentarios en `.env.production.example`).
- **HTTPS**: Caddy renueva el certificado solo, no hay que hacer nada.
