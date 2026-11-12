#!/usr/bin/env sh
set -eu

GRADLE_VERSION="8.13"
GRADLE_SHA256="20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78"
GRADLE_HOME_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}/geo-videos-wrapper/gradle-${GRADLE_VERSION}"
GRADLE_BIN="${GRADLE_HOME_DIR}/bin/gradle"

if [ ! -x "$GRADLE_BIN" ]; then
  ARCHIVE_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}/geo-videos-wrapper"
  ARCHIVE_FILE="${ARCHIVE_DIR}/gradle-${GRADLE_VERSION}-bin.zip"
  mkdir -p "$ARCHIVE_DIR"

  if [ ! -f "$ARCHIVE_FILE" ]; then
    URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
    echo "Descargando Gradle ${GRADLE_VERSION}..."
    if command -v curl >/dev/null 2>&1; then
      curl -fL --retry 3 --connect-timeout 20 "$URL" -o "$ARCHIVE_FILE"
    elif command -v wget >/dev/null 2>&1; then
      wget -O "$ARCHIVE_FILE" "$URL"
    else
      echo "Error: se necesita curl o wget para descargar Gradle." >&2
      exit 1
    fi
  fi

  if command -v sha256sum >/dev/null 2>&1; then
    ACTUAL_SHA256="$(sha256sum "$ARCHIVE_FILE" | awk '{print $1}')"
    if [ "$ACTUAL_SHA256" != "$GRADLE_SHA256" ]; then
      echo "Error: el archivo de Gradle no supera la verificacion SHA-256." >&2
      rm -f "$ARCHIVE_FILE"
      exit 1
    fi
  fi

  rm -rf "$GRADLE_HOME_DIR"
  unzip -q "$ARCHIVE_FILE" -d "$ARCHIVE_DIR"
fi

exec "$GRADLE_BIN" "$@"
