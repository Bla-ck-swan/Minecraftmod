package com.yourname.networkop.mixin;

import com.yourname.networkop.network.UnbatchHandler;
import io.netty.channel.ChannelPipeline;
import net.minecraft.network.BandwidthDebugMonitor;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 客户端：连接建立时在 pipeline 插入拆帧 handler（自动适配，无需配置）。
 *  - flow 是连接的接收方向：客户端=CLIENTBOUND，服务端=SERVERBOUND，仅客户端方向插入
 *  - UnbatchHandler 自适应：收到自定义帧（[VarInt 0]+"NTOP" magic）就拆帧解压，
 *    收到原版帧就原样透传 → 无论服务器开不开 zstd，客户端都无需任何配置
 * 插在 "splitter" 之后（原版 decompress 之后、PacketDecoder 之前）
 */
@Mixin(Connection.class)
public abstract class ConnectionMixin {

    @Inject(method = "configureSerialization", at = @At("RETURN"))
    private static void networkop$addUnbatchHandler(ChannelPipeline pipeline, PacketFlow flow, boolean flag, BandwidthDebugMonitor monitor, CallbackInfo ci) {
        if (flow != PacketFlow.CLIENTBOUND) {
            return; // 仅客户端接收方向插入（服务端收不到自定义帧，不插）
        }
        pipeline.addAfter("splitter", "networkop_unbatch", new UnbatchHandler());
    }
}
