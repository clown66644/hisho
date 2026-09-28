# AI執事・秘書アプリ
# AI開発運用仕様書 v2.0
## GitHubハブ・AntiGravity主実装・ChatGPT通常レビュー・Codex重点監査方式

---

# 0. この文書の目的

本書は、本プロジェクトにおけるAI開発の正式な運用仕様である。

使用する役割は以下とする。

- AntiGravity：主任開発者
- ChatGPT：要件管理・通常コードレビュー
- Codex：高リスク技術監査
- GitHub：コード・仕様・レビュー結果の共通保管場所
- 人間：最終判断者

目的は、

**少ないAI利用量で、開発速度、安全性、コードの一貫性を最大化すること**

である。

AI同士を頻繁に往復させること自体を目的にしない。

---

# 1. 基本原則

正式な役割分担は以下とする。

## AntiGravity

**作る役・直す役**

担当：

- 設計
- 実装
- UI
- データベース
- API連携
- Android機能
- 自動テスト
- ビルド確認
- ドキュメント更新
- ChatGPTレビュー指摘の改修
- Codex監査指摘の改修

コードベースの一貫した実装責任はAntiGravityが持つ。

---

## ChatGPT

**仕様を管理し、普段レビューする役**

担当：

- 要件整理
- 仕様変更
- 設計相談
- GitHub上のPR・差分・関連コードの通常レビュー
- REQUIREMENTS.mdとの整合確認
- バグ候補
- 要件漏れ
- テスト不足
- セキュリティ上の一般的問題
- AntiGravityへ渡す改修指示書作成

通常SprintではChatGPTレビューを基本とする。

---

## Codex

**重要なところを疑う役**

担当：

- 高リスク機能の監査
- 複雑なバグ調査
- セキュリティ監査
- 暗号化
- 認証・認可
- データベース移行
- バックアップ・復元
- 並行処理
- 二重実行
- AI Tool Calling
- 個人情報処理
- 健康・服薬機能
- 家計機能
- フェーズ終了時の全体監査
- リリース前監査

Codexを毎Sprintの通常レビューには原則使用しない。

---

# 2. GitHubを共通作業ハブとする

仕様、コード、レビュー結果をGitHubへ集約する。

AIごとに別々の仕様を持たせない。

リポジトリ内の文書を正式な情報源とする。

推奨構成：

```text
/
├─ README.md
├─ REQUIREMENTS.md
├─ ARCHITECTURE.md
├─ DATA_MODEL.md
├─ SECURITY.md
├─ API_SPEC.md
├─ TEST_PLAN.md
├─ TASKS.md
├─ DECISIONS.md
├─ CHANGELOG.md
│
├─ reviews/
│   ├─ REVIEW_REQUEST.md
│   ├─ CHATGPT_REVIEW.md
│   ├─ CODEX_REVIEW.md
│   └─ REVIEW_HISTORY.md
│
├─ docs/
│
├─ app/
│
└─ tests/
```

プロジェクト構造に合わせて配置場所の変更は可能。

---

# 3. 正式な情報源

優先順位は以下とする。

1. REQUIREMENTS.md
2. DECISIONS.md
3. ARCHITECTURE.md
4. SECURITY.md
5. DATA_MODEL.md
6. API_SPEC.md
7. TEST_PLAN.md
8. TASKS.md
9. 現在のコード
10. AIとの一時的な会話

チャットだけで決まった重要仕様を放置しない。

正式決定したら文書へ反映する。

---

# 4. 標準開発フロー

標準フロー：

```text
人間＋ChatGPT
要件決定
      ↓
REQUIREMENTS.md更新
      ↓
AntiGravity
設計・実装
      ↓
AntiGravity
自己テスト
      ↓
Git commit
      ↓
feature branchをpush
      ↓
REVIEW_REQUEST.md更新
      ↓
レビュー対象commit SHAを固定
      ↓
ChatGPT
通常レビュー
      ↓
CHATGPT_REVIEW.md
      ↓
AntiGravity
妥当性確認・改修
      ↓
テスト
      ↓
必要条件を満たす場合だけ
Codex監査
      ↓
CODEX_REVIEW.md
      ↓
AntiGravity
必要箇所を改修
      ↓
人間
実機確認
      ↓
developへ統合
```

---

# 5. 重要：レビュー中は対象コードを固定する

レビューを開始するときは、

**commit SHAを必ず記録する。**

例：

```text
Review target:
commit: 4a9cd301...
branch: feature/todo-crud
```

ChatGPTまたはCodexがレビューしている間に、同じレビュー対象へ大量の追加変更を混ぜない。

変更が必要な場合は、新しいcommitとして明確に分ける。

これにより、

「レビューしたコード」と
「現在のコード」

が食い違うことを防止する。

---

# 6. Sprint方式

アプリ全体を一度に実装しない。

1 Sprintは、

**1つの主要機能**

または

**密接に関連した2〜3個の小機能**

とする。

---

# 7. AntiGravityの作業ルール

あなたがAntiGravityの場合、本項に従う。

## 作業開始前

以下を確認する。

- REQUIREMENTS.md
- ARCHITECTURE.md
- DATA_MODEL.md
- SECURITY.md
- API_SPEC.md
- TEST_PLAN.md
- TASKS.md
- DECISIONS.md
- 過去レビュー

そのうえで今回のSprintについて、

- 実装範囲
- 変更予定ファイル
- 設計
- 必要権限
- 外部API
- データ変更
- リスク
- テスト方法
- 完成条件

を整理する。

---

# 8. AntiGravityの実装ルール

以下を守る。

- 必要以上の全面書き換えをしない
- APIキーを直書きしない
- 秘密情報をGitへcommitしない
- エラーを握りつぶさない
- 入力値を検証する
- 外部操作は成功確認する
- 再試行で二重実行させない
- データ変更は可能な範囲でUndo可能にする
- 新機能にはテストを追加する
- 将来機能を先回りしすぎない
- 初期版へ不要なライブラリを増やさない

---

# 9. AntiGravityの自己テスト

ChatGPTへレビュー依頼する前に最低限、

- ビルド
- lint
- 単体テスト
- 関連統合テスト
- 正常系
- 不正入力
- 空欄
- 境界値
- 二重操作
- 通信失敗
- アプリ再起動
- 関連既存機能

を確認する。

未実行項目があれば明記する。

---

# 10. REVIEW_REQUEST.md

AntiGravityはレビュー依頼前に以下を更新する。

---

# 11. ChatGPT通常レビュー

通常Sprintでは、まずChatGPTがレビューする。

レビュー対象：

- REVIEW_REQUEST.md
- 指定commit
- Git diff
- 変更ファイル
- 直接影響する周辺コード
- 関連テスト
- REQUIREMENTS.md
- SECURITY.md
- DATA_MODEL.md

毎回リポジトリ全体を最初から読む必要はない。

---

# 12. ChatGPTレビュー基準

以下を確認する。

1. 要件適合性
2. バグ
3. データ破損
4. 入力値検証
5. エラー処理
6. 二重処理
7. 外部API失敗
8. 個人情報
9. APIキー
10. 権限
11. Undo
12. テスト不足
13. 回帰リスク
14. 保守性
15. 過剰実装

問題を以下で分類する。

- P0 / Critical
- P1 / High
- P2 / Medium
- P3 / Low

---

# 13. CHATGPT_REVIEW.md

形式：

```text
# ChatGPT Review

## Review Target
Branch:
Commit:
Sprint:

## 結論
PASS / FIX REQUIRED / CODEX AUDIT REQUIRED

## Critical
...
## High
...
## Medium
...
## Low
...
## 要件漏れ
...
## テスト不足
...
## Codex監査推奨
Yes / No
理由:
## 再レビュー対象
...
```

---

# 14. AntiGravityによるChatGPT指摘対応

AntiGravityはCHATGPT_REVIEW.mdを読んで修正する。

ただし、指摘を盲目的に適用しない。

以下と照合する。

- REQUIREMENTS.md
- DECISIONS.md
- 現在のコード
- 技術仕様
- 実際の動作

妥当なら修正する。

妥当でない場合は、

- 修正しない理由
- 技術的根拠

をレビュー文書へ追記する。

---

# 15. Codexを使用する条件

以下のいずれかを満たす場合のみ、原則としてCodex監査を行う。

## 条件A：高リスク機能
対象：
- 認証
- 認可
- 暗号化
- APIキー管理
- データベースMigration
- バックアップ
- 復元
- クラウド同期
- Google Calendar書き込み
- 外部共有
- AI Tool Calling
- 自動実行
- 個人情報
- 健康情報
- 服薬
- 家計
- 課金
- Webhook
- ファイルアップロード

## 条件B：ChatGPTレビューでCritical / High
## 条件C：フェーズ完了
## 条件D：リリース前
## 条件E：AntiGravityで解決困難

---

# 16. Codexの通常Sprint利用は禁止

---

# 17. Codexの場合の作業ルール

---

# 18. Codex重点確認項目

---

# 19. Codexは原則レビューのみ

---

# 20. CODEX_REVIEW.md

---

# 21. 再レビュー

---

# 22. Sprint終了条件

以下を満たしたらSprint終了可能。

- Critical = 0
- High = 0
- Build成功
- 必須自動テスト成功
- 対象機能正常
- 主要既存機能に明確な回帰なし

---

# 23. レビューの無限ループ禁止

---

# 24. Gitブランチ

基本：

```text
main
│
develop
│
├─ feature/todo
├─ feature/calendar
├─ feature/notification
├─ feature/ai-provider
├─ feature/backup
└─ ...
```

---

# 25. mainの扱い

mainは安定版専用。AntiGravityが直接mainで開発しない。

---

# 26. コミット

1コミットへ複数目的を混ぜすぎない。

---

# 27. セキュリティ：GitHubへ入れてはいけないもの

---

# 28. .gitignore

---

# 29. Secret Scan

---

# 30. AI利用量の節約

---

# 31. 推奨AI作業配分

- AntiGravity：70〜80%
- ChatGPT：15〜25%
- Codex：5〜10%

---

# 32. ChatGPTへのレビュー依頼方法

---

# 33. フェーズゲート

---

# 34. リリース前

---

# 35. AI同士の意見が違う場合

---

# 36. 重要設計判断

---

# 37. ロールバック

---

# 38. 最終的な正式フロー

---

# 39. 最重要原則

AntiGravityを主開発者として維持する。
ChatGPTは日常的なレビューへ使用する。
Codexは高リスク・難易度の高い問題へ温存する。
GitHubを共通の事実情報として使用する。

**AI利用回数ではなく、完成した安全な機能の数を最大化する。**

---

# 40. この文書を受け取った直後の動作

AntiGravityは現在のリポジトリを確認し、不足している運用ファイルだけを追加して現在Sprintを通常どおり続行する。
