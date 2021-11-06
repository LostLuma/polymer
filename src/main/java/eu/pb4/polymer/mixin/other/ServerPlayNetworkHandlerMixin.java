package eu.pb4.polymer.mixin.other;

import eu.pb4.polymer.api.resourcepack.PolymerRPUtils;
import eu.pb4.polymer.api.utils.PolymerUtils;
import eu.pb4.polymer.impl.networking.ServerPacketHandler;
import eu.pb4.polymer.impl.interfaces.PolymerNetworkHandlerExtension;
import eu.pb4.polymer.impl.other.ScheduledPacket;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.c2s.play.CustomPayloadC2SPacket;
import net.minecraft.network.packet.c2s.play.ResourcePackStatusC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin implements PolymerNetworkHandlerExtension {
    @Shadow private int ticks;

    @Shadow public abstract void sendPacket(Packet<?> packet);

    @Shadow public abstract ServerPlayerEntity getPlayer();

    @Unique
    private boolean polymer_hasResourcePack = false;
    @Unique
    private ArrayList<ScheduledPacket> polymer_scheduledPackets = new ArrayList<>();
    @Unique private int polymer_protocolVersion = -2;
    @Unique private String polymer_version = "0.0.0";

    @Unique private long polymer_lastSync = 0;

    @Override
    public boolean polymer_hasResourcePack() {
        return this.polymer_hasResourcePack;
    }

    @Override
    public void polymer_setResourcePack(boolean value) {
        this.polymer_hasResourcePack = value;
        PolymerUtils.reloadWorld(this.getPlayer());
    }

    @Override
    public void polymer_schedulePacket(Packet<?> packet, int duration) {
        this.polymer_scheduledPackets.add(new ScheduledPacket(packet, this.ticks + duration));
    }

    @Override
    public boolean polymer_hasPolymer() {
        return this.polymer_protocolVersion > -1;
    }

    @Override
    public int polymer_protocolVersion() {
        return this.polymer_protocolVersion;
    }

    @Override
    public String polymer_version() {
        return this.polymer_version;
    }

    @Override
    public void polymer_setVersion(int protocol, String version) {
        this.polymer_protocolVersion = protocol;
        this.polymer_version = version;
    }

    @Override
    public long polymer_lastSyncUpdate() {
        return this.polymer_lastSync;
    }

    @Override
    public void polymer_saveSyncTime() {
        this.polymer_lastSync = System.currentTimeMillis();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void polymer_sendScheduledPackets(CallbackInfo ci) {
        if (!this.polymer_scheduledPackets.isEmpty()) {
            var array = this.polymer_scheduledPackets;
            this.polymer_scheduledPackets = new ArrayList<>();

            for (var entry : array) {
                if (entry.time() <= this.ticks) {
                    this.sendPacket(entry.packet());
                } else {
                    this.polymer_scheduledPackets.add(entry);
                }
            }
        }
    }

    @Inject(method = "onCustomPayload", at = @At("HEAD"))
    private void polymer_catchPackets(CustomPayloadC2SPacket packet, CallbackInfo ci) {
        if (packet.getChannel().getNamespace().equals(PolymerUtils.ID)) {
            ServerPacketHandler.handle((ServerPlayNetworkHandler) (Object) this, packet.getChannel(), packet.getData());
        }
    }

    @Inject(method = "onResourcePackStatus", at = @At("TAIL"))
    private void polymer_changeStatus(ResourcePackStatusC2SPacket packet, CallbackInfo ci) {
        if (PolymerRPUtils.shouldCheckByDefault()) {
            this.polymer_setResourcePack(switch (packet.getStatus()) {
                case ACCEPTED, SUCCESSFULLY_LOADED -> true;
                case DECLINED, FAILED_DOWNLOAD -> false;
            });
        }
    }
}
