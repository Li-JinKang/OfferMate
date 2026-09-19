#!/usr/bin/env bash
#
# 知识库锚点校验：检查 docs/kb/ 下的卡片是否还在描述真实存在的代码。
#
# 判定（规范见 docs/kb/README.md）：
#   STALE   锚点文件或符号已不存在            → 硬错误，退出码 1
#   SUSPECT verified_commit..HEAD 之间锚点文件被改过 → 软告警，读卡前须先核对代码
#   OK      锚点齐全且自上次核对以来未变动
#
# 不按时间过期：纯时间衰减会误伤长期稳定的知识、又漏掉高频变动的热点。
# 失效的真正原因是「被描述的代码变了」，所以直接测这件事。
#
# 用法：
#   tools/kb-verify.sh            全量校验
#   tools/kb-verify.sh --quiet    只输出问题（适合 hook / CI）

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT" || exit 2

KB_DIR="docs/kb"
QUIET=0
[ "${1:-}" = "--quiet" ] && QUIET=1

STALE_COUNT=0
SUSPECT_COUNT=0
OK_COUNT=0
CARD_COUNT=0

# 只校验主库；inbox/ 是未审候选，不纳入门槛
CARDS="$(find "$KB_DIR/map" "$KB_DIR/decisions" "$KB_DIR/pitfalls" -name '*.md' 2>/dev/null | sort)"

if [ -z "$CARDS" ]; then
  echo "未找到任何卡片（$KB_DIR/{map,decisions,pitfalls}/*.md）"
  exit 0
fi

say() { [ "$QUIET" -eq 1 ] || printf '%s\n' "$1"; }

# 取 front-matter 里的标量字段值
scalar_field() {
  awk -v key="$2" '
    NR == 1 && $0 == "---" { inFm = 1; next }
    inFm && $0 == "---" { exit }
    inFm && index($0, key ":") == 1 {
      sub(/^[^:]*:[ \t]*/, "")
      gsub(/^["'"'"']|["'"'"']$/, "")
      print
      exit
    }
  ' "$1"
}

# 输出源文件的「代码行」：剔除行尾 // 注释，丢掉 KDoc / 块注释行。
# 符号检查必须基于代码行——否则符号被重命名后，注释里残留的旧名字会让校验误判为仍然存在
# （这个 bug 在 KB2 验收时被抓到过：grep 整个文件时 KDoc 里的 PuzzleGrid 掩盖了函数已改名的事实）。
code_lines() {
  sed -e 's://.*$::' "$1" | grep -v -E '^[[:space:]]*(\*|/\*)'
}

# 取 anchors 列表（front-matter 内 "anchors:" 之后缩进的 "- xxx" 行）
anchor_list() {
  awk '
    NR == 1 && $0 == "---" { inFm = 1; next }
    inFm && $0 == "---" { exit }
    inFm && /^anchors:[ \t]*$/ { inAnchors = 1; next }
    inAnchors && /^[ \t]+-[ \t]+/ {
      sub(/^[ \t]+-[ \t]+/, "")
      gsub(/[ \t]+$/, "")
      print
      next
    }
    inAnchors && /^[^ \t-]/ { inAnchors = 0 }
  ' "$1"
}

say "知识库锚点校验（HEAD=$(git rev-parse --short HEAD 2>/dev/null || echo '?')）"
say ""

for card in $CARDS; do
  CARD_COUNT=$((CARD_COUNT + 1))
  card_stale=0
  card_suspect=0
  problems=""

  trust="$(scalar_field "$card" trust)"
  commit="$(scalar_field "$card" verified_commit)"
  anchors="$(anchor_list "$card")"

  # front-matter 完整性
  if [ -z "$trust" ]; then
    problems="${problems}
    缺少 front-matter 字段 trust"
    card_stale=1
  fi
  if [ -z "$commit" ]; then
    problems="${problems}
    缺少 front-matter 字段 verified_commit"
    card_stale=1
  fi
  if [ -z "$anchors" ]; then
    problems="${problems}
    缺少 anchors（无法校验，等于没有保鲜机制）"
    card_stale=1
  fi

  commit_ok=0
  if [ -n "$commit" ]; then
    if git rev-parse --verify --quiet "$commit^{commit}" >/dev/null 2>&1; then
      commit_ok=1
    else
      problems="${problems}
    verified_commit=$commit 在当前仓库中不存在（被 rebase/squash 掉？）→ 请重新核对并更新"
      card_suspect=1
    fi
  fi

  # 逐个锚点
  while IFS= read -r anchor; do
    [ -z "$anchor" ] && continue
    path="${anchor%%#*}"
    symbol=""
    case "$anchor" in
      *#*) symbol="${anchor#*#}" ;;
    esac

    if [ ! -f "$path" ]; then
      problems="${problems}
    STALE 文件不存在：$path"
      card_stale=1
      continue
    fi

    # 用 grep -c 而非 grep -q：-q 命中后立即退出会让上游 sed 收到 SIGPIPE，
    # 在 `set -o pipefail` 下整个管道返回非零，于是"符号存在"被误判成 STALE
    # （命中位置越靠前越容易触发，排查起来很迷惑）。
    if [ -n "$symbol" ]; then
      hits="$(code_lines "$path" | grep -cwF -- "$symbol" || true)"
    else
      hits="1"
    fi
    if [ "${hits:-0}" -eq 0 ]; then
      problems="${problems}
    STALE 符号不存在：${symbol}（在 ${path} 的代码行中未找到，注释不算）"
      card_stale=1
      continue
    fi

    if [ "$commit_ok" -eq 1 ]; then
      changes="$(git log --oneline "$commit..HEAD" -- "$path" 2>/dev/null | head -3)"
      if [ -n "$changes" ]; then
        problems="${problems}
    SUSPECT 自 $commit 起被改过：$path"
        while IFS= read -r line; do
          [ -n "$line" ] && problems="${problems}
             · $line"
        done <<< "$changes"
        card_suspect=1
      fi
    fi
  done <<< "$anchors"

  if [ "$card_stale" -eq 1 ]; then
    STALE_COUNT=$((STALE_COUNT + 1))
    printf 'STALE    %s\n' "$card"
    printf '%s\n' "$problems"
  elif [ "$card_suspect" -eq 1 ]; then
    SUSPECT_COUNT=$((SUSPECT_COUNT + 1))
    printf 'SUSPECT  %s\n' "$card"
    printf '%s\n' "$problems"
  else
    OK_COUNT=$((OK_COUNT + 1))
    say "OK       $card"
  fi
done

say ""
say "合计 $CARD_COUNT 张：OK $OK_COUNT ／ SUSPECT $SUSPECT_COUNT ／ STALE $STALE_COUNT"

if [ "$STALE_COUNT" -gt 0 ]; then
  echo ""
  echo "存在 STALE 卡片：它们在描述已不存在的代码，必须修正或删除。"
  exit 1
fi

if [ "$SUSPECT_COUNT" -gt 0 ]; then
  [ "$QUIET" -eq 1 ] && echo ""
  echo "存在 SUSPECT 卡片：锚点代码自上次核对后有改动，使用前请先核对代码；"
  echo "核对无误后更新该卡的 verified_commit / verified_at 即可消除告警。"
fi

exit 0
