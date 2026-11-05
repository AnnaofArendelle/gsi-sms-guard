"""Remove generated output while preserving an ignored local development key."""
from pathlib import Path
import shutil
build = Path(__file__).resolve().parents[1] / 'build'
if build.exists():
    for entry in build.iterdir():
        if entry.name == 'development.jks':
            continue
        if entry.is_dir() and not entry.is_symlink():
            shutil.rmtree(entry)
        else:
            entry.unlink()
