package com.yourname.elytraslot.mixin;

import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 访问 LivingEntity.equipment（protected 字段，26.2 无公开 getter）。 */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {

    @Accessor("equipment")
    EntityEquipment elytraslot$getEquipment();
}
