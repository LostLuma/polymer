package eu.pb4.polymer.mixin.entity;

import com.mojang.datafixers.util.Pair;
import eu.pb4.polymer.entity.VirtualEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.server.network.EntityTrackerEntry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Mixin(EntityTrackerEntry.class)
public class EntityTrackerEntryMixin {
    @Shadow
    @Final
    private Entity entity;

    @Inject(method = "sendPackets", at = @At("TAIL"))
    private void sendVirtualStuff(Consumer<Packet<?>> sender, CallbackInfo ci) {
        try {
            if (this.entity instanceof VirtualEntity) {
                Map<EquipmentSlot, ItemStack> map = new HashMap<>();

                if (this.entity instanceof LivingEntity) {
                    for (EquipmentSlot slot : EquipmentSlot.values()) {
                        ItemStack stack = ((LivingEntity) this.entity).getEquippedStack(slot);
                        if (!stack.isEmpty()) {
                            map.put(slot, stack);
                        }
                    }
                }

                List<Pair<EquipmentSlot, ItemStack>> list = ((VirtualEntity) this.entity).getVirtualEntityEquipment(map);
                sender.accept(new EntityEquipmentUpdateS2CPacket(this.entity.getEntityId(), list));
                ((VirtualEntity) this.entity).sendPacketsAfterCreation(sender);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
