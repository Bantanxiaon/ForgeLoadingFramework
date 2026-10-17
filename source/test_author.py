from pathlib import Path
import argparse, subprocess, sys, uuid
sys.stdout.reconfigure(encoding="utf-8")
parser=argparse.ArgumentParser()
parser.add_argument("--jdk",required=True)
args=parser.parse_args()
project=Path(__file__).resolve().parent.parent
work=project/"work"/("author-test-"+uuid.uuid4().hex)
classes=work/"classes";classes.mkdir(parents=True)
jar=project/"dist/ForgeLoadingAuthor-0.4.1.jar"
def run(command):
    result=subprocess.run([str(x) for x in command],capture_output=True)
    print(result.stdout.decode("utf-8",errors="replace"),end="")
    if result.returncode:
        print(result.stderr.decode("utf-8",errors="replace"),end="")
        raise SystemExit(result.returncode)
jdk=Path(args.jdk)/"bin"
run([jdk/"javac.exe","-encoding","UTF-8","--release","17","-classpath",jar,"-d",classes,project/"source/author/tests/AuthorDiscoverySmoke.java"])
run([jdk/"java.exe","-Dfile.encoding=UTF-8","-Djava.awt.headless=true","-classpath",str(classes)+";"+str(jar),"AuthorDiscoverySmoke",work/"fixtures"])
print("TEST_WORK",work)
