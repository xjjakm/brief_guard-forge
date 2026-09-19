package cn.blockforge.generated.briefguard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * 玄冰系列装备（第 16 轮扩展）：
 * <ul>
 *   <li>寒噬之刃（武器）：命中"已结霜"的目标触发冻裂爆发并重置霜层；无霜时只挂一层薄冰减速。</li>
 *   <li>霜璧护符（装备，副手）：充能式寒霜盾，受大额伤害时消耗一层减伤并反咬攻击者冰冻；缓慢回充。</li>
 *   <li>凝霜瓶（道具）：右键投向视线前方，生成一片持续减速并逐渐给敌人积霜的霜区。</li>
 *   <li>冷凝器（方块）：放置后默认开启，站在附近会给范围内敌人缓慢积霜，积满触发"深寒"强减速。</li>
 * </ul>
 * 霜层（frost）是这套装备共享的资源：凝霜瓶/冷凝器给敌人"积霜"，寒刃负责"冻裂"收割。
 * 全部为事件驱动、状态挂物品 NBT / 服务端列表，不依赖常驻 buff。
 */
public final class BriefsCryo {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, GeneratedMod.MOD_ID);

    // ---------------------------------------------------------------- 注册
    public static final RegistryObject<Item> FROSTBITE_BLADE =
            GeneratedMod.ITEMS.register("frostbite_blade", FrostbiteBladeItem::new);
    public static final RegistryObject<Item> FROST_BARRIER =
            GeneratedMod.ITEMS.register("frost_barrier", FrostBarrierItem::new);
    public static final RegistryObject<Item> FROST_FLASK =
            GeneratedMod.ITEMS.register("frost_flask", FrostFlaskItem::new);
    public static final RegistryObject<Block> CRYO_CONDENSER =
            BLOCKS.register("cryo_condenser", CryoCondenserBlock::new);
    public static final RegistryObject<Item> CRYO_CONDENSER_ITEM =
            GeneratedMod.ITEMS.register("cryo_condenser", () -> new BlockItem(CRYO_CONDENSER.get(), new Item.Properties()));

    private BriefsCryo() {}

    public static void init(IEventBus bus) {
        BLOCKS.register(bus);
    }

    // ---------------------------------------------------------------- 霜层共享资源
    /** 霜层在目标身上最多叠的层数，超过不再叠加，只刷新时间。 */
    private static final int FROST_CAP = 10;
    /** 霜层存在的时间窗口（秒），过期自动散掉。 */
    private static final long FROST_TTL = 200L;
    /** 冷凝器把目标冻到"深寒"强减速的层数。 */
    private static final int FROST_DEEP = 6;

    private static final Map<UUID, Frost> FROST = new HashMap<>();

    private static final class Frost {
        final int stacks;
        final long time;
        Frost(int stacks, long time) { this.stacks = stacks; this.time = time; }
    }

    private static int frostStacks(LivingEntity entity) {
        Frost f = FROST.get(entity.getUUID());
        return f == null ? 0 : f.stacks;
    }

    private static void addFrost(Level level, LivingEntity entity, int add) {
        long now = level.getGameTime();
        Frost f = FROST.get(entity.getUUID());
        int base = (f == null || now - f.time > FROST_TTL) ? 0 : f.stacks;
        FROST.put(entity.getUUID(), new Frost(Math.min(FROST_CAP, base + add), now));
    }

    private static void clearFrost(LivingEntity entity) {
        FROST.remove(entity.getUUID());
    }

    /** 由 BriefsEvents.tick 调用：定期清理过期霜层。 */
    private static void pruneFrost(Player player) {
        if (player.tickCount % 20 != 0) return;
        long cutoff = player.level().getGameTime() - FROST_TTL;
        FROST.entrySet().removeIf(e -> e.getValue().time < cutoff);
    }

    // ---------------------------------------------------------------- 寒噬之刃
    private static final Tier FROST_TIER = new Tier() {
        @Override public int getUses() { return 480; }
        @Override public float getSpeed() { return 7.0F; }
        @Override public float getAttackDamageBonus() { return 5.0F; }
        @Override public int getLevel() { return 3; }
        @Override public int getEnchantmentValue() { return 12; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.of(net.minecraft.world.item.Items.PACKED_ICE); }
    };

    private static final float SHATTER_BASE = 3.0F;
    private static final float SHATTER_PER_STACK = 1.0F;
    private static final float SHATTER_CAP = 6.5F;

    /** 寒噬之刃：命中已结霜的目标触发冻裂并清霜；无霜时只挂薄冰减速。 */
    public static final class FrostbiteBladeItem extends SwordItem {
        public FrostbiteBladeItem() {
            super(FROST_TIER, 2, -2.0F, new Item.Properties().durability(FROST_TIER.getUses()));
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.brief_guard.g_frost_blade"));
        }
    }

    /** 由 BriefsEvents.attackEntity 调用：寒刃的结霜/冻裂逻辑。 */
    static void onAttack(Player player, LivingEntity target) {
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof FrostbiteBladeItem)) return;
        Level level = player.level();
        if (level.isClientSide()) return;
        int frost = frostStacks(target);
        if (frost > 0) {
            float dmg = Math.min(SHATTER_CAP, SHATTER_BASE + frost * SHATTER_PER_STACK);
            target.hurt(level.damageSources().sweetBerryBush(), dmg);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1), player);
            clearFrost(target);
            level.addParticle(ParticleTypes.SNOWFLAKE,
                    target.getX(), target.getY() + 0.6D, target.getZ(), 0.0D, 0.12D, 0.0D);
            level.playSound(null, target.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.NEUTRAL, 0.8F, 1.4F);
        } else {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0), player);
            addFrost(level, target, 1);
            level.addParticle(ParticleTypes.ITEM_SNOWBALL,
                    target.getX(), target.getY() + 0.6D, target.getZ(), 0.0D, 0.0D, 0.0D);
        }
    }

    // ---------------------------------------------------------------- 霜璧护符
    private static final int BARRIER_MAX = 3;
    private static final int BARRIER_RECHARGE = 100; // 5 秒回充 1 层
    private static final float BARRIER_MIN_HIT = 3.0F;
    private static final float BARRIER_REDUCE = 0.45F;

    /** 霜璧护符：副手佩戴，充能式寒霜盾。受大额伤害消耗 1 层减伤，并冰冻反咬攻击者。 */
    public static final class FrostBarrierItem extends Item {
        public FrostBarrierItem() {
            super(new Item.Properties().stacksTo(1));
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.brief_guard.g_frost_barrier"));
            int charges = stack.getTag() == null ? 0 : stack.getTag().getInt("frostCharges");
            tooltip.add(Component.translatable("tooltip.brief_guard.g_frost_charges", charges));
        }
    }

    /** 由 BriefsEvents.hurt 调用：霜璧减伤 + 反咬。 */
    static void onHurt(Player player, LivingHurtEvent event) {
        ItemStack off = player.getOffhandItem();
        if (!(off.getItem() instanceof FrostBarrierItem)) return;
        float amount = event.getAmount();
        if (amount < BARRIER_MIN_HIT) return;
        CompoundTag tag = off.getOrCreateTag();
        int charges = tag.getInt("frostCharges");
        if (charges <= 0) return;
        tag.putInt("frostCharges", charges - 1);
        event.setAmount(amount * (1.0F - BARRIER_REDUCE));
        if (event.getSource().getEntity() instanceof LivingEntity attacker) {
            attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2));
            addFrost(player.level(), attacker, 2);
            player.level().addParticle(ParticleTypes.SNOWFLAKE,
                    attacker.getX(), attacker.getY() + 0.9D, attacker.getZ(), 0.0D, 0.1D, 0.0D);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9F, 1.5F);
    }

    private static void tickBarrier(Player player) {
        ItemStack off = player.getOffhandItem();
        if (!(off.getItem() instanceof FrostBarrierItem)) return;
        CompoundTag tag = off.getOrCreateTag();
        int charges = tag.getInt("frostCharges");
        if (charges >= BARRIER_MAX) return;
        int counter = tag.getInt("frostRecharge") + 1;
        if (counter >= BARRIER_RECHARGE) {
            tag.putInt("frostCharges", charges + 1);
            tag.putInt("frostRecharge", 0);
        } else {
            tag.putInt("frostRecharge", counter);
        }
    }

    // ---------------------------------------------------------------- 凝霜瓶
    private static final int FLASK_RANGE = 14;
    private static final int FLASK_RADIUS = 4;
    private static final int FLASK_DURATION = 160; // 8 秒

    private static final List<FrostField> FROST_FIELDS = new ArrayList<>();

    private static final class FrostField {
        final Level level;
        final Vec3 center;
        long expiry;
        int age;
        FrostField(Level level, Vec3 center, long expiry) {
            this.level = level;
            this.center = center;
            this.expiry = expiry;
        }
    }

    /** 凝霜瓶：右键投向视线前方，生成一处持续减速并逐渐积霜的霜区。 */
    public static final class FrostFlaskItem extends Item {
        public FrostFlaskItem() {
            super(new Item.Properties().stacksTo(4));
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
                FROST_FIELDS.add(new FrostField(level, center, level.getGameTime() + FLASK_DURATION));
                stack.shrink(1);
                level.playSound(null, player.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.7F, 1.8F);
                for (int i = 0; i < 6; i++) {
                    level.addParticle(ParticleTypes.SNOWFLAKE,
                            center.x + (level.getRandom().nextDouble() - 0.5D), center.y + (level.getRandom().nextDouble() - 0.5D) * 1.6D,
                            center.z + (level.getRandom().nextDouble() - 0.5D), 0.0D, 0.06D, 0.0D);
                }
                player.getCooldowns().addCooldown(stack.getItem(), 30);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.brief_guard.g_frost_flask"));
        }
    }

    private static void tickFrostFields(Player player) {
        long now = player.level().getGameTime();
        Iterator<FrostField> it = FROST_FIELDS.iterator();
        while (it.hasNext()) {
            FrostField f = it.next();
            if (f.level != player.level()) continue;
            if (now > f.expiry) { it.remove(); continue; }
            f.age++;
            AABB box = AABB.ofSize(f.center, FLASK_RADIUS * 2.0D, FLASK_RADIUS * 2.0D, FLASK_RADIUS * 2.0D);
            for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, box,
                    t -> t != player && t.isAlive() && t instanceof Enemy)) {
                if (f.age % 10 == 0) {
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0));
                }
                if (f.age % 25 == 0) {
                    addFrost(player.level(), e, 1);
                }
            }
            if (f.age % 15 == 0) {
                player.level().addParticle(ParticleTypes.SNOWFLAKE,
                        f.center.x + (player.level().getRandom().nextDouble() - 0.5D), f.center.y + player.level().getRandom().nextDouble() * 1.6D,
                        f.center.z + (player.level().getRandom().nextDouble() - 0.5D), 0.0D, 0.04D, 0.0D);
            }
        }
    }

    // ---------------------------------------------------------------- 冷凝器
    private static final int CONDENSER_RADIUS = 6;
    private static final int CONDENSER_SCAN = 3;

    /** 冷凝器：放置后默认开启，站在附近会让范围内敌人逐渐积霜，积满触发"深寒"强减速；右键切换开关。 */
    public static final class CryoCondenserBlock extends Block {
        public static final BooleanProperty RUNNING = BooleanProperty.create("running");

        public CryoCondenserBlock() {
            super(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.ICE)
                    .strength(2.0F, 5.0F)
                    .sound(SoundType.GLASS)
                    .lightLevel(state -> state.getValue(RUNNING) ? 6 : 0));
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
                player.level().playSound(null, pos, running ? SoundEvents.PISTON_CONTRACT : SoundEvents.PISTON_EXTEND,
                        SoundSource.BLOCKS, 1.0F, running ? 1.4F : 1.0F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        @Override
        public void appendHoverText(ItemStack stack, net.minecraft.world.level.BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.brief_guard.g_cryo_condenser"));
        }
    }

    private static void tickCondenser(Player player) {
        AABB box = player.getBoundingBox().inflate(CONDENSER_SCAN);
        BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            BlockState bs = player.level().getBlockState(p);
            if (bs.getBlock() instanceof CryoCondenserBlock && bs.getValue(CryoCondenserBlock.RUNNING)) {
                chillAround(player, Vec3.atCenterOf(p));
            }
        }
    }

    private static void chillAround(Player player, Vec3 center) {
        AABB box = AABB.ofSize(center, CONDENSER_RADIUS * 2.0D, CONDENSER_RADIUS * 2.0D, CONDENSER_RADIUS * 2.0D);
        for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, box,
                t -> t != player && t.isAlive() && t instanceof Enemy)) {
            addFrost(player.level(), e, 1);
            int s = frostStacks(e);
            if (s >= FROST_DEEP) {
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 4));
                player.level().addParticle(ParticleTypes.SNOWFLAKE,
                        e.getX(), e.getY() + 0.7D, e.getZ(), 0.0D, 0.1D, 0.0D);
            } else {
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            }
        }
    }

    // ---------------------------------------------------------------- 每 tick 服务端物理
    /** 由 BriefsEvents.tick 调用。 */
    static void tick(Player player) {
        tickBarrier(player);
        tickFrostFields(player);
        if (player.tickCount % 20 == 0) tickCondenser(player);
        pruneFrost(player);
    }

    // ---------------------------------------------------------------- 事件钩子转发
}
