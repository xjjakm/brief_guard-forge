package cn.blockforge.generated.briefguard;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class BriefsNetwork {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            GeneratedMod.id("network"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static int nextId;

    private BriefsNetwork() {}

    public static void init() {
        CHANNEL.registerMessage(nextId++, SyncPacket.class, SyncPacket::encode, SyncPacket::decode, SyncPacket::handle);
        CHANNEL.registerMessage(nextId++, RemovePacket.class, RemovePacket::encode, RemovePacket::decode, RemovePacket::handle);
    }

    public static void sync(ServerPlayer player) {
        SyncPacket packet = new SyncPacket(player.getId(), BriefsCapability.get(player).getStack());
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), packet);
    }

    /** Client -> server: ask the server to take the worn briefs off (HUD slot click). */
    public static void sendRemove() {
        CHANNEL.send(PacketDistributor.SERVER.with(() -> null), new RemovePacket());
    }

    private static final class SyncPacket {
        private final int entityId;
        private final ItemStack stack;

        private SyncPacket(int entityId, ItemStack stack) {
            this.entityId = entityId;
            this.stack = stack.copy();
        }

        private static void encode(SyncPacket packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.entityId);
            buffer.writeItem(packet.stack);
        }

        private static SyncPacket decode(FriendlyByteBuf buffer) {
            return new SyncPacket(buffer.readVarInt(), buffer.readItem());
        }

        private static void handle(SyncPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    cn.blockforge.generated.briefguard.client.BriefsClient.sync(packet.entityId, packet.stack)));
            context.setPacketHandled(true);
        }
    }

    private static final class RemovePacket {
        private RemovePacket() {}

        private static void encode(RemovePacket packet, FriendlyByteBuf buffer) {}

        private static RemovePacket decode(FriendlyByteBuf buffer) {
            return new RemovePacket();
        }

        private static void handle(RemovePacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null) return;
                net.minecraft.world.item.ItemStack worn = BriefsCapability.get(player).getStack();
                if (!worn.isEmpty()) {
                    BriefsCapability.get(player).setStack(net.minecraft.world.item.ItemStack.EMPTY);
                    player.getInventory().placeItemBackInInventory(worn);
                    BriefsEvents.refreshAttributes(player);
                    BriefsNetwork.sync(player);
                }
            });
            context.setPacketHandled(true);
        }
    }
}
