#!/usr/bin/env bash
#
# 知识库主库写入守卫（供 PreToolUse hook 调用）。
#
# 规则见 docs/kb/README.md：agent 只能写 docs/kb/inbox/；
# 主库（map / decisions / pitfalls）的写入需要人确认，防止未经审阅的内容污染知识库。
#
# 输入：stdin 收到 hook 的 JSON 上下文（含本次工具调用的参数）。
# 输出：命中主库路径时打印 permissionDecision=ask，让用户确认后再执行。
#       其余情况静默放行（exit 0，无输出）。

set -uo pipefail

input="$(cat)"

# inbox 放行：那是 agent 的合法写入区
if printf '%s' "$input" | grep -qE 'docs/kb/inbox/'; then
  exit 0
fi

if printf '%s' "$input" | grep -qE 'docs/kb/(map|decisions|pitfalls)/'; then
  cat <<'JSON'
{"hookSpecificOutput":{"permissionDecision":"ask","permissionDecisionReason":"这是知识库主库（docs/kb/map|decisions|pitfalls），内容会被后续所有需求当作事实使用。按 docs/kb/README.md 的写入规则，agent 的新知识应写入 docs/kb/inbox/ 等待人工审阅；仅在『已核对代码无误』时才可更新 verified_commit / verified_at。确认要直接改主库吗？"}}
JSON
  exit 0
fi

exit 0
