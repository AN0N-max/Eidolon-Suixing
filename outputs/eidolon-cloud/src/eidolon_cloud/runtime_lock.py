"""One scheduler process per data directory. Local admin commands do not take this lock."""

import os
from pathlib import Path


class RuntimeLock:
    def __init__(self, path: Path):
        self.path = path
        self.handle = None

    def acquire(self) -> None:
        if self.handle is not None:
            raise RuntimeError("Runtime lock already held")
        self.path.parent.mkdir(parents=True, exist_ok=True)
        handle = self.path.open("a+b")
        try:
            if os.name == "nt":
                import msvcrt

                handle.seek(0, 2)
                if handle.tell() == 0:
                    handle.write(b"0")
                    handle.flush()
                handle.seek(0)
                msvcrt.locking(handle.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl

                fcntl.flock(handle.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError:
            handle.close()
            raise RuntimeError(
                "Another runtime owns this cloud data directory"
            ) from None
        self.handle = handle

    def release(self) -> None:
        if self.handle is not None:
            self.handle.close()  # OS releases the lock even after a process crash.
            self.handle = None
