import unittest
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from check_commit_message import validate
from check_commit_history import invalid_records, parse_records


class CommitMessageTest(unittest.TestCase):
    def test_accepts_supported_types_and_scopes(self):
        for subject in ("feat: 添加楼层跳转", "fix(ui): 修复吸顶渐显", "docs: 更新说明", "ci!: 调整工作流"):
            with self.subTest(subject=subject):
                self.assertTrue(validate(subject)[0])

    def test_rejects_untyped_or_unknown_subjects(self):
        for subject in ("修复楼层跳转", "update: change", "feat:", ""):
            with self.subTest(subject=subject):
                self.assertFalse(validate(subject)[0])

    def test_allows_merge_subjects_and_ignores_comment_lines(self):
        self.assertTrue(validate("# comment\nfix: 修复问题\n")[0])
        self.assertTrue(validate("Merge branch 'main'\n")[0])

    def test_history_parser_keeps_subjects_with_non_ascii_text(self):
        records = parse_records("a1\x00feat: 修复中文标题\na2\x00fix(ui): 修复界面\n")
        self.assertEqual([("a1", "feat: 修复中文标题"), ("a2", "fix(ui): 修复界面")], records)

    def test_history_validator_reports_only_unprefixed_subjects(self):
        records = [("good", "docs: 更新说明"), ("bad", "修复旧问题"), ("merge", "Merge branch 'main'")]
        self.assertEqual([("bad", "修复旧问题")], invalid_records(records))


if __name__ == "__main__":
    unittest.main()
