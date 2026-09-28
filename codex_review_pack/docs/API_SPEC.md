# API_SPEC.md - API連携仕様書

## 1. OpenAI連携

- API: Responses API (`POST /v1/responses`)
- 要求: `instructions`、`input`、`store=false`、匿名化済み`safety_identifier`
- 通常応答: トップレベル`status == completed`かつ`output_text`が存在する場合だけ成功
- 構造化操作: `text.format.type=json_schema`、`strict=true`
- 現在の構造化操作: `CREATE_TODO`のみ
- 操作ID: 要求schemaの単一enumへ固定し、応答JSONでも一致を再検証
- refusal: 操作候補として使用せず`INVALID_RESPONSE`
- incomplete・status欠落: `INVALID_RESPONSE`
- タイムアウト: 接続15秒、読取60秒
- 再試行: ネットワーク例外とHTTP 5xxだけ最大1回、250ms後に同じ`X-Client-Request-Id`で再試行
- 非再試行: 401、403、429、その他4xx
- 非露出: HTTP本文、例外本文、Authorization、会話本文を利用者向けエラーへ含めない

## 2. 未実装API

- Google Calendar
- サブAI連携
- サーバー側APIキー管理
