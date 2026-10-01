# ChatGPT 再レビュー依頼

Repository:
clown66644/hisho

Branch:
fix/persistence-undo-ci-remediation

Base SHA:
b478caae520a7286deb072e5c9af1aaf9f6c61ce

Code Head SHA:
00481037010e9fec0a9d1442462c79bab8f2b63b

Push確認:
SUCCESS

GitHub Actions:
PENDING

変更目的:
Persistence / Undo / CI Reliability Remediation (Review 19 指摘対応)

重点確認:
- H-001: DeleteEventCommandのhistoryをvarに変更。undo()で新Provider IDを取得した際にhistory.newStateJsonへCalendarEvent snapshotとして永続化。redo()成功時にnewStateJsonをクリア。
- H-001: CommandResolverのDELETE_EVENT復元時、history.newStateJsonからrestoredEventIdを復元し、再起動後のRedo対象が正しいProvider IDになるように対応。
- M-001: FakeOperationHistoryDaoにshouldFailUpdate/updateFailCountを追加し、Compensation注入テスト4件を新規追加（Undo DB失敗→Compensation成功、Redo DB失敗→Compensation成功、Compensation false時のISE、Execute失敗+Compensation失敗時のISE）。
