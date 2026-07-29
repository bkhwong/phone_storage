from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import uuid

PIN = "123456"

class H(BaseHTTPRequestHandler):
    def _cors(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "*")
        self.send_header("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS")

    def _json(self, code, obj):
        body = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self._cors()
        self.end_headers()
        self.wfile.write(body)

    def do_OPTIONS(self):
        self.send_response(204)
        self._cors()
        self.end_headers()

    def do_GET(self):
        path = self.path.split("?")[0]
        if path in ("/api/health", "/health"):
            return self._json(200, {"ok": True, "status": "ok", "version": "mock"})
        if path.startswith("/api/assets/") and (path.endswith("/thumbnail") or path.endswith("/original")):
            self.send_response(404)
            self.end_headers()
            return
        if path == "/api/assets":
            return self._json(200, {"items": [], "next_cursor": None})
        self._json(404, {"detail": "not found"})

    def do_POST(self):
        path = self.path.split("?")[0]
        length = int(self.headers.get("Content-Length", 0))
        raw = self.rfile.read(length) if length else b"{}"
        try:
            data = json.loads(raw.decode() or "{}")
        except Exception:
            data = {}
        if path == "/api/pair":
            pin = str(data.get("pin", ""))
            if pin != PIN:
                return self._json(401, {"detail": "Invalid pairing PIN"})
            return self._json(200, {
                "device_token": "mock-token-" + uuid.uuid4().hex[:16],
                "device_id": str(uuid.uuid4()),
            })
        if path == "/api/assets/by-hash/lookup":
            return self._json(200, {"matches": []})
        if path.endswith("/archive"):
            aid = path.split("/")[-2]
            return self._json(200, {"id": aid, "state": "archived"})
        if path.endswith("/discard"):
            aid = path.split("/")[-2]
            return self._json(200, {"id": aid, "discarded": True})
        if path == "/api/uploads/init":
            return self._json(200, {"upload_id": "u1", "chunk_size": 4194304, "offset": 0})
        if "/complete" in path:
            return self._json(200, {"id": "a1", "state": "ready"})
        self._json(404, {"detail": "not found"})

    def log_message(self, fmt, *args):
        print("[%s] %s" % (self.log_date_time_string(), fmt % args))

if __name__ == "__main__":
    port = 8787
    print("Mock Photo Sync listening on 0.0.0.0:%s PIN=%s" % (port, PIN), flush=True)
    ThreadingHTTPServer(("0.0.0.0", port), H).serve_forever()
