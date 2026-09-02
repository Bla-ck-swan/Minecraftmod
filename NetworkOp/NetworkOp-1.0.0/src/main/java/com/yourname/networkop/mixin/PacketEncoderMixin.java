package com.yourname.networkop.mixin;

import com.yourname.networkop.NetworkOpConfig;
import com.yourname.networkop.network.BatchBuffer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketEncoder;
import net.minecraft.network.PacketListener;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 服务端「打标 + 调度」（压缩后拦截架构，攒批本体在 BatchBuffer / CompressionEncoderMixin）
 *
 * 时序：原版编码(PacketEncoder) -> 原版zlib压缩(CompressionEncoder) -> [模组拦截压缩后帧] -> 攒批
 *       -> 攒够N / ChunkBatchFinished -> zstd整批 -> 自定义帧发送
 *
 * 本 mixin 只做三件事（都在 PacketEncoder.encode 里，编码完成后）：
 *  - 区块包 -> channel attr 打标 PENDING_CHUNK = 坐标（告诉 CompressionEncoderMixin"下一个
 *              进 CompressionEncoder 的帧是区块包"），并更新 BatchBuffer.n = min(玩家视距,服务器视距)
 *              —— 区块包照常走原版编码 + 原版 zlib 压缩（out 不清空！）
 *  - 卸载包 -> BatchBuffer.remove(x,z)：攒批中的区块离开视距直接作废释放，不计数
 *  - ChunkBatchFinished -> BatchBuffer.flush()：保持原版批次时序
 *
 * 打标必须在 PacketEncoder 层：CompressionEncoder 层只有字节流，不知道包类型/坐标。
 * 客户端装上无副作用：客户端出站不会有区块包，PENDING_CHUNK 永远不被打标，攒批不触发。
 */
@Mixin(PacketEncoder.class)
public abstract class PacketEncoderMixin {

    @Unique
    private ChannelHandlerContext networkop$ctx;

    @Inject(method = "encode(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;Lio/netty/buffer/ByteBuf;)V", at = @At("HEAD"))
    private void networkop$saveCtx(ChannelHandlerContext ctx, Packet<?> packet, ByteBuf out, CallbackInfo ci) {
        this.networkop$ctx = ctx;
    }

    @Redirect(
            method = "encode(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;Lio/netty/buffer/ByteBuf;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/codec/StreamCodec;encode(Ljava/lang/Object;Ljava/lang/Object;)V")
    )
    private void networkop$interceptEncode(StreamCodec codec, Object outObj, Object packetObj) {
        // 原编码逻辑（区块包也正常编码、正常发送给 CompressionEncoder，out 不清空！）
        codec.encode(outObj, packetObj);

        if (!NetworkOpConfig.zstd) return;
        if (!(outObj instanceof ByteBuf out)) return;
        if (this.networkop$ctx == null) return;

        if (packetObj instanceof ClientboundLevelChunkWithLightPacket chunkPacket) {
            // 打标：下一个进入 CompressionEncoder 的帧是区块包（含坐标）
            this.networkop$ctx.channel().attr(BatchBuffer.PENDING_CHUNK)
                    .set(new ChunkPos(chunkPacket.getX(), chunkPacket.getZ()));
            // 更新动态 N（每次遇区块包实时计算）
            BatchBuffer.get(this.networkop$ctx.channel()).setBatchN(this.networkop$batchSize());
        } else if (packetObj instanceof ClientboundForgetLevelChunkPacket unloadPacket) {
            // 作废释放：攒批中的区块离开视距直接丢弃，不计数
            BatchBuffer.get(this.networkop$ctx.channel())
                    .remove(unloadPacket.pos().x(), unloadPacket.pos().z());
        } else if (packetObj instanceof ClientboundChunkBatchFinishedPacket) {
            // 原版批次结束：强制 flush（保持批次时序）
            BatchBuffer.get(this.networkop$ctx.channel()).flush();
        }
    }

    /**
     * 动态攒批数量 N = min(玩家视距, 服务器视距上限)
     * 从当前连接的玩家信息实时取；配置阶段/异常时退回 networkop.batch 兜底值。
     */
    @Unique
    private int networkop$batchSize() {
        try {
            if (this.networkop$ctx == null) return NetworkOpConfig.batchSize;
            ChannelPipeline pipeline = this.networkop$ctx.channel().pipeline();
            Connection conn = pipeline.get(Connection.class);
            if (conn == null) return NetworkOpConfig.batchSize;
            PacketListener listener = conn.getPacketListener();
            if (listener instanceof ServerGamePacketListenerImpl gameListener) {
                ServerPlayer player = gameListener.getPlayer();
                int playerView = player.clientInformation().viewDistance();
                int serverView = player.level().getServer().getPlayerList().getViewDistance();
                if (playerView <= 0 || serverView <= 0) return NetworkOpConfig.batchSize;
                return Math.min(playerView, serverView);
            }
        } catch (Throwable t) {
            // 任何异常都不影响主流程，退回兜底值
        }
        return NetworkOpConfig.batchSize;
    }
}
