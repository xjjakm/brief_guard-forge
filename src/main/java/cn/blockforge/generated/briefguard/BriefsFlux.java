package cn.blockforge.generated.briefguard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 磁力/斥引系列装备（第 17 轮超大型更新）：
 * <ul>
 *   <li>磁轨巨剑（武器）：命中给目标"磁化"，磁化的敌人会被缓缓吸向持剑者聚成一团；
 *       命中已磁化的目标触发磁爆，把周围一小团的磁化敌人一起炸开；再右键做"磁核爆发"把
 *       身边所有磁化敌人一次性轰掉。形成"磁化→聚拢→爆发"的节奏。</li>
 *   <li>斥引护符（装备，副手）：对掉落物是"引"——把附近物品吸到自己身上；对来箭是"斥"——
 *       把飞向自己的箭矢/三叉戟弹开。</li>
 *   <li>磁暴珠（道具）：右键投向视线前方，生成一个磁力场，把范围里的敌人聚过去，到期引爆，
 *       伤害按聚到的敌人数加成；消耗 1 个。</li>
 *   <li>磁锚钉（道具）：右键钉在目标点，生成一个磁锚，把附近的敌人磁化并吸向钉点，最近的敌人
 *       被钉住当作支点；只聚拢不引爆，方便接磁轨巨剑的磁爆。消耗 1 个。</li>
 *   <li>磁化石（方块）：放置后默认开启，守在附近会把范围里的敌人磁化并吸向自己，同时把掉落
 *       物吸过来；右键切换开关。</li>
 * </ul>
 * 全部为事件驱动、状态挂在物品 NBT / 服务端列表上，不依赖常驻 buff。
 * 磁化（magnetized）是全套共享的标记：磁化石/磁锚钉负责"吸过来"，磁轨巨剑负责"炸开"。
 */
public final class BriefsFlux {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, GeneratedMod.MOD_ID);

    // ---------------------------------------------------------------- 注册
    public static final RegistryObject<Item> FLUXBRAND =
            GeneratedMod.ITEMS.register("fluxbrand", FluxBrandItem::new);
    public static final RegistryObject<Item> FLUXCHARM =
            GeneratedMod.ITEMS.register("fluxcharm", FluxCharmItem::new);
    public static final RegistryObject<Item> FLUXCHARGE =
            GeneratedMod.ITEMS.register("fluxcharge", FluxChargeItem::new);
    public static final RegistryObject<Item> FLUXSHARD =
            GeneratedMod.ITEMS.register("fluxshard", FluxShardItem::new);
    public static final RegistryObject<Block> FLUXANCHOR =
            BLOCKS.register("fluxanchor", FluxAnchorBlock::new);
    public static final RegistryObject<Item> FLUXANCHOR_ITEM =
            GeneratedMod.ITEMS.register("fluxanchor", () -> new BlockItem(FLUXANCHOR.get(), new Item.Properties()));

    private BriefsFlux() {}

    public static void init(IEventBus bus) {
        BLOCKS.register(bus);
    }

    // ---------------------------------------------------------------- 磁化共享标记
    /** 磁化标记在目标身上保留的时间（tick），过期自动失效，避免永久磁化。 */
    private static final long MAGNET_TTL = 80L;

    private static final Map<UUID, Long> MAGNET = new HashMap<>();

    private static void magnetize(Level level, LivingEntity entity) {
        MAGNET.put(entity.getUUID(), level.getGameTime());
    }

    private static boolean isMagnetized(LivingEntity entity) {
        Long t = MAGNET.get(entity.getUUID());
        return t != null && (entity.level().getGameTime() - t) < MAGNET_TTL;
    }

    private static void demagnetize(LivingEntity entity) {
        MAGNET.remove(entity.getUUID());
    }

    private static void pruneMagnet(Player player) {
        if (player.tickCount % 20 != 0) return;
        long cutoff = player.level().getGameTime() - MAGNET_TTL;
        MAGNET.values().removeIf(t -> t < cutoff);
    }

    // ---------------------------------------------------------------- 通用物理助手
    /** 把一个实体朝某个点拖/吸一段距离。 */
    private static void pull(Level level, LivingEntity entity, Vec3 point, double strength) {
        Vec3 diff = point.subtract(entity.position());
        double d = diff.length();
        if (d < 0.05D) return;
        Vec3 dir = diff.scale(1.0D / d);
        entity.setDeltaMovement(dir.scale(strength));
        entity.hurtMarked = true;
    }

    /** 把范围里的磁化敌人朝点吸附。 */
    private static void pullMagnetized(Level level, Vec3 point, double radius, double strength) {
        AABB box = AABB.ofSize(point, radius * 2.0D, radius * 2.0D, radius * 2.0D);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                t -> t != null && t.isAlive() && t instanceof Enemy && isMagnetized(t))) {
            pull(level, e, point, strength);
        }
    }

    /** 把范围里的掉落物朝点吸附（物品磁吸）。 */
    private static void magnetItems(Level level, Vec3 point, double radius, double strength) {
        AABB box = AABB.ofSize(point, radius * 2.0D, radius * 2.0D, radius * 2.0D);
        for (ItemEntity it : level.getEntitiesOfClass(ItemEntity.class, box)) {
            Vec3 diff = point.subtract(it.position());
            double d = diff.length();
            if (d < 0.05D) continue;
            it.setDeltaMovement(diff.scale(strength / d));
            it.hurtMarked = true;
        }
    }

    // ---------------------------------------------------------------- 磁轨巨剑
    private static final double PULL_RADIUS = 6.0D;
    private static final double PULL_FORCE = 0.22D;
    private static final double BURST_RADIUS = 3.5D;
    private static final float BURST_DAMAGE = 6.0F;
    private static final double CORE_RADIUS = 6.0D;
    private static final float CORE_BASE = 3.0F;
    private static final float CORE_PER = 1.6F;
    private static final float CORE_MAX = 14.0F;
    private static final int CORE_MIN = 2;

    private static final Tier FLUX_TIER = new Tier() {
        @Override public int getUses() { return 480; }
        @Override public float getSpeed() { return 7.0F; }
        @Override public float getAttackDamageBonus() { return 4.5F; }
        @Override public int getLevel() { return 3; }
        @Override public int getEnchantmentValue() { return 10; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.of(net.minecraft.world.item.Items.IRON_INGOT); }
    };

    /** 磁轨巨剑：命中给目标磁化，磁化会吸到持剑者身边；命中已磁化的目标触发磁爆。 */
    public static final class FluxBrandItem extends SwordItem {
        public FluxBrandItem() {
            super(FLUX_TIER, 2, -2.0F, new Item.Properties().durability(FLUX_TIER.getUses()));
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (level.isClientSide()) {
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
            }
            List<LivingEntity> targets = magnetizedNear(player, CORE_RADIUS);
            if (targets.size() >= CORE_MIN) {
                coreBurst(player, targets);
                player.getCooldowns().addCooldown(stack.getItem(), 40);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.brief_guard.g_flux_brand"));
        }
    }

    /** 由 BriefsEvents.attackEntity 调用：磁化 / 磁爆逻辑。 */
    static void onAttack(Player player, LivingEntity target) {
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof FluxBrandItem)) return;
        Level level = player.level();
        if (level.isClientSide()) return;
        if (isMagnetized(target)) {
            fluxBurst(player, target);
        } else {
            magnetize(level, target);
            level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                    target.getX(), target.getY() + 0.7D, target.getZ(), 0.0D, 0.12D, 0.0D);
        }
    }

    /** 磁爆：把目标周围一小团磁化敌人一起炸开，随后全部解除磁化。 */
    private static void fluxBurst(Player player, LivingEntity target) {
        Level level = player.level();
        AABB box = AABB.ofSize(target.position(), BURST_RADIUS * 2.0D, BURST_RADIUS * 2.0D, BURST_RADIUS * 2.0D);
        int hit = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                t -> t.isAlive() && t instanceof Enemy && isMagnetized(t))) {
            e.hurt(level.damageSources().sweetBerryBush(), BURST_DAMAGE);
            Vec3 away = e.position().subtract(target.position());
            double d = away.length();
            if (d > 0.05D) {
                e.knockback(0.5D, away.x / d, away.z / d);
            }
            demagnetize(e);
            hit++;
        }
        level.addParticle(ParticleTypes.EXPLOSION,
                target.getX(), target.getY() + 0.6D, target.getZ(), 0.0D, 0.0D, 0.0D);
        level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9F, 1.5F);
        if (hit == 0) {
            player.level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                    target.getX(), target.getY() + 0.6D, target.getZ(), 0.0D, 0.1D, 0.0D);
        }
    }

    /** 磁核爆发：把身边所有磁化敌人一次性轰掉，伤害按数量加成。 */
    private static void coreBurst(Player player, List<LivingEntity> targets) {
        Level level = player.level();
        float dmg = Math.min(CORE_MAX, CORE_BASE + targets.size() * CORE_PER);
        for (LivingEntity e : targets) {
            e.hurt(level.damageSources().sweetBerryBush(), dmg);
            Vec3 away = e.position().subtract(player.position());
            double d = away.length();
            if (d > 0.05D) {
                e.knockback(0.7D, away.x / d, away.z / d);
            }
            demagnetize(e);
        }
        level.addParticle(ParticleTypes.EXPLOSION,
                player.getX(), player.getY() + 0.6D, player.getZ(), 0.0D, 0.0D, 0.0D);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8F, 1.2F);
    }

    private static List<LivingEntity> magnetizedNear(Player player, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        AABB box = player.getBoundingBox().inflate(radius);
        for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, box,
                t -> t.isAlive() && t instanceof Enemy && isMagnetized(t))) {
            out.add(e);
        }
        return out;
    }

    /** 持剑时把磁化敌人吸到身边（聚拢）。 */
    private static void tickBrand(Player player) {
        if (!(player.getMainHandItem().getItem() instanceof FluxBrandItem)) return;
        pullMagnetized(player.level(), player.position(), PULL_RADIUS, PULL_FORCE);
    }

    // ---------------------------------------------------------------- 斥引护符
    private static final double PICKUP_RADIUS = 7.0D;
    private static final double DEFLECT_RADIUS = 2.6D;

    /** 斥引护符：副手佩戴。对掉落物是"引"，对箭矢是"斥"。 */
    public static final class FluxCharmItem extends Item {
        public FluxCharmItem() {
            super(new Item.Properties().stacksTo(1));
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.brief_guard.g_flux_charm"));
        }
    }

    private static void tickCharm(Player player) {
        ItemStack off = player.getOffhandItem();
        if (!(off.getItem() instanceof FluxCharmItem)) return;
        // 引：把附近掉落物吸到身上。
        magnetItems(player.level(), player.position(), PICKUP_RADIUS, 0.9D);
        // 斥：弹开飞向自己的箭矢/三叉戟。
        if (player.tickCount % 2 == 0) {
            for (AbstractArrow arrow : player.level().getEntitiesOfClass(AbstractArrow.class,
                    player.getBoundingBox().inflate(DEFLECT_RADIUS))) {
                Vec3 motion = arrow.getDeltaMovement();
                Vec3 away = arrow.position().subtract(player.position());
                double d = away.length();
                if (d < 0.05D) continue;
                away = away.scale(1.0D / d);
                if (motion.dot(away) < 0.0D) {
                    arrow.setDeltaMovement(away.scale(0.5D + motion.length()));
                    arrow.hurtMarked = true;
                    player.level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                            arrow.getX(), arrow.getY() + 0.2D, arrow.getZ(), 0.0D, 0.1D, 0.0D);
                }
            }
        }
    }

    // ---------------------------------------------------------------- 磁暴珠
    private static final double CHARGE_RANGE = 14.0D;
    private static final double CHARGE_RADIUS = 5.0D;
    private static final long CHARGE_DURATION = 44L;
    private static final float CHARGE_BASE = 4.0F;
    private static final float CHARGE_PER = 2.0F;
    private static final float CHARGE_MAX = 16.0F;

    private static final List<ChargeZone> CHARGE_ZONES = new ArrayList<>();

    private static final class ChargeZone {
        final Level level;
        final Vec3 center;
        long expiry;
        int peak;
        ChargeZone(Level level, Vec3 center, long expiry) {
            this.level = level;
            this.center = center;
            this.expiry = expiry;
        }
    }

    /** 磁暴珠：右键投向视线前方，磁力场聚敌，到期引爆；消耗 1 个。 */
    public static final class FluxChargeItem extends Item {
        public FluxChargeItem() {
            super(new Item.Properties().stacksTo(16));
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!level.isClientSide()) {
                BlockHitResult hit = level.clip(new ClipContext(
                        player.getEyePosition(),
                        player.getEyePosition().add(player.getLookAngle().scale(CHARGE_RANGE)),
                        ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
                Vec3 center = hit.getType() == BlockHitResult.Type.BLOCK
                        ? Vec3.atCenterOf(hit.getBlockPos())
                        : player.getEyePosition().add(player.getLookAngle().scale(CHARGE_RANGE * 0.6D));
                CHARGE_ZONES.add(new ChargeZone(level, center, level.getGameTime() + CHARGE_DURATION));
                stack.shrink(1);
                player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 1.4F);
                for (int i = 0; i < 6; i++) {
                    level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                            center.x + (level.getRandom().nextDouble() - 0.5D), center.y + level.getRandom().nextDouble() * 1.4D,
                            center.z + (level.getRandom().nextDouble() - 0.5D), 0.0D, 0.08D, 0.0D);
                }
                player.getCooldowns().addCooldown(stack.getItem(), 12);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.brief_guard.g_flux_charge"));
        }
    }

    private static void tickChargeZones(Player player) {
        long now = player.level().getGameTime();
        Iterator<ChargeZone> it = CHARGE_ZONES.iterator();
        while (it.hasNext()) {
            ChargeZone z = it.next();
            if (z.level != player.level()) continue;
            if (now > z.expiry) {
                detonateCharge(z);
                it.remove();
                continue;
            }
            AABB box = AABB.ofSize(z.center, CHARGE_RADIUS * 2.0D, CHARGE_RADIUS * 2.0D, CHARGE_RADIUS * 2.0D);
            int count = 0;
            for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, box,
                    t -> t.isAlive() && t instanceof Enemy)) {
                pull(player.level(), e, z.center, 0.18D);
                magnetize(player.level(), e);
                count++;
            }
            z.peak = Math.max(z.peak, count);
            if (player.tickCount % 10 == 0) {
                player.level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                        z.center.x + (player.level().getRandom().nextDouble() - 0.5D), z.center.y + player.level().getRandom().nextDouble() * 1.4D,
                        z.center.z + (player.level().getRandom().nextDouble() - 0.5D), 0.0D, 0.06D, 0.0D);
            }
        }
    }

    private static void detonateCharge(ChargeZone z) {
        AABB box = AABB.ofSize(z.center, CHARGE_RADIUS * 2.0D, CHARGE_RADIUS * 2.0D, CHARGE_RADIUS * 2.0D);
        float dmg = Math.min(CHARGE_MAX, CHARGE_BASE + z.peak * CHARGE_PER);
        for (LivingEntity e : z.level.getEntitiesOfClass(LivingEntity.class, box,
                t -> t.isAlive() && t instanceof Enemy)) {
            e.hurt(z.level.damageSources().sweetBerryBush(), dmg);
            Vec3 away = e.position().subtract(z.center);
            double d = away.length();
            if (d > 0.05D) {
                e.knockback(0.6D, away.x / d, away.z / d);
            }
            demagnetize(e);
        }
        z.level.addParticle(ParticleTypes.EXPLOSION, z.center.x, z.center.y + 0.5D, z.center.z, 0.0D, 0.0D, 0.0D);
        z.level.playSound(null, BlockPos.containing(z.center), SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 1.0F, 1.1F);
    }

    // ---------------------------------------------------------------- 磁锚钉
    private static final double SHARD_RANGE = 12.0D;
    private static final double SHARD_RADIUS = 5.0D;
    private static final long SHARD_DURATION = 60L;

    private static final List<ShardZone> SHARD_ZONES = new ArrayList<>();

    private static final class ShardZone {
        final Level level;
        final Vec3 center;
        long expiry;
        ShardZone(Level level, Vec3 center, long expiry) {
            this.level = level;
            this.center = center;
            this.expiry = expiry;
        }
    }

    /** 磁锚钉：右键钉在目标点，把附近敌人磁化吸向钉点，最近的敌人被钉住作支点；消耗 1 个。 */
    public static final class FluxShardItem extends Item {
        public FluxShardItem() {
            super(new Item.Properties().stacksTo(8));
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!level.isClientSide()) {
                BlockHitResult hit = level.clip(new ClipContext(
                        player.getEyePosition(),
                        player.getEyePosition().add(player.getLookAngle().scale(SHARD_RANGE)),
                        ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
                Vec3 center = hit.getType() == BlockHitResult.Type.BLOCK
                        ? Vec3.atCenterOf(hit.getBlockPos())
                        : player.getEyePosition().add(player.getLookAngle().scale(SHARD_RANGE * 0.6D));
                SHARD_ZONES.add(new ShardZone(level, center, level.getGameTime() + SHARD_DURATION));
                stack.shrink(1);
                player.playSound(SoundEvents.PISTON_EXTEND, 0.8F, 1.4F);
                for (int i = 0; i < 4; i++) {
                    level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                            center.x, center.y + 0.5D, center.z, 0.0D, 0.08D, 0.0D);
                }
                player.getCooldowns().addCooldown(stack.getItem(), 12);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.brief_guard.g_flux_shard"));
        }
    }

    private static void tickShardZones(Player player) {
        long now = player.level().getGameTime();
        Iterator<ShardZone> it = SHARD_ZONES.iterator();
        while (it.hasNext()) {
            ShardZone z = it.next();
            if (z.level != player.level()) continue;
            if (now > z.expiry) { it.remove(); continue; }
            AABB box = AABB.ofSize(z.center, SHARD_RADIUS * 2.0D, SHARD_RADIUS * 2.0D, SHARD_RADIUS * 2.0D);
            LivingEntity pivot = null;
            double best = Double.MAX_VALUE;
            for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, box,
                    t -> t.isAlive() && t instanceof Enemy)) {
                magnetize(player.level(), e);
                double dist = e.position().distanceToSqr(z.center);
                if (dist < best) {
                    best = dist;
                    pivot = e;
                }
            }
            for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, box,
                    t -> t.isAlive() && t instanceof Enemy)) {
                if (e == pivot) {
                    // 支点：钉得死死的。
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 4), player);
                    pull(player.level(), e, z.center, 0.30D);
                } else {
                    pull(player.level(), e, z.center, 0.20D);
                }
            }
            if (player.tickCount % 10 == 0) {
                player.level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                        z.center.x, z.center.y + 0.5D, z.center.z, 0.0D, 0.08D, 0.0D);
            }
        }
    }

    // ---------------------------------------------------------------- 磁化石
    private static final double ANCHOR_RADIUS = 6.0D;
    private static final int ANCHOR_SCAN = 3;

    /** 磁化石：放置后默认开启，把范围内敌人磁化吸向自己，并把掉落物吸过来；右键切换开关。 */
    public static final class FluxAnchorBlock extends Block {
        public static final BooleanProperty RUNNING = BooleanProperty.create("running");

        public FluxAnchorBlock() {
            super(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.2F, 6.0F)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(RUNNING) ? 5 : 0));
            this.registerDefaultState(this.stateDefinition.any().setValue(RUNNING, Boolean.TRUE));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(RUNNING);
        }

        @Override
        public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (!level.isClientSide()) {
                boolean running = state.getValue(RUNNING);
                level.setBlockAndUpdate(pos, state.setValue(RUNNING, !running));
                player.playSound(running ? SoundEvents.PISTON_CONTRACT : SoundEvents.PISTON_EXTEND, 1.0F, running ? 1.4F : 1.0F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        @Override
        public void appendHoverText(ItemStack stack, net.minecraft.world.level.BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.brief_guard.g_flux_anchor"));
        }
    }

    private static void tickAnchor(Player player) {
        if (player.tickCount % 10 != 0) return;
        AABB box = player.getBoundingBox().inflate(ANCHOR_SCAN);
        BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            BlockState bs = player.level().getBlockState(p);
            if (bs.getBlock() instanceof FluxAnchorBlock && bs.getValue(FluxAnchorBlock.RUNNING)) {
                fluxAnchorField(player.level(), Vec3.atCenterOf(p));
            }
        }
    }

    private static void fluxAnchorField(Level level, Vec3 center) {
        AABB box = AABB.ofSize(center, ANCHOR_RADIUS * 2.0D, ANCHOR_RADIUS * 2.0D, ANCHOR_RADIUS * 2.0D);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                t -> t.isAlive() && t instanceof Enemy)) {
            magnetize(level, e);
            pull(level, e, center, 0.20D);
        }
        magnetItems(level, center, ANCHOR_RADIUS, 0.9D);
    }

    // ---------------------------------------------------------------- 每 tick 服务端物理
    /** 由 BriefsEvents.tick 调用。 */
    static void tick(Player player) {
        tickBrand(player);
        tickCharm(player);
        tickChargeZones(player);
        tickShardZones(player);
        tickAnchor(player);
        pruneMagnet(player);
    }

    // ---------------------------------------------------------------- 事件钩子转发
}
