from pathlib import Path
import argparse, hashlib, json, os, subprocess, sys, uuid, zipfile
sys.stdout.reconfigure(encoding="utf-8")
parser=argparse.ArgumentParser()
parser.add_argument("--jdk",required=True)
args=parser.parse_args()
project=Path(__file__).resolve().parent.parent
profile=json.loads((project/"author-release.json").read_text(encoding="utf-8"))
runtime=json.loads((project/"release.json").read_text(encoding="utf-8"))["version"]
if profile["runtimeVersion"]!=runtime:raise SystemExit("Author/runtime version mismatch")
version=profile["version"]
work=project/"work"/("author-test-"+uuid.uuid4().hex)
classes=work/"classes";classes.mkdir(parents=True)
temporary=work/"tmp";temporary.mkdir()
environment=dict(os.environ);environment.update({key:str(temporary) for key in ["TEMP","TMP","TMPDIR"]})
jar=project/"dist"/("ForgeLoadingAuthor-"+version+".jar")
if not jar.is_file():raise SystemExit("Build current author tool first: "+jar.name)
with zipfile.ZipFile(jar) as archive:
    if archive.read("author-version.txt").decode().strip()!=version:raise SystemExit("Wrong author version in Jar")
    for name in ["ForgeLoadingFramework-bootstrap-"+runtime+".jar","ForgeLoadingFramework-"+runtime+".jar"]:
        expected=(project/"dist"/name).read_bytes()
        if archive.read("payload/"+name)!=expected:raise SystemExit("Embedded runtime differs: "+name)
print("AUTHOR_UNDER_TEST",jar.name,hashlib.sha256(jar.read_bytes()).hexdigest())
def run(command):
    result=subprocess.run([str(x) for x in command],capture_output=True,env=environment)
    output=result.stdout.decode("utf-8",errors="replace")+result.stderr.decode("utf-8",errors="replace")
    print(output,end="")
    if result.returncode:raise SystemExit(result.returncode)
jdk=Path(args.jdk)/"bin"
run([jdk/"javac.exe","-J-Dfile.encoding=UTF-8","-J-Duser.language=en","-encoding","UTF-8","--release","17","-classpath",jar,"-d",classes,project/"source/author/tests/AuthorDiscoverySmoke.java"])
run([jdk/"java.exe","-Dfile.encoding=UTF-8","-Djava.awt.headless=true","-Djava.io.tmpdir="+str(temporary),"-Dauthor.expectedToolVersion="+version,"-Dauthor.expectedRuntimeVersion="+runtime,"-classpath",str(classes)+";"+str(jar),"AuthorDiscoverySmoke",work/"fixtures"])
print("TEST_WORK",work)
