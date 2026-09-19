---
trust: L2
anchors:
  - tools/kb-verify.sh
verified_commit: 63c65fb
verified_at: 2026-09-19
supersedes: null
---

# 候选 · 写校验类 shell 脚本的两个静默失效陷阱

> `trust: L2`，未经人工审阅，**不得作为实现依据**。审阅结论请写在文末。

## 现象

`tools/kb-verify.sh` 首版能跑、输出"全绿"，但**两个判定都是错的**，靠 KB2 的验收用例才暴露：

1. 故意把 `fun PuzzleGrid(` 改名后，脚本仍报 OK —— 应该报 STALE。
2. 修完第 1 个问题后，`categoryColor` 这个明明存在的符号被报成 STALE —— 误报。

## 当时的归因

**问题 1：符号检查匹配到了注释。**
原实现是 `grep -F "$symbol" "$path"`，对整个文件做子串匹配。函数改名后，KDoc 注释里残留的旧名字（`PuzzleGrid` 出现在文件头的说明注释里）让匹配继续成立。
→ 改为先 `code_lines()` 剔除行尾 `//` 注释与 `^\s*\*` / `^\s*/\*` 注释行，再用 `grep -w` 按词匹配。子串匹配也有问题：`PuzzleGridRenamed` 会命中 `PuzzleGrid`。

**问题 2：`set -o pipefail` 与 `grep -q` 冲突。**
`code_lines "$path" | grep -qwF -- "$symbol"`：`grep -q` 命中后立即退出，上游 `sed` 继续写入时收到 SIGPIPE 而非零退出，`pipefail` 把整个管道判为失败，于是"符号存在"被当成"不存在"。
**匹配位置越靠前越容易触发**（`categoryColor` 在文件靠前处），所以表现得像随机误报，很难定位。
→ 改用 `grep -c` 读完全部输入后再判计数。

**附带问题 3：中文标点紧跟变量名。**
`"...：$symbol（在 $path 中未找到）"` 里 `$symbol（` 的全角括号被 bash 当作变量名的一部分，`set -u` 下报 `symbol?: unbound variable`。
→ 用 `${symbol}` 花括号界定。中文文案里插值一律加花括号。

## 证据

- `tools/kb-verify.sh` 内 `code_lines()` 与 `grep -c` 两处的代码注释记录了这两个归因。
- KB2 验收记录：重命名符号 → STALE + 退出码 1；删除锚点文件 → STALE；旧 `verified_commit` → SUSPECT；恢复后 12 张全绿、`--quiet` 零输出。

## 待审阅决定

- [ ] 是否升级为 `pitfalls/` 正式卡？倾向**是**：问题 2 与问题 3 是通用 shell 陷阱，本项目后续再写校验/CI 脚本会再遇到。
- [ ] 若升级，是否合并成一张「写 shell 校验脚本的陷阱」卡，而不是拆三张。
- [ ] 更普适的一条教训（值得单独确认）：**校验工具本身必须有"能否发现问题"的反向测试**。首版脚本之所以"全绿"，是因为它什么都发现不了——只跑正向用例会把这种失效完全掩盖。
