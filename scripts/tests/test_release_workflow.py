"""Exercise the actual release workflow's native toolchain setup contract."""
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]


class ReleaseWorkflowTest(unittest.TestCase):
    def setUp(self):
        self.workflow = (ROOT / ".github/workflows/release.yml").read_text(encoding="utf-8")

    def step(self, name):
        marker = f"      - name: {name}\n"
        self.assertTrue(marker in self.workflow, f"Release must provide {name} before compilation")
        return self.workflow.split(marker, 1)[1].split("      - name:", 1)[0]

    def test_android_setup_installs_the_native_builder_requirements(self):
        builder = (ROOT / "scripts/build_doh.py").read_text(encoding="utf-8")
        ndk = re.search(r'add_argument\("--ndk", default="([^"]+)"\)', builder).group(1)
        setup = self.step("Set up Android SDK")
        packages = re.search(r"packages: '([^']+)'", setup).group(1).split()
        for package in (f"ndk;{ndk}", "cmake;3.22.1", "platforms;android-36", "build-tools;35.0.0"):
            self.assertIn(package, packages)
        self.assertLess(self.workflow.index("name: Set up Android SDK"), self.workflow.index("name: Unit tests and Android Lint"))

    def test_rust_setup_installs_the_android_target_and_stops_on_install_failure(self):
        step = self.step("Set up Rust")
        version = re.search(r"RUST_VERSION: '([0-9]+\.[0-9]+\.[0-9]+)'", step).group(1)
        self.assertIn("        run: |\n", step)
        shell = "\n".join(line[10:] for line in step.split("        run: |\n", 1)[1].splitlines())
        bash = r"C:\Program Files\Git\bin\bash.exe" if os.name == "nt" else shutil.which("bash")
        if not bash or not Path(bash).is_file():
            self.skipTest("Bash is required for the workflow fixture")
        stub = '''set -euo pipefail
rustup() { printf 'rustup %s\\n' "$*" >> calls.txt; [[ "$SCENARIO" != failure || "$1" != toolchain ]]; }
rustc() { printf 'rustc %s\\n' "$*" >> calls.txt; }
cargo() { printf 'cargo %s\\n' "$*" >> calls.txt; }
'''
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "setup.sh").write_text(stub + shell + "\n", encoding="utf-8", newline="\n")
            for scenario in ("success", "failure"):
                with self.subTest(scenario=scenario):
                    calls = root / "calls.txt"
                    calls.unlink(missing_ok=True)
                    result = subprocess.run([bash, "setup.sh"], cwd=root, capture_output=True,
                        env={**os.environ, "SCENARIO": scenario, "RUST_VERSION": version})
                    recorded = calls.read_text(encoding="utf-8").splitlines()
                    install = f"rustup toolchain install {version} --profile minimal --target aarch64-linux-android"
                    self.assertEqual(scenario == "success", result.returncode == 0, result.stderr)
                    self.assertEqual(install, recorded[0])
                    if scenario == "success":
                        self.assertEqual([install, f"rustup default {version}", "rustc --version", "cargo --version"], recorded)
                    else:
                        self.assertEqual([install], recorded)

    def test_native_tests_use_the_lockfile_before_android_checks(self):
        native = self.step("Test native DoH")
        self.assertIn("cargo test --locked --manifest-path native/doh/Cargo.toml --lib", native)
        self.assertLess(self.workflow.index("name: Set up Rust"), self.workflow.index("name: Test native DoH"))
        self.assertLess(self.workflow.index("name: Test native DoH"), self.workflow.index("name: Unit tests and Android Lint"))


if __name__ == "__main__":
    unittest.main()
