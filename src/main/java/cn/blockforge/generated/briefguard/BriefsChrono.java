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
import net.minecraft.world.entity.projectile.Projectile;
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
 * 时序/回溯系列装备（第 22 轮超大型更新）：一切机制都围绕"把状态退回过去的时间点"，
 * 与已有的聚拢（Flux，位移靠吸）、霜冻（Cryo，持续减速积霜）、余震（Echo，伤害延迟补发）、
 * 雷暴（Storm，链式放电）、影缚（Shadow，叠印斩杀）互不重叠——本系列玩的是"位置与状态的回档"。
 * <ul>
 *   <li>时渊刃（武器）：命中给目标烙上"时滞"并记录它此刻的位置；在时滞过期前再次命中，
 *       触发"回溯斩"——把目标强行拽回被标记时的位置，按它"逃掉的距离"追加大额时空伤害。
 *       风筝得越远，回溯越痛；站在开启的时锚石旁回溯更狠。</li>
 *   <li>回溯徽章（装备，副手）：每秒把你的位置存进时间线；受击时"自溯"——瞬回一秒前的位置，
 *       并按回溯距离恢复生命（回溯得越远，回血越多），冷却较长。</li>
 *   <li>凝时瓶（道具）：右键投向视线前方，生成一片"凝时场"：场内敌人时间近乎停摆——
 *       速度清零、动作迟滞，走不动也飞不起；消耗 1 个。</li>
 *   <li>回沙（道具）：右键掷出，区域内所有飞行中的弹射物（箭、三叉戟、火球…）被"回溯湮灭"，
 *       同时给范围内的敌人烙上时滞——是接时渊刃回溯斩的铺场道具；消耗 1 个。</li>
 *   <li>时锚石（方块）：记录附近玩家的位置与生命值；右键触发"时间线回溯"，把附近玩家
 *       拉回最近的存档点并把生命拨回当时的值；潜行右键切换记录开关。</li>
 * </ul>
 * 时滞（lag）与时间线快照（snapshot）是本套共享的两份服务端状态，全部事件驱动，不依赖常驻 buff。
 */
public final class BriefsChrono {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, GeneratedMod.MOD_ID);

    // ---------------------------------------------------------------- 注册
    public static final RegistryObject<Item> CHRONOBLADE =
            GeneratedMod.ITEMS.register("chronoblade", ChronobladeItem::new);
    public static final RegistryObject<Item> REWIND_CHARM =
            GeneratedMod.ITEMS.register("rewind_charm", RewindCharmItem::new);
    public static final RegistryObject<Item> STASIS_FLASK =
            GeneratedMod.ITEMS.register("stasis_flask", StasisFlaskItem::new);
    public static final RegistryObject<Item> REWIND_SAND =
            GeneratedMod.ITEMS.register("rewind_sand", RewindSandItem::new);
    public static final RegistryObject<Block> CHRONO_ANCHOR =
            BLOCKS.register("chrono_anchor", ChronoAnchorBlock::new);
    public static final RegistryObject<Item> CHRONO_ANCHOR_ITEM =
            GeneratedMod.ITEMS.register("chrono_anchor", () -> new BlockItem(CHRONO_ANCHOR.get(), new Item.Properties()));

    private BriefsChrono() {}

    public static void init(IEventBus bus) {
        BLOCKS.register(bus);
    }

    // ---------------------------------------------------------------- 时滞标记（敌人位置回档用）
    /** 时滞保留时间（tick）：过期后目标的位置记录作废。 */
    private static final long LAG_TTL = 100L;

    private static final class Lag {
        final Vec3 pos;
        final long time;
        Lag(Vec3 pos, long time) {
            this.pos = pos;
            this.time = time;
        }
    }

    private static final Map<UUID, Lag> LAG = new HashMap<>();

    private static void setLag(Level level, LivingEntity target) {
        LAG.put(target.getUUID(), new Lag(target.position(), level.getGameTime()));
    }

    /** 返回目标的有效时滞记录，没有则 null。 */
    private static Lag lagOf(LivingEntity target, long now) {
        Lag lag = LAG.get(target.getUUID());
        if (lag == null || now - lag.time > LAG_TTL) return null;
        return lag;
    }

    // ---------------------------------------------------------------- 时间线快照（玩家回档用）
    /** 快照超过这个时长就视为"时间线已流失"，回溯不再采信。 */
    private static final long SNAP_TTL = 140L;

    private static final class Snapshot {
        final Vec3 pos;
        final float health;
        final long time;
        Snapshot(Vec3 pos, float health, long time) {
            this.pos = pos;
            this.health = health;
            this.time = time;
        }
    }

    /** 回溯徽章每秒写入的个人时间线。 */
    private static final Map<UUID, Snapshot> SNAPSHOT = new HashMap<>();
    /** 时锚石写入的存档点（与徽章分开，互不干扰）。 */
    private static final Map<UUID, Snapshot> ANCHOR_SNAP = new HashMap<>();

    private static void record(Level level, Player player) {
        SNAPSHOT.put(player.getUUID(), new Snapshot(player.position(), player.getHealth(), level.getGameTime()));
    }

    private static Snapshot freshSnapshot(Player player, long now) {
        Snapshot snap = SNAPSHOT.get(player.getUUID());
        return (snap != null && now - snap.time <= SNAP_TTL) ? snap : null;
    }

    // ---------------------------------------------------------------- 通用助手
    private static List<LivingEntity> enemiesIn(Level level, Vec3 center, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        AABB box = AABB.ofSize(center, radius * 2.0D, radius * 2.0D, radius * 2.0D);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                t -> t != null && t.isAlive() && t instanceof Enemy)) {
            if (e.position().distanceToSqr(center) < radius * radius) out.add(e);
        }
        return out;
    }

    /** 时间主题的粒子：白棘 + 逆界门。 */
    private static void timeFx(Level level, Vec3 pos) {
        level.addParticle(ParticleTypes.END_ROD, pos.x, pos.y + 0.5D, pos.z, 0.0D, 0.05D, 0.0D);
        level.addParticle(ParticleTypes.REVERSE_PORTAL, pos.x, pos.y + 0.7D, pos.z, 0.0D, 0.10D, 0.0D);
    }

    private static void burstFx(Level level, Vec3 pos, int count) {
        for (int i = 0; i < count; i++) {
            double a = level.getRandom().nextDouble() * Math.PI * 2.0D;
            double r = 0.3D + level.getRandom().nextDouble() * 0.7D;
            level.addParticle(ParticleTypes.REVERSE_PORTAL,
                    pos.x + Math.cos(a) * r, pos.y + 0.4D + level.getRandom().nextDouble(), pos.z + Math.sin(a) * r,
                    0.0D, 0.12D, 0.0D);
        }
    }

    /** 射线指向落点：命中方块取方块中心，否则取视线前方 60% 处。 */
    private static Vec3 aimPoint(Level level, Player player, double range) {
        BlockHitResult hit = level.clip(new ClipContext(
                player.getEyePosition(),
                player.getEyePosition().add(player.getLookAngle().scale(range)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit.getType() == BlockHitResult.Type.BLOCK
                ? Vec3.atCenterOf(hit.getBlockPos())
                : player.getEyePosition().add(player.getLookAngle().scale(range * 0.6D));
    }

    // ---------------------------------------------------------------- 时渊刃
    private static final Tier CHRONO_TIER = new Tier() {
        @Override public int getUses() { return 520; }
        @Override public float getSpeed() { return 6.5F; }
        @Override public float getAttackDamageBonus() { return 3.0F; }
        @Override public int getLevel() { return 2; }
        @Override public int getEnchantmentValue() { return 14; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.of(net.minecraft.world.item.Items.CLOCK); }
    };

    /** 时渊刃：首击烙下时滞并定位，再击把目标拽回定位点，按逃掉的距离追加时空伤害。 */
    public static final class ChronobladeItem extends SwordItem {
        public ChronobladeItem() {
            super(CHRONO_TIER, 3, -2.4F, new Item.Properties().durability(CHRONO_TIER.getUses()));
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.brief_guard.g_chronoblade"));
        }
    }

    /** 由 BriefsEvents.attackEntity 调用：时滞/回溯斩逻辑。 */
    static void onAttack(Player player, LivingEntity target) {
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof ChronobladeItem)) return;
        Level level = player.level();
        if (level.isClientSide()) return;
        long now = level.getGameTime();
        Lag lag = lagOf(target, now);
        if (lag != null) {
            // 回溯斩：把目标拽回被标记时的位置，按它逃掉的距离追伤。
            LAG.remove(target.getUUID());
            double dist = target.position().distanceTo(lag.pos);
            boolean boosted = boostFromAnchor(player);
            float bonus = (float) Math.min(11.0D, 2.0D + dist * 0.9D + (boosted ? 2.0D : 0.0D));
            if (dist > 1.2D) {
                target.teleportTo(lag.pos.x, lag.pos.y, lag.pos.z);
                target.setDeltaMovement(Vec3.ZERO);
            }
            target.hurt(level.damageSources().magic(), bonus);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 1), player);
            burstFx(level, lag.pos, 10);
            timeFx(level, target.position());
            player.playSound(SoundEvents.CHORUS_FRUIT_TELEPORT, 0.9F, 1.3F);
            player.playSound(SoundEvents.PLAYER_ATTACK_CRIT, 1.0F, 1.1F);
        } else {
            // 落印：记录目标此刻的位置，等待下一次命中收网。
            setLag(level, target);
            timeFx(level, target.position());
        }
    }

    // ---------------------------------------------------------------- 回溯徽章
    /** 回溯徽章：副手佩戴，每秒把自己的位置写进时间线；受击时自溯回档并按距离回血。 */
    public static final class RewindCharmItem extends Item {
        public RewindCharmItem() {
            super(new Item.Properties().stacksTo(1));
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.brief_guard.g_rewind_charm"));
        }
    }

    /** 由 BriefsEvents.hurt 调用：受击自溯。伤害太小时不回档，避免被小伤害白白骗掉冷却。 */
    static void onHurt(Player player, LivingHurtEvent event) {
        ItemStack off = player.getOffhandItem();
        if (!(off.getItem() instanceof RewindCharmItem)) return;
        Level level = player.level();
        if (level.isClientSide()) return;
        if (event.getAmount() < 2.0F) return;
        if (player.getCooldowns().isOnCooldown(off.getItem())) return;
        Snapshot snap = freshSnapshot(player, level.getGameTime());
        if (snap == null) return;
        double dist = player.position().distanceTo(snap.pos);
        if (dist < 0.5D) return;
        player.teleportTo(snap.pos.x, snap.pos.y, snap.pos.z);
        player.setDeltaMovement(Vec3.ZERO);
        float heal = (float) Math.min(6.0D, 1.0D + dist * 0.45D);
        player.heal(heal);
        player.getCooldowns().addCooldown(off.getItem(), 240);
        burstFx(level, snap.pos, 8);
        player.playSound(SoundEvents.CHORUS_FRUIT_TELEPORT, 1.0F, 0.9F);
        player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8F, 1.5F);
    }

    /** 由 tick 调用：佩戴回溯徽章时每秒把当前位置写进个人时间线。 */
    private static void tickTimeline(Player player) {
        if (!(player.getOffhandItem().getItem() instanceof RewindCharmItem)) return;
        if (player.tickCount % 20 != 0) return;
        record(player.level(), player);
    }

    // ---------------------------------------------------------------- 凝时瓶
    private static final double FLASK_RANGE = 12.0D;
    private static final double FLASK_RADIUS = 4.0D;
    private static final long FLASK_DURATION = 120L;

    private static final List<StasisZone> ZONES = new ArrayList<>();

    private static final class StasisZone {
        final Level level;
        final Vec3 center;
        long expiry;
        StasisZone(Level level, Vec3 center, long expiry) {
            this.level = level;
            this.center = center;
            this.expiry = expiry;
        }
    }

    /** 凝时瓶：右键投向视线前方，生成一片让敌人时间近乎停摆的凝时场；消耗 1 个。 */
    public static final class StasisFlaskItem extends Item {
        public StasisFlaskItem() {
            super(new Item.Properties().stacksTo(8));
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!level.isClientSide()) {
                Vec3 center = aimPoint(level, player, FLASK_RANGE);
                ZONES.add(new StasisZone(level, center, level.getGameTime() + FLASK_DURATION));
                stack.shrink(1);
                burstFx(level, center, 12);
                player.playSound(SoundEvents.BREWING_STAND_BREW, 0.8F, 0.7F);
                player.getCooldowns().addCooldown(stack.getItem(), 15);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.brief_guard.g_stasis_flask"));
        }
    }

    private static void tickZones(Player player) {
        long now = player.level().getGameTime();
        Iterator<StasisZone> it = ZONES.iterator();
        while (it.hasNext()) {
            StasisZone z = it.next();
            if (z.level != player.level()) continue;
            if (now > z.expiry) { it.remove(); continue; }
            if (player.tickCount % 5 != 0) continue;
            for (LivingEntity e : enemiesIn(player.level(), z.center, FLASK_RADIUS)) {
                // 时间近乎停摆：速度清零 + 极限减速，走不动也飞不起。
                e.setDeltaMovement(Vec3.ZERO);
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 25, 4), player);
                timeFx(player.level(), e.position());
            }
        }
    }

    // ---------------------------------------------------------------- 回沙
    private static final double SAND_RANGE = 14.0D;
    private static final double SAND_RADIUS = 4.5D;

    /** 回沙：右键掷出，区域内飞行弹射物被回溯湮灭，并给范围内敌人烙上时滞；消耗 1 个。 */
    public static final class RewindSandItem extends Item {
        public RewindSandItem() {
            super(new Item.Properties().stacksTo(8));
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!level.isClientSide()) {
                Vec3 center = aimPoint(level, player, SAND_RANGE);
                AABB box = AABB.ofSize(center, SAND_RADIUS * 2.0D, SAND_RADIUS * 2.0D, SAND_RADIUS * 2.0D);
                int annihilated = 0;
                for (Projectile p : level.getEntitiesOfClass(Projectile.class, box)) {
                    if (p.position().distanceToSqr(center) < SAND_RADIUS * SAND_RADIUS) {
                        p.discard();
                        annihilated++;
                    }
                }
                List<LivingEntity> targets = enemiesIn(level, center, SAND_RADIUS);
                for (LivingEntity e : targets) {
                    setLag(level, e);
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 1), player);
                }
                stack.shrink(1);
                burstFx(level, center, 14);
                player.playSound(SoundEvents.SAND_FALL, 0.9F, 1.3F);
                player.getCooldowns().addCooldown(stack.getItem(), 15);
                if (annihilated == 0 && targets.isEmpty()) {
                    player.displayClientMessage(Component.translatable("message.brief_guard.sand_miss"), true);
                } else if (annihilated > 0) {
                    player.displayClientMessage(Component.translatable("message.brief_guard.sand_hit", annihilated), true);
                }
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.brief_guard.g_rewind_sand"));
        }
    }

    // ---------------------------------------------------------------- 时锚石
    private static final double ANCHOR_SCAN = 7.0D;
    private static final double ANCHOR_RADIUS = 6.0D;
    private static final double BOOST_RADIUS = 5.0D;
    private static final long ANCHOR_CD = 60L;

    private static final Map<BlockPos, Long> ANCHOR_COOLDOWN = new HashMap<>();

    /** 时锚石：默认开启记录附近玩家存档点；右键触发时间线回溯，潜行右键切换记录开关。 */
    public static final class ChronoAnchorBlock extends Block {
        public static final BooleanProperty RUNNING = BooleanProperty.create("running");

        public ChronoAnchorBlock() {
            super(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_LIGHT_BLUE)
                    .strength(2.6F, 9.0F)
                    .sound(SoundType.STONE)
                    .lightLevel(state -> state.getValue(RUNNING) ? 5 : 0));
            this.registerDefaultState(this.stateDefinition.any().setValue(RUNNING, Boolean.TRUE));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(RUNNING);
        }

        @Override
        public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            boolean running = state.getValue(RUNNING);
            if (player.isShiftKeyDown()) {
                // 潜行右键：切换记录开关。
                level.setBlockAndUpdate(pos, state.setValue(RUNNING, !running));
                player.playSound(running ? SoundEvents.PISTON_CONTRACT : SoundEvents.PISTON_EXTEND, 1.0F, running ? 1.4F : 1.0F);
                return InteractionResult.CONSUME;
            }
            // 右键：触发时间线回溯。
            long now = level.getGameTime();
            Long cd = ANCHOR_COOLDOWN.get(pos.immutable());
            if (cd != null && now - cd < ANCHOR_CD) {
                player.displayClientMessage(Component.translatable("message.brief_guard.anchor_cooling"), true);
                return InteractionResult.CONSUME;
            }
            int rewound = 0;
            Vec3 center = Vec3.atCenterOf(pos);
            AABB box = AABB.ofSize(center, ANCHOR_RADIUS * 2.0D, ANCHOR_RADIUS * 2.0D, ANCHOR_RADIUS * 2.0D);
            for (Player p : level.getEntitiesOfClass(Player.class, box)) {
                Snapshot snap = ANCHOR_SNAP.get(p.getUUID());
                if (snap == null || now - snap.time > SNAP_TTL * 4) continue;
                p.teleportTo(snap.pos.x, snap.pos.y, snap.pos.z);
                p.setDeltaMovement(Vec3.ZERO);
                float target = Math.min(snap.health, p.getMaxHealth());
                if (p.getHealth() < target) p.heal(target - p.getHealth());
                burstFx(level, snap.pos, 10);
                rewound++;
            }
            if (rewound > 0) {
                ANCHOR_COOLDOWN.put(pos.immutable(), now);
                player.playSound(SoundEvents.CHORUS_FRUIT_TELEPORT, 1.1F, 0.8F);
                player.playSound(SoundEvents.BEACON_POWER_SELECT, 0.9F, 1.4F);
            } else {
                player.displayClientMessage(Component.translatable("message.brief_guard.anchor_no_snap"), true);
            }
            return InteractionResult.CONSUME;
        }

        @Override
        public void appendHoverText(ItemStack stack, net.minecraft.world.level.BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.brief_guard.g_chrono_anchor"));
        }
    }

    /** 站在开启的时锚石附近时，时渊刃回溯斩更痛。 */
    private static boolean boostFromAnchor(Player player) {
        AABB box = player.getBoundingBox().inflate(BOOST_RADIUS);
        BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            BlockState bs = player.level().getBlockState(p);
            if (bs.getBlock() instanceof ChronoAnchorBlock && bs.getValue(ChronoAnchorBlock.RUNNING)) {
                return true;
            }
        }
        return false;
    }

    private static void tickAnchors(Player player) {
        if (player.level().isClientSide() || player.tickCount % 20 != 0) return;
        AABB box = player.getBoundingBox().inflate(ANCHOR_SCAN);
        BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            BlockState bs = player.level().getBlockState(p);
            if (bs.getBlock() instanceof ChronoAnchorBlock && bs.getValue(ChronoAnchorBlock.RUNNING)) {
                Vec3 center = Vec3.atCenterOf(p);
                if (player.position().distanceTo(center) <= ANCHOR_RADIUS) {
                    ANCHOR_SNAP.put(player.getUUID(),
                            new Snapshot(player.position(), player.getHealth(), player.level().getGameTime()));
                    timeFx(player.level(), center);
                }
            }
        }
    }

    // ---------------------------------------------------------------- 状态清理
    private static void prune(long now) {
        LAG.values().removeIf(l -> now - l.time > LAG_TTL * 3);
        SNAPSHOT.values().removeIf(s -> now - s.time > SNAP_TTL * 3);
        ANCHOR_SNAP.values().removeIf(s -> now - s.time > SNAP_TTL * 8);
        ANCHOR_COOLDOWN.values().removeIf(t -> now - t > 2000L);
    }

    // ---------------------------------------------------------------- 每 tick 服务端物理
    /** 由 BriefsEvents.tick 调用。 */
    static void tick(Player player) {
        if (player.level().isClientSide()) return;
        tickTimeline(player);
        tickZones(player);
        tickAnchors(player);
        if (player.tickCount % 200 == 0) prune(player.level().getGameTime());
    }
}
