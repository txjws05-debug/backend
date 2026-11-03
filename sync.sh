#!/bin/bash
# backend 同步脚本：本地改代码 → 服务器编译 → 推送 GitHub
#
# 为什么需要这个：
#   - 本地（公司电脑）连不上 GitHub（SSL 被重置）
#   - 阿里云服务器有 GitHub SSH 密钥，且能直连 GitHub
#   - 所以：本地 rsync 到服务器 → 服务器 mvn 编译 → 服务器 push
#
# 用法：
#   ./sync.sh          # 只同步 + 编译（不推送）
#   ./sync.sh push     # 同步 + 编译 + 推送到 GitHub
#   ./sync.sh commit "提交信息"   # 同步 + 编译 + 提交 + 推送

set -e

SSH_CFG="D:/ws/.ssh/sshcfg"
HOST="txj"
REMOTE_DIR="/root/backend"
LOCAL_DIR="D:/Lesson/study/backend"
ACTION="${1:-build}"

echo "=== 1/4 同步本地代码到服务器 ==="
# 排除 .git（服务器端用 SSH 密钥推 GitHub，本地 git 目录不需要同步）
# 排除 target（编译产物，在服务器上重新生成）
rsync -az --delete \
  --exclude '.git' \
  --exclude 'target' \
  --exclude '.idea' \
  -e "ssh -F $SSH_CFG" \
  "$LOCAL_DIR/" "$HOST:$REMOTE_DIR/"
echo "同步完成"

echo "=== 2/4 服务器端编译验证 ==="
ssh -F "$SSH_CFG" "$HOST" "cd $REMOTE_DIR && export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 && export PATH=\$JAVA_HOME/bin:/opt/apache-maven-3.9.9/bin:\$PATH && mvn -B clean package -DskipTests"

if [ "$ACTION" = "push" ] || [ "$ACTION" = "commit" ]; then
  if [ "$ACTION" = "commit" ]; then
    MSG="${2:-chore: update}"
    echo "=== 3/4 提交：$MSG ==="
    ssh -F "$SSH_CFG" "$HOST" "cd $REMOTE_DIR && git add -A && git commit -m '$MSG'"
  else
    echo "=== 3/4 跳过提交（push 模式） ==="
    ssh -F "$SSH_CFG" "$HOST" "cd $REMOTE_DIR && git status --short"
  fi

  echo "=== 4/4 推送到 GitHub ==="
  ssh -F "$SSH_CFG" "$HOST" "cd $REMOTE_DIR && git push origin main"
else
  echo "=== 3-4/4 跳过提交与推送（编译验证模式） ==="
fi

echo ""
echo "完成。"
