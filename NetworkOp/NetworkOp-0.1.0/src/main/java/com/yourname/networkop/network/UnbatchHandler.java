package com.yourname.networkop.network;

import com.github.luben.zstd.Zstd;
import com.yourname.networkop.NetworkOp;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.network.VarInt;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.InflaterInputStream;

/**
 * 客户端拆帧 handler（插在 splitter 之后、原版 PacketDecoder 之前）
 *
 * 收到自定义批帧：[magic "NTOP"][版本3][count][zstd数据]（有压缩时 decompress 已消费 [VarInt 0] 前缀）
 * 处理：zstd 解压 -> 按子帧切分 count 个子帧
 *      子帧 = [VarInt 数据长度][VarInt 原始长度][数据]
 *        - 原始长度 >0：数据是 zlib 压缩的（原版 zlib 语义），解压后长度 = 原始长度
 *        - 原始长度 =0：数据未压缩，原样
 *      -> 逐个 fireChannelRead 给下游 PacketDecoder 正常解码（本 handler 位于 decompress 之后）
 *
 * 版本历史：ver1 = 单层zstd；ver2 = 模组自压zlib逐包+zstd；ver3 = 原版zlib压缩后拦截+zstd（当前）
 */
public class UnbatchHandler extends ChannelInboundHandlerAdapter {

    private static final int MAGIC = 0x4E544F50; // "NTOP"
    private static final int MAX_BATCH_BYTES = 268_435_455; // 268MB
    private static final int FRAME_VERSION = 3;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof ByteBuf buf && buf.readableBytes() >= 4) {
            int ri = buf.readerIndex();
            int dataStart = -1;

            if (buf.readableBytes() >= 5 && buf.getByte(ri) == 0 && buf.getInt(ri + 1) == MAGIC) {
                // 无压缩场景：decompress 不存在，帧带 [VarInt 0] 前缀
                dataStart = ri + 1;
            } else if (buf.getInt(ri) == MAGIC) {
                // 有压缩场景：decompress 已消费 [VarInt 0]
                dataStart = ri;
            }

            if (dataStart >= 0) {
                try {
                    ByteBuf data = buf.slice(dataStart, buf.readableBytes() - (dataStart - ri));
                    data.readInt();                       // magic
                    int version = data.readByte();        // 版本
                    if (version != FRAME_VERSION) {
                        throw new RuntimeException("不支持的帧版本: " + version + "（请更新 networkop 模组）");
                    }
                    int count = data.readUnsignedShort(); // 子帧数
                    if (count <= 0 || count > 256) {
                        throw new RuntimeException("非法子帧数: " + count);
                    }
                    byte[] compressed = new byte[data.readableBytes()];
                    data.readBytes(compressed);

                    long size = Zstd.decompressedSize(compressed);
                    if (size <= 0 || size > MAX_BATCH_BYTES) {
                        throw new RuntimeException("非法批大小: " + size);
                    }
                    byte[] raw = Zstd.decompress(compressed, (int) size);
                    ByteBuf rawBuf = Unpooled.wrappedBuffer(raw);
                    try {
                        for (int i = 0; i < count; i++) {
                            if (!rawBuf.isReadable()) {
                                throw new RuntimeException("批数据截断: 第 " + i + "/" + count + " 个子帧");
                            }
                            // 子帧 = [VarInt 数据长度][VarInt 原始长度][数据]
                            int len = VarInt.read(rawBuf);
                            if (len <= 0 || len > rawBuf.readableBytes()) {
                                throw new RuntimeException("非法子帧长度: " + len);
                            }
                            int uncompressedLen = VarInt.read(rawBuf);
                            if (uncompressedLen < 0 || uncompressedLen > MAX_BATCH_BYTES) {
                                throw new RuntimeException("非法原始长度: " + uncompressedLen);
                            }
                            if (len > rawBuf.readableBytes()) {
                                throw new RuntimeException("非法子帧长度(读头后): " + len);
                            }
                            byte[] subData = new byte[len];
                            rawBuf.readBytes(subData);
                            // copy()：独立引用计数，避免与 rawBuf 共享 refCnt 导致 IllegalReferenceCountException
                            ByteBuf sub;
                            if (uncompressedLen > 0) {
                                byte[] inflated = zlibInflate(subData);
                                if (inflated.length != uncompressedLen) {
                                    throw new RuntimeException("zlib解压长度不符: " + inflated.length + " != " + uncompressedLen);
                                }
                                sub = Unpooled.wrappedBuffer(inflated);
                            } else {
                                sub = Unpooled.wrappedBuffer(subData);
                            }
                            ctx.fireChannelRead(sub); // 直接给 PacketDecoder（此 handler 位于 decompress 之后）
                        }
                    } finally {
                        rawBuf.release();
                    }
                    buf.release();
                    return;
                } catch (Throwable t) {
                    NetworkOp.LOGGER.error("[networkop] 批解压失败，断开连接: {}", t.toString());
                    ctx.close();
                    buf.release();
                    return;
                }
            }
        }
        ctx.fireChannelRead(msg);
    }

    /** zlib 解压（服务端由原版 CompressionEncoder 按 level6 压缩，这里用 InflaterInputStream 还原） */
    private static byte[] zlibInflate(byte[] data) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length * 2);
        try (InflaterInputStream iis = new InflaterInputStream(new ByteArrayInputStream(data))) {
            byte[] chunk = new byte[8192];
            int n;
            while ((n = iis.read(chunk)) != -1) {
                bos.write(chunk, 0, n);
            }
        }
        return bos.toByteArray();
    }
}
