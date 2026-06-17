#!/usr/bin/env bash
set -euo pipefail

JAR_PATH="${1:?jar path required}"
DIST_ARCHIVE="${2:?frontend archive path required}"
APP_BASE="${APP_BASE:-/opt/market-opinion-tracker}"
ENV_DIR="${ENV_DIR:-/etc/market-opinion-tracker}"
WWW_ROOT="${WWW_ROOT:-/var/www/market-opinion-tracker}"
SERVICE_NAME="${SERVICE_NAME:-market-opinion-tracker}"

validate_frontend_archive() {
  local archive="$1"
  local entries
  entries="$(tar -tzf "$archive")"

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
}

validate_frontend_archive "$DIST_ARCHIVE"

id -u markettracker >/dev/null 2>&1 || \
  useradd --system --home "$APP_BASE" --shell /usr/sbin/nologin markettracker

mkdir -p "$APP_BASE" "$ENV_DIR" "$WWW_ROOT/market"
cp -f "$JAR_PATH" "$APP_BASE/market-opinion-tracker.jar"
rm -rf "$WWW_ROOT/market"/*
tar -xzf "$DIST_ARCHIVE" -C "$WWW_ROOT/market"

if [[ ! -f "$ENV_DIR/app.env" ]]; then
  echo "Missing $ENV_DIR/app.env" >&2
  exit 1
fi

if grep -q '^SERVER_ADDRESS=' "$ENV_DIR/app.env"; then
  sed -i 's/^SERVER_ADDRESS=.*/SERVER_ADDRESS=127.0.0.1/' "$ENV_DIR/app.env"
else
  printf '\nSERVER_ADDRESS=127.0.0.1\n' >> "$ENV_DIR/app.env"
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
    listen 8888;
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

nginx -t
systemctl daemon-reload
systemctl enable "$SERVICE_NAME"
systemctl restart "$SERVICE_NAME"
systemctl restart nginx

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
