package com.example.leak;

import java.util.HashMap;
import java.util.Map;

/**
 * パターン1: キャッシュの解放忘れ
 *
 * HashMap にエントリを追加し続けるが削除しないため、
 * キャッシュが際限なく肥大化してヒープを圧迫する。
 * 対策: WeakHashMap や Caffeine/Guava Cache でサイズ上限・TTL を設ける。
 */
public class CacheMemoryLeak {

    // エントリが削除されることのないキャッシュ
    private static final Map<String, byte[]> cache = new HashMap<>();

    public static void run() throws InterruptedException {
        System.out.println("[CacheMemoryLeak] 開始 — キャッシュへの無限追加");

        Runtime rt = Runtime.getRuntime();
        int i = 0;

        while (true) {
            String key = "key-" + i++;
            // 1エントリあたり 10 KB のデータを追加
            cache.put(key, new byte[10 * 1024]);

            if (i % 1000 == 0) {
                long used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
                System.out.printf("エントリ数: %,d  使用ヒープ: %d MB%n", cache.size(), used);
            }

            Thread.sleep(1);
        }
    }
}
