"""Import only age and sleep habits from the user's requested Hibi branch."""
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import json,tomllib,urllib.request

project=Path(__file__).resolve().parents[1]
workspace=project.parents[1]
def read(url):
    request=urllib.request.Request(url,headers={'User-Agent':'Eidolon-Android-Importer'})
    with urllib.request.urlopen(request,timeout=30) as response:return response.read()
tree=json.loads(read('https://api.github.com/repos/x499806631-commits/Eidolon-Hibi/git/trees/my-features?recursive=1'))
commit=tree['sha']
paths=[e['path'] for e in tree['tree'] if e['path'].startswith('characters/hearts/') and e['path'].endswith('.toml')]
reference=workspace/'work/hibi-routines-source'
reference.mkdir(parents=True,exist_ok=True)
def fetch(path):
    raw=read('https://raw.githubusercontent.com/x499806631-commits/Eidolon-Hibi/'+commit+'/'+path)
    (reference/Path(path).name).write_bytes(raw)
    data=tomllib.loads(raw.decode('utf-8'))
    assert isinstance(data['age'],int) and isinstance(data['sleep'],str)
    return Path(path).stem,{'age':data['age'],'sleep':data['sleep']}
with ThreadPoolExecutor(max_workers=5) as pool:profiles=dict(pool.map(fetch,paths))
assert len(profiles)==28
result={'source':'https://github.com/x499806631-commits/Eidolon-Hibi','commit':commit,'profiles':profiles}
(project/'assets/routines.json').write_text(json.dumps(result,ensure_ascii=False,separators=(',',':')),encoding='utf-8')
print('Imported',len(profiles),'age/sleep profiles at',commit)
