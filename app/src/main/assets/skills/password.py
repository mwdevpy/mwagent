"""Strong random passwords. params: length (default 20), count (default 3), symbols (default true)"""

import secrets
import string

length = max(8, min(128, int(params.get("length", 20))))
count = max(1, min(20, int(params.get("count", 3))))
symbols = str(params.get("symbols", True)).lower() not in ("false", "0", "no")

alphabet = string.ascii_letters + string.digits + ("!@#$%^&*-_=+?" if symbols else "")
groups = [string.ascii_lowercase, string.ascii_uppercase, string.digits] + (["!@#$%^&*-_=+?"] if symbols else [])

for _ in range(count):
    while True:
        password = "".join(secrets.choice(alphabet) for _ in range(length))
        if all(any(char in group for char in password) for group in groups):
            break
    print(password)
