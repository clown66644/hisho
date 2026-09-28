# API_SPEC.md - API連携仕様書

## 1. OpenAI (ChatGPT) 連携
- **用途**: 統括AI（執事長・メイド長）
- **モデル**: GPT-4o / GPT-4o-mini
- **要求構造**: System Prompt により persona (執事長 / メイド長) を切り替え、構造化JSON (Function Calling / Structured Outputs) により ToDo / カレンダー / アラーム操作を抽出。

---

## 2. Google Calendar API 連携
- **用途**: 予定の閲覧・追加・変更・削除
- **認証**: OAuth2 (Google Sign-In)
- **制御**: 端末ローカルの Calendar Provider との同期併用
