#!/usr/bin/env bash
#
# 备份本机开发包的应用私有数据，用于"被迫卸载重装"或"改 applicationId"前后保住配置。
#
# 备份内容（/data/data/<包名>/ 下）：
#   databases/   题库、帖子、分类、会话（Room，含 -wal/-shm）
#   datastore/   provider/模型/baseUrl/阈值/MCP 列表、分类顺序、答案更新、简历元信息
#   files/       简历记忆 Markdown（memory/**）、简历原件 resume.pdf
#
# 刻意**不**备份 shared_prefs/offermate_secure_prefs.xml（API Key）：
#   它由 EncryptedSharedPreferences 加密，主密钥在 Android Keystore 里按 app UID 归属，
#   随包卸载一起删除。拷回密文也没有能解开它的密钥，恢复它比不恢复更糟
#   （得到一个打不开的文件，而不是干净的"空 Key"状态）。
#   API Key 请改用 local.properties 的 devApiKey 自动回填，见 README。
#
# 用法:
#   tools/dev-backup.sh                      # 自动探测开发包，备份到默认目录
#   tools/dev-backup.sh -p com.jk.offermate   # 指定包名（如迁移前备份旧包）
#   tools/dev-backup.sh -o ~/om.tar           # 指定输出文件
#
# 多设备时用环境变量 ANDROID_SERIAL 指定目标设备。

set -euo pipefail

BACKUP_DIR="${HOME}/.offermate/dev-backups"
PKG=""
OUT=""

usage() {
    sed -n '2,/^set -euo/p' "$0" | sed 's/^# \{0,1\}//; $d'
    exit "${1:-0}"
}

while [ $# -gt 0 ]; do
    case "$1" in
        -p|--package) PKG="${2:-}"; shift 2 ;;
        -o|--output)  OUT="${2:-}"; shift 2 ;;
        -h|--help)    usage 0 ;;
        *) echo "未知参数: $1" >&2; usage 1 ;;
    esac
done

command -v adb >/dev/null 2>&1 || { echo "找不到 adb，请确认 Android SDK platform-tools 在 PATH 里" >&2; exit 1; }

# 设备数量检查：多设备且未指定 ANDROID_SERIAL 时 adb 会失败得很含糊，这里提前说清楚
device_count=$(adb devices | awk 'NR>1 && $2=="device"' | wc -l | tr -d ' ')
if [ "$device_count" -eq 0 ]; then
    echo "没有已连接的设备（adb devices 为空）" >&2
    exit 1
fi
if [ "$device_count" -gt 1 ] && [ -z "${ANDROID_SERIAL:-}" ]; then
    echo "检测到多台设备，请用 ANDROID_SERIAL=<序列号> 指定，例如：" >&2
    adb devices | awk 'NR>1 && $2=="device" {print "  ANDROID_SERIAL=" $1 " " }' >&2
    exit 1
fi

installed() { adb shell pm path "$1" >/dev/null 2>&1; }

# 未指定包名时自动探测：优先带后缀的开发包，回退到无后缀（改后缀之前的旧包）
if [ -z "$PKG" ]; then
    for candidate in com.jk.offermate.dev com.jk.offermate; do
        if installed "$candidate"; then PKG="$candidate"; break; fi
    done
    if [ -z "$PKG" ]; then
        echo "设备上没找到 com.jk.offermate.dev 或 com.jk.offermate，请用 -p 指定包名" >&2
        exit 1
    fi
    echo "自动探测到包名: $PKG"
elif ! installed "$PKG"; then
    echo "设备上未安装 $PKG" >&2
    exit 1
fi

# run-as 只对 debuggable 包可用。正式签名的 release 包备不了，这里明确报错而不是产出空包。
if ! adb shell run-as "$PKG" true >/dev/null 2>&1; then
    echo "run-as $PKG 失败：该包不是 debuggable 构建（正式 release 包无法用此方式备份）" >&2
    exit 1
fi

if [ -z "$OUT" ]; then
    mkdir -p "$BACKUP_DIR"
    OUT="${BACKUP_DIR}/${PKG}-$(date +%Y%m%d-%H%M%S).tar"
fi
mkdir -p "$(dirname "$OUT")"

# 先停进程：让 Room 把 WAL 落盘、DataStore 把挂起的写刷完。
# 不停进程备份出来的 db 可能处在半写状态。
echo "停止 $PKG ..."
adb shell am force-stop "$PKG"

# 只打包实际存在的顶层目录：tar 遇到不存在的路径会整体失败，
# 而全新安装的包可能还没有 datastore/ 或 files/。
targets=$(adb shell run-as "$PKG" sh -c \
    "cd /data/data/$PKG 2>/dev/null && for d in databases datastore files; do [ -e \"\$d\" ] && printf '%s ' \"\$d\"; done" \
    | tr -d '\r')

if [ -z "${targets// /}" ]; then
    echo "「$PKG」还没有任何可备份的数据（databases/datastore/files 都不存在）" >&2
    exit 1
fi

echo "打包: $targets"
# 必须用 exec-out：adb shell 会对 stdout 做 LF→CRLF 转换，二进制流会被破坏。
adb exec-out run-as "$PKG" sh -c "cd /data/data/$PKG && tar cf - $targets" > "$OUT"

if [ ! -s "$OUT" ]; then
    echo "备份文件为空，备份失败" >&2
    rm -f "$OUT"
    exit 1
fi

size=$(du -h "$OUT" | cut -f1)
echo
echo "备份完成: $OUT  ($size)"
echo
echo "提醒：API Key 不在备份里（加密主密钥随卸载删除，拷不回来）。"
echo "     在 local.properties 写 devApiKey=<你的 Key>，debug 包启动时会自动回填。"
echo
echo "恢复: tools/dev-restore.sh $OUT"
echo "迁移到新包名: tools/dev-restore.sh -p com.jk.offermate.dev $OUT"
