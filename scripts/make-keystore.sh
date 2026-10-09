#!/usr/bin/env bash
# Creates a release keystore and prints the four values to add as GitHub Secrets.
# Keep carf-release.jks somewhere safe: losing it means users can't update the app.
set -euo pipefail
ALIAS="${1:-carf}"
PASS="${2:-$(openssl rand -base64 18 | tr -dc 'A-Za-z0-9' | head -c 20)}"
keytool -genkeypair -v -keystore carf-release.jks -alias "$ALIAS" \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "$PASS" -keypass "$PASS" \
  -dname "CN=Carf.INC, OU=Mobile, O=Carf.INC, C=US"
echo
echo "Add these in GitHub -> Settings -> Secrets and variables -> Actions:"
echo "  KEYSTORE_PASSWORD = $PASS"
echo "  KEY_PASSWORD      = $PASS"
echo "  KEY_ALIAS         = $ALIAS"
echo "  KEYSTORE_BASE64   = (contents of keystore.b64 below)"
base64 -w0 carf-release.jks > keystore.b64 2>/dev/null || base64 carf-release.jks | tr -d '\n' > keystore.b64
echo "Wrote keystore.b64 (do NOT commit it; it is git-ignored)."
