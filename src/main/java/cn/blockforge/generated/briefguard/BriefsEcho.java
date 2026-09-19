package cn.blockforge.generated.briefguard;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 * 共鸣回响系列装备（第 15 轮扩展）：
 * <ul>
 *   <li>回响之刃（武器）：连续命中同一目标叠"回响层"，每次命中触发共振余震，层数越高余震越重。</li>
 *   <li>回声护符（装备，副手佩戴）：受击时把伤害存入回声池；站在原地不动，回声池缓慢化为回音治疗。</li>
 *   <li>声呐脉冲（道具）：右键朝视线方向发出声呐脉冲，揭示并计数范围内的敌对生物，消耗 1 个。</li>
 *   <li>共鸣钟（方块）：右键敲响，以钟为中心震慑并驱散附近敌人；附近其它共鸣钟会跳转连响。</li>
 * </ul>
 * 全部为事件驱动、状态挂在物品 NBT / 服务端列表上，不依赖常驻 buff。
 */
public final class BriefsEcho {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, GeneratedMod.MOD_ID);

    // ---------------------------------------------------------------- 注册
    public static final RegistryObject<Item> ECHO_BLADE =
            GeneratedMod.ITEMS.register("echo_blade", EchoBladeItem::new);
    public static final RegistryObject<Item> ECHO_AMULET =
            GeneratedMod.ITEMS.register("echo_amulet", EchoAmuletItem::new);
    public static final RegistryObject<Item> SONAR_PULSE =
            GeneratedMod.ITEMS.register("sonar_pulse", SonarPulseItem::new);
    public static final RegistryObject<Block> RESONANCE_BELL =
            BLOCKS.register("resonance_bell", ResonanceBellBlock::new);
    public static final RegistryObject<Item> RESONANCE_BELL_ITEM =
            GeneratedMod.ITEMS.register("resonance_bell", () -> new BlockItem(RESONANCE_BELL.get(), new Item.Properties()));

    private BriefsEcho() {}

    public static void init(IEventBus bus) {
        BLOCKS.register(bus);
    }

    // ---------------------------------------------------------------- 回响之刃
    private static final int ECHO_MAX = 3;
    private static final int ECHO_WINDOW = 50; // 2.5 秒内连续命中同一目标才叠层

    private static final Tier ECHO_TIER = new Tier() {
        @Override public int getUses() { return 520; }
        @Override public float getSpeed() { return 7.0F; }
        @Override public float getAttackDamageBonus() { return 5.0F; }
        @Override public int getLevel() { return 2; }
        @Override public int getEnchantmentValue() { return 14; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.of(net.minecraft.world.item.Items.AMETHYST_SHARD); }
    };

    /** 回响之刃：连续命中同一目标叠"回响层"，每次命中触发共振余震。 */
    public static final class EchoBladeItem extends SwordItem {
        public EchoBladeItem() {
            super(ECHO_TIER, 2, -2.0F, new Item.Properties().durability(ECHO_TIER.getUses()));
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.brief_guard.g_echo_blade"));
            CompoundTag tag = stack.getTag();
            int stacks = tag == null ? 0 : tag.getInt("echoStacks");
            if (stacks > 0) {
                tooltip.add(Component.translatable("tooltip.brief_guard.g_echo_stacks", stacks));
            }
        }
    }

    /**
     * 由 BriefsEvents.attackEntity 调用：回响层连击逻辑。
     * 命中同一目标且在窗口内则累加层数并结算一次"共振余震"；换目标则重置。
     */
    static void onAttack(Player player, LivingEntity target) {
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof EchoBladeItem)) return;
        CompoundTag tag = held.getOrCreateTag();
        long now = player.level().getGameTime();
        String tid = target.getStringUUID();
        String cur = tag.getString("echoTarget");
        int stacks = cur.equals(tid) && (now - tag.getLong("echoTime")) < ECHO_WINDOW
                ? Math.min(ECHO_MAX, tag.getInt("echoStacks") + 1)
                : 1;
        tag.putString("echoTarget", tid);
        tag.putInt("echoStacks", stacks);
        tag.putLong("echoTime", now);
        if (stacks >= 2) {
            // 共振余震：额外伤害 + 高层数附加击退。
            target.hurt(player.level().damageSources().sweetBerryBush(), 1.0F + stacks * 1.5F);
            if (stacks >= ECHO_MAX) {
                target.knockback(0.9D, target.getX() - player.getX(), target.getZ() - player.getZ());
            }
            player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.0F + stacks * 0.12F);
            player.level().addParticle(ParticleTypes.SONIC_BOOM,
                    target.getX(), target.getY() + 0.6D, target.getZ(), 0.0D, 0.0D, 0.0D);
        }
    }

    // ---------------------------------------------------------------- 回声护符
    private static final int ECHO_POOL_CAP = 30;
    private static final int ECHO_CONVERT = 3; // 每 3 点回声转化 1 点（半颗心）治疗

    /** 回声护符：装备在副手。受击把伤害存入回声池；站定不动时回声池化为治疗。 */
    public static final class EchoAmuletItem extends Item {
        public EchoAmuletItem() {
            super(new Item.Properties().stacksTo(1));
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.brief_guard.g_echo_amulet"));
            int pool = stack.getTag() == null ? 0 : stack.getTag().getInt("echo");
            if (pool > 0) {
                tooltip.add(Component.translatable("tooltip.brief_guard.g_echo_pool", pool));
            }
        }
    }

    private static void tickAmulet(Player player) {
        ItemStack off = player.getOffhandItem();
        if (!(off.getItem() instanceof EchoAmuletItem)) return;
        CompoundTag tag = off.getOrCreateTag();
        int pool = tag.getInt("echo");
        if (pool <= 0) return;
        boolean still = player.onGround() && player.getDeltaMovement().horizontalDistanceSqr() < 0.01D;
        if (still) {
            if (pool >= ECHO_CONVERT && player.tickCount % 20 == 0) {
                tag.putInt("echo", pool - ECHO_CONVERT);
                player.heal(1.0F);
                player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F, 1.6F);
                player.level().addParticle(ParticleTypes.HAPPY_VILLAGER,
                        player.getX(), player.getY() + 1.0D, player.getZ(), 0.0D, 0.4D, 0.0D);
            }
        } else if (player.tickCount % 10 == 0) {
            // 移动时回声池缓慢散失，避免存着不放。
            tag.putInt("echo", Math.max(0, pool - 1));
        }
    }

    static void onHurt(Player player, LivingHurtEvent event) {
        ItemStack off = player.getOffhandItem();
        if (!(off.getItem() instanceof EchoAmuletItem)) return;
        float amount = event.getAmount();
        if (amount <= 0.0F) return;
        CompoundTag tag = off.getOrCreateTag();
        int add = Math.max(1, Math.round(amount * 0.25F));
        tag.putInt("echo", Math.min(ECHO_POOL_CAP, tag.getInt("echo") + add));
        player.level().addParticle(ParticleTypes.END_ROD,
                player.getX(), player.getY() + 1.0D, player.getZ(), 0.0D, 0.1D, 0.0D);
    }

    // ---------------------------------------------------------------- 声呐脉冲
    private static final int SONAR_RANGE = 20;
    private static final int SONAR_RADIUS = 8;

    /** 声呐脉冲：右键朝视线方向发出声呐，揭示范围内的敌对生物并计数，消耗 1 个。 */
    public static final class SonarPulseItem extends Item {
        public SonarPulseItem() {
            super(new Item.Properties().stacksTo(16));
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!level.isClientSide()) {
                BlockHitResult hit = level.clip(new ClipContext(
                        player.getEyePosition(),
                        player.getEyePosition().add(player.getLookAngle().scale(SONAR_RANGE)),
                        ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
                Vec3 center = hit.getType() == BlockHitResult.Type.BLOCK
                        ? Vec3.atCenterOf(hit.getBlockPos())
                        : player.getEyePosition().add(player.getLookAngle().scale(SONAR_RANGE * 0.5D));
                int found = ping(player, center);
                player.displayClientMessage(Component.translatable("message.brief_guard.sonar_found", found), true);
                stack.shrink(1);
                player.playSound(SoundEvents.BELL_RESONATE, 0.9F, 1.2F);
                player.getCooldowns().addCooldown(stack.getItem(), 8);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.brief_guard.g_sonar"));
        }
    }

    private static int ping(Player player, Vec3 center) {
        int found = 0;
        for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(center, SONAR_RADIUS * 2.0D, SONAR_RADIUS * 2.0D, SONAR_RADIUS * 2.0D),
                e -> e != player && e.isAlive() && e instanceof Enemy)) {
            entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, 80, 0), player);
            found++;
        }
        player.level().addParticle(ParticleTypes.SONIC_BOOM, center.x, center.y, center.z, 0.0D, 0.0D, 0.0D);
        return found;
    }

    // ---------------------------------------------------------------- 共鸣钟
    private static final int BELL_COOLDOWN = 80; // 4 秒
    private static final int BELL_RADIUS = 6;
    private static final int BELL_CHAIN = 10;
    private static final Map<BlockPos, Long> LAST_RING = new HashMap<>();

    /** 共鸣钟：右键敲响，推出并震慑附近敌人；附近其它共鸣钟跳转连响。 */
    public static final class ResonanceBellBlock extends Block {
        public ResonanceBellBlock() {
            super(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 8.0F)
                    .sound(SoundType.METAL));
        }

        @Override
        public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (!level.isClientSide()) {
                prune(level, pos);
                long now = level.getGameTime();
                long last = LAST_RING.getOrDefault(pos, Long.MIN_VALUE);
                if (now - last < BELL_COOLDOWN) {
                    player.displayClientMessage(Component.translatable("message.brief_guard.bell_rest"), true);
                    return InteractionResult.sidedSuccess(true);
                }
                LAST_RING.put(pos, now);
                ring(level, pos, new HashSet<>());
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        @Override
        public void appendHoverText(ItemStack stack, net.minecraft.world.level.BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.brief_guard.g_bell"));
        }
    }

    /** 以某个钟为起点连响：敲击该钟并跳转到附近其它共鸣钟。 */
    private static void ring(Level level, BlockPos pos, Set<BlockPos> visited) {
        if (!visited.add(pos)) return;
        emitPulse(level, pos);
        level.playSound(null, pos, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 1.0F, 1.0F);
        long now = level.getGameTime();
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-BELL_CHAIN, -BELL_CHAIN, -BELL_CHAIN), pos.offset(BELL_CHAIN, BELL_CHAIN, BELL_CHAIN))) {
            if (visited.contains(p)) continue;
            if (!(level.getBlockState(p).getBlock() instanceof ResonanceBellBlock)) continue;
            if (now - LAST_RING.getOrDefault(p, Long.MIN_VALUE) >= BELL_COOLDOWN) {
                LAST_RING.put(p, now);
                ring(level, p, visited);
            }
        }
    }

    /** 以钟为圆心，推出/减速并震慑范围内的敌对生物。 */
    private static void emitPulse(Level level, BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);
        AABB box = AABB.ofSize(center, BELL_RADIUS * 2.0D, BELL_RADIUS * 2.0D, BELL_RADIUS * 2.0D);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && e instanceof Enemy)) {
            Vec3 diff = entity.position().subtract(center);
            double d = diff.length();
            if (d < BELL_RADIUS && d > 0.01D) {
                Vec3 dir = diff.scale(1.0D / d);
                entity.knockback(0.35D + 0.35D * (1.0D - d / BELL_RADIUS), dir.x, dir.z);
                entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0));
            }
        }
        level.addParticle(ParticleTypes.SONIC_BOOM, center.x, center.y + 0.4D, center.z, 0.0D, 0.0D, 0.0D);
    }

    /** 偶尔清理较旧的冷却记录，避免长时间积累。 */
    private static void prune(Level level, BlockPos current) {
        if (LAST_RING.size() < 512 && !LAST_RING.containsKey(current)) return;
        long cutoff = level.getGameTime() - 6000L;
        LAST_RING.values().removeIf(t -> t < cutoff);
    }

    // ---------------------------------------------------------------- 每 tick 服务端物理
    /** 由 BriefsEvents.tick 调用：回声护符的回音治疗。 */
    static void tick(Player player) {
        tickAmulet(player);
        // 共鸣钟无需每 tick 处理：敲击时用 LAST_RING 冷却即可。
    }
}
