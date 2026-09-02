package com.yourname.networkop;

import com.github.luben.zstd.Zstd;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

public class NetworkOp implements ModInitializer {
    public static final String MOD_ID = "networkop";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("NetworkOp已加载 - 区块包攒批 zstd压缩");
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            LOGGER.info("客户端模式：自动适配服务器配置（无需设置 networkop.* 参数）");
        } else {
            NetworkOpConfig.load(); // 服务器：加载/生成 config/networkop.properties
            LOGGER.info("配置: zstd={}, debug={}, batch兜底(N)={}, zstdLevel={}（优先级: -D参数 > 配置文件 > 默认值）",
                    NetworkOpConfig.zstd, NetworkOpConfig.debug, NetworkOpConfig.batchSize, NetworkOpConfig.zstdLevel);
        }
        selfTestZstd();
    }

    /** zstd-jni 自检：验证 native 库能否从 jar-in-jar 正常加载 */
    private static void selfTestZstd() {
        try {
            byte[] data = ("networkop zstd self-test " + System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8);
            byte[] compressed = Zstd.compress(data, 3);
            long size = Zstd.decompressedSize(compressed);
            byte[] decompressed = Zstd.decompress(compressed, (int) size);
            boolean ok = java.util.Arrays.equals(data, decompressed);
            if (ok) {
                LOGGER.info("[zstd自检] 通过! {} bytes -> {} bytes -> {} bytes", data.length, compressed.length, decompressed.length);
            } else {
                LOGGER.error("[zstd自检] 失败! 解压数据不一致");
            }
        } catch (Throwable t) {
            LOGGER.error("[zstd自检] native库加载失败: {}", t.toString());
        }
    }
}
