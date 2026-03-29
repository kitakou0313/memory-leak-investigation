package com.example.leak;

import java.util.ArrayList;
import java.util.List;

/**
 * パターン3: static フィールドへの無限蓄積
 *
 * static フィールドはクラスローダーが生きている限り GC されない。
 * そこに大きなオブジェクトを追加し続けると、
 * ヒープが解放されずに OOM に至る。
 * 対策: static コレクションへの追加は行わない。必要なら上限を設ける。
 */
public class StaticFieldMemoryLeak {

    // static リストは GC のルートとなり中身が解放されない
    private static final List<LargeObject> REGISTRY = new ArrayList<>();

    static class LargeObject {
        private final byte[] data;
        private final int id;

        LargeObject(int id, int sizeKb) {
            this.id = id;
            this.data = new byte[sizeKb * 1024];
        }

        int getId() { return id; }
    }

    public static void run() throws InterruptedException {
        System.out.println("[StaticFieldMemoryLeak] 開始 — static フィールドへの無限蓄積");

        Runtime rt = Runtime.getRuntime();
        int i = 0;

        while (true) {
            // 1オブジェクトあたり 20 KB を static リストへ追加
            REGISTRY.add(new LargeObject(i++, 20));

            if (i % 500 == 0) {
                long used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
                System.out.printf("登録数: %,d  使用ヒープ: %d MB%n", REGISTRY.size(), used);
            }

            Thread.sleep(1);
        }
    }
}
