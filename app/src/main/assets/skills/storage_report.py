"""Largest files and top-level folders on the phone storage. params: path (default the shared storage), top (default 15)"""

import os

root = params.get("path") or "/storage/emulated/0"
top = int(params.get("top", 15))


def human(size):
    for unit in ("B", "KB", "MB", "GB"):
        if size < 1024:
            return "%.1f %s" % (size, unit)
        size /= 1024
    return "%.1f TB" % size


files = []
folders = {}
for base, dirs, names in os.walk(root, onerror=lambda error: None):
    dirs[:] = [name for name in dirs if not name.startswith(".") and name != "Android"]
    first = os.path.relpath(base, root).split(os.sep)[0]
    for name in names:
        path = os.path.join(base, name)
        try:
            size = os.path.getsize(path)
        except OSError:
            continue
        files.append((size, path))
        folders[first] = folders.get(first, 0) + size

if not files:
    print("Nothing readable in %s. Phone storage needs All files access in Access and permissions." % root)
else:
    print("Total: %s in %d files\n" % (human(sum(size for size, _ in files)), len(files)))
    print("Folders:")
    for name, size in sorted(folders.items(), key=lambda item: -item[1])[:top]:
        print("  %s  %s" % (human(size), "(root)" if name == "." else name))
    print("\nFiles:")
    for size, path in sorted(files, reverse=True)[:top]:
        print("  %s  %s" % (human(size), os.path.relpath(path, root)))
