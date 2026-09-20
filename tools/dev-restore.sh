#!/usr/bin/env bash
#
# 把 dev-backup.sh 产出的备份恢复到设备上的开发包。
#
# tar 里存的是相对路径（databases/ datastore/ files/），不含包名，
# 所以**支持跨包名恢复** —— 改 applicationId 后把旧包数据搬到新包就靠这个：
#   tools/dev-backup.sh  -p com.jk.offermate      -o ~/om-old.tar
#   tools/dev-restore.sh -p com.jk.offermate.dev     ~/om-old.tar
#
# 恢复是覆盖式的：会先删掉目标包里同名的顶层目录再解包。
# 必须这么做 —— 只覆盖 .db 而留下旧的 -wal/-shm 会让 Room 读到不一致的状态。
#
# API Key 不在备份里（见 dev-backup.sh 的说明），恢复后需要靠 local.properties
# 的 devApiKey 自动回填，或在设置页手填。
#
# 用法:
#   tools/dev-restore.sh                       # 恢复最新一份备份到自动探测的包
#   tools/dev-restore.sh ~/om.tar              # 指定备份文件
#   tools/dev-restore.sh -p com.jk.offermate.dev ~/om-old.tar   # 跨包名恢复
#   tools/dev-restore.sh -y ~/om.tar           # 跳过确认

set -euo pipefail

BACKUP_DIR="${HOME}/.offermate/dev-backups"
REMOTE_TMP="/data/local/tmp/offermate-restore.tar"
PKG=""
FILE=""
ASSUME_YES=0

usage() {
    sed -n '2,/^set -euo/p' "$0" | sed 's/^# \{0,1\}//; $d'
    exit "${1:-0}"
}

while [ $# -gt 0 ]; do
    case "$1" in
        -p|--package) PKG="${2:-}"; shift 2 ;;
        -y|--yes)     ASSUME_YES=1; shift ;;
        -h|--help)    usage 0 ;;
        -*) echo "未知参数: $1" >&2; usage 1 ;;
        *) FILE="$1"; shift ;;
    esac
done

command -v adb >/dev/null 2>&1 || { echo "找不到 adb，请确认 Android SDK platform-tools 在 PATH 里" >&2; exit 1; }

device_count=$(adb devices | awk 'NR>1 && $2=="device"' | wc -l | tr -d ' ')
if [ "$device_count" -eq 0 ]; then
    echo "没有已连接的设备（adb devices 为空）" >&2
    exit 1
fi
if [ "$device_count" -gt 1 ] && [ -z "${ANDROID_SERIAL:-}" ]; then
    echo "检测到多台设备，请用 ANDROID_SERIAL=<序列号> 指定" >&2
    adb devices | awk 'NR>1 && $2=="device" {print "  " $1}' >&2
    exit 1
fi

# 未指定备份文件时取默认目录里最新的一份
if [ -z "$FILE" ]; then
    FILE=$(ls -t "$BACKUP_DIR"/*.tar 2>/dev/null | head -1 || true)
    if [ -z "$FILE" ]; then
        echo "没找到备份文件。先跑 tools/dev-backup.sh，或用参数指定 tar 路径" >&2
        exit 1
    fi
    echo "使用最新备份: $FILE"
fi
[ -f "$FILE" ] || { echo "备份文件不存在: $FILE" >&2; exit 1; }
[ -s "$FILE" ] || { echo "备份文件为空: $FILE" >&2; exit 1; }

installed() { adb shell pm path "$1" >/dev/null 2>&1; }

if [ -z "$PKG" ]; then
    for candidate in com.jk.offermate.dev com.jk.offermate; do
        if installed "$candidate"; then PKG="$candidate"; break; fi
    done
    if [ -z "$PKG" ]; then
        echo "设备上没找到 com.jk.offermate.dev 或 com.jk.offermate，请用 -p 指定包名" >&2
        exit 1
    fi
    echo "自动探测到目标包: $PKG"
elif ! installed "$PKG"; then
    echo "设备上未安装 $PKG（先把 app 装上再恢复）" >&2
    exit 1
fi

if ! adb shell run-as "$PKG" true >/dev/null 2>&1; then
    echo "run-as $PKG 失败：该包不是 debuggable 构建，无法恢复数据" >&2
    exit 1
fi

# 从 tar 读出顶层目录名，恢复前按这个列表清理，避免残留文件与恢复的数据不一致
top_dirs=$(tar tf "$FILE" | cut -d/ -f1 | sort -u | tr '\n' ' ')
[ -n "${top_dirs// /}" ] || { echo "备份内容为空，无法恢复" >&2; exit 1; }

echo
echo "即将恢复到: $PKG"
echo "备份文件:   $FILE"
echo "将被**删除并覆盖**的目录: $top_dirs"
echo
if [ "$ASSUME_YES" -ne 1 ]; then
    printf "确认继续？[y/N] "
    read -r reply
    case "$reply" in
        y|Y|yes|YES) ;;
        *) echo "已取消"; exit 0 ;;
    esac
fi

echo "停止 $PKG ..."
adb shell am force-stop "$PKG"

echo "上传备份 ..."
adb push "$FILE" "$REMOTE_TMP" >/dev/null

cleanup_remote() { adb shell rm -f "$REMOTE_TMP" >/dev/null 2>&1 || true; }
trap cleanup_remote EXIT

echo "清理旧数据 ..."
adb shell run-as "$PKG" sh -c "cd /data/data/$PKG && rm -rf $top_dirs"

# 由 shell 用户 cat 文件、再把字节喂给 run-as 进程的 stdin。
# 不让 app 进程直接读 /data/local/tmp —— 那是 shell_data_file，SELinux 通常不允许
# untrusted_app 访问；走管道就只涉及一个 pipe，稳定得多。
echo "解包 ..."
adb shell "cat $REMOTE_TMP | run-as $PKG sh -c 'cd /data/data/$PKG && tar xf -'"

echo
echo "恢复完成。"
echo
echo "API Key 不在备份里，需要单独处理："
echo "  在 local.properties 写 devApiKey=<你的 Key>，重新构建 debug 包即可自动回填；"
echo "  或直接在 App 设置页手填一次。"
