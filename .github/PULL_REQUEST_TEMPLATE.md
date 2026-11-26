<!--
PR title: <type>(<scope>): <summary>
Types: feat, fix, refactor, docs, test, perf, build, ci, chore
Scopes: android, data, reader, explore, review, vocabulary, settings, design
Omit the scope for repository-wide changes. Use a lowercase imperative summary with no trailing period, roughly 72 characters or fewer.
Read CONTRIBUTING.md before submitting. Check only items you actually verified; explain N/A items in Test plan or Risks and compatibility.

仓库级变更可省略 scope。标题使用小写祈使句，不加句号，约 72 个字符以内。
请先阅读 CONTRIBUTING.md。仅勾选实际验证过的项目；不适用时在 Test plan 或 Risks and compatibility 中说明。
-->

## Summary

<!-- Describe the problem and the intended result. -->
<!-- 说明要解决的问题和预期结果。 -->

Related issue: <!-- For example, Refs #12. Delete this line if there is no related issue. --><!-- 例如 Refs #12；没有可删除此行。 -->

## Changes

<!-- List the important implementation, documentation, or configuration changes. -->
<!-- 列出关键实现、文档或配置变更。 -->

## Test plan

<!-- Record the exact commands run, device or emulator, Android version, manual steps, and results. Do not write only "tests passed". -->
<!-- 填写实际执行的命令、设备/模拟器、Android 版本，以及手动验证步骤和结果。不要只写“测试通过”。 -->

```text

```

| Check / 检查 | Environment / 环境 | Result / 结果 |
| --- | --- | --- |
| `./gradlew :app:assembleDebug` | | |
| `./gradlew :app:lint` | | |
| Manual feature check / 手动功能验证 | | |
| English UI / 英文界面 | | |
| Chinese UI / 中文界面 | | |

## Screenshots or recordings

<!-- Provide before-and-after screenshots or recordings for visible UI changes when practical. Use N/A for non-UI changes. Do not include real accounts, tokens, or other sensitive information. -->
<!-- UI 变更在可行时提供前后对比；非 UI 变更可写 N/A。请勿包含真实账号、token 或其他敏感信息。 -->

## Risks and compatibility

<!-- Describe migration, data, network, storage, signing, release, or compatibility impact. Write None if there is no identified risk. -->
<!-- 说明迁移、数据、网络、存储、签名、发布版本或兼容性影响；没有可写 None。 -->

## Checklist

### Conventions and scope

- [ ] The branch was created from current `main`, and the branch name, commit messages, and PR title follow repository conventions.<br>分支基于当前 `main`，分支名、提交信息和 PR 标题符合仓库约定。
- [ ] This PR contains one coherent change with no unrelated cleanup or build-environment workarounds.<br>本 PR 只包含一个连贯的变更，没有混入无关清理或构建环境绕过。
- [ ] Changes affecting architecture, persistence, authentication, network providers, release versioning, or public behavior were discussed in an issue or discussion first.<br>涉及架构、持久化、认证、网络服务商、发布版本或公开行为的变更已先通过 Issue 或 Discussion 确认。
- [ ] By submitting this PR, I agree to provide this contribution under the repository's MIT License.<br>提交即表示同意按照仓库的 MIT License 提供本贡献。

### Verification

- [ ] `./gradlew :app:assembleDebug` was actually run and passed.<br>`./gradlew :app:assembleDebug` 已实际执行并通过。
- [ ] `./gradlew :app:lint` was actually run, or known unrelated warnings were identified and explained.<br>`./gradlew :app:lint` 已实际执行，或已识别并说明无关的既有警告。
- [ ] Behavior changes were manually verified on a device or emulator, including loading, empty, error, cancellation, and rapid-input states where relevant.<br>行为变更已在设备或模拟器上手动验证；相关场景包含加载、空状态、错误、取消和快速输入。
- [ ] Visible UI changes were checked in both English and Chinese, with screenshots or recordings provided when practical.<br>可见 UI 变更已检查英文和中文界面，并在适用时提供截图或录屏。
- [ ] CI passes, and all review findings are addressed or explicitly accepted by the maintainer.<br>CI 通过，所有审查意见已处理或由维护者明确接受。

### Security, compatibility, and documentation

- [ ] No tokens, passwords, real account credentials, AI API keys, keystores, signed distribution artifacts, or local configuration are included.<br>未提交 token、密码、真实账号凭据、AI API key、keystore、签名包或本地配置。
- [ ] Authentication credentials remain encrypted and are never logged; request headers and cookies are sent only to intended hosts.<br>认证凭据保持加密且不会写入日志；请求头和 Cookie 仅发送到预期主机。
- [ ] Debug and Release application identity and side-by-side installation remain intact, and Release signing configuration was not added to PR CI.<br>未破坏 Debug/Release 的应用 ID 与并存安装行为，也未向 PR CI 添加 Release 签名配置。
- [ ] New UI strings were added to the localization map and cover both English and Chinese.<br>新增 UI 文本已加入本地化映射，并同时覆盖英文和中文。
- [ ] CONTRIBUTING.md, AGENTS.md, and both READMEs were updated where applicable when public behavior or release rules changed.<br>公开行为或发布规则发生变化时，已同步更新 CONTRIBUTING.md、AGENTS.md 及两个 README 中适用的内容。
- [ ] Version metadata was changed only when release preparation was explicitly assigned, and release commits follow SemVer and repository conventions.<br>仅在维护者明确分配发布准备工作时修改版本元数据；发布提交遵循 SemVer 与仓库约定。
