package com.yourname.networkop.mixin;

import com.yourname.networkop.NetworkOpConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.Varint21FrameDecoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * 解码侧：把帧长度上限从 21位(3字节, 2MB) 提升到 28位(4字节, 268MB)
 *  - 客户端：始终放宽 —— 自动适配服务器配置（客户端不知道服务器是否开 zstd，必须能收大帧）
 *  - 服务端：仅 networkop.zstd=true 时放宽；zstd=false 保持原版 2MB（完全原版行为）
 */
@Mixin(Varint21FrameDecoder.class)
public abstract class Varint21FrameDecoderMixin {

    /** 物理端判断：客户端=true（FabricLoader 在 netty 运行期必然已初始化） */
    private static final boolean IS_CLIENT = FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;

    /**
     * 构造函数：helperBuf = Unpooled.directBuffer(3)
     * 4 字节长度前缀需要 4 字节容量的 helper buffer
     */
    @ModifyConstant(method = "<init>", constant = @Constant(intValue = 3))
    private static int networkop$largerHelperBuf(int original) {
        return (IS_CLIENT || NetworkOpConfig.zstd) ? 4 : original;
    }

    /**
     * copyVarint：循环上限 3 次，超过抛 CorruptedFrameException("length wider than 21-bit")
     * 改成 4 次，允许读取 4 字节的 28 位长度
     */
    @ModifyConstant(method = "copyVarint", constant = @Constant(intValue = 3))
    private static int networkop$widerVarint21(int original) {
        return (IS_CLIENT || NetworkOpConfig.zstd) ? 4 : original;
    }
}
