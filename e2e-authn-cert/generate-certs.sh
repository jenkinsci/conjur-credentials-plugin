#!/bin/bash
# Generates CA, server, and client certificates for authn-cert e2e testing.
# Modeled after ansible-conjur-collection's generate_authn_cert_certificates().
set -euo pipefail

CERT_DIR="$(cd "$(dirname "$0")" && pwd)/certs"
rm -rf "$CERT_DIR"
mkdir -p "$CERT_DIR"

ACCOUNT="myaccount"
SERVICE_ID="jenkins-cert"
HOST_CN="host/jenkins/cert-node"

echo "=== Generating CA ==="
openssl genrsa -out "$CERT_DIR/ca.key" 4096
openssl req -x509 -new -nodes \
  -key "$CERT_DIR/ca.key" \
  -sha256 -days 365 \
  -subj "/CN=conjur-authn-cert-ca" \
  -out "$CERT_DIR/ca.pem"

echo "=== Generating server certificate ==="
openssl genrsa -out "$CERT_DIR/server.key" 2048
openssl req -new \
  -key "$CERT_DIR/server.key" \
  -subj "/CN=localhost" \
  -out "$CERT_DIR/server.csr"

cat > "$CERT_DIR/server_ext.cnf" <<EOF
[v3_req]
subjectAltName = @alt_names
[alt_names]
DNS.1 = localhost
DNS.2 = conjur-proxy
IP.1 = 127.0.0.1
EOF

openssl x509 -req \
  -in "$CERT_DIR/server.csr" \
  -CA "$CERT_DIR/ca.pem" \
  -CAkey "$CERT_DIR/ca.key" \
  -CAcreateserial \
  -out "$CERT_DIR/server.pem" \
  -days 365 -sha256 \
  -extensions v3_req -extfile "$CERT_DIR/server_ext.cnf"

echo "=== Generating client certificate (CN=$HOST_CN) ==="
openssl genrsa -out "$CERT_DIR/client.key" 2048

# CN with slashes requires a config file approach
cat > "$CERT_DIR/client_req.cnf" <<EOF
[req]
default_bits = 2048
prompt = no
default_md = sha256
distinguished_name = dn

[dn]
CN = $HOST_CN
EOF

openssl req -new \
  -key "$CERT_DIR/client.key" \
  -config "$CERT_DIR/client_req.cnf" \
  -out "$CERT_DIR/client.csr"

openssl x509 -req \
  -in "$CERT_DIR/client.csr" \
  -CA "$CERT_DIR/ca.pem" \
  -CAkey "$CERT_DIR/ca.key" \
  -CAcreateserial \
  -out "$CERT_DIR/client.pem" \
  -days 365 -sha256

# Create PKCS12 keystore for OkHttp (Java)
openssl pkcs12 -export \
  -in "$CERT_DIR/client.pem" \
  -inkey "$CERT_DIR/client.key" \
  -certfile "$CERT_DIR/ca.pem" \
  -out "$CERT_DIR/client.p12" \
  -password pass:changeit

# Create truststore (CA cert) for Java
keytool -importcert -noprompt \
  -alias conjur-ca \
  -file "$CERT_DIR/ca.pem" \
  -keystore "$CERT_DIR/truststore.jks" \
  -storepass changeit 2>/dev/null

# Cleanup intermediate files
rm -f "$CERT_DIR/server.csr" "$CERT_DIR/client.csr" "$CERT_DIR/ca.srl" "$CERT_DIR/server_ext.cnf" "$CERT_DIR/client_req.cnf"

echo "=== Certificates generated in $CERT_DIR ==="
echo "  CA:          ca.pem / ca.key"
echo "  Server:      server.pem / server.key"
echo "  Client:      client.pem / client.key / client.p12"
echo "  Truststore:  truststore.jks"
