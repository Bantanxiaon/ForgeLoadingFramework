"""Build a complete Windows author bundle, retaining the published runtime Jars."""
from pathlib import Path
import argparse
import hashlib
import io
import json
import os
import shutil
import subprocess
import sys
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parent.parent
FFMPEG_URL = "https://www.gyan.dev/ffmpeg/builds/packages/ffmpeg-9.0.2-essentials_build.zip"
FFMPEG_SHA256 = "60f467265b1e312373dbcd92200c2618a74850f98d3d078e94296bb3fa2047ba"
GSON_URL = "https://repo.maven.apache.org/maven2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar"
GSON_SHA256 = "4241c14a7727c34feea6507ec801318a3d4a90f070e4525681079fb94ee4c593"
RUNTIME_HASHES = {
    "ForgeLoadingFramework-bootstrap-0.4.0.jar": "61485eec996afcc09c7bb534a97d523025b7a2853d3aa8fc46a0f661ad58901a",
    "ForgeLoadingFramework-0.4.0.jar": "b258e291626e0e79f352a602012eb6ebcd40aca7d9c9e84a434726a7c1615e56",
}

def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()

def download(url, path, expected):
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists() and sha256(path) == expected:
        return
    with urllib.request.urlopen(url, timeout=60) as response, path.open("wb") as output:
        total = 0
        while block := response.read(1024 * 1024):
            total += len(block)
            if total > 200_000_000:
                raise RuntimeError("Dependency exceeds download limit")
            output.write(block)
    if sha256(path) != expected:
        raise RuntimeError("Dependency checksum mismatch: " + path.name)

def run(command, **kwargs):
    subprocess.run([str(arg) for arg in command], check=True, cwd=ROOT, **kwargs)

def source_files():
    for name in ("README.md", "INSTALL.md", "CHANGELOG.md", "LICENSE", "release.json", "author-release.json", ".gitignore"):
        path = ROOT / name
        if path.is_file():
            yield path
    for folder in ("source", "docs", ".github"):
        for path in sorted((ROOT / folder).rglob("*")):
            relative = path.relative_to(ROOT)
            if (not path.is_file() or any(part in {"dependencies", "__pycache__", ".git"} for part in relative.parts)
                    or path.suffix in {".log", ".pyc", ".args"}
                    or path.name == "TEST_REPORT.md" or path.name.startswith("PCL-matrix")):
                continue
            yield path

def verify_bundle(path, runtime_hashes):
    with zipfile.ZipFile(path) as archive:
        if archive.testzip() is not None:
            raise RuntimeError("Corrupt bundle")
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise RuntimeError("Duplicate ZIP entry")
        if any("TEST_REPORT" in name or "PCL-matrix" in name or name.endswith(".log") for name in names):
            raise RuntimeError("Reports or logs in public bundle")
        if archive.read("README.md") != (ROOT / "README.md").read_bytes():
            raise RuntimeError("README differs")
        if archive.read("docs/作者使用教程.md") != (ROOT / "docs/作者使用教程.md").read_bytes():
            raise RuntimeError("Tutorial differs")
        for name, expected in runtime_hashes.items():
            if hashlib.sha256(archive.read("可复制到实例/mods/" + name)).hexdigest() != expected:
                raise RuntimeError("Runtime differs: " + name)
        nested = next(name for name in names if name.endswith("-source.zip"))
        with zipfile.ZipFile(io.BytesIO(archive.read(nested))) as source:
            if any("TEST_REPORT" in name or "PCL-matrix" in name for name in source.namelist()):
                raise RuntimeError("Reports in source ZIP")

def existing_release(repository, version, dist):
    """Keep published bytes intact; repair a filename normalized by GitHub."""
    result = subprocess.run(
        ["gh", "api", "repos/" + repository + "/releases/tags/v" + version],
        capture_output=True, encoding="utf-8", errors="replace")
    if result.returncode:
        if "404" in result.stderr:
            return False
        raise RuntimeError("Cannot inspect existing release: " + result.stderr)
    release = json.loads(result.stdout)
    expected = "ForgeLoadingFramework-" + version + "-Windows-Complete.zip"
    renamed = False
    for asset in release["assets"]:
        if (asset["name"].startswith("ForgeLoadingFramework-" + version + "-")
                and asset["name"].endswith(".zip")
                and not asset["name"].endswith("-source.zip") and asset["size"] > 1_000_000):
            if asset["name"] != expected:
                run(["gh", "api", "--method", "PATCH",
                     "repos/" + repository + "/releases/assets/" + str(asset["id"]),
                     "-f", "name=" + expected, "-f", "label=完整工具包（Windows）"],
                    stdout=subprocess.DEVNULL)
                asset["name"] = expected
                renamed = True
    if renamed:
        # A new checksum file uses the downloadable filenames; preserve the original manifest.
        checksums = dist / "Download-SHA256.txt"
        lines = []
        for asset in release["assets"]:
            if asset["name"].endswith(".zip"):
                digest = asset.get("digest", "")
                if not digest.startswith("sha256:"):
                    raise RuntimeError("Missing server-side asset digest")
                lines.append(digest.removeprefix("sha256:") + "  " + asset["name"] + "\n")
        checksums.write_text("".join(lines), encoding="utf-8")
        run(["gh", "release", "upload", "v" + version, checksums, "--repo", repository])
        for asset in release["assets"]:
            if asset["name"] == "SHA256SUMS.txt":
                run(["gh", "api", "--method", "PATCH",
                     "repos/" + repository + "/releases/assets/" + str(asset["id"]),
                     "-f", "label=原始构建校验（下载文件请使用 Download-SHA256.txt）"],
                    stdout=subprocess.DEVNULL)
    print("RELEASE_EXISTS_OK", release["html_url"])
    return True

def main():
    sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jdk", required=True)
    parser.add_argument("--ffmpeg-dir", type=Path)
    parser.add_argument("--author-dir", type=Path, help="Existing portable author directory when using --reuse-author")
    parser.add_argument("--reuse-author", action="store_true", help="Package an already built author tool")
    parser.add_argument("--publish", action="store_true", help="Publish from an authenticated GitHub Actions run")
    args = parser.parse_args()
    version = json.loads((ROOT / "author-release.json").read_text(encoding="utf-8"))["version"]
    runtime = json.loads((ROOT / "release.json").read_text(encoding="utf-8"))["version"]
    if runtime != "0.4.0":
        raise RuntimeError("Update the reviewed runtime hashes before releasing another runtime")
    dist = ROOT / "dist"
    work = ROOT / "work/release-bundle"
    dist.mkdir(exist_ok=True)
    if args.publish and existing_release(os.environ["GITHUB_REPOSITORY"], version, dist):
        return
    work.mkdir(parents=True, exist_ok=True)
    for name, expected in RUNTIME_HASHES.items():
        artifact = dist / name
        if not artifact.exists():
            shutil.copy2(ROOT / "downloads" / name, artifact)
        if sha256(artifact) != expected:
            raise RuntimeError("Published runtime checksum mismatch: " + name)
    ffmpeg_dir = args.ffmpeg_dir
    if ffmpeg_dir is None:
        dependency = work / "ffmpeg.zip"
        download(FFMPEG_URL, dependency, FFMPEG_SHA256)
        ffmpeg_dir = work / "ffmpeg"
        with zipfile.ZipFile(dependency) as archive:
            for wanted in ("bin/ffmpeg.exe", "bin/ffprobe.exe", "LICENSE", "README.txt"):
                matches = [name for name in archive.namelist() if name.endswith("/" + wanted)]
                if len(matches) != 1:
                    raise RuntimeError("Unexpected FFmpeg archive layout")
                target = ffmpeg_dir / wanted
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(archive.read(matches[0]))
    ffmpeg = ffmpeg_dir / "bin/ffmpeg.exe"
    portable = args.author_dir or dist / "author-portable" / version / "ForgeLoadingAuthor"
    if not args.reuse_author:
        libraries = work / "libraries"
        download(GSON_URL, libraries / "gson-2.10.1.jar", GSON_SHA256)
        run([sys.executable, "-B", ROOT / "source/build_author.py",
             "--jdk", args.jdk, "--libraries", libraries, "--ffmpeg-dir", ffmpeg_dir, "--portable"])
        run([sys.executable, "-B", ROOT / "source/test_author.py", "--jdk", args.jdk])
    tool_jar = portable / "app" / ("ForgeLoadingAuthor-" + version + ".jar")
    with zipfile.ZipFile(tool_jar) as tool:
        if tool.read("author-version.txt").decode().strip() != version:
            raise RuntimeError("Wrong author version")
        for name, expected in RUNTIME_HASHES.items():
            if hashlib.sha256(tool.read("payload/" + name)).hexdigest() != expected:
                raise RuntimeError("Wrong embedded runtime")
    demo = work / "demo.mp4"
    run([ffmpeg, "-nostdin", "-y", "-v", "error", "-f", "lavfi", "-i",
         "color=c=0x14251e:s=640x360:r=30:d=5", "-f", "lavfi", "-i",
         "anullsrc=r=48000:cl=stereo", "-vf",
         "drawgrid=w=80:h=80:t=1:c=0xa9ddae@0.12,hue=h=12*t",
         "-t", "5", "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac",
         "-movflags", "+faststart", demo])
    source = dist / ("ForgeLoadingFramework-" + version + "-source.zip")
    bundle = dist / ("ForgeLoadingFramework-" + version + "-Windows-Complete.zip")
    # Refuse to overwrite a previously delivered bundle. Move it aside or recycle it after review.
    if bundle.exists():
        raise RuntimeError("Bundle already exists; preserve it before rebuilding")
    with zipfile.ZipFile(source, "w", zipfile.ZIP_DEFLATED) as archive:
        for path in source_files():
            archive.write(path, path.relative_to(ROOT).as_posix())
    with zipfile.ZipFile(bundle, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for name in ("README.md", "INSTALL.md", "CHANGELOG.md", "LICENSE", "docs/作者使用教程.md"):
            archive.write(ROOT / name, name)
        archive.write(source, source.name)
        for path in sorted(portable.rglob("*")):
            if path.is_file():
                archive.write(path, "作者工具/" + path.relative_to(portable).as_posix())
        for name in RUNTIME_HASHES:
            archive.write(dist / name, "可复制到实例/mods/" + name)
        prefix = "可复制到实例/loading/"
        archive.write(ROOT / "source/examples/loading/config.json", prefix + "config.json")
        archive.write(demo, prefix + "video/loading.mp4")
        for original, name in (("bin/ffmpeg.exe", "ffmpeg.exe"), ("LICENSE", "FFmpeg-LICENSE"), ("README.txt", "FFmpeg-README.txt")):
            archive.write(ffmpeg_dir / original, prefix + "bin/" + name)
        archive.writestr("fml配置合并说明.txt", '在实例 config/fml.toml 合并以下设置，保留其他设置：\nearlyWindowControl = true\nearlyWindowProvider = "shanghanearlyloading"\n')
        archive.writestr("接入说明.txt", "先阅读 README.md 或 docs/作者使用教程.md。\n自动安装：打开作者工具/ForgeLoadingAuthor.exe，选择已安装实例与自己的视频。\n手动安装：合并“可复制到实例”中的文件，按说明合并 fml.toml。\n")
        archive.writestr("素材使用说明.txt", "本包使用程序生成的演示视频和静音AAC音轨，不包含原整合包视频或图片。请替换成自己的素材。\nFFmpeg和便携Java的许可随文件提供，项目MIT许可不替代第三方许可。\n")
    verify_bundle(bundle, RUNTIME_HASHES)
    sums = dist / "SHA256SUMS.txt"
    artifacts = [bundle, source]
    sums.write_text("".join(sha256(path) + "  " + path.name + "\n" for path in artifacts), encoding="utf-8")
    print("BUNDLE_OK", bundle.name, bundle.stat().st_size, sha256(bundle))
    if args.publish:
        repository = os.environ["GITHUB_REPOSITORY"]
        commit = os.environ["GITHUB_SHA"]
        notes = ROOT / "docs/releases" / (version + ".md")
        # gh creates the tag at this exact commit and attaches the validated complete ZIP.
        run(["gh", "release", "create", "v" + version, *artifacts, sums,
             "--repo", repository, "--target", commit, "--title",
             "Forge Loading Framework " + version, "--notes-file", notes])

if __name__ == "__main__":
    main()
