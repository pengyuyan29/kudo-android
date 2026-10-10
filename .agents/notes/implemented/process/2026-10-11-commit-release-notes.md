# Agent Note: 从提交整理中文Release日志

Status: implemented

## Problem

GitHub的generate-notes主要按PR整理，直接提交可能没有可用更新说明，App需要读取可理解的Release正文。

## Decision

脚本从当前稳定Tag的祖先中选择上一个数字版本较小的稳定Tag，只整理两者之间提交；首次版本整理全部历史。提交标题按新增、修复、调整及维护分组，支持常用英文提交前缀与现有中文动词，去掉合并提交、空项和重复标题，并附完整比较链接。自动流程生成Markdown作为发布产物，创建或恢复草稿时用同一正文写入Release，保留验签、摘要和已发布版本不覆盖规则。手动发布前可用脚本预览并编辑生成文件，通过gh --notes-file保持真实换行。

## Existing note audit

[签名与Tag发布](../../implemented/process/2026-10-08-signing-and-tag-releases.md)部分重叠，本篇接管正文生成，保留签名、权限和草稿发布顺序；[更新检查](../../implemented/feature/2026-10-11-github-updates.md)读取该正文。无完全吸收和过时提案。

## Alternatives considered

- GitHub自动生成说明可复用平台PR分类，但仓库以直接中文提交为主，无法覆盖实际变化，采用确定性的提交分组。
- 调用LLM自动改写能更贴近用户语言，但CI需要额外凭据且输出不可复现，保留中文提交标题和可人工预览文件。

## Verification

临时Git仓库测试首次版本、增量范围、版本排序、非祖先Tag、重复及合并提交、中文分类和无功能变化；工作流始终在发布前上传并写入该正文。

20项发布工具测试通过（含新增5项测试），日志artifacts/release-notes-tests.txt。从实际release.yml提取发布shell，在离线gh替身下验证新建Release、恢复草稿、已公开拒绝修改、上传失败和摘要失败；新建及重试均先写notes-file，再上传附件，最后公开。actionlint 1.7.12通过，本地验证阶段未执行远程发布。

2026-10-11首次真实发布v0.1.5：Tag固定指向b3c072c，因没有先前稳定Tag，正文整理全部祖先提交。云端运行38074297223第二轮完整通过，GitHub Release正文与脚本本地生成文件逐字一致（忽略尾部空白），包含高频拖动修复并附Tag提交历史链接。发布后实际下载APK和摘要并验签通过，验收记录见[签名与Tag发布](2026-10-08-signing-and-tag-releases.md)；云端正文留存于artifacts/release/0.1.5/cloud/release-notes.md。仓库仍私有，App匿名更新检查留待公开后验收。

## Consequences

日志质量取决于commit标题，发布前需要查看生成结果；首次版本包含完整历史，后续版本按数字较小的祖先稳定Tag增量生成。真实云端正文已验收，但私有仓库内认证访问不能证明App的匿名访问路径。
