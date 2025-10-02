# コンパスアプリ開発ログ

## 概要
リアルタイムコンパス表示と目的地への方角・距離を示すAndroidアプリ

## 完了した機能

### v1.0 基本機能
- [x] リアルタイムコンパス表示（加速度計+地磁気センサー）
- [x] コンパスメーター UI（方角バー表示）
- [x] 目的地登録機能（最大3件）
- [x] 地図表示（OpenStreetMap）
- [x] 地図上で場所をタップして登録
- [x] 場所検索機能（Nominatim API）
- [x] 検索レート制限（5秒間隔）

### v1.1 位置情報連携
- [x] 現在地取得機能（FusedLocationProviderClient）
- [x] 目的地への方角計算（Haversine式）
- [x] 目的地までの距離計算（メートル単位）
- [x] コンパスメーター上に目的地マーカー表示（青い丸）
- [x] 目的地情報のリスト表示（名前・方角・距離）
- [x] ViewModel画面間共有（Navigation対応）

### v1.2 UI改善
- [x] Aboutページ作成
- [x] 設定ページ作成
- [x] ヘッダーにボタン追加（地図・設定・About）
- [x] 文字列リソース化（日本語/英語対応）
- [x] 地図画面レイアウト改善（ボタン配置変更）

## 技術スタック

### フロントエンド
- Kotlin
- Jetpack Compose
- Material Design 3
- Navigation Compose

### センサー・位置情報
- SensorManager（加速度計・地磁気センサー）
- FusedLocationProviderClient（現在地取得）
- Google Play Services Location

### 地図・検索
- OSMDroid（OpenStreetMap）
- Nominatim API（場所検索）

### アーキテクチャ
- MVVM
- StateFlow
- Coroutines

## ファイル構成

```
app/src/main/java/jp/saitama/orange/mycompass/
├── MainActivity.kt              # メイン画面・ナビゲーション
├── CompassViewModel.kt          # コンパス・位置情報ロジック
├── CompassMeter.kt              # コンパスUI
├── MapScreen.kt                 # 地図画面
├── DestinationViewModel.kt     # 目的地管理
├── Destination.kt               # 目的地データクラス
├── AboutScreen.kt               # Aboutページ
├── SettingsScreen.kt            # 設定ページ
└── ui/theme/                    # テーマ設定

app/src/main/res/
├── values/strings.xml           # 日本語リソース（デフォルト）
└── values-en/strings.xml        # 英語リソース
```

## 今後の実装予定

### 優先度：高
- [ ] 設定の永続化（DataStore）
- [ ] 目的地データの永続化（Room Database）
- [ ] 距離単位切り替え機能（メートル/マイル）
- [ ] 目的地削除の確認ダイアログ
- [ ] パーミッション要求のUI改善

### 優先度：中
- [ ] 目的地到着通知機能
- [ ] 目的地の色分け表示
- [ ] 距離に応じたマーカーサイズ変更
- [ ] コンパス較正機能
- [ ] ダークモード対応
- [ ] アプリアイコンのカスタマイズ

### 優先度：低（3D機能）
- [ ] 3Dコンパス針（graphicsLayerで疑似3D）
- [ ] 地球儀ビュー（Filament使用）
- [ ] ARコンパスモード（ARCore）

## 3D機能実装の参考資料

### 3Dコンパス針（疑似3D - Jetpack Compose）

#### 公式ドキュメント
- [Graphics modifiers | Android Developers](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers)

#### チュートリアル
- [Have Fun With Jetpack Compose GraphicsLayer Modifier](https://medium.com/mobile-app-development-publication/have-fun-with-jetpack-compose-graphicslayer-modifier-e39c12a4791f)

#### GitHub実装例
- [CzarqR/Compass](https://github.com/CzarqR/Compass) - Jetpack Composeコンパスアプリ
- [MBCompass](https://github.com/MubarakNative/MBCompass) - モダンなコンパスアプリ
- [bussola-agil](https://github.com/joaoplay16/bussola-agil) - 美しいデザインのコンパス
- [GraphicsLayer Demo](https://github.com/elye/demo_android_jetpack_compose_graphicslayer_modifier) - graphicsLayerの様々な使用例

### 3D地球儀ビュー（Filament）

#### 公式リソース
- [Google Filament GitHub](https://github.com/google/filament) - android/samplesディレクトリにサンプルあり
- [Filament Materials Guide](https://google.github.io/filament/Materials.html) - テクスチャマッピングの詳細

#### チュートリアル
- [Getting Started with Filament on Android](https://medium.com/@philiprideout/getting-started-with-filament-on-android-d10b16f0ec67) - 約100行のコードで3Dモデル表示
- [Basics of Filament on Android](https://medium.com/@ashirkul/basics-of-filament-on-android-be25590e4d11)

#### 球体テクスチャマッピング参考
- [OpenGL Sphere Texture Mapping](https://stackoverflow.com/questions/17488259/opengl-mapping-texture-to-sphere)

## Jetpack Composeの3D表現限界

### ネイティブでできること
- 2D描画（Canvas API）
- 疑似3D（graphicsLayerでrotationX/Y/Z）
- カード回転・フリップアニメーション
- 視差効果

### 本格的な3Dに必要なもの
- OpenGL ES（フル3D）
- Vulkan（高パフォーマンス）
- Filament（リアルタイム3Dレンダリング）
- SceneView（ARCore対応）

これらは`AndroidView`で埋め込んで使用する。

## 既知の問題
- [ ] 地図検索時のエラーハンドリング改善
- [ ] 位置情報取得失敗時のフォールバック
- [ ] センサー精度低下時の通知

## ビルド情報
- compileSdk: 36
- minSdk: 24
- targetSdk: 36
- Kotlin: 最新
- Compose BOM: androidx.compose.bom
