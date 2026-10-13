#!/usr/bin/env bash
# Validate the selected persistent key without logging credentials or changing it.
set -eu
set +x
umask 077
fail() { echo "::error::$1"; exit 1; }
check_complete() {
  if [ -n "$1" ] || [ -n "$2" ] || [ -n "$3" ]; then
    [ -n "$1" ] && [ -n "$2" ] && [ -n "$3" ] || fail "Incomplete $4 signing configuration. Set all three signing secrets."
  fi
}
check_complete "${RELEASE_KEYSTORE:-}" "${RELEASE_ALIAS:-}" "${RELEASE_PASSWORD:-}" release
if [ "${VARIANT:-}" = test ]; then
  check_complete "${TEST_KEYSTORE:-}" "${TEST_ALIAS:-}" "${TEST_PASSWORD:-}" test
fi
if [ "${VARIANT:-}" = test ] && [ -n "${TEST_KEYSTORE:-}" ]; then
  encoded=$TEST_KEYSTORE
  alias=$TEST_ALIAS
  STORE_PASSWORD=$TEST_PASSWORD
  PRIVATE_KEY_PASSWORD=$TEST_PASSWORD
elif [ -n "${RELEASE_KEYSTORE:-}" ]; then
  encoded=$RELEASE_KEYSTORE
  alias=$RELEASE_ALIAS
  STORE_PASSWORD=$RELEASE_PASSWORD
  PRIVATE_KEY_PASSWORD=${RELEASE_KEY_PASSWORD:-$RELEASE_PASSWORD}
else
  echo '::warning::This APK will use an ephemeral signing key and cannot reliably update previous installations.'
  exit 0
fi
export STORE_PASSWORD PRIVATE_KEY_PASSWORD
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
printf '%s' "$encoded" | base64 --decode > "$work/source.keystore" 2>/dev/null || fail 'Signing keystore is not valid Base64.'
# Java KeyStore checks the actual private-key password; keytool can ignore
# -srckeypass for PKCS12 stores, which is insufficient for this preflight.
cat > "$work/SigningKeyCheck.java" <<'JAVA'
import java.io.File;
import java.security.KeyStore;
import java.security.PrivateKey;
class SigningKeyCheck {
    public static void main(String[] args) {
        try {
            var store = KeyStore.getInstance(new File(args[0]),
                System.getenv("STORE_PASSWORD").toCharArray());
            var key = store.getKey(args[1],
                System.getenv("PRIVATE_KEY_PASSWORD").toCharArray());
            if (!(key instanceof PrivateKey)) System.exit(1);
        } catch (Exception ignored) {
            System.exit(1);
        }
    }
}
JAVA
java "$work/SigningKeyCheck.java" "$work/source.keystore" "$alias" \
  > /dev/null 2>&1 || fail 'Cannot unlock signing private key. Check keystore, alias, store password and key password.'
echo 'Persistent signing private key verified before compilation.'
