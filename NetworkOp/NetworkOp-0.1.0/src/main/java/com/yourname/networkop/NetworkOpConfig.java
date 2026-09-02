package com.yourname.networkop;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * networkop 配置 —— 仅服务器侧生效，客户端自动适配（无需配置）
 * 优先级：JVM参数(-Dnetworkop.xxx) > config/networkop.properties > 默认值
 * 服务器启动时自动生成 config/networkop.properties（不存在时）
 */
public final class NetworkOpConfig {
    /** 服务器开关：true=启用「攒批 + zstd 压缩」；false=纯原版 zlib，完全不干预任何流程 */
    public static volatile boolean zstd = false;
    /** 调试开关：true=计算并打印「原版zlib压缩率 vs zstd压缩率」；false=不计算、不打印（零开销） */
    public static volatile boolean debug = false;
    /** 兜底攒批数量 N：正常 N=min(玩家视距,服务器视距上限)，仅拿不到视距时使用 */
    public static volatile int batchSize = 8;
    /** zstd 压缩级别（1-22，越大压缩率越高但越耗 CPU；3=官方推荐的平衡点） */
    public static volatile int zstdLevel = 3;

    private NetworkOpConfig() {
    }

    /**
     * 加载配置（仅服务器调用；客户端自动适配，不需要配置）。
     * 优先级：JVM参数(-Dnetworkop.xxx) > config/networkop.properties > 默认值。
     * 配置文件不存在时自动生成默认文件（带中文注释）。
     */
    public static void load() {
        // 1) JVM 参数（显式传入才生效，作为最高优先级覆盖）
        String sysZstd = System.getProperty("networkop.zstd");
        String sysDebug = System.getProperty("networkop.debug");
        String sysBatch = System.getProperty("networkop.batch");
        String sysLevel = System.getProperty("networkop.zstdLevel");

        // 2) 配置文件（<游戏目录>/config/networkop.properties）
        Path configFile = FabricLoader.getInstance().getConfigDir().resolve("networkop.properties");
        boolean z = false, d = false;
        int b = 8, l = 3;
        if (Files.exists(configFile)) {
            Properties props = new Properties();
            try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                props.load(reader);
                z = parseBool(props.getProperty("zstd"), false);
                d = parseBool(props.getProperty("debug"), false);
                b = parseInt(props.getProperty("batch"), 8);
                l = parseInt(props.getProperty("zstdLevel"), 3);
            } catch (IOException e) {
                NetworkOp.LOGGER.error("[networkop] 读取配置文件失败，使用默认值: {}", e.toString());
            }
        } else {
            writeDefaultConfig(configFile); // 不存在则生成默认文件
        }

        // 3) 覆盖顺序：JVM参数 > 配置文件 > 默认值
        zstd = sysZstd != null ? parseBool(sysZstd, z) : z;
        debug = sysDebug != null ? parseBool(sysDebug, d) : d;
        batchSize = sysBatch != null ? parseInt(sysBatch, b) : b;
        zstdLevel = sysLevel != null ? parseInt(sysLevel, l) : l;
    }

    private static boolean parseBool(String v, boolean def) {
        if (v == null) return def;
        String s = v.trim();
        return s.equalsIgnoreCase("true") || s.equals("1") || s.equalsIgnoreCase("yes");
    }

    private static int parseInt(String v, int def) {
        if (v == null) return def;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** 生成默认配置文件（UTF-8，含中文注释） */
    private static void writeDefaultConfig(Path file) {
        String content = """
                # ==================== networkop 配置 ====================
                # 仅服务器侧生效；客户端自动适配，无需配置。
                # 优先级：JVM参数(-Dnetworkop.xxx) > 本文件 > 默认值
                #
                # zstd: 服务器总开关
                #   true  = 启用「区块包攒批 + zstd压缩」(需客户端也装本模组)
                #   false = 纯原版 zlib，完全不干预任何流程
                zstd=false
                #
                # debug: 调试开关
                #   true  = 计算并分别打印「原版zlib压缩率」与「zstd压缩率」
                #   false = 不计算、不打印（零开销）
                debug=false
                #
                # batch: 兜底攒批数量 N
                #   正常情况 N = min(玩家视距, 服务器视距上限)，此值仅在拿不到视距时使用
                batch=8
                #
                # zstdLevel: zstd 压缩级别（1-22）
                #   越大压缩率越高但越耗 CPU；3 = 官方推荐的平衡点
                zstdLevel=3
                """;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
            NetworkOp.LOGGER.info("[networkop] 已生成默认配置文件: {}", file.toAbsolutePath());
        } catch (IOException e) {
            NetworkOp.LOGGER.error("[networkop] 生成配置文件失败: {}", e.toString());
        }
    }
}
