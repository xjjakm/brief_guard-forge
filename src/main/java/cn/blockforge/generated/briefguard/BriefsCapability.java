package cn.blockforge.generated.briefguard;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.Direction;

public final class BriefsCapability {
    public static final Capability<IUnderwearHandler> UNDERWEAR = CapabilityManager.get(new CapabilityToken<>() {});
    public static final ResourceLocation ID = GeneratedMod.id("underwear");

    private BriefsCapability() {}

    public static void registerCapabilities(net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent event) {
        event.register(IUnderwearHandler.class);
    }

    public interface IUnderwearHandler extends IItemHandlerModifiableSerializable {
        ItemStack getStack();
        void setStack(ItemStack stack);
    }

    public interface IItemHandlerModifiableSerializable extends net.minecraftforge.items.IItemHandlerModifiable, INBTSerializable<Tag> {}

    public static final class Handler implements IUnderwearHandler {
        private ItemStack stack = ItemStack.EMPTY;

        @Override public ItemStack getStack() { return stack; }
        @Override public void setStack(ItemStack stack) { this.stack = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1); }
        @Override public int getSlots() { return 1; }
        @Override public ItemStack getStackInSlot(int slot) { return slot == 0 ? stack : ItemStack.EMPTY; }
        @Override public void setStackInSlot(int slot, ItemStack stack) { if (slot == 0) this.stack = stack; }
        @Override public ItemStack insertItem(int slot, ItemStack incoming, boolean simulate) {
            if (slot != 0 || incoming.isEmpty() || !(incoming.getItem() instanceof BriefsArmorItem) || !((BriefsArmorItem) incoming.getItem()).kind().usesBriefsSlot()) return incoming;
            if (!stack.isEmpty()) return incoming;
            if (!simulate) stack = incoming.copyWithCount(1);
            return incoming.getCount() > 1 ? incoming.copyWithCount(incoming.getCount() - 1) : ItemStack.EMPTY;
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 0 || amount <= 0 || stack.isEmpty()) return ItemStack.EMPTY;
            ItemStack result = stack.copyWithCount(Math.min(1, amount));
            if (!simulate) stack = ItemStack.EMPTY;
            return result;
        }
        @Override public int getSlotLimit(int slot) { return slot == 0 ? 1 : 0; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return slot == 0 && stack.getItem() instanceof BriefsArmorItem item && item.kind().usesBriefsSlot(); }
        @Override public Tag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            if (!stack.isEmpty()) tag.put("Item", stack.save(new CompoundTag()));
            return tag;
        }
        @Override public void deserializeNBT(Tag nbt) {
            stack = ItemStack.EMPTY;
            if (nbt instanceof CompoundTag tag && tag.contains("Item")) stack = ItemStack.of(tag.getCompound("Item"));
        }
    }

    public static final class Provider implements ICapabilitySerializable<Tag> {
        private final Handler handler = new Handler();
        private final LazyOptional<IUnderwearHandler> optional = LazyOptional.of(() -> handler);
        @Override public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
            return capability == UNDERWEAR ? optional.cast() : LazyOptional.empty();
        }
        @Override public Tag serializeNBT() { return handler.serializeNBT(); }
        @Override public void deserializeNBT(Tag nbt) { handler.deserializeNBT(nbt); }
    }

    /**
     * Returns the player's underwear handler, or a safe empty handler when the capability is not yet
     * attached. The capability is attached to every {@link Player} via {@link #attach} during entity
     * construction; but during a death/respawn transition there is a brief window where a freshly
     * created player can be ticked/rendered before its capability is reachable. In that window this
     * method must never throw, otherwise the whole game crashes with an IllegalStateException.
     *
     * <p>The returned fallback is per-call and never shared, so writing to it cannot corrupt other
     * players; in practice callers only read it (an empty stack means "no briefs worn") and the
     * capability is re-attached and re-synced on respawn by {@link BriefsEvents}.
     */
    public static IUnderwearHandler get(Player player) {
        return player.getCapability(UNDERWEAR).orElseGet(Handler::new);
    }

    public static void copy(Player oldPlayer, Player newPlayer) {
        oldPlayer.getCapability(UNDERWEAR).ifPresent(oldHandler -> newPlayer.getCapability(UNDERWEAR).ifPresent(newHandler -> newHandler.setStack(oldHandler.getStack().copy())));
    }

    public static void attach(AttachCapabilitiesEvent<net.minecraft.world.entity.Entity> event) {
        if (event.getObject() instanceof Player) event.addCapability(ID, new Provider());
    }

    public static void clone(PlayerEvent.Clone event) {
        copy(event.getOriginal(), event.getEntity());
    }
}
