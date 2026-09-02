package com.yourname.networkop.client;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NetworkOp 客户端入口
 * 负责接收服务器发来的自定义压缩批帧，zstd 解压后还原成原版帧。
 */
public class NetworkOpClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("networkop-client");

	@Override
	public void onInitializeClient() {
		LOGGER.info("[NetworkOp] 客户端已加载 - 区块批解压就绪");
	}
}
