"""Group stable-tag commit subjects into a reproducible Chinese GitHub Release body."""

import argparse
from pathlib import Path
import re
import subprocess

from release_version import parse_tag


GROUPS = ("新增", "修复", "调整", "维护")


def git(*arguments: str, cwd: Path | None = None) -> str:
    result = subprocess.run(["git", *arguments], cwd=cwd, capture_output=True, encoding="utf-8", check=False)
    if result.returncode:
        raise ValueError("无法读取发布提交，请确认Tag存在并已获取完整Git历史。")
    return result.stdout.strip()


def previous_tag(tag: str, cwd: Path | None = None) -> str | None:
    current = parse_tag(tag)
    git("rev-parse", "--verify", f"refs/tags/{tag}^{{commit}}", cwd=cwd)
    candidates = []
    for candidate in git("tag", "--merged", f"refs/tags/{tag}", cwd=cwd).splitlines():
        try:
            version = parse_tag(candidate)
        except ValueError:
            continue
        if version.code < current.code:
            candidates.append((version.code, candidate))
    return max(candidates)[1] if candidates else None


def classify(subject: str) -> tuple[str, str]:
    subject = " ".join(subject.split())
    conventional = re.match(r"^(feat|fix|perf|refactor|style|build|ci|docs|chore|test)(?:\([^)]*\))?!?:\s*(.+)$", subject, re.I)
    if conventional:
        kind, title = conventional.groups()
        group = {"feat": "新增", "fix": "修复", "perf": "调整", "refactor": "调整", "style": "调整"}.get(kind.lower(), "维护")
        return group, title.strip()
    if re.match(r"^(修复|解决|纠正|修正)", subject):
        return "修复", subject
    if re.match(r"^(新增|添加|支持|实现|接入)", subject):
        return "新增", subject
    if re.match(r"^(调整|优化|改进|完善|精简|收紧|上移|移除|更换|统一)", subject):
        return "调整", subject
    return "维护", subject


def render_notes(tag: str, subjects: list[str], repository: str, previous: str | None) -> str:
    version = parse_tag(tag)
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9-]*/[A-Za-z0-9_][A-Za-z0-9_.-]*", repository):
        raise ValueError("仓库需使用owner/repository格式。")
    groups: dict[str, list[str]] = {name: [] for name in GROUPS}
    seen = set()
    for subject in subjects:
        if not subject.strip() or subject.startswith(("Merge ", "合并分支")):
            continue
        group, title = classify(subject)
        key = title.rstrip("。.").casefold()
        if key and key not in seen:
            groups[group].append(title)
            seen.add(key)
    lines = [f"# 酷DO {version.name}", ""]
    for group, titles in groups.items():
        if titles:
            lines.extend([f"## {group}", "", *(f"- {title}" for title in titles), ""])
    if not seen:
        lines.extend(["本版本没有新增提交。", ""])
    base = f"https://github.com/{repository}"
    link = f"{base}/compare/{previous}...{tag}" if previous else f"{base}/commits/{tag}"
    lines.extend([f"[完整变更]({link})", ""])
    return "\n".join(lines)


def generate_notes(tag: str, repository: str, cwd: Path | None = None) -> str:
    previous = previous_tag(tag, cwd)
    revision = f"refs/tags/{previous}..refs/tags/{tag}" if previous else f"refs/tags/{tag}"
    subjects = git("log", "--no-merges", "--reverse", "--format=%s", revision, cwd=cwd).splitlines()
    return render_notes(tag, subjects, repository, previous)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--repository", default="woliubang256/kudo-android")
    parser.add_argument("--output", type=Path, required=True)
    arguments = parser.parse_args()
    try:
        body = generate_notes(arguments.tag, arguments.repository)
        arguments.output.parent.mkdir(parents=True, exist_ok=True)
        arguments.output.write_text(body, encoding="utf-8", newline="\n")
    except (ValueError, OSError) as error:
        parser.exit(1, f"{error}\n")


if __name__ == "__main__":
    main()
