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
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 雷暴/静电系列装备（第 18 轮超大型更新）：
 * <ul>
 *   <li>雷犽之刃（武器）：命中触发"链式雷弧"——从目标开始逐跳到周围敌人，伤害逐级衰减；
 *       命中已"过电"的目标会消耗其过电，打出更长更痛的"强化电击"。雷弧每命中一个敌人就给它
 *       积一层过电，形成"过电→强化电击"的闭环。</li>
 *   <li>引雷徽章（装备，副手）：周期性自动电击离你最近的过电敌人（活体哨塔）；佩戴者免疫闪电。</li>
 *   <li>雷云囊（道具）：右键投向视线前方，生成一小片雷云，持续给范围内敌人积"过电"并电击最近者；
 *       消耗 1 个。</li>
 *   <li>雷索镖（道具）：右键掷出，钉住首个命中的敌人，持续放电并保持其"过电"，成为雷弧的活体引电点；
 *       消耗 1 个。</li>
 *   <li>蓄雷石（方块）：放置后默认开启，雷击范围内最近的敌人并给敌人积过电；站在附近会为雷犽之刃的
 *       雷弧多跳一次；右键切换开关。</li>
 * </ul>
 * 全部为事件驱动、状态挂在物品 NBT / 服务端列表上，不依赖常驻 buff。
 * 过电（charged）是全套共享的标记：雷犽之刃负责"造过电并放电"，引雷徽章/雷云囊/蓄雷石负责"自动放电"。
 * 核心机制与已有的聚拢（Flux）、霜冻（Cryo）、余震（Echo）互不重叠。
 */
public final class BriefsStorm {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, GeneratedMod.MOD_ID);

    // ---------------------------------------------------------------- 注册
    public static final RegistryObject<Item> THUNDERFANG =
            GeneratedMod.ITEMS.register("thunderfang", ThunderfangItem::new);
    public static final RegistryObject<Item> VOLT_CHARM =
            GeneratedMod.ITEMS.register("volt_charm", VoltCharmItem::new);
    public static final RegistryObject<Item> STORM_SACHET =
            GeneratedMod.ITEMS.register("storm_sachet", StormSachetItem::new);
    public static final RegistryObject<Item> VOLT_DART =
            GeneratedMod.ITEMS.register("volt_dart", VoltDartItem::new);
    public static final RegistryObject<Block> VOLT_STONE =
            BLOCKS.register("volt_stone", VoltStoneBlock::new);
    public static final RegistryObject<Item> VOLT_STONE_ITEM =
            GeneratedMod.ITEMS.register("volt_stone", () -> new BlockItem(VOLT_STONE.get(), new Item.Properties()));

    private BriefsStorm() {}

    public static void init(IEventBus bus) {
        BLOCKS.register(bus);
    }

    // ---------------------------------------------------------------- 过电共享标记
    /** 过电标记在目标身上保留的时间（tick），过期自动失效，避免永久过电。 */
    private static final long CHARGED_TTL = 60L;

    private static final Map<UUID, Long> CHARGED = new HashMap<>();

    private static void charge(Level level, LivingEntity entity) {
        CHARGED.put(entity.getUUID(), level.getGameTime());
    }

    private static boolean isCharged(LivingEntity entity) {
        Long t = CHARGED.get(entity.getUUID());
        return t != null && (entity.level().getGameTime() - t) < CHARGED_TTL;
    }

    private static void uncharge(LivingEntity entity) {
        CHARGED.remove(entity.getUUID());
    }

    private static void pruneCharged(Player player) {
        if (player.tickCount % 20 != 0) return;
        long cutoff = player.level().getGameTime() - CHARGED_TTL;
        CHARGED.values().removeIf(t -> t < cutoff);
    }

    // ---------------------------------------------------------------- 通用雷弧助手
    /** 找出以 from 为圆心、radius 为半径、且不在 exclude 里的最近一个敌人。 */
    private static LivingEntity nearestEnemyExcept(Level level, Vec3 from, double radius, List<LivingEntity> exclude) {
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        AABB box = AABB.ofSize(from, radius * 2.0D, radius * 2.0D, radius * 2.0D);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                t -> t != null && t.isAlive() && t instanceof Enemy && !exclude.contains(t))) {
            double d = e.position().distanceToSqr(from);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    /** 给某个位置打一道落雷视觉/音效。 */
    private static void boltFx(Level level, Vec3 pos) {
        level.addParticle(ParticleTypes.SONIC_BOOM, pos.x, pos.y + 0.5D, pos.z, 0.0D, 0.0D, 0.0D);
        level.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y + 0.6D, pos.z, 0.0D, 0.12D, 0.0D);
        level.playSound(null, BlockPos.containing(pos), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.NEUTRAL, 0.5F, 1.4F);
    }

    /** 对目标制造一道指向它的单发闪电伤害。 */
    private static void strike(Level level, LivingEntity target, float damage) {
        target.hurt(level.damageSources().sweetBerryBush(), damage);
        boltFx(level, target.position());
    }

    // ---------------------------------------------------------------- 雷犽之刃
    private static final int ARC_CHAIN = 3;        // 基础雷弧最多追加命中的敌人数
    private static final double ARC_JUMP = 4.0D;   // 每次跳跃的最大距离
    private static final float ARC_DMG = 3.0F;     // 雷弧第一跳伤害
    private static final float ARC_FALL = 0.8F;    // 每跳衰减
    private static final int ARC_COOLDOWN = 12;    // 普通雷弧的最小间隔（tick）

    private static final Map<UUID, Long> ARC_LAST = new HashMap<>();

    private static final Tier THUNDER_TIER = new Tier() {
        @Override public int getUses() { return 480; }
        @Override public float getSpeed() { return 7.0F; }
        @Override public float getAttackDamageBonus() { return 4.0F; }
        @Override public int getLevel() { return 2; }
        @Override public int getEnchantmentValue() { return 12; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.of(net.minecraft.world.item.Items.COPPER_INGOT); }
    };

    /** 雷犽之刃：命中触发链式雷弧；命中已"过电"目标触发强化电击并消耗其过电。 */
    public static final class ThunderfangItem extends SwordItem {
        public ThunderfangItem() {
            super(THUNDER_TIER, 2, -2.0F, new Item.Properties().durability(THUNDER_TIER.getUses()));
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.brief_guard.g_thunderfang"));
        }
    }

    /**
     * 由 BriefsEvents.attackEntity 调用：雷弧/过电逻辑。
     * 命中已过电的目标会消耗其过电并打出强化电击（雷弧更长更痛）；否则只先给目标过电。
     */
    static void onAttack(Player player, LivingEntity target) {
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof ThunderfangItem)) return;
        Level level = player.level();
        if (level.isClientSide()) return;
        boolean strong = isCharged(target);
        if (strong) uncharge(target);
        else charge(level, target);
        long now = level.getGameTime();
        Long last = ARC_LAST.get(player.getUUID());
        boolean offCd = last == null || (now - last) >= ARC_COOLDOWN;
        if (strong || offCd || isDarted(target)) {
            arc(player, target, strong);
            ARC_LAST.put(player.getUUID(), now);
        } else {
            // 冷却中只保留过电，等下一次命中再放电。
            level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                    target.getX(), target.getY() + 0.7D, target.getZ(), 0.0D, 0.10D, 0.0D);
        }
    }

    /** 链式雷弧：从起点敌人开始，逐跳命中周围的其它敌人，伤害逐级衰减。 */
    private static void arc(Player player, LivingEntity start, boolean strong) {
        Level level = player.level();
        int hops = ARC_CHAIN + (strong ? 1 : 0) + (boostFromStone(player) ? 1 : 0);
        float dmg = strong ? ARC_DMG * 1.4F : ARC_DMG;
        List<LivingEntity> hit = new ArrayList<>();
        hit.add(start);
        LivingEntity prev = start;
        charge(level, start);
        for (int i = 0; i < hops; i++) {
            LivingEntity next = nearestEnemyExcept(level, prev.position(), ARC_JUMP, hit);
            if (next == null) break;
            dmg *= ARC_FALL;
            next.hurt(level.damageSources().sweetBerryBush(), dmg);
            charge(level, next);
            hit.add(next);
            prev = next;
            level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                    prev.getX(), prev.getY() + 0.6D, prev.getZ(), 0.0D, 0.14D, 0.0D);
        }
        boltFx(level, start.position());
        if (hit.size() > 1) {
            player.playSound(SoundEvents.LIGHTNING_BOLT_IMPACT, 0.6F, 1.3F);
        }
    }

    // ---------------------------------------------------------------- 引雷徽章
    private static final double CHARM_RADIUS = 12.0D;
    private static final float CHARM_DMG = 4.0F;

    /** 引雷徽章：装备在副手。周期性电击最近的过电敌人；让佩戴者免疫闪电。 */
    public static final class VoltCharmItem extends Item {
        public VoltCharmItem() {
            super(new Item.Properties().stacksTo(1));
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.brief_guard.g_volt_charm"));
        }
    }

    /** 佩戴引雷徽章时免疫闪电伤害。 */
    static boolean blocksLightning(Player player) {
        return player.getOffhandItem().getItem() instanceof VoltCharmItem;
    }

    private static void tickCharm(Player player) {
        if (player.level().isClientSide()) return;
        ItemStack off = player.getOffhandItem();
        if (!(off.getItem() instanceof VoltCharmItem)) return;
        if (player.tickCount % 20 != 0) return;
        LivingEntity target = nearestCharged(player, CHARM_RADIUS);
        if (target != null) {
            uncharge(target);
            strike(player.level(), target, CHARM_DMG);
            player.playSound(SoundEvents.LIGHTNING_BOLT_IMPACT, 0.7F, 1.2F);
        }
    }

    /** 找出离玩家最近的一个过电敌人。 */
    private static LivingEntity nearestCharged(Player player, double radius) {
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        AABB box = player.getBoundingBox().inflate(radius);
        for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, box,
                t -> t != null && t.isAlive() && t instanceof Enemy && isCharged(t))) {
            double d = e.position().distanceToSqr(player.position());
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- 雷云囊
    private static final double CLOUD_RANGE = 14.0D;
    private static final double CLOUD_RADIUS = 5.0D;
    private static final long CLOUD_DURATION = 90L;
    private static final float CLOUD_DMG = 3.0F;
    private static final int CLOUD_STRIKE = 14;

    private static final List<CloudZone> CLOUDS = new ArrayList<>();

    private static final class CloudZone {
        final Level level;
        final Vec3 center;
        long expiry;
        CloudZone(Level level, Vec3 center, long expiry) {
            this.level = level;
            this.center = center;
            this.expiry = expiry;
        }
    }

    /** 雷云囊：右键投向视线前方，生成一片持续落雷、给敌人积"过电"的雷云；消耗 1 个。 */
    public static final class StormSachetItem extends Item {
        public StormSachetItem() {
            super(new Item.Properties().stacksTo(8));
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!level.isClientSide()) {
                BlockHitResult hit = level.clip(new ClipContext(
                        player.getEyePosition(),
                        player.getEyePosition().add(player.getLookAngle().scale(CLOUD_RANGE)),
                        ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
                Vec3 center = hit.getType() == BlockHitResult.Type.BLOCK
                        ? Vec3.atCenterOf(hit.getBlockPos())
                        : player.getEyePosition().add(player.getLookAngle().scale(CLOUD_RANGE * 0.6D));
                CLOUDS.add(new CloudZone(level, center, level.getGameTime() + CLOUD_DURATION));
                stack.shrink(1);
                player.playSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6F, 1.3F);
                boltFx(level, center);
                player.getCooldowns().addCooldown(stack.getItem(), 10);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.brief_guard.g_storm_sachet"));
        }
    }

    private static void tickClouds(Player player) {
        long now = player.level().getGameTime();
        Iterator<CloudZone> it = CLOUDS.iterator();
        while (it.hasNext()) {
            CloudZone z = it.next();
            if (z.level != player.level()) continue;
            if (now > z.expiry) { it.remove(); continue; }
            if (player.tickCount % CLOUD_STRIKE != 0) continue;
            AABB box = AABB.ofSize(z.center, CLOUD_RADIUS * 2.0D, CLOUD_RADIUS * 2.0D, CLOUD_RADIUS * 2.0D);
            LivingEntity nearest = null;
            double best = Double.MAX_VALUE;
            for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, box,
                    t -> t.isAlive() && t instanceof Enemy)) {
                charge(player.level(), e);
                double d = e.position().distanceToSqr(z.center);
                if (d < best) { best = d; nearest = e; }
            }
            if (nearest != null) {
                strike(player.level(), nearest, CLOUD_DMG);
            }
            player.level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                    z.center.x + (player.level().getRandom().nextDouble() - 0.5D), z.center.y + player.level().getRandom().nextDouble() * 1.5D,
                    z.center.z + (player.level().getRandom().nextDouble() - 0.5D), 0.0D, 0.08D, 0.0D);
        }
    }

    // ---------------------------------------------------------------- 雷索镖
    private static final double DART_RANGE = 14.0D;
    private static final double DART_HIT_RADIUS = 2.5D;
    private static final long DART_DURATION = 120L;
    private static final float DART_DMG = 1.5F;

    private static final List<DartZone> DARTS = new ArrayList<>();

    private static final class DartZone {
        final Level level;
        final UUID target;
        long expiry;
        DartZone(Level level, UUID target, long expiry) {
            this.level = level;
            this.target = target;
            this.expiry = expiry;
        }
    }

    /** 雷索镖：右键掷出，钉住首个命中的敌人，持续放电并保持其过电；消耗 1 个。 */
    public static final class VoltDartItem extends Item {
        public VoltDartItem() {
            super(new Item.Properties().stacksTo(8));
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!level.isClientSide()) {
                BlockHitResult hit = level.clip(new ClipContext(
                        player.getEyePosition(),
                        player.getEyePosition().add(player.getLookAngle().scale(DART_RANGE)),
                        ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
                Vec3 end = hit.getType() == BlockHitResult.Type.BLOCK
                        ? Vec3.atCenterOf(hit.getBlockPos())
                        : player.getEyePosition().add(player.getLookAngle().scale(DART_RANGE * 0.6D));
                LivingEntity target = nearestEnemyExcept(level, end, DART_HIT_RADIUS, List.<LivingEntity>of(player));
                if (target != null) {
                    DARTS.add(new DartZone(level, target.getUUID(), level.getGameTime() + DART_DURATION));
                    boltFx(level, target.position());
                    player.playSound(SoundEvents.LIGHTNING_BOLT_IMPACT, 0.8F, 1.2F);
                } else {
                    player.displayClientMessage(Component.translatable("message.brief_guard.dart_miss"), true);
                }
                stack.shrink(1);
                player.getCooldowns().addCooldown(stack.getItem(), 10);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.brief_guard.g_volt_dart"));
        }
    }

    /** 雷索镖命中的目标在钉住期间视为"过电"，增强雷弧。 */
    private static boolean isDarted(LivingEntity target) {
        for (DartZone z : DARTS) {
            if (z.target.equals(target.getUUID()) && target.level().getGameTime() < z.expiry) {
                return true;
            }
        }
        return false;
    }

    private static void tickDarts(Player player) {
        long now = player.level().getGameTime();
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        Iterator<DartZone> it = DARTS.iterator();
        while (it.hasNext()) {
            DartZone z = it.next();
            if (z.level != player.level()) continue;
            if (now > z.expiry) { it.remove(); continue; }
            if (player.tickCount % 10 != 0) continue;
            LivingEntity t = sl.getEntity(z.target) instanceof LivingEntity le && le.isAlive() ? le : null;
            if (t == null) { it.remove(); continue; }
            charge(player.level(), t);
            strike(player.level(), t, DART_DMG);
            player.level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                    t.getX(), t.getY() + 0.7D, t.getZ(), 0.0D, 0.1D, 0.0D);
        }
    }

    // ---------------------------------------------------------------- 蓄雷石
    private static final double STONE_RADIUS = 6.0D;
    private static final float STONE_DMG = 4.0F;
    private static final double BOOST_RADIUS = 5.0D;
    private static final int STONE_SCAN = 6;

    /** 蓄雷石：放置后默认开启，雷击范围内最近的敌人并给敌人积过电；站旁边为雷弧增跳；右键切换开关。 */
    public static final class VoltStoneBlock extends Block {
        public static final BooleanProperty RUNNING = BooleanProperty.create("running");

        public VoltStoneBlock() {
            super(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.2F, 6.0F)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(RUNNING) ? 7 : 0));
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
            tooltip.add(Component.translatable("tooltip.brief_guard.g_volt_stone"));
        }
    }

    /** 站在开启的蓄雷石附近时，雷犽之刃的雷弧多跳一次。 */
    private static boolean boostFromStone(Player player) {
        AABB box = player.getBoundingBox().inflate(BOOST_RADIUS);
        BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            BlockState bs = player.level().getBlockState(p);
            if (bs.getBlock() instanceof VoltStoneBlock && bs.getValue(VoltStoneBlock.RUNNING)) {
                return true;
            }
        }
        return false;
    }

    private static void tickStones(Player player) {
        if (player.level().isClientSide() || player.tickCount % 10 != 0) return;
        AABB box = player.getBoundingBox().inflate(STONE_SCAN);
        BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            BlockState bs = player.level().getBlockState(p);
            if (bs.getBlock() instanceof VoltStoneBlock && bs.getValue(VoltStoneBlock.RUNNING)) {
                voltStoneField(player.level(), Vec3.atCenterOf(p));
            }
        }
    }

    private static void voltStoneField(Level level, Vec3 center) {
        AABB box = AABB.ofSize(center, STONE_RADIUS * 2.0D, STONE_RADIUS * 2.0D, STONE_RADIUS * 2.0D);
        LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                t -> t.isAlive() && t instanceof Enemy)) {
            charge(level, e);
            double d = e.position().distanceToSqr(center);
            if (d < best) { best = d; nearest = e; }
        }
        if (nearest != null) {
            strike(level, nearest, STONE_DMG);
        }
        level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                center.x, center.y + 0.6D, center.z, 0.0D, 0.10D, 0.0D);
    }

    // ---------------------------------------------------------------- 每 tick 服务端物理
    /** 由 BriefsEvents.tick 调用。 */
    static void tick(Player player) {
        tickCharm(player);
        tickClouds(player);
        tickDarts(player);
        tickStones(player);
        pruneCharged(player);
    }
}
