from pathlib import Path

FORGE_COMPONENTS = {"forge", "fmlloader", "fmlearlydisplay", "fmlcore", "javafmllanguage", "lowcodelanguage", "mclanguage"}

def collect_libraries(installed, forge, forge_libraries=None):
    installed = Path(installed)
    all_jars = sorted(installed.rglob("*.jar"))
    target = "1.20.1-" + forge
    if forge_libraries:
        exact = sorted(Path(forge_libraries).glob("*.jar"))
        required = {"fmlloader", "fmlearlydisplay", "fmlcore", "javafmllanguage", "forge"}
        present = {f.name.split("-1.20.1-")[0] for f in exact if target in f.name}
        if not required.issubset(present):
            raise ValueError("Missing exact Forge components: " + str(required - present))
        exact = [f for f in exact if target in f.name]
        def old_component(f):
            try:
                parts = f.relative_to(installed).parts
                return len(parts) >= 4 and parts[:2] == ("net", "minecraftforge") and parts[2] in FORGE_COMPONENTS
            except ValueError:
                return False
        shared = [f for f in all_jars if not old_component(f)]
        return exact + sorted(shared, key=lambda f: ("1.20.1-20230612.114412-srg.jar" not in f.name, str(f)))
    preferred = [f for f in all_jars if target in str(f) or f.name.endswith("1.20.1-20230612.114412-srg.jar")]
    return preferred + [f for f in all_jars if f not in preferred]
