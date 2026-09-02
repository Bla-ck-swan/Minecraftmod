package com.yourname.networkop.mixin;

import com.yourname.networkop.NetworkOpConfig;
import com.yourname.networkop.network.BatchBuffer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Varint21LengthFieldPrepender;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 服务端攒批扣帧点（写帧头之前）：
 * 时序：PacketEncoder编码(打标) -> CompressionEncoder原版zlib压缩 -> [本mixin扣帧] -> 攒批 -> zstd -> 自定义帧
 *
 * 扣帧放在 prepender（压缩之后、写帧头之前）：
 *  - 攒批帧 = 原版压缩后的完整帧（[VarInt原始长度][zlib数据] 或 [0][数据]），BatchBuffer 直接使用
 *  - ci.cancel() 吞帧：out 保持空，MessageToByteEncoder 框架会写 EMPTY_BUFFER，
 *    空缓冲沿出站传播到 socket 只写 0 字节、不产生网络数据，客户端收不到任何东西 ✓
 *
 * 不能改在 CompressionEncoder 层 out.clear() 吞帧（历史教训）：
 *  框架对空 out 会补发 EMPTY_BUFFER 给下游，prepender 对空缓冲写 0 长度帧头，
 *  客户端 Varint21FrameDecoder 抛 "Frame length cannot be zero" 直接断开。
 *
 * 防御：空帧（readableBytes==0）直接 cancel，不写 0 长度帧头。
 * 客户端加载本 mixin 无副作用：客户端出站不打标，PENDING_CHUNK 恒为 null，只做空帧防御。
 */
@Mixin(Varint21LengthFieldPrepender.class)
public abstract class Varint21LengthFieldPrependerMixin {

    @Inject(method = "encode(Lio/netty/channel/ChannelHandlerContext;Lio/netty/buffer/ByteBuf;Lio/netty/buffer/ByteBuf;)V", at = @At("HEAD"), cancellable = true)
    private void networkop$captureChunkFrames(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out, CallbackInfo ci) {
        // 防御：空帧不写长度头（否则客户端 "Frame length cannot be zero" 断开）
        if (!msg.isReadable()) {
            ci.cancel();
            return;
        }
        if (!NetworkOpConfig.zstd) {
            return;
        }
        ChunkPos pos = ctx.channel().attr(BatchBuffer.PENDING_CHUNK).getAndSet(null);
        if (pos == null) {
            return; // 非区块包：原版照常写帧头
        }
        // 扣帧：压缩后的区块包帧（[VarInt原始长度][zlib数据] 或 [0][数据]）
        byte[] frame = new byte[msg.readableBytes()];
        msg.getBytes(msg.readerIndex(), frame);
        BatchBuffer.get(ctx.channel()).add(pos.x(), pos.z(), frame);
        ci.cancel(); // 吞掉：不写帧头
    }
}
