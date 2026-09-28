# ARCHITECTURE.md - システムアーキテクチャ概要

## 1. 概要
本アプリケーションは、対話型パーソナルAI執事・秘書アプリであり、Android端末上の安全なローカル環境を中心としたクリーンアーキテクチャ設計を採用します。

---

## 2. レイヤー構成 (Clean Architecture)

```
[ UI Layer / Presentation ]
   └── Jetpack Compose / Flutter UI (チャット, カード, ホーム)
         ↓
[ Domain Layer / Business Logic ]
   └── Persona Engine (執事長/メイド長プロンプト制御)
   └── Decision Engine (優先順位算出, モード判定)
   └── Command Processor (対話からの操作解析, 取り消し/履歴管理)
         ↓
[ Data Layer / Infrastructure ]
   └── Encrypted Local DB (Room/SQLite + SQLCipher)
   └── Secure Storage (KeyStore / EncryptedSharedPreferences)
   └── External Services (ChatGPT API, Google Calendar API, Sub-AI)
   └── Android System (AlarmManager, WorkManager, SpeechRecognizer)
```

---

## 3. コンポーネント設計方針
- **マルチプラットフォーム考慮**: ドメインロジック（優先度計算、データモデル、状態管理）はAndroid依存を排除し分離。
- **データ共有**: 執事長・メイド長間で同一データストア（ToDo, 予定, 記憶, 履歴）を参照。
- **操作履歴と取り消し (Undo)**: 全ての操作命令は Command パターンで実装し、変更前後のステートを暗号化データベースに保持。
