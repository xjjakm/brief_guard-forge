package cn.blockforge.generated.briefguard;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 松紧/晾晒系列配套装备（第 14 轮扩展）：
 * <ul>
 *   <li>伸缩腰带剑（武器）：把受到的/打出的战意存成"张力"，右键甩腰带鞭横扫群敌。</li>
 *   <li>弹力钩索（工具/装备）：右键瞄准方块勾住，持续把玩家拉过去；再右键脱钩。</li>
 *   <li>滑腻粉（道具）：右键在脚下扬出一片滑腻粉区，自己踩上沿地面滑行，
 *       快速移动进入的敌对生物会被绊倒。</li>
 *   <li>换气扇（方块）：放置后开启，站在附近会为穿着的内裤积蓄充能，并把敌人吹开。</li>
 * </ul>
 * 全部为事件驱动、状态挂在物品 NBT / 服务端列表上，不依赖常驻 buff。
 */
public final class BriefsGear {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, GeneratedMod.MOD_ID);

    // ---------------------------------------------------------------- 注册
    public static final RegistryObject<Item> ELASTIC_BELT_SWORD =
            GeneratedMod.ITEMS.register("elastic_belt_sword", ElasticBeltSwordItem::new);
    public static final RegistryObject<Item> ELASTIC_GRAPPLE =
            GeneratedMod.ITEMS.register("elastic_grapple", ElasticGrappleItem::new);
    public static final RegistryObject<Item> TALC_POWDER =
            GeneratedMod.ITEMS.register("talc_powder", TalcPowderItem::new);
    public static final RegistryObject<Block> FRESH_AIR_FAN =
            BLOCKS.register("fresh_air_fan", FreshAirFanBlock::new);
    public static final RegistryObject<Item> FRESH_AIR_FAN_ITEM =
            GeneratedMod.ITEMS.register("fresh_air_fan", () -> new BlockItem(FRESH_AIR_FAN.get(), new Item.Properties()));

    private BriefsGear() {}

    public static void init(IEventBus bus) {
        BLOCKS.register(bus);
    }

    // ---------------------------------------------------------------- 伸缩腰带剑
    private static final Tier BELT_TIER = new Tier() {
        @Override public int getUses() { return 520; }
        @Override public float getSpeed() { return 7.0F; }
        @Override public float getAttackDamageBonus() { return 5.0F; }
        @Override public int getLevel() { return 2; }
        @Override public int getEnchantmentValue() { return 12; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.of(net.minecraft.world.item.Items.LEATHER); }
    };

    /** 伸缩腰带剑：攻击/受击攒"张力"，满格右键甩鞭横扫。 */
    public static final class ElasticBeltSwordItem extends SwordItem {
        public static final int TENSION_MAX = 10;

        public ElasticBeltSwordItem() {
            super(BELT_TIER, 2, -2.0F, new Item.Properties().durability(BELT_TIER.getUses()));
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            CompoundTag tag = stack.getTag();
            int tension = tag == null ? 0 : tag.getInt("beltTension");
            if (tension < 4) {
                return InteractionResultHolder.pass(stack);
            }
            boolean full = tension >= TENSION_MAX;
            if (!level.isClientSide()) {
                stack.getOrCreateTag().putInt("beltTension", full ? 0 : tension - 4);
                if (full) {
                    lash(player);
                } else {
                    forwardBurst(player);
                }
                player.getCooldowns().addCooldown(stack.getItem(), full ? 20 : 10);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.brief_guard.g_sword"));
        }
    }

    /** 甩鞭横扫：对视线前方扇形内的敌人造成伤害+击退，并把玩家向前带一小段。 */
    private static void lash(Player player) {
        Vec3 look = player.getLookAngle();
        AABB box = player.getBoundingBox().inflate(4.5D);
        for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, box, t -> t != player && t.isAlive())) {
            Vec3 to = e.position().subtract(player.position());
            double len = to.length();
            if (len < 0.01D) continue;
            Vec3 dir = to.scale(1.0D / len);
            if (look.dot(dir) > 0.35D) {
                e.hurt(player.level().damageSources().sweetBerryBush(), 5.0F);
                e.knockback(1.2D, dir.x, dir.z);
            }
        }
        player.setDeltaMovement(look.x * 0.5D, 0.05D, look.z * 0.5D);
        player.hurtMarked = true;
        player.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 1.0F);
        for (int i = 0; i < 5; i++) {
            player.level().addParticle(ParticleTypes.SWEEP_ATTACK,
                    player.getX() + (player.getRandom().nextDouble() - 0.5D), player.getY() + 0.7D,
                    player.getZ() + (player.getRandom().nextDouble() - 0.5D), look.x * 0.2D, 0.0D, look.z * 0.2D);
        }
    }

    /** 小前冲：张力未满时的轻击，前冲并撞开最前面的一个敌人。 */
    private static void forwardBurst(Player player) {
        Vec3 look = player.getLookAngle();
        player.setDeltaMovement(look.x * 0.7D, 0.0D, look.z * 0.7D);
        player.hurtMarked = true;
        LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(2.2D), t -> t != player && t.isAlive())) {
            Vec3 to = e.position().subtract(player.position());
            double len = to.length();
            if (len < 0.01D) continue;
            if (look.dot(to.scale(1.0D / len)) > 0.5D && len < best) {
                best = len;
                nearest = e;
            }
        }
        if (nearest != null) {
            nearest.hurt(player.level().damageSources().sweetBerryBush(), 2.0F);
            nearest.knockback(0.8D, nearest.getX() - player.getX(), nearest.getZ() - player.getZ());
        }
        player.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.8F, 1.2F);
    }

    // ---------------------------------------------------------------- 弹力钩索
    private static final double GRAPPLE_RANGE = 28.0D;

    /** 弹力钩索：右键勾住视线前方的方块并持续拉近，再右键松开。 */
    public static final class ElasticGrappleItem extends Item {
        public ElasticGrappleItem() {
            super(new Item.Properties().stacksTo(1));
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            CompoundTag tag = stack.getTag();
            boolean attached = tag != null && tag.getInt("attached") == 1;
            if (!level.isClientSide()) {
                if (attached) {
                    stack.getOrCreateTag().putInt("attached", 0);
                    player.playSound(SoundEvents.PISTON_CONTRACT, 0.8F, 1.0F);
                } else {
                    attach(player, stack);
                }
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.brief_guard.g_grapple"));
        }
    }

    private static void attach(Player player, ItemStack stack) {
        BlockHitResult hit = player.level().clip(new ClipContext(
                player.getEyePosition(),
                player.getEyePosition().add(player.getLookAngle().scale(GRAPPLE_RANGE)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() == BlockHitResult.Type.BLOCK) {
            BlockPos pos = hit.getBlockPos();
            CompoundTag tag = stack.getOrCreateTag();
            tag.putInt("attached", 1);
            tag.putInt("gx", pos.getX());
            tag.putInt("gy", pos.getY());
            tag.putInt("gz", pos.getZ());
            player.playSound(SoundEvents.PISTON_EXTEND, 0.9F, 1.0F);
            for (int i = 0; i < 4; i++) {
                player.level().addParticle(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0D, player.getZ(),
                        0.0D, 0.1D, 0.0D);
            }
        } else {
            player.displayClientMessage(Component.translatable("message.brief_guard.grapple_miss"), true);
            player.playSound(SoundEvents.PISTON_CONTRACT, 0.7F, 1.4F);
        }
    }

    // ---------------------------------------------------------------- 滑腻粉
    private static final int SLIP_RADIUS = 2;
    private static final int SLIP_DURATION = 240; // 12 秒
    private static final int SLIP_CAP = 40;
    private static final List<SlipZone> SLIP_ZONES = new ArrayList<>();

    /** 滑腻粉：右键在脚下扬粉，生成一片会绊人、让人打滑的滑腻区。 */
    public static final class TalcPowderItem extends Item {
        public TalcPowderItem() {
            super(new Item.Properties().stacksTo(16));
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!level.isClientSide()) {
                if (SLIP_ZONES.size() < SLIP_CAP) {
                    SLIP_ZONES.add(new SlipZone(level, player.position().add(0.0D, 0.0D, 0.0D), level.getGameTime() + SLIP_DURATION));
                }
                stack.shrink(1);
                player.playSound(SoundEvents.SAND_PLACE, 0.9F, 1.1F);
                for (int i = 0; i < 8; i++) {
                    player.level().addParticle(ParticleTypes.CLOUD,
                            player.getX() + (player.getRandom().nextDouble() - 0.5D), player.getY() + 0.2D,
                            player.getZ() + (player.getRandom().nextDouble() - 0.5D), 0.0D, 0.04D, 0.0D);
                }
                player.getCooldowns().addCooldown(stack.getItem(), 10);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.brief_guard.g_talc"));
        }
    }

    private static final class SlipZone {
        final Level level;
        final Vec3 center;
        long expiry;
        SlipZone(Level level, Vec3 center, long expiry) {
            this.level = level;
            this.center = center;
            this.expiry = expiry;
        }
    }

    // ---------------------------------------------------------------- 换气扇
    /** 换气扇：放置后默认开启，站在附近给穿着的内裤充能并把敌人吹开；右键切换开关。 */
    public static final class FreshAirFanBlock extends Block {
        public static final BooleanProperty RUNNING = BooleanProperty.create("running");

        public FreshAirFanBlock() {
            super(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.5F, 6.0F)
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
            tooltip.add(Component.translatable("tooltip.brief_guard.g_fan"));
        }
    }

    // ---------------------------------------------------------------- 每 tick 服务端处理
    /** 由 BriefsEvents.tick 调用：钩索拉动、滑腻粉区、换气扇充能。 */
    static void tick(Player player) {
        tickGrapple(player);
        tickSlip(player);
        tickFan(player);
    }

    private static void tickGrapple(Player player) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof ElasticGrappleItem)) return;
        CompoundTag tag = stack.getTag();
        if (tag == null || tag.getInt("attached") != 1) return;
        BlockPos anchor = new BlockPos(tag.getInt("gx"), tag.getInt("gy"), tag.getInt("gz"));
        Vec3 target = Vec3.atCenterOf(anchor);
        if (player.level().getBlockState(anchor).isAir() || player.distanceToSqr(target) < 1.69D) {
            stack.getOrCreateTag().putInt("attached", 0);
            player.playSound(SoundEvents.PISTON_CONTRACT, 0.7F, 1.3F);
            return;
        }
        Vec3 pull = target.subtract(player.position());
        double dist = pull.length();
        if (dist < 0.01D) return;
        Vec3 dir = pull.scale(1.0D / dist);
        double speed = Math.min(1.1D, 0.3D + dist * 0.05D);
        player.setDeltaMovement(dir.x * speed, Math.max(0.05D, dir.y * speed), dir.z * speed);
        player.hurtMarked = true;
        if (player.tickCount % 4 == 0) {
            player.level().addParticle(ParticleTypes.PORTAL,
                    player.getX() + (player.getRandom().nextDouble() - 0.3D), player.getY() + 1.0D,
                    player.getZ() + (player.getRandom().nextDouble() - 0.3D), 0.0D, 0.0D, 0.0D);
        }
    }

    private static void tickSlip(Player player) {
        long now = player.level().getGameTime();
        double radiusSqr = (double) SLIP_RADIUS * SLIP_RADIUS;
        Iterator<SlipZone> it = SLIP_ZONES.iterator();
        while (it.hasNext()) {
            SlipZone z = it.next();
            if (z.level != player.level()) continue;
            if (now > z.expiry) { it.remove(); continue; }
            boolean inside = player.distanceToSqr(z.center) < radiusSqr;
            if (inside) {
                Vec3 v = player.getDeltaMovement();
                double h = Math.sqrt(v.x * v.x + v.z * v.z);
                if (player.onGround() && h > 0.04D) {
                    // 打滑：保留并略微放大水平动量
                    player.setDeltaMovement(v.x * 1.10D, v.y, v.z * 1.10D);
                }
            }
            // 绊倒快速冲入的敌对生物
            for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(SLIP_RADIUS), t -> t != player && t.isAlive() && t instanceof Enemy)) {
                if (e.distanceToSqr(z.center) < radiusSqr && e.onGround()) {
                    Vec3 ev = e.getDeltaMovement();
                    double eh = Math.sqrt(ev.x * ev.x + ev.z * ev.z);
                    if (eh > 0.45D) {
                        e.hurt(player.level().damageSources().sweetBerryBush(), 2.0F);
                        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), player);
                        e.setDeltaMovement(ev.x * 0.15D, ev.y, ev.z * 0.15D);
                        if (player.tickCount % 5 == 0) {
                            player.level().addParticle(ParticleTypes.CLOUD, e.getX(), e.getY() + 0.4D, e.getZ(), 0.0D, 0.05D, 0.0D);
                        }
                    }
                }
            }
        }
    }

    private static void tickFan(Player player) {
        if (player.tickCount % 10 != 0) return;
        AABB box = player.getBoundingBox().inflate(3.0D);
        BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
        boolean boosted = false;
        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            BlockState bs = player.level().getBlockState(p);
            if (bs.getBlock() instanceof FreshAirFanBlock && bs.getValue(FreshAirFanBlock.RUNNING)) {
                boosted = true;
                pushEnemiesFromFan(player, p);
            }
        }
        if (boosted) {
            BriefsMechanic.boostWorn(BriefsMechanic.wornStack(player), 2);
            if (player.tickCount % 30 == 0) {
                player.level().addParticle(ParticleTypes.END_ROD, player.getX(), player.getY() + 0.6D, player.getZ(), 0.0D, 0.04D, 0.0D);
            }
        }
    }

    private static void pushEnemiesFromFan(Player player, BlockPos fanPos) {
        Vec3 center = Vec3.atCenterOf(fanPos);
        for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(4.0D), t -> t != player && t.isAlive() && t instanceof Enemy)) {
            Vec3 diff = e.position().subtract(center);
            double d = diff.length();
            if (d < 4.0D && d > 0.01D) {
                Vec3 dir = diff.scale(1.0D / d);
                double power = 0.30D * (1.0D - d / 4.0D) + 0.06D;
                e.setDeltaMovement(e.getDeltaMovement().add(dir.scale(power).add(0.0D, 0.08D, 0.0D)));
            }
        }
    }

    // ---------------------------------------------------------------- 攻击/受击钩子
    /** 由 BriefsEvents.attackEntity 调用：拿着伸缩腰带剑攻击时攒张力。 */
    static void onAttack(Player player) {
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof ElasticBeltSwordItem)) return;
        CompoundTag tag = held.getOrCreateTag();
        int t = Math.min(ElasticBeltSwordItem.TENSION_MAX, tag.getInt("beltTension") + 3);
        tag.putInt("beltTension", t);
        if (t >= ElasticBeltSwordItem.TENSION_MAX) {
            player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8F, 1.4F);
            player.level().addParticle(ParticleTypes.CRIT, player.getX(), player.getY() + 1.0D, player.getZ(), 0.0D, 0.2D, 0.0D);
        }
    }

    /** 由 BriefsEvents.hurt 调用：拿着伸缩腰带剑受击时也攒张力。 */
    static void onHurt(Player player) {
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof ElasticBeltSwordItem)) return;
        CompoundTag tag = held.getOrCreateTag();
        int t = Math.min(ElasticBeltSwordItem.TENSION_MAX, tag.getInt("beltTension") + 2);
        tag.putInt("beltTension", t);
    }
}
