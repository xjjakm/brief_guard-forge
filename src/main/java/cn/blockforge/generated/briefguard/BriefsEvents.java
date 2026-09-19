package cn.blockforge.generated.briefguard;

import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GeneratedMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BriefsEvents {
    private static final UUID ATTACK_DAMAGE_UUID = UUID.fromString("1b7e99d2-1ef0-4e5f-8e1e-4a6fced8a001");
    private static final UUID ATTACK_KNOCKBACK_UUID = UUID.fromString("1b7e99d2-1ef0-4e5f-8e1e-4a6fced8a002");
    private static final UUID LUCK_UUID = UUID.fromString("1b7e99d2-1ef0-4e5f-8e1e-4a6fced8a003");
    private static final UUID ARMOR_UUID = UUID.fromString("1b7e99d2-1ef0-4e5f-8e1e-4a6fced8a004");
    private static final UUID TOUGHNESS_UUID = UUID.fromString("1b7e99d2-1ef0-4e5f-8e1e-4a6fced8a005");
    private static final UUID KNOCKBACK_RESISTANCE_UUID = UUID.fromString("1b7e99d2-1ef0-4e5f-8e1e-4a6fced8a006");

    private BriefsEvents() {}

    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<Entity> event) {
        BriefsCapability.attach(event);
    }

    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        BriefsCapability.copy(event.getOriginal(), event.getEntity());
        refreshAttributes(event.getEntity());
    }

    @SubscribeEvent
    public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            refreshAttributes(player);
            BriefsNetwork.sync(player);
        }
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            refreshAttributes(player);
            BriefsNetwork.sync(player);
        }
    }

    @SubscribeEvent
    public static void equipmentChanged(LivingEquipmentChangeEvent event) {
        if (event.getEntity() instanceof Player player && event.getSlot().isArmor()) refreshAttributes(player);
    }

    /**
     * Gold briefs discount. Applied when the merchant menu is opened on the server, before the offer
     * list is serialized and sent to the client. Applying it during the per-tick loop does not work:
     * the client already received a deserialized copy of the offers when trading started, and the
     * villager's {@code resetSpecialPrices()} (fired from {@code stopTrading()} on menu close) zeroes
     * {@code specialPriceDiff} again — so the discount was lost and never shown. Hooking the open
     * event means the discounted prices are present in the very first offer packet the client sees,
     * and the vanilla cleanup on close keeps the villager's state clean.
     */
    @SubscribeEvent
    public static void openContainer(PlayerContainerEvent.Open event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        if (wornKind(player) != BriefsMaterialKind.GOLD) return;
        if (!(event.getContainer() instanceof MerchantMenu menu)) return;
        for (MerchantOffer offer : menu.getOffers()) {
            offer.addToSpecialPriceDiff(-3);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) return;
        BriefsMechanic.tick(event.player);
    }

    @SubscribeEvent
    public static void fall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        BriefsMechanic.onFall(player, event);
    }

    @SubscribeEvent
    public static void attack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        BriefsMaterialKind kind = wornKind(player);
        if (kind == BriefsMaterialKind.COPPER && event.getSource().is(DamageTypeTags.IS_LIGHTNING)) event.setCanceled(true);
        if ((kind == BriefsMaterialKind.NETHERITE || kind == BriefsMaterialKind.DRAGON_HEAD) && event.getSource().is(DamageTypeTags.IS_FIRE)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void hurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        BriefsMaterialKind kind = wornKind(player);
        // 基础款（链甲/下界合金）的减伤逻辑。
        if (kind == BriefsMaterialKind.CHAIN && event.getSource().is(DamageTypeTags.IS_PROJECTILE)) {
            event.setAmount(event.getAmount() * 0.25F);
        }
        if (kind == BriefsMaterialKind.NETHERITE) {
            if (event.getSource().is(DamageTypeTags.IS_FIRE)) event.setCanceled(true);
            else if (event.getSource().is(DamageTypeTags.IS_EXPLOSION)) event.setAmount(event.getAmount() * 0.5F);
        }
        // 扩展款（机制型）统一交给机制引擎。
        BriefsMechanic.onHurt(player, event);
    }

    @SubscribeEvent
    public static void attackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || !(event.getTarget() instanceof LivingEntity target)) return;
        // 手持内裤当武器：占用一次攻击并对目标造成伤害。
        if (player.getMainHandItem().getItem() instanceof BriefsArmorItem heldBriefs) {
            event.setCanceled(true);
            float damage = (float) (player.getAttributeValue(Attributes.ATTACK_DAMAGE) * player.getAttackStrengthScale(0.5F));
            if (damage > 0.0F) target.hurt(player.level().damageSources().sweetBerryBush(), damage);
        }
        BriefsMaterialKind worn = wornKind(player);
        if (worn == BriefsMaterialKind.COPPER && !player.getCooldowns().isOnCooldown(GeneratedMod.COPPER_BRIEFS.get())) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1), player);
            player.getCooldowns().addCooldown(GeneratedMod.COPPER_BRIEFS.get(), 60);
        }
        BriefsMechanic.onAttack(player, target);
    }

    @SubscribeEvent
    public static void removeUnderwear(PlayerInteractEvent.RightClickEmpty event) {
        Player player = event.getEntity();
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND || player.level().isClientSide()) return;
        // 蹲下 + 空手右键 = 脱下内裤；空主手右键 = 触发当前内裤的主动技能。
        if (!player.isShiftKeyDown()) {
            if (player.getMainHandItem().isEmpty()) BriefsMechanic.activeRightClick(player);
            return;
        }
        BriefsCapability.IUnderwearHandler handler = BriefsCapability.get(player);
        ItemStack worn = handler.getStack();
        if (!worn.isEmpty()) {
            handler.setStack(ItemStack.EMPTY);
            player.getInventory().placeItemBackInInventory(worn);
            refreshAttributes(player);
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) BriefsNetwork.sync(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void drops(LivingDropsEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player) || wornKind(player) != BriefsMaterialKind.GOLD) return;
        if (player.getRandom().nextFloat() < 0.35F) {
            event.getDrops().add(new ItemEntity(player.level(), event.getEntity().getX(),
                    event.getEntity().getY(), event.getEntity().getZ(),
                    new ItemStack(net.minecraft.world.item.Items.GOLD_NUGGET, 1 + player.getRandom().nextInt(3))));
        }
    }

    @SubscribeEvent
    public static void experienceDrop(LivingExperienceDropEvent event) {
        Player player = event.getAttackingPlayer();
        if (player == null) return;
        ItemStack worn = BriefsMechanic.wornStack(player);
        if (!(worn.getItem() instanceof BriefsArmorItem item) || item.kind() != BriefsMaterialKind.PROMOTION) return;
        event.setDroppedExperience((int) (event.getOriginalExperience() * 1.5F) + 1);
        BriefsMechanic.onKill(player, worn);
    }

    private static BriefsMaterialKind wornKind(Player player) {
        ItemStack head = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
        if (head.getItem() instanceof BriefsArmorItem headBriefs && headBriefs.kind() == BriefsMaterialKind.LEATHER) return BriefsMaterialKind.LEATHER;
        return BriefsCapability.get(player).getStack().getItem() instanceof BriefsArmorItem briefs ? briefs.kind() : null;
    }

    public static void refreshAttributes(Player player) {
        remove(player, Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE_UUID);
        remove(player, Attributes.ATTACK_KNOCKBACK, ATTACK_KNOCKBACK_UUID);
        remove(player, Attributes.LUCK, LUCK_UUID);
        remove(player, Attributes.ARMOR, ARMOR_UUID);
        remove(player, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_UUID);
        remove(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE_UUID);
        BriefsMaterialKind kind = wornKind(player);
        if (kind == null) return;
        add(player, Attributes.ATTACK_DAMAGE, new AttributeModifier(ATTACK_DAMAGE_UUID, "Briefs attack damage", kind.attackDamage(), AttributeModifier.Operation.ADDITION));
        add(player, Attributes.ATTACK_KNOCKBACK, new AttributeModifier(ATTACK_KNOCKBACK_UUID, "Briefs attack knockback", kind.attackKnockback(), AttributeModifier.Operation.ADDITION));
        add(player, Attributes.LUCK, new AttributeModifier(LUCK_UUID, "Gold briefs luck", kind.luck(), AttributeModifier.Operation.ADDITION));
        BriefsArmorItem item = kind == BriefsMaterialKind.LEATHER
                ? (BriefsArmorItem) player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).getItem()
                : (BriefsArmorItem) BriefsCapability.get(player).getStack().getItem();
        if (kind != BriefsMaterialKind.LEATHER) {
            add(player, Attributes.ARMOR, new AttributeModifier(ARMOR_UUID, "Briefs armor", item.getMaterial().getDefenseForType(item.getType()), AttributeModifier.Operation.ADDITION));
            add(player, Attributes.ARMOR_TOUGHNESS, new AttributeModifier(TOUGHNESS_UUID, "Briefs toughness", item.getMaterial().getToughness(), AttributeModifier.Operation.ADDITION));
            add(player, Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(KNOCKBACK_RESISTANCE_UUID, "Briefs knockback resistance", item.getMaterial().getKnockbackResistance(), AttributeModifier.Operation.ADDITION));
        }
    }

    private static void add(Player player, net.minecraft.world.entity.ai.attributes.Attribute attribute, AttributeModifier modifier) {
        var instance = player.getAttribute(attribute);
        if (instance != null && modifier.getAmount() != 0.0D) instance.addTransientModifier(modifier);
    }

    private static void remove(Player player, net.minecraft.world.entity.ai.attributes.Attribute attribute, UUID id) {
        var instance = player.getAttribute(attribute);
        if (instance != null) instance.removeModifier(id);
    }
}
