#!/bin/bash
# backend 提交脚本：本地改代码 → 本地编译 → 传到服务器 → 推送 GitHub
#
# 为什么需要这个：
#   - 公司电脑连不上 GitHub（TLS 被重置，curl/git 都失败）
#   - 阿里云服务器有 GitHub SSH 密钥，且能直连 GitHub
#   - 所以本地只负责写代码和编译，服务器只负责 push 这一跳
#
# 用法：
#   ./push.sh                        # 编译 + 提交 + 推送（自动生成提交信息）
#   ./push.sh "提交信息"              # 指定提交信息
#   ./push.sh --no-build              # 跳过本地编译（不建议）
#
# 前提：服务器 /root/backend 已配好 GitHub SSH 密钥并能 push。

set -e

SSH_CFG="D:/ws/.ssh/sshcfg"
HOST="txj"
REMOTE_DIR="/root/backend"
LOCAL_DIR="D:/Lesson/study/backend"

BUILD=1
MSG=""
for arg in "$@"; do
  case "$arg" in
    --no-build) BUILD=0 ;;
    *) MSG="$arg" ;;
  esac
done

cd "$LOCAL_DIR"

echo "=== 1/4 本地编译验证 ==="
if [ "$BUILD" = "1" ]; then
  mvn -B clean package -DskipTests
else
  echo "跳过编译（--no-build）"
fi

echo "=== 2/4 本地提交 ==="
if [ -z "$(git status --porcelain)" ]; then
  echo "工作区干净，无需提交"
else
  if [ -z "$MSG" ]; then
    # 没有指定信息时，用改动文件名拼一个初版，人可后续 amend
    MSG="chore: update $(git status --porcelain | awk '{print $2}' | head -3 | tr '\n' ' ')"
  fi
  git add -A
  git commit -m "$MSG"
  echo "已提交：$MSG"
fi

echo "=== 3/4 传输到服务器（git bundle）==="
# 用 bundle 传对象，比 scp 传文件更省、且能保证两端历史一致
git bundle create /tmp/backend.bundle --all
scp -F "$SSH_CFG" /tmp/backend.bundle "$HOST:/tmp/backend.bundle"

echo "=== 4/4 服务器解包并推送 ==="
ssh -F "$SSH_CFG" "$HOST" "cd $REMOTE_DIR && \
  git fetch /tmp/backend.bundle 'refs/heads/*:refs/remotes/bundle/*' && \
  git checkout -B main refs/remotes/bundle/main && \
  git status --short && \
  git log --oneline -1 && \
  git push origin main && \
  echo '--- 推送完成 ---' && \
  git ls-remote origin HEAD"

rm -f /tmp/backend.bundle
echo ""
echo "全部完成。"
