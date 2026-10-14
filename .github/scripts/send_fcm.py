#!/usr/bin/env python3

import base64
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

try:
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import padding as asym_padding
except ImportError:
    sys.exit("ERROR: 'cryptography' is not installed. Run: pip install cryptography")

def env(name: str) -> str:
    return os.environ.get(name, "").strip()

def fail(msg: str) -> None:
    print(f"ERROR: {msg}", file=sys.stderr)
    sys.exit(1)

project_id = env("FCM_PROJECT_ID")
sa_json = env("FCM_SERVICE_ACCOUNT_JSON")
if not project_id:
    fail("FCM_PROJECT_ID is not set")
if not sa_json:
    fail("FCM_SERVICE_ACCOUNT_JSON is not set")

msg_type = env("FCM_TYPE") or "app_update"
data: dict[str, str] = {"type": msg_type}

if msg_type == "app_update":
    tag = env("NEW_TAG")
    version = tag.lstrip("v") if tag else env("FCM_VERSION")
    if version:
        data["version"] = version
    is_prerelease = "-" in version
    topic = env("FCM_TOPIC") or ("animex_updates_dev" if is_prerelease else "animex_updates")
else:
    fail(f"unknown FCM_TYPE '{msg_type}'")

print(f"Topic        : {topic}")
print(f"Message type : {msg_type}")
print(f"Data         : {json.dumps(data, ensure_ascii=False)}")

try:
    sa = json.loads(sa_json)
except json.JSONDecodeError as e:
    fail(f"FCM_SERVICE_ACCOUNT_JSON is not valid JSON: {e}")
for key in ("client_email", "private_key", "token_uri"):
    if key not in sa:
        fail(f"FCM_SERVICE_ACCOUNT_JSON is missing '{key}'")

def b64(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode()

now = int(time.time())
header = b64(json.dumps({"alg": "RS256", "typ": "JWT"}).encode())
claims = b64(
    json.dumps(
        {
            "iss": sa["client_email"],
            "sub": sa["client_email"],
            "aud": sa["token_uri"],
            "iat": now,
            "exp": now + 3600,
            "scope": "https://www.googleapis.com/auth/firebase.messaging",
        }
    ).encode()
)
try:
    key = serialization.load_pem_private_key(sa["private_key"].encode(), password=None)
except Exception as e:
    fail(f"cannot load private key: {e}")
signature = key.sign(f"{header}.{claims}".encode(), asym_padding.PKCS1v15(), hashes.SHA256())
assertion = f"{header}.{claims}.{b64(signature)}"

try:
    resp = urllib.request.urlopen(
        urllib.request.Request(
            sa["token_uri"],
            data=urllib.parse.urlencode(
                {"grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer", "assertion": assertion}
            ).encode(),
        )
    )
    access_token = json.loads(resp.read())["access_token"]
except Exception as e:
    fail(f"cannot obtain access token: {e}")
print("OAuth2 token obtained")

body = {"message": {"topic": topic, "data": data, "android": {"priority": "high"}}}
req = urllib.request.Request(
    f"https://fcm.googleapis.com/v1/projects/{project_id}/messages:send",
    data=json.dumps(body).encode(),
    headers={"Authorization": f"Bearer {access_token}", "Content-Type": "application/json"},
)
try:
    r = urllib.request.urlopen(req)
    status, text = r.status, r.read().decode()
except urllib.error.HTTPError as e:
    status, text = e.code, e.read().decode()

print(f"\nFCM response (HTTP {status}):")
try:
    print(json.dumps(json.loads(text), indent=2))
except ValueError:
    print(text)

if status != 200:
    fail(
        f"FCM push failed with HTTP {status}. Check FCM_PROJECT_ID, the service account JSON "
        "and that the account has the 'Firebase Cloud Messaging Admin' role."
    )
print(f"\nSent to topic '{topic}'")
