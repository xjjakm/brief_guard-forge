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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
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
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 影缚/阴影系列装备（第 19 轮超大型更新）：
 * <ul>
 *   <li>影刃（武器）：命中给目标叠"影印"，叠到 3 层时下一击打出"影袭"终结——消耗全部影印、
 *       造成大量加成伤害并让目标虚弱；未满层时每次命中都附带小幅影击。站在开启的影锚石旁会让
 *       影袭更痛。</li>
 *   <li>影匿徽章（装备，副手）：受击时几率遁入阴影——短暂隐形并加速逃离，冷却较长。</li>
 *   <li>影遁瓶（道具）：右键投向视线前方，留下一片"阴影区"，范围内敌人持续被迟滞并削弱；消耗 1 个。</li>
 *   <li>染影尘（道具）：右键掷出，把范围内所有敌人打上影印并减速，便于用影刃接终结；消耗 1 个。</li>
 *   <li>影锚石（方块）：放置后默认开启，周期性迟滞并削弱周围敌人；站在
 *       旁边会强化影刃的影袭；右键切换开关。</li>
 * </ul>
 * 全部为事件驱动、状态挂在服务端列表上，不依赖常驻 buff。
 * 影印（mark）是整套共享的一套标记：影刃负责"叠印→终结"，染影尘负责"铺印"，影遁瓶/影锚石负责"迟滞削弱"。
 * 核心机制与已有的聚拢（Flux）、霜冻（Cryo）、余震（Echo）、雷暴（Storm）互不重叠。
 */
public final class BriefsShadow {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, GeneratedMod.MOD_ID);

    // ---------------------------------------------------------------- 注册
    public static final RegistryObject<Item> SHADOWBLADE =
            GeneratedMod.ITEMS.register("shadowblade", ShadowbladeItem::new);
    public static final RegistryObject<Item> VEIL_CHARM =
            GeneratedMod.ITEMS.register("veil_charm", VeilCharmItem::new);
    public static final RegistryObject<Item> VEIL_FLASK =
            GeneratedMod.ITEMS.register("veil_flask", VeilFlaskItem::new);
    public static final RegistryObject<Item> SHADE_DUST =
            GeneratedMod.ITEMS.register("shade_dust", ShadeDustItem::new);
    public static final RegistryObject<Block> SHADE_ANCHOR =
            BLOCKS.register("shade_anchor", ShadeAnchorBlock::new);
    public static final RegistryObject<Item> SHADE_ANCHOR_ITEM =
            GeneratedMod.ITEMS.register("shade_anchor", () -> new BlockItem(SHADE_ANCHOR.get(), new Item.Properties()));

    private BriefsShadow() {}

    public static void init(IEventBus bus) {
        BLOCKS.register(bus);
    }

    // ---------------------------------------------------------------- 影印共享标记
    /** 影印最大层数。 */
    private static final int MARK_MAX = 3;
    /** 影印保留时间（tick），过期自动失效，避免敌人永久挂印。 */
    private static final long MARK_TTL = 120L;
    private static final float FINISH_BASE = 4.0F;
    private static final float FINISH_PER = 1.5F;

    private static final class Mark {
        int count;
        long time;
        Mark(int count, long time) {
            this.count = count;
            this.time = time;
        }
    }

    private static final Map<UUID, Mark> SHADOW_MARK = new HashMap<>();

    private static int markCount(LivingEntity entity) {
        Mark m = SHADOW_MARK.get(entity.getUUID());
        if (m == null) return 0;
        return (entity.level().getGameTime() - m.time) < MARK_TTL ? m.count : 0;
    }

    private static void mark(Level level, LivingEntity entity, int amount) {
        long now = level.getGameTime();
        Mark m = SHADOW_MARK.get(entity.getUUID());
        int c = (m != null && (now - m.time) < MARK_TTL) ? m.count : 0;
        SHADOW_MARK.put(entity.getUUID(), new Mark(Math.min(MARK_MAX, c + amount), now));
    }

    private static void unmark(LivingEntity entity) {
        SHADOW_MARK.remove(entity.getUUID());
    }

    private static void pruneMarks(Player player) {
        if (player.tickCount % 20 != 0) return;
        long cutoff = player.level().getGameTime() - MARK_TTL;
        SHADOW_MARK.values().removeIf(m -> m.time < cutoff);
    }

    // ---------------------------------------------------------------- 通用助手
    /** 收集以 center 为圆心、radius 为半径内的敌人。 */
    private static List<LivingEntity> enemiesIn(Level level, Vec3 center, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        AABB box = AABB.ofSize(center, radius * 2.0D, radius * 2.0D, radius * 2.0D);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                t -> t != null && t.isAlive() && t instanceof Enemy)) {
            if (e.position().distanceToSqr(center) < radius * radius) out.add(e);
        }
        return out;
    }

    private static void inkFx(Level level, Vec3 pos) {
        level.addParticle(ParticleTypes.SQUID_INK, pos.x, pos.y + 0.5D, pos.z, 0.0D, 0.04D, 0.0D);
        level.addParticle(ParticleTypes.SCULK_SOUL, pos.x, pos.y + 0.6D, pos.z, 0.0D, 0.10D, 0.0D);
    }

    private static void slashFx(Level level, Vec3 pos) {
        level.addParticle(ParticleTypes.SWEEP_ATTACK, pos.x, pos.y + 0.7D, pos.z, 0.0D, 0.0D, 0.0D);
        level.addParticle(ParticleTypes.SMOKE, pos.x, pos.y + 0.6D, pos.z, 0.0D, 0.06D, 0.0D);
    }

    // ---------------------------------------------------------------- 影刃
    private static final Tier SHADOW_TIER = new Tier() {
        @Override public int getUses() { return 460; }
        @Override public float getSpeed() { return 7.0F; }
        @Override public float getAttackDamageBonus() { return 3.5F; }
        @Override public int getLevel() { return 2; }
        @Override public int getEnchantmentValue() { return 12; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.of(net.minecraft.world.item.Items.OBSIDIAN); }
    };

    /** 影刃：命中叠"影印"，叠满 3 层后下一击打出影袭终结。 */
    public static final class ShadowbladeItem extends SwordItem {
        public ShadowbladeItem() {
            super(SHADOW_TIER, 2, -2.0F, new Item.Properties().durability(SHADOW_TIER.getUses()));
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.brief_guard.g_shadowblade"));
        }
    }

    /** 由 BriefsEvents.attackEntity 调用：影印/影袭逻辑。 */
    static void onAttack(Player player, LivingEntity target) {
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof ShadowbladeItem)) return;
        Level level = player.level();
        if (level.isClientSide()) return;
        int c = markCount(target);
        boolean boosted = boostFromAnchor(player);
        if (c >= MARK_MAX) {
            unmark(target);
            float bonus = FINISH_BASE + FINISH_PER * c + (boosted ? 2.0F : 0.0F);
            target.hurt(level.damageSources().sweetBerryBush(), bonus);
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0), player);
            inkFx(level, target.position());
            player.playSound(SoundEvents.PLAYER_ATTACK_CRIT, 1.0F, 1.2F);
        } else {
            mark(level, target, 1);
            if (c > 0) {
                float bonus = c * (boosted ? 1.5F : 1.0F);
                target.hurt(level.damageSources().sweetBerryBush(), bonus);
                slashFx(level, target.position());
            }
        }
    }

    // ---------------------------------------------------------------- 影匿徽章
    /** 影匿徽章：装备在副手。受击时几率遁入阴影并加速逃离。 */
    public static final class VeilCharmItem extends Item {
        public VeilCharmItem() {
            super(new Item.Properties().stacksTo(1));
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.brief_guard.g_veil_charm"));
        }
    }

    /** 由 BriefsEvents.hurt 调用：影匿徽章的遁影逃离。 */
    static void onHurt(Player player, LivingHurtEvent event) {
        ItemStack off = player.getOffhandItem();
        if (!(off.getItem() instanceof VeilCharmItem)) return;
        if (player.level().isClientSide()) return;
        if (player.getCooldowns().isOnCooldown(off.getItem())) return;
        if (player.getRandom().nextFloat() < 0.45F) {
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 50, 0));
            player.getCooldowns().addCooldown(off.getItem(), 200);
            inkFx(player.level(), player.position());
            player.playSound(SoundEvents.ENDERMAN_TELEPORT, 0.8F, 1.4F);
        }
    }

    // ---------------------------------------------------------------- 影遁瓶
    private static final double FLASK_RANGE = 12.0D;
    private static final double FLASK_RADIUS = 4.0D;
    private static final long FLASK_DURATION = 100L;

    private static final List<ShadowZone> ZONES = new ArrayList<>();

    private static final class ShadowZone {
        final Level level;
        final Vec3 center;
        long expiry;
        ShadowZone(Level level, Vec3 center, long expiry) {
            this.level = level;
            this.center = center;
            this.expiry = expiry;
        }
    }

    /** 影遁瓶：右键投向视线前方，留下一片迟滞并削弱敌人的阴影区；消耗 1 个。 */
    public static final class VeilFlaskItem extends Item {
        public VeilFlaskItem() {
            super(new Item.Properties().stacksTo(8));
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!level.isClientSide()) {
                BlockHitResult hit = level.clip(new ClipContext(
                        player.getEyePosition(),
                        player.getEyePosition().add(player.getLookAngle().scale(FLASK_RANGE)),
                        ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
                Vec3 center = hit.getType() == BlockHitResult.Type.BLOCK
                        ? Vec3.atCenterOf(hit.getBlockPos())
                        : player.getEyePosition().add(player.getLookAngle().scale(FLASK_RANGE * 0.6D));
                ZONES.add(new ShadowZone(level, center, level.getGameTime() + FLASK_DURATION));
                stack.shrink(1);
                inkFx(level, center);
                player.playSound(SoundEvents.BOTTLE_EMPTY, 0.8F, 1.2F);
                player.getCooldowns().addCooldown(stack.getItem(), 10);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.brief_guard.g_veil_flask"));
        }
    }

    private static void tickZones(Player player) {
        long now = player.level().getGameTime();
        Iterator<ShadowZone> it = ZONES.iterator();
        while (it.hasNext()) {
            ShadowZone z = it.next();
            if (z.level != player.level()) continue;
            if (now > z.expiry) { it.remove(); continue; }
            if (player.tickCount % 10 != 0) continue;
            for (LivingEntity e : enemiesIn(player.level(), z.center, FLASK_RADIUS)) {
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1), player);
                e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30, 0), player);
            }
            inkFx(player.level(), z.center);
        }
    }

    // ---------------------------------------------------------------- 染影尘
    private static final double DUST_RANGE = 14.0D;
    private static final double DUST_RADIUS = 4.5D;

    /** 染影尘：右键掷出，给范围内敌人打上影印并减速；消耗 1 个。 */
    public static final class ShadeDustItem extends Item {
        public ShadeDustItem() {
            super(new Item.Properties().stacksTo(8));
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!level.isClientSide()) {
                BlockHitResult hit = level.clip(new ClipContext(
                        player.getEyePosition(),
                        player.getEyePosition().add(player.getLookAngle().scale(DUST_RANGE)),
                        ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
                Vec3 center = hit.getType() == BlockHitResult.Type.BLOCK
                        ? Vec3.atCenterOf(hit.getBlockPos())
                        : player.getEyePosition().add(player.getLookAngle().scale(DUST_RANGE * 0.6D));
                List<LivingEntity> targets = enemiesIn(level, center, DUST_RADIUS);
                for (LivingEntity e : targets) {
                    mark(level, e, 2);
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 0), player);
                }
                stack.shrink(1);
                inkFx(level, center);
                player.playSound(SoundEvents.BONE_MEAL_USE, 0.8F, 1.2F);
                player.getCooldowns().addCooldown(stack.getItem(), 10);
                if (targets.isEmpty()) {
                    player.displayClientMessage(Component.translatable("message.brief_guard.dust_miss"), true);
                }
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.brief_guard.g_shade_dust"));
        }
    }

    // ---------------------------------------------------------------- 影锚石
    private static final double STONE_RADIUS = 5.0D;
    private static final double BOOST_RADIUS = 5.0D;
    private static final int STONE_SCAN = 7;

    /** 影锚石：放置后默认开启，周期性迟滞并削弱周围敌人；站旁边强化影刃影袭；右键切换开关。 */
    public static final class ShadeAnchorBlock extends Block {
        public static final BooleanProperty RUNNING = BooleanProperty.create("running");

        public ShadeAnchorBlock() {
            super(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(2.4F, 8.0F)
                    .sound(SoundType.STONE)
                    .lightLevel(state -> state.getValue(RUNNING) ? 4 : 0));
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
            tooltip.add(Component.translatable("tooltip.brief_guard.g_shade_anchor"));
        }
    }

    /** 站在开启的影锚石附近时，影刃的影袭更痛。 */
    private static boolean boostFromAnchor(Player player) {
        AABB box = player.getBoundingBox().inflate(BOOST_RADIUS);
        BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            BlockState bs = player.level().getBlockState(p);
            if (bs.getBlock() instanceof ShadeAnchorBlock && bs.getValue(ShadeAnchorBlock.RUNNING)) {
                return true;
            }
        }
        return false;
    }

    private static void tickAnchors(Player player) {
        if (player.level().isClientSide() || player.tickCount % 10 != 0) return;
        AABB box = player.getBoundingBox().inflate(STONE_SCAN);
        BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            BlockState bs = player.level().getBlockState(p);
            if (bs.getBlock() instanceof ShadeAnchorBlock && bs.getValue(ShadeAnchorBlock.RUNNING)) {
                shadeField(player.level(), Vec3.atCenterOf(p));
            }
        }
    }

    private static void shadeField(Level level, Vec3 center) {
        for (LivingEntity e : enemiesIn(level, center, STONE_RADIUS)) {
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1));
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30, 0));
        }
        level.addParticle(ParticleTypes.SCULK_SOUL,
                center.x + (level.getRandom().nextDouble() - 0.5D), center.y + 0.6D,
                center.z + (level.getRandom().nextDouble() - 0.5D), 0.0D, 0.08D, 0.0D);
    }

    // ---------------------------------------------------------------- 每 tick 服务端物理
    /** 由 BriefsEvents.tick 调用。 */
    static void tick(Player player) {
        tickZones(player);
        tickAnchors(player);
        pruneMarks(player);
    }
}
