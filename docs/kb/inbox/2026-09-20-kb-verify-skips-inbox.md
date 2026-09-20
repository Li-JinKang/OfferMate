---
trust: L2
anchors:
  - tools/kb-verify.sh
verified_commit: 62062a5
verified_at: 2026-09-20
supersedes: null
---

# kb-verify.sh 不校验 inbox/：它的"全绿"不能用来证明自己刚写的候选卡有效

## 现象

写完两张 `inbox/` 候选卡后跑 `tools/kb-verify.sh --quiet`，零输出、退出码 0，
于是推断"anchors 都有效"。**这个推断是错的** —— 全量模式下输出的是：

```
合计 12 张：OK 12 ／ SUSPECT 0 ／ STALE 0
```

12 正好等于 `map/` + `decisions/` + `pitfalls/` 三个目录的卡片数之和，
`inbox/` 里的卡**一张都没被计入**。绿色只覆盖主库。

判据（不依赖具体张数，不会随时间过时）：
**全量输出的「合计 N 张」若等于主库三个目录的卡数之和，就说明 inbox 未被覆盖。**

## 根因

不是 bug，是刻意设计。`tools/kb-verify.sh` 里：

```bash
# 只校验主库；inbox/ 是未审候选，不纳入门槛
CARDS="$(find "$KB_DIR/map" "$KB_DIR/decisions" "$KB_DIR/pitfalls" -name '*.md' 2>/dev/null | sort)"
```

认知落差来自 `docs/kb/README.md`：「失效判定（`tools/kb-verify.sh`）」那节列了
STALE / SUSPECT / OK 三态，但没写范围不含 `inbox/`；`inbox/README.md` 也没提
anchors 不受自动校验保护。两边都读完，仍会以为全库都测。

后果：agent 写的候选卡 anchors 写错（路径拼错、符号不存在、把文档当 anchor）
**没有任何自动拦截**，而规范明确要求 anchors「必须真实存在」。
审阅时才发现锚点是假的，等于整张卡失去保鲜依据。

## 规避规则

1. **写完 `inbox/` 卡，自己核 anchors，不要跑 kb-verify 然后看绿色。**
2. 核对必须复刻脚本的判定语义，否则结论与 verify 不一致：
   剔除注释行（行尾 `//` 与 `^\s*\*` / `^\s*/\*`）后再**按词**匹配。
   直接 `grep 整个文件` 会被注释里残留的旧符号名骗过
   （根因见 `docs/kb/inbox/2026-09-19-bash-verify-script-traps.md` 的问题 1）。

   ```bash
   /bin/bash -c '
   code_lines() { sed -e "s://.*\$::" "$1" | grep -v -E "^[[:space:]]*(\*|/\*)"; }
   code_lines <文件> | grep -cwF -- "<符号>"   # 0 即等价于 STALE
   '
   ```
3. `anchors` 只放**代码**路径。本次一度把
   `docs/kb/inbox/2026-09-19-app-build-type-variant-explosion.md` 写进 anchors——
   文件确实存在、脚本也不会报错（如果它扫 inbox 的话），但它不是被描述的代码，
   起不到保鲜作用。卡之间的关系写正文引用或 `supersedes`。

## 证据

- `tools/kb-verify.sh` 里限定 `find` 范围的那行及其上方注释（见上）。
- 全量模式输出 `合计 12 张`，而当时 `docs/kb/` 下的卡片总数（含 `inbox/`）多于 12。
  可复现（注意用 grep 取合计行，不能用 `tail -1`——有 SUSPECT 时末行是告警文案）：

  ```bash
  ./tools/kb-verify.sh | grep 合计
  find docs/kb -name '*.md' -not -name README.md | wc -l   # 含 inbox 的真实总数
  ```

  实测 2026-09-20：前者报 12，后者为 20，差额 8 即当时 inbox 的张数。
- 本次实例：`--quiet` 退出码 0 被误读为"我的卡已验证"，随后靠全量模式的
  计数才发现 inbox 未被覆盖。
- 手工核对可行性已验证：`OfferMateApplication.kt#seedDevApiKeyIfBlank`
  按上述命令在代码行命中 2 次（调用处 + 定义处）。

## 触发条件

每次 agent 往 `inbox/` 写卡。只要 `kb-verify.sh` 的 `find` 范围不含 `inbox/`，
这个落差就一直在。

> 审阅时可一并决定：要不要给脚本加一个 `--include-inbox` 开关，
> 让 agent 有办法自查而又不把 inbox 纳入 CI 门槛。
