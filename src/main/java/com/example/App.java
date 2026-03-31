package com.example;

import com.example.leak.CacheMemoryLeak;
import com.example.leak.StaticFieldMemoryLeak;

public class App {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("メモリリーク調査アプリ起動");
        System.out.println("Java version: " + System.getProperty("java.version"));
        System.out.println();

        // どのリークを実行するか引数で選択（デフォルト: cache）
        String mode = args.length > 0 ? args[0] : "cache";

        CacheMemoryLeak cacheMemoryLeak = new CacheMemoryLeak();
        switch (mode) {
            case "cache"    -> cacheMemoryLeak.run();
            case "static"   -> StaticFieldMemoryLeak.run();
            default         -> System.out.println("usage: gradle run --args='<cache|static>'");
        }
    }

    public String greet(String name) {
        return "Hello, " + name + "!";
    }
}
