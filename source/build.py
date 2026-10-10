"""Build against a standard installed Forge 1.20.1-47.3.22 runtime.
Uses production SRG names; it does not compile or modify Forge.
"""
from pathlib import Path
import argparse, subprocess, zipfile, hashlib, json, sys, uuid
sys.stdout.reconfigure(encoding="utf-8")
p=argparse.ArgumentParser()
p.add_argument('--forge',choices=['47.3.22','47.3.33'],default='47.3.22')
p.add_argument('--libraries',required=True)
p.add_argument('--jdk',required=True)
a=p.parse_args()
profile=json.loads((Path(__file__).resolve().parent.parent/'release.json').read_text(encoding='utf-8'));version=profile['version']
if a.forge not in profile['forgeBuilds']:raise SystemExit('Unsupported build profile')
root=Path(__file__).resolve().parent
lib=Path(a.libraries); javac=Path(a.jdk)/'bin/javac.exe'
jars=sorted(lib.rglob('*.jar'))
preferred=[x for x in jars if ('1.20.1-'+a.forge) in str(x) or x.name.endswith('1.20.1-20230612.114412-srg.jar')]
jars=preferred+[x for x in jars if x not in preferred]
dist=root.parent/'dist';dist.mkdir(exist_ok=True)
for component,name in [('boot',f'ForgeLoadingFramework-bootstrap-{version}.jar'),('client',f'ForgeLoadingFramework-{version}.jar')]:
 classes=root.parent/'work/build'/(component+'-'+uuid.uuid4().hex); classes.mkdir(parents=True,exist_ok=True)
 cp=[str(x).replace('\\','/') for x in jars]
 if component=='client':cp.insert(0,str(dist/f'ForgeLoadingFramework-bootstrap-{version}.jar').replace('\\','/'))
 sources=list((root/component/'src').rglob('*.java'))
 args=['-J-Dfile.encoding=UTF-8','-J-Duser.language=en','-J-Duser.country=US','-proc:none','-encoding','UTF-8','--release','17','-classpath',';'.join(cp),'-d',str(classes).replace('\\','/')]+[str(x).replace('\\','/') for x in sources]
 argfile=root.parent/'work/build'/f'{component}-javac.args'
 argfile.write_text('\n'.join('"'+x.replace('"','\\"')+'"' for x in args),encoding='utf-8')
 result=subprocess.run([str(javac),'@'+str(argfile)],capture_output=True)
 print(result.stdout.decode('utf-8',errors='replace'),result.stderr.decode('utf-8',errors='replace'))
 if result.returncode:raise SystemExit(result.returncode)
 out=dist/name
 with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as z:
  for b in [classes,root/component/'resources']:
   for f in sorted(b.rglob('*')):
    if f.is_file():
     entry=zipfile.ZipInfo(f.relative_to(b).as_posix(),date_time=(2026,1,1,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED;entry.external_attr=0o644<<16;data=f.read_bytes()
     if component=='client' and f.name=='mods.toml':
      text=data.decode('utf-8');text=__import__('re').sub(r'(?m)^version="[^"]*"$',f'version="{version}"',text);data=text.encode('utf-8')
     z.writestr(entry,data)
  if component in ['boot','client']:
   entry=zipfile.ZipInfo('shanghan/'+('early' if component=='boot' else 'client')+'/runtime.json',date_time=(2026,1,1,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED;z.writestr(entry,json.dumps(profile,sort_keys=True).encode('utf-8'))
 print(name,out.stat().st_size,hashlib.file_digest(out.open('rb'),'sha256').hexdigest())
