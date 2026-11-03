"""Build a local Eidolon sticker package from manifest.json and images/. Standard library only."""
import argparse,hashlib,json,re,zipfile,os,tempfile
from pathlib import Path

def build(folder,output):
    folder=Path(folder).resolve();output=Path(output).resolve()
    manifest=json.loads((folder/'manifest.json').read_text(encoding='utf-8-sig'))
    if manifest.get('format')!='eidolon-sticker-pack' or manifest.get('version')!=1:raise ValueError('Expected eidolon-sticker-pack version 1')
    rows=manifest.get('stickers',[])
    if not 1<=len(rows)<=400:raise ValueError('Expected 1..400 stickers')
    files={};hashes=set();total=0
    for row in rows:
        name=row['file']
        if not re.fullmatch(r'images/[A-Za-z0-9_-]+\.(png|jpg|jpeg|webp|gif)',name):raise ValueError('Invalid image path')
        if not isinstance(row.get('name'),str) or not row['name'].strip():raise ValueError('Sticker needs a name')
        path=(folder/name).resolve()
        if folder not in path.parents:raise ValueError('Image must remain in the input folder')
        data=path.read_bytes();total+=len(data)
        if not data or len(data)>12*1024*1024 or total>47*1024*1024:raise ValueError('Package image size limit exceeded')
        digest=hashlib.sha256(data).hexdigest()
        if name in files or digest in hashes:raise ValueError('Duplicate image')
        hashes.add(digest);files[name]=data;row['sha256']=digest
    raw=(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n').encode('utf-8')
    if len(raw)>1024*1024:raise ValueError('Manifest exceeds 1MB')
    output.parent.mkdir(parents=True,exist_ok=True);fd,tmp=tempfile.mkstemp(prefix='sticker-pack-',suffix='.zip',dir=output.parent);os.close(fd)
    try:
        with zipfile.ZipFile(tmp,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
            z.writestr('manifest.json',raw)
            for name,data in files.items():z.writestr(name,data)
        if Path(tmp).stat().st_size>32*1024*1024:raise ValueError('ZIP exceeds 32MB')
        os.replace(tmp,output)
    finally:
        if Path(tmp).exists():Path(tmp).unlink()
    print(f'Created {output.name}: {len(rows)} stickers, SHA256 {hashlib.sha256(output.read_bytes()).hexdigest()}')

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('folder');parser.add_argument('output');args=parser.parse_args();build(args.folder,args.output)
