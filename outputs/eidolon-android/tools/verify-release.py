"""Verify the upgrade identity and actual DEX class definitions without a device."""
from pathlib import Path
import hashlib,json,re,struct,subprocess,zipfile,xml.etree.ElementTree as ET

project=Path(__file__).resolve().parents[1]
workspace=project.parents[1]
apk=project.parent/'Eidolon-0.9.6-original-upgrade.apk'
original=project.parent/'Eidolon-SC-development.apk'
tools=workspace/'work/android-tools/build-tools/android-15'
jdk=next((workspace/'work/android-tools/jdk').iterdir())
expected='d576e97cae45aca6dedb80064e56a2aae97553fdaa8705904b63420b1d4503db'
def cert(path):
    text=subprocess.check_output([str(jdk/'bin/java.exe'),'-jar',str(tools/'lib/apksigner.jar'),'verify','--print-certs',str(path)],text=True)
    return re.search(r'certificate SHA-256 digest: (\w+)',text)[1]
assert cert(apk)==cert(original)==expected
badging=subprocess.check_output([str(tools/'aapt2.exe'),'dump','badging',str(apk)],text=True,encoding='utf-8')
assert "name='com.eidolon.sc.mobile'" in badging and "versionCode='32'" in badging and "versionName='0.9.6'" in badging
old_badging=subprocess.check_output([str(tools/'aapt2.exe'),'dump','badging',str(original)],text=True,encoding='utf-8')
assert "name='com.eidolon.sc.mobile'" in old_badging and "versionName='0.8.8-dev'" in old_badging
assert int(re.search(r"versionCode='(\d+)'",old_badging)[1])<32
def class_defs(dex):
    u=lambda off:struct.unpack_from('<I',dex,off)[0]
    strings_count,strings_offset=u(56),u(60)
    strings=[]
    for i in range(strings_count):
        offset=u(strings_offset+4*i)
        while dex[offset]&128:offset+=1
        offset+=1
        strings.append(dex[offset:dex.index(b'\0',offset)].decode('utf-8',errors='replace'))
    types_count,types_offset=u(64),u(68)
    types=[strings[u(types_offset+4*i)] for i in range(types_count)]
    return {types[u(u(100)+32*i)] for i in range(u(96))}
with zipfile.ZipFile(apk) as z:
    assert z.getinfo('resources.arsc').compress_type==zipfile.ZIP_STORED
    assert 'assets/ui/writings.js' in z.namelist() and 'assets/ui/schedules.js' in z.namelist() and 'assets/ui/promises.js' in z.namelist()
    defined=set().union(*(class_defs(z.read(name)) for name in z.namelist() if re.fullmatch(r'classes\d*\.dex',name)))
for name in ('PromiseData','PromiseDate'):
    assert 'Lcom/eidolon/sc/mobile/'+name+';' in defined,name
with zipfile.ZipFile(apk) as z:
    for name in ('index.html','app.js','writings.js','schedules.js','promises.js','style.css'):
        assert z.read('assets/ui/'+name)==(project/'assets/ui'/name).read_bytes(),name
ns='{http://schemas.android.com/apk/res/android}'
manifest=ET.parse(project/'AndroidManifest.xml').getroot()
components=[e.attrib[ns+'name'] for tag in ('activity','service') for e in manifest.iter(tag)]
for component in components:assert 'L'+component.replace('.','/')+';' in defined,component
baseline=workspace/'work/eidolon-0.9-import/eidolon-0.9/src/com/eidolon/sc/mobile'
voice_sources={f.name:hashlib.sha256(f.read_bytes()).hexdigest()==hashlib.sha256((project/'src/com/eidolon/sc/mobile'/f.name).read_bytes()).hexdigest() for f in baseline.glob('*.java')}
assert all(voice_sources.values())
result={'version':'0.9.6','versionCode':32,'package':manifest.attrib['package'],'upgrade_target':'original 0.8.8-dev / com.eidolon.sc.mobile / versionCode 21','same_certificate_as_original_0_8_8':True,'certificate_sha256':expected,'manifest_components_defined_in_dex':components,'resources_arsc_uncompressed':True,'promise_classes_defined_in_dex':True,'bundled_ui_matches_current_sources':True,'unchanged_0_9_voice_theme_sources':voice_sources,'apk_sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'device_or_live_api_test':False}
(project.parent/'Eidolon-0.9.6-verification.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print('PASS: original 0.8.8 upgrade identity, higher version code, components in DEX, resource packaging and preserved 0.9 voice/theme sources.')
