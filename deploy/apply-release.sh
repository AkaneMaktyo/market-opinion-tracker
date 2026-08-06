#!/usr/bin/env bash
set -euo pipefail

JAR_PATH="${1:?jar path required}"
DIST_ARCHIVE="${2:?frontend archive path required}"
RUNTIME_ENV_PATH="${3:-}"
APP_BASE="${APP_BASE:-/opt/market-opinion-tracker}"
ENV_DIR="${ENV_DIR:-/etc/market-opinion-tracker}"
WWW_ROOT="${WWW_ROOT:-/var/www/market-opinion-tracker}"
SERVICE_NAME="${SERVICE_NAME:-market-opinion-tracker}"
MUX_BASE="${MUX_BASE:-/opt/market-opinion-tracker-deploy}"

validate_frontend_archive() {
  local archive="$1"
  local entries
  entries="$(tar -tzf "$archive" | sed 's#^\./##')"

  if [[ -z "$entries" ]]; then
    echo "Frontend archive is empty: $archive" >&2
    exit 1
  fi

  if ! grep -qx 'index.html' <<<"$entries"; then
    echo "Frontend archive missing index.html: $archive" >&2
    exit 1
  fi

  if ! grep -Eq '^assets/index-.*\.js$' <<<"$entries"; then
    echo "Frontend archive missing main JS bundle: $archive" >&2
    exit 1
  fi

  if ! grep -qx 'live-update.json' <<<"$entries"; then
    echo "Frontend archive missing Android update manifest: $archive" >&2
    exit 1
  fi

  if ! grep -Eq '^updates/web-[a-f0-9]+\.zip$' <<<"$entries"; then
    echo "Frontend archive missing signed Android update bundle: $archive" >&2
    exit 1
  fi
}

validate_frontend_archive "$DIST_ARCHIVE"

id -u markettracker >/dev/null 2>&1 || \
  useradd --system --home "$APP_BASE" --shell /usr/sbin/nologin markettracker

mkdir -p "$APP_BASE" "$ENV_DIR" "$WWW_ROOT/market"
cp -f "$JAR_PATH" "$APP_BASE/market-opinion-tracker.jar"
if [ -d "$WWW_ROOT/market/apk" ]; then
  mv "$WWW_ROOT/market/apk" "$MUX_BASE/web-apk-backup"
fi
rm -rf "$WWW_ROOT/market"/*
if [ -d "$MUX_BASE/web-apk-backup" ]; then
  mv "$MUX_BASE/web-apk-backup" "$WWW_ROOT/market/apk"
fi
tar -xzf "$DIST_ARCHIVE" -C "$WWW_ROOT/market"
mkdir -p "$MUX_BASE"
cp -f "$(dirname "$0")/ssh_http_mux.py" "$MUX_BASE/ssh_http_mux.py"
chmod 755 "$MUX_BASE/ssh_http_mux.py"

if [[ ! -f "$ENV_DIR/app.env" ]]; then
  echo "Missing $ENV_DIR/app.env" >&2
  exit 1
fi

if [[ -n "$RUNTIME_ENV_PATH" && -f "$RUNTIME_ENV_PATH" ]]; then
  while IFS= read -r line; do
    [[ "$line" == *=* ]] || continue
    key="${line%%=*}"
    value="${line#*=}"
    case "$key" in
      PRICE_ALERT_WXPUSHER_SPT)
        temp_env="$(mktemp)"
        grep -v "^${key}=" "$ENV_DIR/app.env" > "$temp_env" || true
        printf '%s=%s\n' "$key" "$value" >> "$temp_env"
        cat "$temp_env" > "$ENV_DIR/app.env"
        rm -f "$temp_env"
        ;;
    esac
  done < "$RUNTIME_ENV_PATH"
  rm -f "$RUNTIME_ENV_PATH"
fi

if grep -q '^SERVER_ADDRESS=' "$ENV_DIR/app.env"; then
  sed -i 's/^SERVER_ADDRESS=.*/SERVER_ADDRESS=127.0.0.1/' "$ENV_DIR/app.env"
else
  printf '\nSERVER_ADDRESS=127.0.0.1\n' >> "$ENV_DIR/app.env"
fi

if ! grep -q '^YOUTUBE_PROXY_URL=' "$ENV_DIR/app.env"; then
  printf 'YOUTUBE_PROXY_URL=http://127.0.0.1:7897\n' >> "$ENV_DIR/app.env"
fi

if grep -q '^YOUTUBE_CHANNEL_SYNC_ENABLED=' "$ENV_DIR/app.env"; then
  sed -i 's/^YOUTUBE_CHANNEL_SYNC_ENABLED=.*/YOUTUBE_CHANNEL_SYNC_ENABLED=false/' "$ENV_DIR/app.env"
else
  printf 'YOUTUBE_CHANNEL_SYNC_ENABLED=false\n' >> "$ENV_DIR/app.env"
fi

chown -R markettracker:markettracker "$APP_BASE"
chown -R www-data:www-data "$WWW_ROOT"
chown root:markettracker "$ENV_DIR/app.env"
chmod 640 "$ENV_DIR/app.env"

cat > /etc/systemd/system/"$SERVICE_NAME".service <<'UNIT'
[Unit]
Description=Market Opinion Tracker
After=network-online.target mysql.service
Wants=network-online.target

[Service]
Type=simple
User=markettracker
Group=markettracker
WorkingDirectory=/opt/market-opinion-tracker
EnvironmentFile=/etc/market-opinion-tracker/app.env
ExecStart=/usr/bin/java -jar /opt/market-opinion-tracker/market-opinion-tracker.jar
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
UNIT

cat > /etc/nginx/sites-available/kol-monitor-trade <<'NGINX'
server {
    listen 80 default_server;
    listen 127.0.0.1:8889;
    server_name _;

    location /market/api/ {
        proxy_pass http://127.0.0.1:18082/api/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /market/ {
        root /var/www/market-opinion-tracker;
        try_files $uri $uri/ /market/index.html;
    }

    location / {
        proxy_pass http://127.0.0.1:8000;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
NGINX

cat > /etc/systemd/system/ssh-http-mux.service <<'UNIT'
[Unit]
Description=SSH and HTTP port mux on 8888
After=network.target nginx.service ssh.service

[Service]
Type=simple
ExecStart=/usr/bin/python3 /opt/market-opinion-tracker-deploy/ssh_http_mux.py --listen 0.0.0.0:8888 --ssh 127.0.0.1:22 --http 127.0.0.1:8889
Restart=always
RestartSec=2

[Install]
WantedBy=multi-user.target
UNIT

nginx -t
systemctl daemon-reload
systemctl enable "$SERVICE_NAME"
systemctl enable ssh-http-mux
systemctl restart "$SERVICE_NAME"
systemctl restart nginx
systemctl restart ssh-http-mux

for _ in $(seq 1 60); do
  if curl -fsS http://127.0.0.1:18082/api/health >/dev/null; then
    rm -f "$JAR_PATH" "$DIST_ARCHIVE"
    echo "deployed market-opinion-tracker"
    exit 0
  fi
  sleep 2
done

journalctl -u "$SERVICE_NAME" -n 100 --no-pager >&2 || true
exit 1
