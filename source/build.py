"""Build one Java17 distribution with isolated legacy and modern SDK compilers."""
from pathlib import Path
import argparse, subprocess, zipfile, hashlib, json, sys, uuid, re, tomllib
sys.stdout.reconfigure(encoding="utf-8")
profile=json.loads((Path(__file__).resolve().parent.parent/'release.json').read_text(encoding='utf-8'))
p=argparse.ArgumentParser()
p.add_argument('--forge',choices=profile['buildProfiles'],default='47.3.22')
p.add_argument('--libraries',default='D:/.minecraft/libraries')
p.add_argument('--forge-libraries',help='Directory containing exact Forge component jars; excludes other Forge versions')
p.add_argument('--jdk',default='D:/workspace/ShangHanEarlyLoading/tools/jdk17/jdk-17.0.15+6')
a=p.parse_args()
profile=json.loads((Path(__file__).resolve().parent.parent/'release.json').read_text(encoding='utf-8'));version=profile['version']
if a.forge not in profile['buildProfiles']:raise SystemExit('Unsupported build profile')
root=Path(__file__).resolve().parent
lib=Path(a.libraries); javac=Path(a.jdk)/'bin/javac.exe'
sys.path.insert(0,str(Path(__file__).resolve().parent))
from library_paths import collect_libraries
jars=collect_libraries(lib,a.forge,a.forge_libraries)
sdl_sdk=root/'adapters/modern/dependencies'
sdk=[sdl_sdk/n for n in ['lwjgl-3.4.3.jar','lwjgl-opengl-3.4.3.jar','lwjgl-sdl-3.4.3.jar']]
if not all(f.is_file() for f in sdk):raise SystemExit('Missing SDL SDK compile dependencies')
print('BUILD_TARGET',a.forge,'exact dependencies',a.forge_libraries or 'installed libraries')
dist=root.parent/'dist';dist.mkdir(exist_ok=True)
for component,name in [('boot',f'ForgeLoadingFramework-bootstrap-{version}.jar'),('client',f'ForgeLoadingFramework-{version}.jar')]:
 classes=root.parent/'work/build'/(component+'-'+uuid.uuid4().hex); classes.mkdir(parents=True,exist_ok=True)
 cp=[str(x).replace('\\','/') for x in jars]
 if component=='client':cp.insert(0,str(dist/f'ForgeLoadingFramework-bootstrap-{version}.jar').replace('\\','/'))
 sources=list((root/component/'src').rglob('*.java'))
 if component=='boot':
  sources+=list((root/'router/java').rglob('*.java'))+list((root/'adapters/modern/src').rglob('NativeFadeSurface.java'))
 else:sources+=list((root/'adapters/shared-client/src').rglob('*.java'))
 args=['-J-Dfile.encoding=UTF-8','-J-Duser.language=en','-J-Duser.country=US','-proc:none','-encoding','UTF-8','--release','17','-classpath',';'.join(cp),'-d',str(classes).replace('\\','/')]+[str(x).replace('\\','/') for x in sources]
 argfile=root.parent/'work/build'/f'{component}-javac.args'
 argfile.write_text('\n'.join('"'+x.replace('"','\\"')+'"' for x in args),encoding='utf-8')
 result=subprocess.run([str(javac),'@'+str(argfile)],capture_output=True)
 print(result.stdout.decode('utf-8',errors='replace'),result.stderr.decode('utf-8',errors='replace'))
 if result.returncode:raise SystemExit(result.returncode)
 if component=='boot':
  # The SDL-only class needs the modern SDK. Shared code remains bound to oldest supported LWJGL.
  modern_sources=[f for f in (root/'adapters/modern/src').rglob('*.java') if f.name!='NativeFadeSurface.java']
  modern_cp=[str(classes)]+[str(x) for x in sdk]+cp
  modern_args=['-J-Dfile.encoding=UTF-8','-proc:none','-encoding','UTF-8','--release','17','-classpath',';'.join(modern_cp),'-d',str(classes)]+[str(x) for x in modern_sources]
  modern_argfile=root.parent/'work/build'/'sdl-javac.args'
  modern_argfile.write_text(chr(10).join(chr(34)+x.replace(chr(92),'/').replace(chr(34),chr(92)+chr(34))+chr(34) for x in modern_args),encoding='utf-8')
  modern_result=subprocess.run([str(javac),'@'+str(modern_argfile)],capture_output=True)
  print(modern_result.stdout.decode('utf-8',errors='replace'),modern_result.stderr.decode('utf-8',errors='replace'))
  if modern_result.returncode:raise SystemExit(modern_result.returncode)
 out=dist/name
 with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as z:
  for b in [classes,root/component/'resources']:
   for f in sorted(b.rglob('*')):
    if f.is_file():
     entry=zipfile.ZipInfo(f.relative_to(b).as_posix(),date_time=(2026,1,1,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED;entry.external_attr=0o644<<16;data=f.read_bytes()
     if component=='client' and f.name=='mods.toml':
      text=data.decode('utf-8')
      text=re.sub(r'(?m)^license="[^"]*"',lambda m:'license="PolyForm-Noncommercial-1.0.0 AND MIT"',text)
      text=re.sub(r'(?m)^version="[^"]*"(?=\r*$)',f'version="{version}"',text)
      text=re.sub(r'(?m)^loaderVersion="[^"]*"',lambda m:'loaderVersion="'+profile['loaderRange']+'"',text)
      mc_dependency=r'(?ms)(^\[\[dependencies\.shanghanearlyloading\]\]\s*modId="minecraft"\s*mandatory=true\s*versionRange=)"[^"]*"'
      text,mc_count=re.subn(mc_dependency,lambda m:m.group(1)+'"'+profile['minecraft']+'"',text)
      if mc_count!=1:raise SystemExit('Missing Minecraft dependency metadata')
      forge_range=profile['forgeRange']
      dependency=r'(?ms)(^\[\[dependencies\.shanghanearlyloading\]\]\s*modId="forge"\s*mandatory=true\s*versionRange=)"[^"]*"'
      text,count=re.subn(dependency,lambda m:m.group(1)+'"'+forge_range+'"',text)
      if count!=1:raise SystemExit('Missing or ambiguous Forge dependency metadata')
      metadata=tomllib.loads(text)
      forge_dependencies=[d for d in metadata['dependencies']['shanghanearlyloading'] if d['modId']=='forge']
      if len(forge_dependencies)!=1 or forge_dependencies[0]['versionRange']!=forge_range:raise SystemExit('Forge declaration differs from runtime profile')
      if metadata['mods'][0]['version']!=version:raise SystemExit('Mod version differs from runtime profile')
      data=text.encode('utf-8')
     z.writestr(entry,data)
  for legal in ['LICENSE','COMMERCIAL-USE.md']:
   z.write(root.parent/legal,'META-INF/'+legal)
  if component in ['boot','client']:
   entry=zipfile.ZipInfo('shanghan/'+('early' if component=='boot' else 'client')+'/runtime.json',date_time=(2026,1,1,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED;z.writestr(entry,json.dumps(profile,sort_keys=True).encode('utf-8'))
 print(name,out.stat().st_size,hashlib.file_digest(out.open('rb'),'sha256').hexdigest())
