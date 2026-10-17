"""Run packaged regressions with isolated legacy and modern graphics SDKs.

The default suite uses Java 17 / LWJGL 3.3.1 for legacy graphics, and a separate
SDL SDK classpath for modern API/fake-native checks. --modern-sdk-check checks
the actual Forge 66 ABI with Java 25. --modern-native additionally opens only
the test's own SDL windows and tests real decoder/native fade cleanup.
All generated files, subprocess temp files and native extraction stay on D:.
"""
from pathlib import Path
import argparse
import hashlib
import json
import os
import subprocess
import sys
import tempfile

sys.stdout.reconfigure(encoding="utf-8")
root = Path(__file__).resolve().parent
project = root.parent
profile = json.loads((project / "release.json").read_text(encoding="utf-8"))
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--forge", default="47.3.22")
parser.add_argument("--forge-libraries")
parser.add_argument("--libraries", default="D:/.minecraft/libraries")
parser.add_argument("--jdk", default="D:/workspace/ShangHanEarlyLoading/tools/jdk17/jdk-17.0.15+6")
parser.add_argument("--modern-jdk", default="D:/workspace/ShangHanEarlyLoading/tools/jdk25/jdk-25.0.4.1+1")
parser.add_argument("--modern-sdk", default=str(root / "adapters/modern/dependencies"))
parser.add_argument("--legacy-lwjgl-version", default="3.3.1")
parser.add_argument("--modern-sdk-check", action="store_true", help="Check the actual Forge 66 ABI on Java 25 without opening a window")
parser.add_argument("--modern-native", action="store_true", help="Explicitly run owned SDL/OpenGL windows and decoder cleanup on Java 25")
parser.add_argument("--ffmpeg", default="D:/workspace/ShangHanEarlyLoading/tools/ffmpeg/ffmpeg-9.0.2-essentials_build/bin/ffmpeg.exe")
parser.add_argument("--non-gl", action="store_true")
parser.add_argument("--only", nargs="+")
parser.add_argument("--video", default="D:/workspace/ShangHanEarlyLoading/assets/loading-h264-360p30.mp4")
args = parser.parse_args()
if args.modern_native and args.non_gl:
    parser.error("--modern-native and --non-gl cannot be used together")

work = project / "work/tests"
classes = work / "classes/legacy"
modern_classes = work / "classes/modern"
audit = project / "audit"
for folder in (work, classes, modern_classes, audit, work / "tmp"):
    if folder.resolve().drive.upper() != "D:":
        raise SystemExit("Generated test files must remain on D: " + str(folder))
    folder.mkdir(parents=True, exist_ok=True)
environment = dict(os.environ)
environment.update({key: str(work / "tmp") for key in ("TEMP", "TMP", "TMPDIR")})

sys.path.insert(0, str(root))
from library_paths import collect_libraries

installed = Path(args.libraries).resolve()
collected = collect_libraries(installed, args.forge, args.forge_libraries)

def is_lwjgl(path):
    try:
        return path.resolve().relative_to(installed).parts[:2] == ("org", "lwjgl")
    except ValueError:
        return False

def windows_x64_jar(path):
    return "-natives-" not in path.name or path.name.endswith("-natives-windows.jar")

legacy = [
    path for path in collected
    if not is_lwjgl(path)
    or (path.parent.name == args.legacy_lwjgl_version and windows_x64_jar(path))
]
if not any(path.name == "lwjgl-" + args.legacy_lwjgl_version + ".jar" for path in legacy):
    raise SystemExit("Missing legacy LWJGL " + args.legacy_lwjgl_version)
sdk_dir = Path(args.modern_sdk).resolve()
sdl_compile = [
    sdk_dir / name for name in (
        "lwjgl-3.4.3.jar", "lwjgl-opengl-3.4.3.jar", "lwjgl-sdl-3.4.3.jar", "lwjgl-stb-3.4.3.jar"
    )
]
for jar in sdl_compile:
    if not jar.is_file():
        raise SystemExit("Missing SDL SDK: " + str(jar))
version = profile["version"]
artifacts = [
    project / "dist" / ("ForgeLoadingFramework-bootstrap-" + version + ".jar"),
    project / "dist" / ("ForgeLoadingFramework-" + version + ".jar"),
]
for artifact in artifacts:
    if not artifact.is_file():
        raise SystemExit("Build current release before testing: " + str(artifact))
shared = [path for path in legacy if not is_lwjgl(path)]
sdk_no_native = [path for path in sdk_dir.glob("*.jar") if "-natives-" not in path.name and not path.name.endswith("-sources.jar") and not path.name.startswith("fmlloader-26.2-")]
sdk_native = [
    path for path in sdk_dir.glob("*.jar")
    if "-natives-" in path.name and path.name.endswith("-natives-windows.jar")
]
legacy_cp = artifacts + legacy
# Modern no-native checks still link the old public SPI on Java 17. The separate
# Forge 66 check below places its actual SDK first and uses Java 25.
modern_api_cp = artifacts + sdl_compile + shared
modern_forge_cp = artifacts + sorted(sdk_no_native) + shared
# The owned-window test exercises media/graphics, not a complete Forge launch.
# Keep its loader dependencies coherent with the legacy shared MediaSession ABI;
# the actual Forge 66 SPI is checked independently by --modern-sdk-check.
modern_native_cp = artifacts + sdl_compile + sorted(sdk_native) + shared
compile_cp = artifacts + sdl_compile + legacy

def classpath(paths):
    return ";".join(str(path).replace("\\", "/") for path in dict.fromkeys(paths))

def argfile(name, values):
    path = work / (name + ".args")
    path.write_text(
        "\n".join('"' + str(value).replace("\\", "/").replace('"', '\\"') + '"' for value in values),
        encoding="utf-8",
    )
    return "@" + str(path)

def execute(command, timeout):
    return subprocess.run(
        command, capture_output=True, timeout=timeout, env=environment, cwd=project
    )

def decoded(result):
    return result.stdout.decode("utf-8", errors="replace") + result.stderr.decode("utf-8", errors="replace")

def compile_tests(name, sources, output, dependencies):
    compile_args = [
        "-J-Djava.io.tmpdir=" + str(work / "tmp"), "-encoding", "UTF-8", "-proc:none",
        "--release", "17", "-classpath", classpath(dependencies), "-d", output,
    ] + sources
    compiled = execute([str(Path(args.jdk) / "bin/javac.exe"), argfile(name, compile_args)], 60)
    if compiled.returncode:
        raise SystemExit(decoded(compiled))

all_sources = sorted((root / "tests").glob("*.java"))
modern_names = {"ModernSdlApiSmoke", "ModernSdlNativeSmoke", "ModernNativeFadeSmoke", "ModernFacadeAbiSmoke"}
# Compiling legacy tests against the modern core can select a newly introduced
# overload that is absent in 3.3.1, even when the old source code is unchanged.
compile_tests("javac-legacy", [path for path in all_sources if path.stem not in modern_names],
              classes, legacy_cp)
compile_tests("javac-modern", [path for path in all_sources if path.stem in modern_names],
              modern_classes, compile_cp)

suite = [
    "RuntimeProfileSmoke", "RuntimeRouterSmoke", "ModMetadataSmoke", "ModuleServiceSmoke",
    "BootArgumentsSmoke", "UniversalOverlaySmoke", "OverlayCreationFallbackSmoke", "TotalProgressSmoke", "RenderCompletionSmoke",
    "WindowEmbeddingSmoke", "AudioClockSmoke", "PackNameSmoke", "RendererRetentionSmoke",
    "OpaqueTransitionSmoke", "MediaFailureSmoke", "ThemeConfigSmoke", "ProgressTaskSmoke",
    "StreamPlaybackSmoke", "ModernSdlApiSmoke", "ModernNativeFadeSmoke",
]
if args.non_gl:
    suite = [name for name in suite if name not in {
        "WindowEmbeddingSmoke", "RendererRetentionSmoke", "OpaqueTransitionSmoke"
    }]
if args.modern_sdk_check or args.modern_native:
    suite.extend(["ModernSdlApiSmoke-forge66", "ModernFacadeAbiSmoke"])
if args.modern_native:
    suite.append("ModernSdlNativeSmoke")
if args.only:
    unknown = set(args.only) - set(suite)
    if unknown:
        parser.error("Unknown or disabled tests: " + ", ".join(sorted(unknown)))
    suite = [name for name in suite if name in args.only]

summary = {
    "release": version,
    "forge": args.forge,
    "legacyLwjgl": args.legacy_lwjgl_version,
    "artifacts": {path.name: hashlib.file_digest(path.open("rb"), "sha256").hexdigest() for path in artifacts},
    "modernNativeExplicitlyEnabled": args.modern_native,
    "tests": [],
}
for name in suite:
    fixture = Path(tempfile.mkdtemp(prefix=name + "-", dir=work))
    for directory in ("tmp", "native/lwjgl", "native/jna"):
        (fixture / directory).mkdir(parents=True, exist_ok=True)
    params = [str(fixture)]
    runtime_jdk, runtime_cp, actual_name = Path(args.jdk), legacy_cp, name
    runtime_classes = modern_classes if name.startswith("Modern") else classes
    if name in {"ModernSdlApiSmoke", "ModernNativeFadeSmoke"}:
        runtime_cp = modern_api_cp
    if name in {"ModernSdlApiSmoke-forge66", "ModernFacadeAbiSmoke"}:
        runtime_jdk, runtime_cp = Path(args.modern_jdk), modern_forge_cp
        actual_name = "ModernSdlApiSmoke" if name.endswith("-forge66") else name
    if name == "ModernSdlNativeSmoke":
        runtime_jdk, runtime_cp = Path(args.modern_jdk), modern_native_cp
        params += [args.ffmpeg, args.video]
    if name == "MediaFailureSmoke":
        params += [args.ffmpeg, args.video]
    if name == "StreamPlaybackSmoke":
        sample = fixture / "short-source.mp4"
        generated = execute([
            args.ffmpeg, "-nostdin", "-y", "-hide_banner", "-loglevel", "error",
            "-f", "lavfi", "-i", "testsrc2=size=640x360:rate=30",
            "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=48000", "-t", "2",
            "-c:v", "libx264", "-threads", "1", "-preset", "ultrafast", "-pix_fmt", "yuv420p",
            "-colorspace", "bt709", "-c:a", "aac", "-ac", "2", "-ar", "48000", str(sample),
        ], 15)
        if generated.returncode:
            raise SystemExit(decoded(generated))
        params += [args.ffmpeg, str(sample)]
    java = [
        "-Dsmoke.exactForge=" + args.forge, "-Djava.awt.headless=true",
        "-Djava.io.tmpdir=" + str(fixture / "tmp"),
        "-Djna.tmpdir=" + str(fixture / "native/jna"),
        "-Dorg.lwjgl.system.SharedLibraryExtractPath=" + str(fixture / "native/lwjgl"),
        "-Dlog4j.configurationFile=" + (root / "tests/log4j-test.xml").as_uri(),
    ]
    if runtime_jdk == Path(args.modern_jdk):
        java += ["--enable-native-access=ALL-UNNAMED"]
    java += ["-classpath", classpath([runtime_classes] + runtime_cp), actual_name] + params
    result = execute([str(runtime_jdk / "bin/java.exe"), argfile(name, java)], 45)
    report = decoded(result)
    (audit / (name + ".txt")).write_text(report, encoding="utf-8")
    entry = {"test": name, "status": "PASS" if result.returncode == 0 else "FAIL",
             "jdk": str(runtime_jdk), "fixture": str(fixture),
             "graphicsSdk": "LWJGL 3.4.3" if name.startswith("Modern") else "LWJGL " + args.legacy_lwjgl_version,
             "forgeAbi": "66.0.12" if name in {"ModernSdlApiSmoke-forge66", "ModernFacadeAbiSmoke"} else args.forge}
    summary["tests"].append(entry)
    (audit / "TestRunSummary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    print("TEST", name, entry["status"], "JDK", runtime_jdk.name, entry["graphicsSdk"], flush=True)
    print(report, flush=True)
    if result.returncode:
        raise SystemExit(name + " failed; see audit/" + name + ".txt")
print("PASS", len(summary["tests"]), "packaged regressions; temporary/native output confined to", work)
