from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import hashlib
import os
import shutil

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from release_notes import generate_notes, previous_tag, render_notes


class ReleaseNotesTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.git("init", "--initial-branch=main")
        self.git("config", "user.name", "Fixture")
        self.git("config", "user.email", "fixture@example.invalid")

    def tearDown(self):
        self.temporary.cleanup()

    def git(self, *args):
        return subprocess.run(["git", *args], cwd=self.root, capture_output=True, check=True, encoding="utf-8").stdout

    def commit(self, subject):
        self.git("-c", "commit.gpgsign=false", "commit", "--allow-empty", "-m", subject)

    def test_first_release_includes_history_and_groups_deduplicated_subjects(self):
        self.commit("支持原生帖子")
        self.commit("fix(ui): 修复头像角标")
        self.commit("调整设置间距")
        self.commit("docs: 更新说明")
        self.commit("支持原生帖子")
        self.git("tag", "v0.1.0")
        notes = generate_notes("v0.1.0", "owner/repo", self.root)
        for heading in ("新增", "修复", "调整", "维护"):
            self.assertIn(f"## {heading}", notes)
        self.assertEqual(1, notes.count("- 支持原生帖子"))
        self.assertIn("- 修复头像角标", notes)
        self.assertNotIn("fix(ui):", notes)
        self.assertIn("/commits/v0.1.0", notes)

    def test_incremental_range_chooses_numeric_ancestor_and_ignores_other_branches(self):
        self.commit("旧版本内容")
        self.git("tag", "v0.9.0")
        self.git("tag", "not-a-release")
        self.git("checkout", "-b", "other")
        self.commit("其他分支")
        self.git("tag", "v0.99.0")
        self.git("checkout", "main")
        self.commit("旧的小版本")
        self.git("tag", "v0.10.0")
        self.commit("feat!: 新版本能力")
        self.git("tag", "v1.0.0")
        self.assertEqual("v0.10.0", previous_tag("v1.0.0", self.root))
        notes = generate_notes("v1.0.0", "owner/repo", self.root)
        self.assertIn("新版本能力", notes)
        self.assertNotIn("旧版本内容", notes)
        self.assertNotIn("其他分支", notes)
        self.assertIn("/compare/v0.10.0...v1.0.0", notes)

    def test_merged_subjects_empty_changes_and_invalid_parameters(self):
        notes = render_notes("v1.0.0", ["Merge branch 'feature'", "", "  "], "owner/repo", "v0.9.0")
        self.assertIn("本版本没有新增提交", notes)
        for repository in ("../unsafe", "https://github.com/a/b", "a/b\n# injected"):
            with self.assertRaises(ValueError):
                render_notes("v1.0.0", [], repository, None)
        with self.assertRaises(ValueError):
            generate_notes("v1.0.0;unsafe", "owner/repo", self.root)
        with self.assertRaises(ValueError):
            generate_notes("v1.0.0", "owner/repo", self.root)

    def test_merge_commit_is_excluded_but_feature_is_kept(self):
        self.commit("初始提交")
        self.git("tag", "v0.1.0")
        self.git("checkout", "-b", "feature")
        self.commit("新增检查更新")
        self.git("checkout", "main")
        self.git("-c", "commit.gpgsign=false", "merge", "--no-ff", "feature", "-m", "合并功能")
        self.git("tag", "v0.2.0")
        notes = generate_notes("v0.2.0", "owner/repo", self.root)
        self.assertIn("新增检查更新", notes)
        self.assertNotIn("合并功能", notes)

    def test_actual_workflow_writes_notes_for_new_and_retried_drafts_before_publication(self):
        bash = str(Path(r"C:\Program Files\Git\bin\bash.exe")) if os.name == "nt" else shutil.which("bash")
        if not bash or not Path(bash).is_file():
            self.skipTest("Bash is required for the workflow shell fixture")
        workflow = (Path(__file__).resolve().parents[2] / ".github/workflows/release.yml").read_text(encoding="utf-8")
        block = workflow.split("      - name: Publish GitHub Release\n", 1)[1].split("        run: |\n", 1)[1]
        shell = "\n".join(line[10:] for line in block.splitlines())
        stub = '''set -euo pipefail
gh() {
  printf '%s\\n' "$*" >> calls.txt
  case "$1 $2" in
    'release view')
      case "$SCENARIO" in
        new|upload-failure) return 1 ;;
        published) printf 'false\\n' ;;
        *) printf 'true\\n' ;;
      esac ;;
    'release create') [[ "$*" == *'--notes-file release-notes.md'* ]] && [[ -s release-notes.md ]] ;;
    'release upload') [[ "$SCENARIO" != 'upload-failure' ]] ;;
    'release edit')
      if [[ "$*" == *'--notes-file'* ]]; then [[ -s release-notes.md ]]; fi ;;
  esac
}
'''
        (self.root / "publish.sh").write_text(stub + shell + "\n", encoding="utf-8", newline="\n")
        (self.root / "release-notes.md").write_text("## 新增\n\n- 检查更新\n", encoding="utf-8")
        apk = self.root / "fixture.apk"
        apk.write_bytes(b"verified APK fixture")
        for scenario in ("new", "draft", "published", "upload-failure", "checksum-failure"):
            with self.subTest(scenario=scenario):
                calls = self.root / "calls.txt"
                calls.unlink(missing_ok=True)
                checksum = "0" * 64 if scenario == "checksum-failure" else hashlib.sha256(apk.read_bytes()).hexdigest()
                (self.root / "fixture.apk.sha256").write_text(f"{checksum}  fixture.apk\n", encoding="ascii", newline="\n")
                result = subprocess.run([bash, "publish.sh"], cwd=self.root, capture_output=True,
                    env={**os.environ, "SCENARIO": scenario, "APK_NAME": "fixture.apk", "RELEASE_TAG": "v1.2.3", "VERSION_NAME": "1.2.3"})
                recorded = calls.read_text(encoding="utf-8") if calls.exists() else ""
                self.assertEqual(scenario in ("new", "draft"), result.returncode == 0, result.stderr)
                self.assertNotIn("--generate-notes", recorded)
                if scenario in ("new", "draft"):
                    self.assertIn("release edit v1.2.3 --notes-file release-notes.md", recorded)
                    self.assertTrue(recorded.index("--notes-file") < recorded.index("release upload"))
                    self.assertTrue(recorded.index("release upload") < recorded.index("--draft=false"))
                else:
                    self.assertNotIn("--draft=false", recorded)
                if scenario == "published":
                    self.assertNotIn("--notes-file", recorded)


if __name__ == "__main__":
    unittest.main()
