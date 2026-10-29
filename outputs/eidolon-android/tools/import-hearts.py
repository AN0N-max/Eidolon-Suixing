"""Build Hibi heart assets from cached TOML, verifying every pinned Git blob (offline)."""
from pathlib import Path
import hashlib,json,tomllib,shutil
root=Path(__file__).resolve().parents[1]
manifest=json.loads((root/'tools/hibi-hearts-manifest.json').read_text(encoding='utf-8'))
source=root.parents[1]/'work/hibi-routines-source'
known={c['id'] for c in json.loads((root/'assets/characters.json').read_text(encoding='utf-8'))}
profiles={}
for entry in manifest['files']:
    name=Path(entry['path']).name
    raw=(source/name).read_bytes()
    assert hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()==entry['sha'],f'Changed source: {name}'
    data=tomllib.loads(raw.decode('utf-8'))
    who=Path(name).stem
    assert who in known and 0<data['age']<100
    assert set(data['tiers'])=={'cold','distant','trust','close','intimate'}
    assert data['pleased'] and data['hurt']
    assert data['age']>=18 or not data.get('romance')
    profiles[who]=data
assert len(profiles)==28
target=root/'assets/hibi-hearts-source';target.mkdir(exist_ok=True)
for entry in manifest['files']:
    name=Path(entry['path']).name;shutil.copyfile(source/name,target/name)
(root/'assets/hearts.json').write_text(json.dumps({'source':manifest['repository'],'commit':manifest['commit'],'profiles':profiles},ensure_ascii=False,separators=(',',':')),encoding='utf-8')
print(f'Imported and verified {len(profiles)} full Hibi hearts; original source comments preserved.')
