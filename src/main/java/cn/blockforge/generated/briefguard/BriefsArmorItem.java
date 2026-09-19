package cn.blockforge.generated.briefguard;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class BriefsArmorItem extends ArmorItem {
    private static final UUID ATTACK_DAMAGE_UUID = UUID.fromString("b0e3db54-9a74-4e2d-9a64-cd6f2b2e6f11");
    private static final UUID ATTACK_KNOCKBACK_UUID = UUID.fromString("7aa1c4de-3c51-4a2e-a7e0-3a76e38c9022");
    private static final UUID LUCK_UUID = UUID.fromString("3c2fd4e5-2221-4c0b-b57c-6fdf4c8ddf22");
    private final BriefsMaterialKind kind;
    private final Multimap<Attribute, AttributeModifier> handModifiers;

    public BriefsArmorItem(ArmorMaterial material, Type type, BriefsMaterialKind kind, Item.Properties properties) {
        super(material, type, properties);
        this.kind = kind;
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        if (kind.attackDamage() != 0.0D) {
            builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(ATTACK_DAMAGE_UUID, "Briefs attack damage", kind.attackDamage(), AttributeModifier.Operation.ADDITION));
        }
        if (kind.attackKnockback() != 0.0D) {
            builder.put(Attributes.ATTACK_KNOCKBACK, new AttributeModifier(ATTACK_KNOCKBACK_UUID, "Briefs attack knockback", kind.attackKnockback(), AttributeModifier.Operation.ADDITION));
        }
        if (kind.luck() != 0.0D) {
            builder.put(Attributes.LUCK, new AttributeModifier(LUCK_UUID, "Gold briefs luck", kind.luck(), AttributeModifier.Operation.ADDITION));
        }
        this.handModifiers = builder.build();
    }

    public BriefsMaterialKind kind() {
        return kind;
    }

    public boolean usesUnderwearSlot() {
        return kind.usesBriefsSlot();
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return kind == BriefsMaterialKind.LEATHER ? EquipmentSlot.HEAD : EquipmentSlot.CHEST;
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        if (slot == EquipmentSlot.MAINHAND) return handModifiers;
        return super.getDefaultAttributeModifiers(slot);
    }

    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
            private net.minecraft.client.model.HumanoidModel<?> emptyModel;

            @Override
            public net.minecraft.client.model.HumanoidModel getHumanoidArmorModel(
                    LivingEntity entity, ItemStack stack, EquipmentSlot slot, net.minecraft.client.model.HumanoidModel original) {
                if (emptyModel == null) {
                    emptyModel = new cn.blockforge.generated.briefguard.client.BriefsArmorModel<>(
                            cn.blockforge.generated.briefguard.client.BriefsArmorModel.createLayer().bakeRoot());
                }
                original.copyPropertiesTo(emptyModel);
                emptyModel.setAllVisible(false);
                return emptyModel;
            }
        });
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!usesUnderwearSlot()) return super.use(level, player, hand);
        if (!level.isClientSide()) {
            // Only equip when the player's capability is reachable; never consume the item in a
            // transient respawn window where the capability is not yet attached.
            player.getCapability(BriefsCapability.UNDERWEAR).ifPresent(handler -> {
                ItemStack previous = handler.getStack();
                handler.setStack(held);
                held.shrink(1);
                if (!previous.isEmpty()) player.getInventory().placeItemBackInInventory(previous);
                player.playSound(getEquipSound(), 1.0F, 1.0F);
                BriefsEvents.refreshAttributes(player);
                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) BriefsNetwork.sync(serverPlayer);
            });
        }
        return InteractionResultHolder.sidedSuccess(held, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, java.util.List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        if (kind == BriefsMaterialKind.LEATHER) tooltip.add(Component.translatable("tooltip.brief_guard.leather_slot"));
        else tooltip.add(Component.translatable("tooltip.brief_guard.underwear_slot"));
        tooltip.add(Component.translatable("tooltip.brief_guard.attack", String.format("%.1f", kind.attackDamage())));
        String mechanicKey = mechanicKey(kind);
        if (mechanicKey != null) {
            tooltip.add(Component.translatable("tooltip.brief_guard.mechanic").append(Component.translatable(mechanicKey)));
        }
        if (usesUnderwearSlot()) tooltip.add(Component.translatable("tooltip.brief_guard.remove"));
    }

    private static String mechanicKey(BriefsMaterialKind kind) {
        switch (kind) {
            case DRAGON_HEAD: return "tooltip.brief_guard.m_dragon_head";
            case CHASTITY: return "tooltip.brief_guard.m_chastity";
            case SLIME: return "tooltip.brief_guard.m_slime";
            case SPICY: return "tooltip.brief_guard.m_spicy";
            case POOP: return "tooltip.brief_guard.m_poop";
            case SILVERFISH: return "tooltip.brief_guard.m_silverfish";
            case TENTACLE: return "tooltip.brief_guard.m_tentacle";
            case EDIBLE: return "tooltip.brief_guard.m_edible";
            case TRAPDOOR: return "tooltip.brief_guard.m_trapdoor";
            case PROMOTION: return "tooltip.brief_guard.m_promotion";
            case STICKY_PISTON: return "tooltip.brief_guard.m_sticky_piston";
            case GASEOUS: return "tooltip.brief_guard.m_gaseous";
            case SWORD: return "tooltip.brief_guard.m_sword";
            case SHIELD: return "tooltip.brief_guard.m_shield";
            default: return null;
        }
    }

    public static boolean isWearing(LivingEntity entity, BriefsMaterialKind wanted) {
        if (!(entity instanceof Player player)) return false;
        if (wanted == BriefsMaterialKind.LEATHER) return player.getItemBySlot(EquipmentSlot.HEAD).getItem() == GeneratedMod.LEATHER_BRIEFS.get();
        return player.getCapability(BriefsCapability.UNDERWEAR).map(handler -> handler.getStack().getItem() instanceof BriefsArmorItem item && item.kind() == wanted).orElse(false);
    }
}
