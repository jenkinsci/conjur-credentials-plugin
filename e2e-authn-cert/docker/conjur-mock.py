"""
Minimal mock that simulates Conjur authn-cert, resources, and secrets endpoints.

Supports both API versions:
  v1: POST /authn-cert/{service-id}/{account}/{host-id}/authenticate
  v2: POST /api/authn-cert/{service-id}/{account}/{host-id}/authenticate

  GET /resources/{account}?kind=variable  — list variables for authenticated host
  GET /secrets/{account}/variable/{id}    — retrieve a secret value
"""
import json
import os
import sys
import base64
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import unquote, urlparse, parse_qs

# Simulated secrets database — host/jenkins/cert-node has access to these
MOCK_SECRETS = {
    "data/vault/db-password": "s3cr3t-db-pass-2026",  # gitleaks:allow — fake mock value, not a real credential
    "data/vault/api-key": "ak_live_9f8e7d6c5b4a3210",  # gitleaks:allow — fake mock value, not a real credential
    "data/vault/tls-cert": "-----BEGIN CERTIFICATE-----\nMIIBxTCCAW...(mock)...\n-----END CERTIFICATE-----",
    "data/app/config-token": "eyJhbGciOiJIUzI1NiJ9.mock-app-token",
}

MOCK_HOST = "host/jenkins/cert-node"
MOCK_ACCOUNT = "myaccount"


def validate_token(auth_header):
    """Extract host identity from Token token=\"base64(json)\" header."""
    if not auth_header or not auth_header.startswith('Token token="'):
        return None
    try:
        b64 = auth_header.split('"')[1]
        # Add padding if missing (Java sends without padding)
        padding = 4 - len(b64) % 4
        if padding != 4:
            b64 += "=" * padding
        decoded = base64.b64decode(b64).decode("utf-8")
        token_data = json.loads(decoded)
        payload = token_data.get("payload", "{}")
        if isinstance(payload, str):
            payload = json.loads(payload)
        sub = payload.get("sub", "")
        # sub format: account:host:host/path
        parts = sub.split(":", 2)
        if len(parts) == 3 and parts[1] == "host":
            return parts[2]
    except Exception as e:
        print(f"[auth] token validation error: {e}", flush=True)
    return None


class ConjurMockHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        parts = self.path.strip("/").split("/")
        # Strip leading "api" prefix for v2 requests
        if parts and parts[0] == "api":
            parts = parts[1:]
        # Expected: authn-cert / {service-id} / {account} / {host-id} / authenticate
        if len(parts) == 5 and parts[0] == "authn-cert" and parts[4] == "authenticate":
            service_id = parts[1]
            account = parts[2]
            host_id = unquote(parts[3])

            # Check the X-SSL-Client-Certificate header (set by nginx)
            client_dn = self.headers.get("X-SSL-Client-Certificate", "")
            # nginx passes DN like: CN=host/jenkins/cert-node
            expected_cn = host_id

            print(f"[authn-cert] service={service_id} account={account} host={host_id}", flush=True)
            print(f"[authn-cert] client_dn={client_dn}", flush=True)

            if expected_cn and (f"CN={expected_cn}" in client_dn or client_dn == ""):
                token = {
                    "protected": "eyJhbGciOiJjb25qdXIub3JnL3Nsb3NpbG8vdjIiLCJraWQiOiJjZXJ0In0=",
                    "payload": json.dumps({
                        "sub": f"{account}:host:{host_id}",
                        "iat": 1700000000,
                        "exp": 1700003600
                    }),
                    "signature": "mock-signature-for-e2e-test"
                }
                response = json.dumps(token).encode("utf-8")
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(response)))
                self.end_headers()
                self.wfile.write(response)
            else:
                self.send_response(401)
                self.end_headers()
                self.wfile.write(b"Unauthorized: certificate CN does not match host identity")
        else:
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b"Not Found")

    def do_GET(self):
        parsed = urlparse(self.path)
        path = parsed.path
        query = parse_qs(parsed.query)

        # Strip /api prefix
        if path.startswith("/api/"):
            path = path[4:]

        parts = path.strip("/").split("/")

        if path == "/health":
            self.send_response(200)
            self.end_headers()
            self.wfile.write(b"OK")
            return

        # GET /resources/{account}?kind=variable — list variables accessible to host
        if len(parts) >= 2 and parts[0] == "resources":
            host_id = validate_token(self.headers.get("Authorization", ""))
            if not host_id:
                self.send_response(401)
                self.end_headers()
                self.wfile.write(b"Unauthorized")
                return

            account = parts[1]
            kind = query.get("kind", [None])[0]
            print(f"[resources] account={account} kind={kind} host={host_id}", flush=True)

            resources = []
            for var_id, value in MOCK_SECRETS.items():
                resource = {
                    "id": f"{account}:variable:{var_id}",
                    "owner": f"{account}:policy:data/vault",
                    "permissions": [
                        {"role": f"{account}:host:{MOCK_HOST}", "privilege": "execute"}
                    ],
                    "annotations": [
                        {"name": "conjur/kind", "value": "secret"}
                    ]
                }
                if kind is None or kind == "variable":
                    resources.append(resource)

            response = json.dumps(resources).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(response)))
            self.end_headers()
            self.wfile.write(response)
            return

        # GET /secrets/{account}/variable/{variable-id} — retrieve secret value
        if len(parts) >= 4 and parts[0] == "secrets" and parts[2] == "variable":
            host_id = validate_token(self.headers.get("Authorization", ""))
            if not host_id:
                self.send_response(401)
                self.end_headers()
                self.wfile.write(b"Unauthorized")
                return

            account = parts[1]
            variable_id = unquote("/".join(parts[3:]))
            print(f"[secrets] account={account} variable={variable_id} host={host_id}", flush=True)

            if variable_id in MOCK_SECRETS:
                value = MOCK_SECRETS[variable_id].encode("utf-8")
                self.send_response(200)
                self.send_header("Content-Type", "application/octet-stream")
                self.send_header("Content-Length", str(len(value)))
                self.end_headers()
                self.wfile.write(value)
            else:
                error = json.dumps({
                    "error": {"code": "not_found", "message": f"Variable '{variable_id}' not found"}
                }).encode("utf-8")
                self.send_response(404)
                self.send_header("Content-Type", "application/json")
                self.end_headers()
                self.wfile.write(error)
            return

        self.send_response(404)
        self.end_headers()
        self.wfile.write(b"Not Found")

    def log_message(self, format, *args):
        print(f"[conjur-mock] {args[0]}", flush=True)


if __name__ == "__main__":
    port = int(os.environ.get("PORT", "8080"))
    server = HTTPServer(("0.0.0.0", port), ConjurMockHandler)
    print(f"Conjur mock server listening on port {port}", flush=True)
    server.serve_forever()
