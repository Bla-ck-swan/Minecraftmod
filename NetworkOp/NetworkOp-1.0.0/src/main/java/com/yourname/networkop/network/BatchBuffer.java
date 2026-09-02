package com.yourname.networkop.network;

import com.github.luben.zstd.Zstd;
import com.yourname.networkop.NetworkOp;
import com.yourname.networkop.NetworkOpConfig;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import net.minecraft.network.CompressionEncoder;
import net.minecraft.network.VarInt;
import net.minecraft.world.level.ChunkPos;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 服务端攒批缓冲（压缩后拦截架构）
 *
 * 攒批对象 = 区块包帧（PacketEncoderMixin 打标 → PrependerMixin 扣帧入批）
 * - zlib 启用（默认）：帧 = 原版 zlib 压缩后（[VarInt0][data] 或 [VarInt原始长度][zlib数据]）
 * - zlib 禁用（network-compression-threshold=-1 或 krypton）：帧 = 原始编码字节 [VarInt id][data]
 *   此时 zstd 直接压原始数据（效率最高），子帧标记 0 = 未压缩，客户端原样喂回 PacketDecoder
 * 每个连接一个实例，Channel attr 挂载；只在 Netty 线程访问，天然无锁。
 *
 * 子帧格式：[VarInt 数据长度][VarInt 原始长度][数据]
 *   - 原始长度 >0：数据是 zlib 压缩的，解压后长度 = 原始长度（原版 zlib 语义）
 *   - 原始长度 =0：数据未压缩，原样（原版 [VarInt 0] 透传语义）
 * 自定义帧：[VarInt 0]["NTOP"][版本3][count][zstd数据]
 *
 * 作废：区块离开视距（原版发 ForgetLevelChunkPacket）→ remove() 直接释放，不计数。
 * flush 时机：攒够 N（动态 N = min(玩家视距,服务器视距)）或 ChunkBatchFinished。
 */
public class BatchBuffer {

    /** PacketEncoderMixin 打标用：标记"下一个进入 CompressionEncoder 的帧是区块包"（含坐标） */
    public static final AttributeKey<ChunkPos> PENDING_CHUNK = AttributeKey.valueOf("networkop_pending_chunk");
    /** 每连接攒批缓冲实例 */
    public static final AttributeKey<BatchBuffer> KEY = AttributeKey.valueOf("networkop_batch_buffer");

    private static final int MAGIC = 0x4E544F50; // "NTOP"

    private final Channel channel;
    private final Map<Long, byte[]> queue = new LinkedHashMap<>(); // packed坐标 -> 帧
    private int batchN = NetworkOpConfig.batchSize; // 动态N：PacketEncoderMixin 每次遇区块包时更新

    private BatchBuffer(Channel channel) {
        this.channel = channel;
    }

    public static BatchBuffer get(Channel channel) {
        BatchBuffer buf = channel.attr(KEY).get();
        if (buf == null) {
            buf = new BatchBuffer(channel);
            channel.attr(KEY).set(buf);
        }
        return buf;
    }

    /** 动态 N = min(玩家视距,服务器视距)（由 PacketEncoderMixin 实时计算后更新） */
    public void setBatchN(int n) {
        this.batchN = n;
    }

    /** 入批：frame = 原版 zlib 压缩后的帧（[VarInt0][data] 或 [VarInt原始长度][zlib数据]） */
    public void add(int chunkX, int chunkZ, byte[] frame) {
        queue.put(ChunkPos.pack(chunkX, chunkZ), frame);
        if (queue.size() >= batchN) {
            flush();
        }
    }

    /** 作废释放：区块离开视距/卸载时直接丢弃，不计数 */
    public void remove(int chunkX, int chunkZ) {
        queue.remove(ChunkPos.pack(chunkX, chunkZ));
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /** 攒批 flush：zstd 整批压缩 → 自定义帧 → 写回原版出站管道（经原版 compress + prepender） */
    public void flush() {
        if (queue.isEmpty()) return;
        int count = queue.size();

        // 检测原版 zlib 是否启用：network-compression-threshold=-1（或 krypton 禁用 setupCompression）时
        // pipeline 里没有 CompressionEncoder，攒批帧 = 原始编码字节，zstd 直接压原始（效率最高）
        boolean zlibEnabled = channel.pipeline().get(CompressionEncoder.class) != null;

        ByteBuf merged = Unpooled.buffer();
        int rawTotal = 0;   // 原始（未压缩）总字节
        int zlibTotal = 0;  // zlib 压缩后总字节（zlib 禁用时 = rawTotal）
        for (byte[] frame : queue.values()) {
            if (zlibEnabled) {
                // 解析原版压缩帧头：[VarInt0][data] 未压缩 / [VarInt原始长度][zlib数据] 压缩
                int first = readVarInt(frame, 0);
                int firstLen = varIntSize(first);
                int dataLen = frame.length - firstLen;
                if (first == 0) {
                    // 未压缩：原始 = 数据，zlib后 = 数据
                    rawTotal += dataLen;
                    zlibTotal += dataLen;
                    VarInt.write(merged, dataLen);
                    VarInt.write(merged, 0);
                    merged.writeBytes(frame, firstLen, dataLen);
                } else {
                    // 压缩：原始 = first（原版写的是未压缩长度），zlib后 = 数据
                    rawTotal += first;
                    zlibTotal += dataLen;
                    VarInt.write(merged, dataLen);
                    VarInt.write(merged, first);
                    merged.writeBytes(frame, firstLen, dataLen);
                }
            } else {
                // zlib 禁用：帧 = 原始编码字节 [VarInt id][data]，子帧标记 0（未压缩）
                rawTotal += frame.length;
                zlibTotal += frame.length;
                VarInt.write(merged, frame.length);
                VarInt.write(merged, 0);
                merged.writeBytes(frame);
            }
        }
        byte[] raw = new byte[merged.readableBytes()];
        merged.getBytes(0, raw);
        merged.release();

        // zstd 整批压缩
        byte[] compressed = Zstd.compress(raw, NetworkOpConfig.zstdLevel);

        // 自定义帧（版本3）
        ByteBuf custom = Unpooled.buffer();
        VarInt.write(custom, 0);       // 未压缩标记：客户端原版 decompress 透传
        custom.writeInt(MAGIC);        // "NTOP"
        custom.writeByte(3);           // 版本
        custom.writeShort(count);      // 子帧数
        custom.writeBytes(compressed); // zstd 数据

        channel.writeAndFlush(custom);

        if (NetworkOpConfig.debug) {
            if (zlibEnabled) {
                // 链路式对比：原始 -> 原版zlib逐包（真实） -> zstd整批，每级压缩率（剩余%）+ 总和
                double p1 = zlibTotal * 100.0 / Math.max(1, rawTotal);          // 原版 zlib 压缩率
                double p2 = compressed.length * 100.0 / Math.max(1, zlibTotal); // zstd 二级压缩率
                double p3 = compressed.length * 100.0 / Math.max(1, rawTotal);  // 总和压缩率
                NetworkOp.LOGGER.info("[networkop] 对比 {}个区块包: 原始 {}B -> 原版zlib逐包 {}B ({}%) -> zstd整批 {}B ({}%) | 总和 {}B ({}%)",
                        count, rawTotal, zlibTotal, String.format("%.1f", p1),
                        compressed.length, String.format("%.2f", p2),
                        compressed.length, String.format("%.1f", p3));
            } else {
                // zlib 已禁用：zstd 直接压原始，只打总和压缩率
                double p = compressed.length * 100.0 / Math.max(1, rawTotal);
                NetworkOp.LOGGER.info("[networkop] 对比 {}个区块包: 原始 {}B -> 原版zlib逐包 未压缩(已禁用) -> zstd整批 {}B ({}%) | 总和 {}B ({}%)",
                        count, rawTotal, compressed.length, String.format("%.2f", p),
                        compressed.length, String.format("%.1f", p));
            }
        }
        queue.clear();
    }

    /** 读帧头 VarInt（第1个字节起；原版 VarInt 最长5字节） */
    private static int readVarInt(byte[] data, int offset) {
        int value = 0;
        int shift = 0;
        int b;
        do {
            b = data[offset++] & 0xFF;
            value |= (b & 0x7F) << shift;
            shift += 7;
            if (shift > 35) break;
        } while ((b & 0x80) != 0);
        return value;
    }

    /** VarInt 编码占用字节数 */
    private static int varIntSize(int value) {
        int size = 0;
        do {
            size++;
            value >>>= 7;
        } while (value != 0);
        return size;
    }
}
