package eu.pb4.polymer.mixin.item;

import eu.pb4.polymer.item.VirtualItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BucketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {

    @Shadow
    public ServerPlayerEntity player;

    @Shadow
    public abstract void sendPacket(Packet<?> packet);

    @Inject(method = "onPlayerInteractBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V", shift = At.Shift.AFTER))
    private void resendToolIfItsBlockClientSide(PlayerInteractBlockC2SPacket packet, CallbackInfo ci) {
        ItemStack itemStack = this.player.getStackInHand(packet.getHand());

        if (itemStack.getItem() instanceof VirtualItem && (((VirtualItem) itemStack.getItem()).getVirtualItem() instanceof BlockItem || ((VirtualItem) itemStack.getItem()).getVirtualItem() instanceof BucketItem)) {
            this.sendPacket(new ScreenHandlerSlotUpdateS2CPacket(this.player.playerScreenHandler.syncId, packet.getHand() == Hand.MAIN_HAND ? 36 + this.player.inventory.selectedSlot : 45, itemStack));
        }
    }

    @Redirect(method = "onPlayerInteractBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayNetworkHandler;sendPacket(Lnet/minecraft/network/Packet;)V", ordinal = 0))
    private void replaceOriginalCall1(ServerPlayNetworkHandler serverPlayNetworkHandler, Packet<?> packet) {
    }

    @Redirect(method = "onPlayerInteractBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayNetworkHandler;sendPacket(Lnet/minecraft/network/Packet;)V", ordinal = 1))
    private void replaceOriginalCall2(ServerPlayNetworkHandler serverPlayNetworkHandler, Packet<?> packet) {
    }


    @Inject(method = "onPlayerInteractBlock", at = @At("TAIL"))
    private void updateMoreBlocks(PlayerInteractBlockC2SPacket packet, CallbackInfo ci) {
        BlockPos base = packet.getBlockHitResult().getBlockPos().offset(packet.getBlockHitResult().getSide());

        for (Direction direction : Direction.values()) {
            this.player.networkHandler.sendPacket(new BlockUpdateS2CPacket(this.player.world, base.offset(direction)));
        }
    }

    @Unique
    private List<ItemStack> armorItems = new ArrayList<>();

    @Inject(method = "onClickSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;updateLastActionTime()V", shift = At.Shift.AFTER))
    private void storeSomeData(ClickSlotC2SPacket packet, CallbackInfo ci) {
        if (this.player.currentScreenHandler == this.player.playerScreenHandler) {
            for (ItemStack stack : this.player.inventory.armor) {
                armorItems.add(stack.copy());
            }
        }
    }

    @Inject(method = "onClickSlot", at = @At("TAIL"))
    private void resendArmorIfNeeded(ClickSlotC2SPacket packet, CallbackInfo ci) {
        if (this.player.currentScreenHandler == this.player.playerScreenHandler && packet.getSlot() != -999) {
            int x = 0;
            for (ItemStack stack : this.player.inventory.armor) {
                if (stack.getItem() instanceof VirtualItem && !ItemStack.areEqual(this.armorItems.get(x), stack)) {
                    this.sendPacket(new ScreenHandlerSlotUpdateS2CPacket(this.player.playerScreenHandler.syncId,
                            8 - x,
                            stack));

                    if (packet.getSlot() != 8 - x) {
                        this.sendPacket(new ScreenHandlerSlotUpdateS2CPacket(this.player.playerScreenHandler.syncId, packet.getSlot(),
                                this.player.playerScreenHandler.getSlot(packet.getSlot()).getStack()));
                    }

                    this.sendPacket(new ScreenHandlerSlotUpdateS2CPacket(-1,
                            0,
                            this.player.inventory.getCursorStack()));
                    return;
                }
                x++;
            }
        }

        this.armorItems.clear();
    }
}
