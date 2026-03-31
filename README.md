# memory-leak-investigation

シンプルなJavaアプリケーションのサンプルプロジェクト。Gradleによるビルド・テスト・実行環境を提供します。

## 構成

```
memory-leak-investigation/
├── build.gradle                     # Gradleビルド設定
├── settings.gradle                  # プロジェクト名設定
└── src/
    ├── main/java/com/example/
    │   └── App.java                 # メインクラス
    └── test/java/com/example/
        └── AppTest.java             # JUnitテスト
```

## 必要環境

- Java 25+
- Gradle 9+

## コマンド

| コマンド | 説明 |
|---|---|
| `gradle build` | コンパイル・テスト・JARの生成 |
| `gradle run` | アプリケーションの実行 |
| `gradle test` | テストのみ実行 |
| `gradle clean` | ビルド成果物の削除 |
| `gradle fatJar` | 実行可能JAR（依存込み）の生成 |

## 起動方法

### 1. Gradle 経由で実行

```bash
# キャッシュリーク（デフォルト）
gradle run --args='cache'

# static フィールドリーク
gradle run --args='static'
```

### 2. 実行可能 JAR として実行

先に fat JAR をビルドする:

```bash
gradle fatJar
```

生成されるファイル: `build/libs/memory-leak-investigation-1.0.0-all.jar`

```bash
# キャッシュリーク
java -jar build/libs/memory-leak-investigation-1.0.0-all.jar cache

# static フィールドリーク
java -jar build/libs/memory-leak-investigation-1.0.0-all.jar static
```

### 3. ヒープ上限を絞って OutOfMemoryError を再現

`-Xmx` でヒープ上限を指定すると短時間で OOM が発生する:

```bash
java -Xmx32m -jar build/libs/memory-leak-investigation-1.0.0-all.jar static
```

#### 実行例（ヒープ 32MB / static モード）

```
メモリリーク調査アプリ起動
Java version: 25.0.2

[StaticFieldMemoryLeak] 開始 — static フィールドへの無限蓄積
登録数: 500  使用ヒープ: 11 MB
登録数: 1,000  使用ヒープ: 22 MB
登録数: 1,500  使用ヒープ: 31 MB

Exception: java.lang.OutOfMemoryError thrown from the UncaughtExceptionHandler in thread "main"
```

## メモリダンプを使った調査

アプリを起動した状態で別ターミナルから以下のコマンドを実行する。
`<pid>` は `jps -l` で確認する。

### PID の確認

**`jps`** (Java Virtual Machine Process Status Tool) は実行中のJavaプロセスの一覧とPIDを表示する。`-l` オプションでメインクラスまたはJARのフルパスが確認できる。調査対象プロセスのPIDを特定するために最初に実行する。

```bash
jps -l
# 例: 16015 build/libs/memory-leak-investigation-1.0.0-all.jar
```

### ヒープ使用状況の確認（リアルタイム）

**`jstat`** (JVM Statistics Monitoring Tool) はGCの統計情報をリアルタイムで収集・表示する。ヒープの各世代（Eden / Old / Metaspace）の使用率やGC回数・時間を一定間隔で出力できる。`-gcutil` オプションで使用率（%）が得られ、Old世代（O列）が増加し続けている場合はメモリリークの疑いがある。

```bash
# GC統計を1秒おきに5回表示（O列=Old世代使用率が増え続けていたらリーク疑い）
jstat -gcutil <pid> 1s 5

# 出力例:
#   S0     S1     E      O      M     CCS    YGC     YGCT     FGC    FGCT     CGC    CGCT       GCT
#      -      -   0.00  40.79  65.47  15.24    0    0.000     1    0.003     0    0.000     0.003
#      -      -  21.74  40.79  65.47  15.24    0    0.000     1    0.003     0    0.000     0.003
```

**`jcmd GC.heap_info`** は対象プロセスのヒープ全体のサマリ（予約済み・コミット済み・使用中のサイズ、GCリージョン数）をスナップショットとして出力する。`jstat` で傾向を確認したあと、現時点の詳細を把握するために使う。

```bash
# ヒープ全体のサマリ（使用量・リージョン数）
jcmd <pid> GC.heap_info

# 出力例:
# garbage-first heap   total reserved 2007040K, committed 65536K, used 28390K
#  region size 1024K, 11 young (11264K), 0 survivors (0K)
```

### オブジェクトヒストグラム（どのクラスが何バイト占有しているか）

**`jmap -histo`** はヒープ上のオブジェクトをクラスごとに集計し、インスタンス数・バイト数の降順で一覧表示する。`live` オプションを付けると直前にFull GCが走り、到達不能オブジェクトを除いた生存オブジェクトのみが対象になる。ダンプ取得より軽量で、リークしているクラスの当たりをつけるのに向いている。

```bash
# 生存オブジェクトのみ集計し、上位20件を表示
jmap -histo:live <pid> | head -25

# 出力例（cacheモード実行中）:
#  num     #instances         #bytes  class name
# -------------------------------------------------------
#    1:         13064       16131344  [B         ← byte[] が肥大化
#    2:         11447         274728  java.lang.String
#    6:          4012         128384  java.util.HashMap$Node  ← Mapエントリが増加
```

**`jcmd GC.class_histogram`** は `jmap -histo` と同等の情報を `jcmd` 経由で取得する。接続方式が異なるため、`jmap` が失敗する環境（コンテナなど）でも動作しやすい。

```bash
# jcmd 経由でも同様に取得可能
jcmd <pid> GC.class_histogram | head -25
```

### ヒープダンプの取得

**`jmap -dump`** / **`jcmd GC.heap_dump`** はヒープ全体をhprof形式のバイナリファイルとして書き出す。ヒストグラムと異なりオブジェクト間の参照関係も含まれるため、どの参照がオブジェクトを生かし続けているか（GCルートからの保持経路）を後から詳細に解析できる。ファイルサイズはヒープサイズに比例して大きくなるため、解析にはEclipse Memory Analyzer (MAT) などのツールを使う。

```bash
# 生存オブジェクトのみをhprofバイナリ形式で出力
jmap -dump:live,format=b,file=./heap-dump/heap.hprof <pid>

# jcmd経由でも取得可能（OOM時も含め確実に取得できる）
jcmd <pid> GC.heap_dump /tmp/heap.hprof

# OOM発生時に自動でダンプを出力するJVMオプション
java -XX:+HeapDumpOnOutOfMemoryError \
     -XX:HeapDumpPath=/tmp/heap.hprof \
     -jar build/libs/memory-leak-investigation-1.0.0-all.jar static
```

ダンプファイルは [Eclipse Memory Analyzer (MAT)](https://eclipse.dev/mat/) や IntelliJ IDEA の Profiler で開くと、リークの根本原因を特定できる。

### Java Flight Recorder (JFR) によるプロファイリング

**Java Flight Recorder (JFR)** はJVMに組み込まれた低オーバーヘッドのプロファイラで、メモリアロケーション・GC・スレッド・I/Oなどのイベントを継続的に記録する。`settings=profile` を指定するとアロケーションのスタックトレースも取得でき、どのコードパスがメモリを確保しているかを特定できる。記録はhprofではなく `.jfr` 形式で保存され、`jfr view` コマンドでターミナルから直接分析できる。

**`jcmd JFR.start`** で記録を開始し、`JFR.dump` で即時書き出し、`JFR.stop` で記録を停止する。

```bash
# 記録開始（profile設定でアロケーションスタックトレースも取得）
jcmd <pid> JFR.start name=leak settings=profile duration=30s filename=/tmp/leak.jfr

# 記録終了を待たずに即時ダンプする場合
jcmd <pid> JFR.dump name=leak filename=/tmp/leak.jfr
```

記録後に **`jfr view`** でターミナルから直接分析できる。`memory-leaks-by-class` はGC後も長時間ヒープに残り続けているオブジェクトをクラス別に集計し、リーク候補を示す。`allocation-by-class` はアロケーション圧力（全割り当てに占める割合）を示し、過剰にメモリを消費しているクラスを特定できる。

```bash
# リーク候補クラス（長時間ヒープに残り続けているオブジェクト）
jfr view memory-leaks-by-class /tmp/leak.jfr

# 出力例（cacheモード 10秒計測）:
#                  Memory Leak Candidates by Class
# Alloc. Time  Object Class                    Object Age  Heap Usage
# -----------  ------------------------------  ----------  ----------
# 18:05:16     java.util.HashMap$Node[]            6.14 s    33.6 MB  ← リーク原因
# 18:05:22     byte[]                               169 ms    33.6 MB

# アロケーション圧力（どのクラスが最もメモリを割り当てているか）
jfr view allocation-by-class /tmp/leak.jfr

# 出力例:
#  Object Type                       Allocation Pressure
#  --------------------------------  -------------------
#  byte[]                                         79.09%
#  java.util.HashMap$Node[]                        1.25%
```