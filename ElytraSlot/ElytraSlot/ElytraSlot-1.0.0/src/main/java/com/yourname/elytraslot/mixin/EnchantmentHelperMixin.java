package com.yourname.elytraslot.mixin;

import com.yourname.elytraslot.ElytraSlotAccess;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * 经验修补候选收集修复。
 *
 * 26.2 的 Mending 走 EnchantmentHelper.getRandomItemWith，它遍历 EquipmentSlot.VALUES
 * 并调用 getItemBySlot —— 而本 mod 把 getItemBySlot(CHEST) 拦截为返回自定义槽鞘翅，
 * 导致真实胸甲被"遮蔽"：胸甲槽放了带经验修补的胸甲、自定义槽放了鞘翅时，
 * 胸甲永远进不了修补候选列表（原版行为被破坏）。
 *
 * 修复：当自定义槽有鞘翅时，候选收集改为 ——
 *  1. CHEST 槽用真实胸甲（equipment.get(CHEST)），不被鞘翅遮蔽；
 *  2. 自定义槽鞘翅作为额外候选（视为 CHEST 槽）。
 * 两者都带经验修补时随机选取，与原版"多件修补装备随机修一件"的行为一致。
 */
@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperMixin {

    @Inject(method = "getRandomItemWith", at = @At("HEAD"), cancellable = true)
    private static void elytraslot$mendingWithRealChest(
            DataComponentType<?> componentType,
            LivingEntity entity,
            Predicate<ItemStack> predicate,
            CallbackInfoReturnable<Optional<EnchantedItemInUse>> cir) {
        if (!(entity instanceof Player player)) return;
        ItemStack elytra = ((ElytraSlotAccess) player).elytraslot$getStack();
        if (elytra.isEmpty() || elytra.getItem() != Items.ELYTRA) return;

        List<EnchantedItemInUse> list = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            ItemStack stack = slot == EquipmentSlot.CHEST
                    ? ((LivingEntityAccessor) player).elytraslot$getEquipment().get(EquipmentSlot.CHEST) // 真实胸甲（不被自定义鞘翅遮蔽）
                    : entity.getItemBySlot(slot);
            elytraslot$collect(list, componentType, predicate, entity, slot, stack);
        }
        // 自定义槽鞘翅作为额外候选（视为 CHEST 槽）
        elytraslot$collect(list, componentType, predicate, entity, EquipmentSlot.CHEST, elytra);
        cir.setReturnValue(Util.getRandomSafe(list, entity.getRandom()));
    }

    /* 与原版 getRandomItemWith 收集逻辑一致：predicate + 附魔含指定 effect 组件 + 槽位匹配 */
    @Unique
    private static void elytraslot$collect(List<EnchantedItemInUse> list, DataComponentType<?> componentType,
                                           Predicate<ItemStack> predicate, LivingEntity entity,
                                           EquipmentSlot slot, ItemStack stack) {
        if (!predicate.test(stack)) return;
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            Enchantment enchantment = entry.getKey().value();
            if (enchantment.effects().has(componentType) && enchantment.matchingSlot(slot)) {
                list.add(new EnchantedItemInUse(stack, slot, entity));
            }
        }
    }
}
