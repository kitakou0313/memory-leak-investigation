# メモリリークの調査に使えるコマンド集

## jmap -histo:live {JavaのプロセスID}
- 実行時点でのObject、バイト数を表示
- -histo:liveとすることで集計前にFull GCさせ、生き残っているObjectのみを対象にする
- ある程度時間を空けて数回実行することで3点ヒープダンプみたいにできそう

```
$ jmap -histo:live 88951 | head -n 20
 num     #instances         #bytes  class name (module)
-------------------------------------------------------
   1:        132600      630946504  [B (java.base@25.0.2)
   2:          1598        9024608  [Ljdk.internal.vm.FillerElement; (java.base@25.0.2)
   3:         63779        2040928  java.util.HashMap$Node (java.base@25.0.2)
   4:         71216        1709184  java.lang.String (java.base@25.0.2)
   5:           267         559232  [Ljava.util.HashMap$Node; (java.base@25.0.2)
   6:          1712         226360  java.lang.Class (java.base@25.0.2)
   7:          1114         149864  [Ljava.lang.Object; (java.base@25.0.2)
   8:           216          56800  [C (java.base@25.0.2)
   9:          1194          45784  [I (java.base@25.0.2)
  10:          1121          35872  java.util.concurrent.ConcurrentHashMap$Node (java.base@25.0.2)
  11:            41          17104  [Ljava.util.concurrent.ConcurrentHashMap$Node; (java.base@25.0.2)
  12:             1          16400  [Ljdk.internal.ref.PhantomCleanable; (java.base@25.0.2)
  13:           271          13008  java.util.HashMap (java.base@25.0.2)
  14:           341          10912  jdk.internal.math.FDBigInteger (java.base@25.0.2)
  15:           367           8808  java.lang.module.ModuleDescriptor$Exports (java.base@25.0.2)
  16:           213           8520  java.lang.classfile.Opcode (java.base@25.0.2)
  17:           145           7256  [Ljava.lang.String; (java.base@25.0.2)
  18:           256           6144  java.lang.Long (java.base@25.0.2)

# 時間を空けた後
$ jmap -histo:live 88951 | head -n 20
 num     #instances         #bytes  class name (module)
-------------------------------------------------------
   1:        299712     1490570632  [B (java.base@25.0.2)
   2:          3250       19949872  [Ljdk.internal.vm.FillerElement; (java.base@25.0.2)
   3:        147335        4714720  java.util.HashMap$Node (java.base@25.0.2)
   4:        154772        3714528  java.lang.String (java.base@25.0.2)
   5:           267        1083520  [Ljava.util.HashMap$Node; (java.base@25.0.2)
   6:          1712         226360  java.lang.Class (java.base@25.0.2)
   7:          1114         149864  [Ljava.lang.Object; (java.base@25.0.2)
   8:           216          56800  [C (java.base@25.0.2)
   9:          1194          45784  [I (java.base@25.0.2)
  10:          1121          35872  java.util.concurrent.ConcurrentHashMap$Node (java.base@25.0.2)
  11:            41          17104  [Ljava.util.concurrent.ConcurrentHashMap$Node; (java.base@25.0.2)
  12:             1          16400  [Ljdk.internal.ref.PhantomCleanable; (java.base@25.0.2)
  13:           271          13008  java.util.HashMap (java.base@25.0.2)
  14:           341          10912  jdk.internal.math.FDBigInteger (java.base@25.0.2)
  15:           367           8808  java.lang.module.ModuleDescriptor$Exports (java.base@25.0.2)
  16:           213           8520  java.lang.classfile.Opcode (java.base@25.0.2)
  17:           145           7256  [Ljava.lang.String; (java.base@25.0.2)
  18:           256           6144  java.lang.Long (java.base@25.0.2)
```

## ヒープダンプによる調査
jmap, jcmdコマンドで取得
```
jmap -dump:live,format=b,file=/tmp/heap.hprof <pid>

jcmd <pid> GC.heap_dump /tmp/heap.hprof
```

取得したheap dumpはInteliJのProfilerなどで調査可能
- https://pleiades.io/help/idea/create-a-memory-snapshot.html
- https://pleiades.io/help/idea/read-the-memory-snapshot.html#productivity-tips

ヒープダンプ全体+クラスごとの調査とインスタンスごとに調査できる。
- ヒープダンプ全体の調査
  - GC root
    - GCで参照のグラフを辿る際のrootとなるobject
      - 実行中のstackで生きているローカル変数
        - この時はMerged Pathが特定のクラスになる
      - thread object
      - クラスにより参照されているstatic変数
        - この時はMerged Pathが `jdk.internal.loader.Classloader$AppClassLoader`になる（はず）
    - `逆に言えばここまでしか辿れない`
      - どのメソッドで追加されているか などは特定後に調査する必要がある
  - Merged Path
    - そのクラスのインスタンスがどのクラスのインスタンスから参照されているか
- インスタンスごとの調査
  - Dominatorsで参照元のルートを辿れる
  - shotest pathでインスタンスごとにどこで参照されているかを辿れる

## GCの仕様
- https://www.w3resource.com/java-tutorial/garbage-collection-in-java.php

## 疑問
- なぜ常に配列のものがある？
  - byte[], char[]
  - -> プリミティブな型なので、単体だとサイズが変わらない
- なぜbyte[]のルートにStringがある？
  - Stringのインスタンスの内部で保持してる？
- staticのHashMapに入れていた時の話
  - なぜjdk.internal.loader.Classloader$AppClassLoaderが最終的なMerged Pathになる？
    - staticな変数はjava.lang.Classのインスタンスがクラスごとにあってそこで管理されるから？