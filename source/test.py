from pathlib import Path
import argparse,subprocess,sys,tempfile,json
sys.stdout.reconfigure(encoding="utf-8")
a=argparse.ArgumentParser();a.add_argument("--libraries",required=True);a.add_argument("--jdk",required=True);a.add_argument("--ffmpeg",required=True);a.add_argument("--non-gl",action="store_true");a.add_argument("--video",required=True);args=a.parse_args()
root=Path(__file__).resolve().parent;project=root.parent;work=project/"work/tests";classes=work/"classes";classes.mkdir(parents=True,exist_ok=True);(project/"audit").mkdir(exist_ok=True)
jars=sorted(Path(args.libraries).rglob("*.jar"));jars=sorted(jars,key=lambda f:("1.20.1-47.3.22" not in str(f),str(f)))
cp=";".join(str(x).replace("\\","/") for x in [project/("dist/ForgeLoadingFramework-bootstrap-"+json.loads((project/"release.json").read_text(encoding="utf-8"))["version"]+".jar")]+jars)
def argfile(name,values):
 f=work/(name+".args");f.write_text("\n".join('"'+str(x).replace("\\","/").replace('"','\\"')+'"' for x in values),encoding="utf-8");return "@"+str(f)
r=subprocess.run([str(Path(args.jdk)/"bin/javac.exe"),argfile("javac",["-encoding","UTF-8","-proc:none","--release","17","-classpath",cp,"-d",classes]+list((root/"tests").glob("*.java")))],capture_output=True)
if r.returncode:raise SystemExit(r.stderr.decode("utf-8",errors="replace"))
for name in (["AudioClockSmoke","PackNameSmoke","MediaFailureSmoke","ThemeConfigSmoke","ProgressTaskSmoke","StreamPlaybackSmoke"] if args.non_gl else ["WindowEmbeddingSmoke","AudioClockSmoke","PackNameSmoke","RendererRetentionSmoke","OpaqueTransitionSmoke","MediaFailureSmoke","ThemeConfigSmoke","ProgressTaskSmoke","StreamPlaybackSmoke"]):
 fixture=Path(tempfile.mkdtemp(prefix=name+"-",dir=work))
 params=[str(fixture)]
 if name=="MediaFailureSmoke":params+=[args.ffmpeg,args.video]
 if name=="StreamPlaybackSmoke":
  sample=fixture/"short-source.mp4"
  make=subprocess.run([args.ffmpeg,"-nostdin","-y","-hide_banner","-loglevel","error","-f","lavfi","-i","testsrc2=size=640x360:rate=30","-f","lavfi","-i","sine=frequency=440:sample_rate=48000","-t","2","-c:v","libx264","-threads","1","-preset","ultrafast","-pix_fmt","yuv420p","-colorspace","bt709","-c:a","aac","-ac","2","-ar","48000",str(sample)],capture_output=True,timeout=15)
  if make.returncode:raise SystemExit(make.stderr.decode("utf-8",errors="replace"))
  params+=[args.ffmpeg,str(sample)]
 java=["-Djava.awt.headless=true","-Dlog4j.configurationFile="+(root/"tests/log4j-test.xml").as_uri(),"-classpath",str(classes)+";"+cp,name]+params
 r=subprocess.run([str(Path(args.jdk)/"bin/java.exe"),argfile(name,java)],capture_output=True,timeout=30)
 report=r.stdout.decode("utf-8",errors="replace")+r.stderr.decode("utf-8",errors="replace")
 (project/"audit"/(name+".txt")).write_text(report,encoding="utf-8");print(report)
 if r.returncode:raise SystemExit(name+" failed")
