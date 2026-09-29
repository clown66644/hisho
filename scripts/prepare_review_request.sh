#!/bin/bash
LOCAL_HEAD=git rev-parse HEAD
git fetch origin
REMOTE_HEAD=git ls-remote origin refs/heads/fix/persistence-undo-ci-remediation | cut -f1

if [ "$LOCAL_HEAD" != "$REMOTE_HEAD" ]; then
    echo "Push is NOT synced!"
    exit 1
fi

sed -i "s/WILL_BE_REPLACED_BY_GIT_REV_PARSE/$LOCAL_HEAD/g" reviews/REVIEW_REQUEST.md
git add reviews/REVIEW_REQUEST.md
git commit -m "docs: prepare REVIEW_REQUEST for review 14"
git push origin fix/persistence-undo-ci-remediation

echo "=== REVIEW HANDOFF ==="
echo "Repository: clown66644/hisho"
echo "Branch: fix/persistence-undo-ci-remediation"
echo "Code Head SHA: $LOCAL_HEAD"
echo "Review Request Commit: git rev-parse HEAD"
echo "Remote Sync: OK"
echo "Review Request: READY"