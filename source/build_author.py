from pathlib import Path
import argparse,subprocess,zipfile,sys,json,uuid
sys.stdout.reconfigure(encoding="utf-8")
a=argparse.ArgumentParser();a.add_argument("--jdk",required=True);a.add_argument("--libraries",required=True);a.add_argument("--ffmpeg-dir",required=True);a.add_argument("--portable",action="store_true");args=a.parse_args()
project=Path(__file__).resolve().parent.parent;profile=json.loads((project/"release.json").read_text(encoding="utf-8"));version=profile["version"]
classes=project/"work"/("author-classes-"+uuid.uuid4().hex);classes.mkdir(parents=True,exist_ok=True)
gson=next(Path(args.libraries).rglob("gson-2.10.1.jar"));jdk=Path(args.jdk)
r=subprocess.run([str(jdk/"bin/javac.exe"),"-J-Dfile.encoding=UTF-8","-J-Duser.language=en","-encoding","UTF-8","--release","17","-classpath",str(gson),"-d",str(classes),str(project/"source/author/AuthorTool.java")],capture_output=True)
if r.returncode:raise SystemExit(r.stderr.decode("utf-8",errors="replace"))
jar=project/"dist"/f"ForgeLoadingAuthor-{version}.jar"
with zipfile.ZipFile(jar,"w",zipfile.ZIP_DEFLATED) as z:
 z.writestr("META-INF/MANIFEST.MF","Manifest-Version: 1.0\nMain-Class: AuthorTool\n\n")
 for f in sorted(classes.rglob("*.class")):z.write(f,f.relative_to(classes).as_posix())
 with zipfile.ZipFile(gson) as dependency:
  for n in dependency.namelist():
   if n.startswith("com/google/gson/") and not n.endswith("/"):z.writestr(n,dependency.read(n))
 z.writestr("runtime.json",json.dumps(profile).encode("utf-8"))
 for name in [f"ForgeLoadingFramework-bootstrap-{version}.jar",f"ForgeLoadingFramework-{version}.jar"]:z.write(project/"dist"/name,"payload/"+name)
 z.write(Path(args.ffmpeg_dir)/"bin/ffmpeg.exe","payload/ffmpeg.exe")
 for name in ["LICENSE","README.txt"]:z.write(Path(args.ffmpeg_dir)/name,"third_party/ffmpeg/"+name)
 z.write(project/"source/third_party/gson-LICENSE","third_party/gson/LICENSE")
 z.writestr("third_party/gson/NOTICE.txt","Google Gson 2.10.1, Apache License 2.0. https://github.com/google/gson\n")
print("BUILT",jar,jar.stat().st_size)
if args.portable:
 target=project/"dist/author-portable"/version;target.mkdir(parents=True,exist_ok=True)
 app=project/"work"/("author-package-"+uuid.uuid4().hex);app.mkdir(exist_ok=True)
 # Only the author tool belongs in its application classpath.
 (app/jar.name).write_bytes(jar.read_bytes())
 cmd=[str(jdk/"bin/jpackage.exe"),"--type","app-image","--name","ForgeLoadingAuthor","--app-version",version,"--input",str(app),"--dest",str(target),"--main-jar",jar.name,"--main-class","AuthorTool","--add-modules","java.desktop,java.logging,java.xml,java.naming","--java-options","-Dfile.encoding=UTF-8","--java-options","-Duser.home=$APPDIR"]
 r=subprocess.run(cmd,capture_output=True)
 if r.returncode:raise SystemExit(r.stderr.decode("utf-8",errors="replace"))
 print("PORTABLE",target/"ForgeLoadingAuthor")
