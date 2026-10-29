"""Check local ZIP headers, not just the central directory used by zipfile."""
import pathlib, struct, sys, zipfile

def verify(path):
    with zipfile.ZipFile(path) as archive, open(path, 'rb') as stream:
        names = set()
        resource_table_checked = False
        for entry in archive.infolist():
            assert entry.filename not in names, entry.filename
            names.add(entry.filename)
            stream.seek(entry.header_offset)
            header = stream.read(30)
            assert header[:4] == b'PK\x03\x04'
            size = struct.unpack_from('<H', header, 26)[0]
            local = stream.read(size).decode('utf-8')
            assert local == entry.filename and '\\' not in local, (entry.filename, local)
            if entry.filename == 'resources.arsc':
                extra_size = struct.unpack_from('<H', header, 28)[0]
                assert entry.compress_type == zipfile.ZIP_STORED, 'resources.arsc must not be compressed'
                assert (entry.header_offset + 30 + size + extra_size) % 4 == 0, 'resources.arsc must be 4-byte aligned'
                resource_table_checked = True
            archive.read(entry)
        assert resource_table_checked
        assert {'assets/ui/index.html', 'assets/ui/app.js', 'assets/ui/style.css', 'classes.dex'} <= names
    return len(names)

if __name__ == '__main__':
    path = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else pathlib.Path(__file__).resolve().parents[2] / 'Eidolon-SC-development.apk'
    print(f'PASS: {verify(path)} APK entries have matching POSIX local/central paths and valid CRCs.')
