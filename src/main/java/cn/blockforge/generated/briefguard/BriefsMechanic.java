package cn.blockforge.generated.briefguard;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

/**
 * 复杂机制引擎：十四种扩展内裤的能力统一在这里实现。
 *
 * <p>设计原则：
 * <ul>
 *   <li>不使用持续性的简单增益（移速、跳跃、呼吸、隐身等 potion 常驻）；改为有起手、有反馈、
 *       需要互动的事件式机制。</li>
 *   <li>机制状态（蓄力、层数、计数）挂在穿着的内裤 ItemStack 的 NBT 上，随穿戴持久化，
 *       死亡/换存档/脱下重穿都不丢，也无需另外修玩家状态，避免崩溃。</li>
 *   <li>所有读取穿着的入口统一走 {@link #wornStack}/{@link #wornKind}，内部用
 *       {@link BriefsCapability#get} 的安全兜底，重生过渡窗口也不会抛异常。</li>
 * </ul>
 */
public final class BriefsMechanic {
    private static final int FIRE_MAX = 10;
    private static final int GUARD_MAX = 3;
    private static final int SPRING_MAX = 20;
    private static final int HEAT_MAX = 10;
    private static final int FULL_MAX = 5;
    private static final int RANK_MAX = 3;

    private BriefsMechanic() {}

    // ---------------------------------------------------------------- 状态存取
    private static int tag(ItemStack stack, String key) {
        if (stack == null || stack.isEmpty()) return 0;
        var compound = stack.getTag();
        return compound == null ? 0 : compound.getInt(key);
    }

    private static void setTag(ItemStack stack, String key, int value) {
        if (stack == null || stack.isEmpty()) return;
        stack.getOrCreateTag().putInt(key, value);
    }

    private static void addTag(ItemStack stack, String key, int delta, int min, int max) {
        setTag(stack, key, Math.max(min, Math.min(max, tag(stack, key) + delta)));
    }

    // ---------------------------------------------------------------- 穿着查询
    static ItemStack wornStack(Player player) {
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (head.getItem() instanceof BriefsArmorItem headBriefs && headBriefs.kind() == BriefsMaterialKind.LEATHER) {
            return head;
        }
        ItemStack cap = BriefsCapability.get(player).getStack();
        return cap;
    }

    static BriefsMaterialKind wornKind(Player player) {
        ItemStack stack = wornStack(player);
        return stack.getItem() instanceof BriefsArmorItem item ? item.kind() : null;
    }

    private static ItemStack wornIf(Player player, BriefsMaterialKind kind) {
        ItemStack stack = wornStack(player);
        return stack.getItem() instanceof BriefsArmorItem item && item.kind() == kind ? stack : ItemStack.EMPTY;
    }

    // ---------------------------------------------------------------- 服务端 tick
    static void tick(Player player) {
        BriefsMaterialKind kind = wornKind(player);
        if (kind == null) return;
        ItemStack worn = wornStack(player);
        switch (kind) {
            case NETHERITE -> netheriteTick(player);
            case DRAGON_HEAD -> dragonHeadTick(player, worn);
            case CHASTITY -> chastityTick(player, worn);
            case SLIME -> slimeTick(player, worn);
            case SPICY -> spicyTick(player, worn);
            case POOP -> poopTick(player, worn);
            case TENTACLE -> tentacleTick(player);
            case EDIBLE -> edibleTick(player, worn);
            case TRAPDOOR -> trapdoorTick(player);
            case PROMOTION -> promotionTick(player, worn);
            case STICKY_PISTON -> stickyPistonTick(player);
            case GASEOUS -> gaseousTick(player);
            default -> { }
        }
    }

    private static void netheriteTick(Player player) {
        // 基础款：岩浆漂浮，保留。
        if (player.isInLava()) {
            player.clearFire();
            if (player.getDeltaMovement().y < 0.05D) {
                player.setDeltaMovement(player.getDeltaMovement().x, 0.05D, player.getDeltaMovement().z);
            }
        }
    }

    private static void dragonHeadTick(Player player, ItemStack worn) {
        // 岩浆/火焰漂浮 + 火焰免疫（免疫在 BriefsEvents 的 attack/hurt 里集中处理）。
        if (player.isInLava()) {
            player.clearFire();
            if (player.getDeltaMovement().y < 0.05D) {
                player.setDeltaMovement(player.getDeltaMovement().x, 0.05D, player.getDeltaMovement().z);
            }
        }
        // 龙息蓄力：待在火/岩浆里持续充能，离开后缓慢泄能。
        boolean hot = player.isInLava() || player.isOnFire();
        if (hot) {
            addTag(worn, "fire", 1, 0, FIRE_MAX);
            if (player.tickCount % 4 == 0) {
                player.level().addParticle(ParticleTypes.FLAME,
                        player.getX() + (player.getRandom().nextDouble() - 0.5D), player.getY() + 0.3D,
                        player.getZ() + (player.getRandom().nextDouble() - 0.5D), 0.0D, 0.03D, 0.0D);
            }
        } else {
            addTag(worn, "fire", -1, 0, FIRE_MAX);
        }
    }

    private static void chastityTick(Player player, ItemStack worn) {
        // 守护层：12 秒(240 tick)未受伤则积一层，上限 3 层；受击在 onHurt 里消耗。
        int lastHit = tag(worn, "lastHit");
        if (lastHit == 0) {
            // 首次穿上：以当前 tick 作为计时起点，避免瞬间叠满。
            setTag(worn, "lastHit", player.tickCount);
            return;
        }
        int guard = tag(worn, "guard");
        if (guard < GUARD_MAX && player.tickCount - lastHit >= 240) {
            setTag(worn, "guard", guard + 1);
            player.playSound(SoundEvents.ARMOR_EQUIP_CHAIN, 0.6F, 1.3F);
        }
    }

    private static void slimeTick(Player player, ItemStack worn) {
        // 弹簧蓄力：蹲下站立蓄力越久，起身/离地弹得越高。
        int spring = tag(worn, "spring");
        if (player.isShiftKeyDown() && player.onGround()) {
            if (spring < SPRING_MAX) {
                setTag(worn, "spring", spring + 1);
                if (spring % 4 == 0) {
                    player.level().addParticle(ParticleTypes.ITEM_SLIME,
                            player.getX() + (player.getRandom().nextDouble() - 0.5D), player.getY() + 0.2D,
                            player.getZ() + (player.getRandom().nextDouble() - 0.5D), 0.0D, -0.05D, 0.0D);
                }
            }
        } else if (spring > 0 && player.onGround()) {
            setTag(worn, "spring", 0);
            player.setDeltaMovement(player.getDeltaMovement().x, 0.3D + spring * 0.05D, player.getDeltaMovement().z);
            player.playSound(SoundEvents.SLIME_JUMP_SMALL, 0.9F, 1.2F);
        }
    }

    private static void spicyTick(Player player, ItemStack worn) {
        // 辣度蓄能：待火/岩浆快速升温，平时缓慢累积；高处辣度引爆敌人+喷火，见 onAttack/activeRightClick。
        int heat = tag(worn, "heat");
        if (player.isOnFire() || player.isInLava()) {
            heat = Math.min(HEAT_MAX, heat + 2);
        } else if (player.tickCount % 10 == 0) {
            heat = Math.min(HEAT_MAX, heat + 1);
        }
        setTag(worn, "heat", heat);
        if (heat >= 7 && player.tickCount % 8 == 0) {
            player.level().addParticle(ParticleTypes.FLAME,
                    player.getX() + (player.getRandom().nextDouble() - 0.5D), player.getY() + 0.8D,
                    player.getZ() + (player.getRandom().nextDouble() - 0.5D), 0.0D, 0.04D, 0.0D);
        }
    }

    private static void poopTick(Player player, ItemStack worn) {
        // 粪臭光环：周期性地让附近生物中毒+减速。
        if (player.tickCount % 20 != 0) return;
        AABB box = player.getBoundingBox().inflate(3.5D);
        for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive() && e instanceof Enemy)) {
            entity.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0), player);
            entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0), player);
        }
        if (player.tickCount % 60 == 0) {
            player.level().addParticle(ParticleTypes.SMOKE,
                    player.getX() + (player.getRandom().nextDouble() - 0.5D), player.getY() + 0.4D,
                    player.getZ() + (player.getRandom().nextDouble() - 0.5D), 0.0D, 0.05D, 0.0D);
        }
    }

    private static void tentacleTick(Player player) {
        // 水下呼吸，其余交给 onAttack 的触手抓取。
        if (player.isInWater()) {
            player.setAirSupply(player.getMaxAirSupply());
        }
    }

    private static void edibleTick(Player player, ItemStack worn) {
        // 饥饿时自动进食，每次累计一层"饱腹"，可在需要时释放回血，见 activeRightClick。
        if (player.getFoodData().getFoodLevel() < 18 && player.tickCount % 60 == 0) {
            addTag(worn, "full", 1, 0, FULL_MAX);
            player.getFoodData().eat(4, 0.5F);
            player.playSound(SoundEvents.GENERIC_EAT, 0.8F, 1.0F);
        }
    }

    private static void trapdoorTick(Player player) {
        // 潜伏：蹲下且几乎不移动时隐身（原地藏身），攻击触发背刺见 onAttack。
        boolean hidden = player.isShiftKeyDown() && player.getDeltaMovement().horizontalDistanceSqr() < 0.01D;
        if (hidden) {
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false));
        }
    }

    private static void promotionTick(Player player, ItemStack worn) {
        // 晋升冲击：军阶攒满自动"晋升"，爆发一次强力 buff 并击退周围敌人；军阶在击杀时累积。
        if (tag(worn, "rank") >= RANK_MAX) {
            setTag(worn, "rank", 0);
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 100, 0, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 0, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 100, 0, false, false));
            AABB box = player.getBoundingBox().inflate(4.0D);
            for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class, box,
                    e -> e != player && e.isAlive() && e instanceof Enemy)) {
                entity.knockback(1.2D, player.getX() - entity.getX(), player.getZ() - entity.getZ());
            }
            player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0F, 1.0F);
            player.level().addParticle(ParticleTypes.TOTEM_OF_UNDYING,
                    player.getX(), player.getY() + 1.0D, player.getZ(), 0.0D, 0.5D, 0.0D);
        }
    }

    private static void stickyPistonTick(Player player) {
        // 磁力吸附：把附近的掉落物吸到自己身边。
        AABB box = player.getBoundingBox().inflate(4.0D);
        Vec3 center = player.position().add(0.0D, 0.5D, 0.0D);
        for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class, box)) {
            Vec3 diff = center.subtract(item.position());
            double len = diff.lengthSqr();
            if (len > 0.05D) {
                item.setDeltaMovement(diff.normalize().scale(0.18D).add(0.0D, 0.08D, 0.0D));
                item.setNoPickUpDelay();
            }
        }
    }

    private static void gaseousTick(Player player) {
        // 浮空操控：空中常态缓降（若离地），蹲下则快速下落；受击喷发气体击退，见 onHurt。
        if (!player.onGround() && player.getDeltaMovement().y < 0.0D) {
            double limit = player.isShiftKeyDown() ? -1.2D : -0.25D;
            if (player.getDeltaMovement().y < limit) {
                player.setDeltaMovement(player.getDeltaMovement().x, limit, player.getDeltaMovement().z);
            }
        }
    }

    // ---------------------------------------------------------------- 受伤反应
    static void onHurt(Player player, LivingHurtEvent event) {
        BriefsMaterialKind kind = wornKind(player);
        if (kind == null) return;
        ItemStack worn = wornStack(player);
        LivingEntity attacker = event.getSource().getEntity() instanceof LivingEntity living ? living : null;
        switch (kind) {
            case CHASTITY -> {
                int guard = tag(worn, "guard");
                if (guard > 0) {
                    setTag(worn, "guard", guard - 1);
                    setTag(worn, "lastHit", player.tickCount);
                    event.setCanceled(true);
                    player.playSound(SoundEvents.SHIELD_BLOCK, 0.8F, 1.2F);
                    player.level().addParticle(ParticleTypes.CRIT,
                            player.getX(), player.getY() + 1.0D, player.getZ(), 0.0D, 0.0D, 0.0D);
                } else {
                    setTag(worn, "lastHit", player.tickCount);
                }
            }
            case POOP -> {
                if (attacker != null) {
                    attacker.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0), player);
                    attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0), player);
                    player.level().addParticle(ParticleTypes.SMOKE, player.getX(), player.getY() + 1.0D, player.getZ(), 0.0D, 0.2D, 0.0D);
                }
            }
            case SILVERFISH -> {
                if (attacker != null && player.getRandom().nextFloat() < 0.30F) {
                    spawnSilverfishPlayer(player, attacker);
                }
            }
            case STICKY_PISTON -> {
                if (attacker != null) {
                    attacker.knockback(1.6D, player.getX() - attacker.getX(), player.getZ() - attacker.getZ());
                    player.playSound(SoundEvents.PISTON_EXTEND, 0.9F, 1.0F);
                }
            }
            case SHIELD -> {
                if (player.isShiftKeyDown()) {
                    // 举盾架势：大幅减伤，且几率招架弹反。
                    event.setAmount(event.getAmount() * 0.3F);
                    if (attacker != null && player.getRandom().nextFloat() < 0.35F) {
                        attacker.knockback(0.8D, player.getX() - attacker.getX(), player.getZ() - attacker.getZ());
                        player.playSound(SoundEvents.SHIELD_BLOCK, 0.9F, 1.2F);
                    }
                }
            }
            case GASEOUS -> {
                // 气体爆风：受击时炸开，击退四周敌人。
                AABB box = player.getBoundingBox().inflate(3.0D);
                for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class, box,
                        e -> e != player && e.isAlive() && e instanceof Enemy)) {
                    entity.knockback(1.0D, entity.getX() - player.getX(), entity.getZ() - player.getZ());
                }
                player.level().addParticle(ParticleTypes.CLOUD,
                        player.getX(), player.getY() + 0.5D, player.getZ(), 0.0D, 0.2D, 0.0D);
            }
            default -> { }
        }
    }

    // ---------------------------------------------------------------- 坠落反应
    static void onFall(Player player, LivingFallEvent event) {
        BriefsMaterialKind kind = wornKind(player);
        if (kind == null) return;
        if (kind == BriefsMaterialKind.SLIME) {
            event.setCanceled(true);
            if (event.getDistance() > 2.0F) {
                double power = Math.min(0.85D, 0.2D + event.getDistance() * 0.04D);
                player.setDeltaMovement(player.getDeltaMovement().x, power, player.getDeltaMovement().z);
            }
            if (player.tickCount % 2 == 0) {
                player.level().addParticle(ParticleTypes.ITEM_SLIME,
                        player.getX() + (player.getRandom().nextDouble() - 0.5D), player.getY() + 0.2D,
                        player.getZ() + (player.getRandom().nextDouble() - 0.5D), 0.0D, -0.05D, 0.0D);
            }
        } else if (kind == BriefsMaterialKind.POOP) {
            if (event.getDistance() > 2.0F) {
                event.setCanceled(true);
                player.setDeltaMovement(player.getDeltaMovement().x, 0.25D, player.getDeltaMovement().z);
                AABB box = player.getBoundingBox().inflate(2.0D);
                for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class, box,
                        e -> e != player && e.isAlive())) {
                    entity.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0), player);
                }
                player.level().addParticle(ParticleTypes.SMOKE,
                        player.getX(), player.getY() + 0.5D, player.getZ(), 0.0D, 0.3D, 0.0D);
            }
        }
    }

    // ---------------------------------------------------------------- 攻击反应
    static void onAttack(Player player, LivingEntity target) {
        BriefsMaterialKind kind = wornKind(player);
        if (kind == null) return;
        ItemStack worn = wornStack(player);
        switch (kind) {
            case DRAGON_HEAD -> {
                int fire = tag(worn, "fire");
                if (fire >= FIRE_MAX) {
                    setTag(worn, "fire", 0);
                    target.setSecondsOnFire(5);
                    target.hurt(player.level().damageSources().explosion(player, player), 4.0F);
                    target.knockback(1.0D, player.getX() - target.getX(), player.getZ() - target.getZ());
                    player.level().addParticle(ParticleTypes.EXPLOSION_EMITTER,
                            target.getX(), target.getY() + 0.5D, target.getZ(), 0.0D, 0.0D, 0.0D);
                    player.playSound(SoundEvents.GENERIC_EXPLODE, 0.8F, 1.0F);
                } else if (fire > 0) {
                    target.setSecondsOnFire(2);
                }
            }
            case SPICY -> {
                int heat = tag(worn, "heat");
                if (heat >= 5) {
                    target.setSecondsOnFire(3);
                    target.knockback(0.6D, player.getX() - target.getX(), player.getZ() - target.getZ());
                    setTag(worn, "heat", heat - 2);
                }
            }
            case TENTACLE -> {
                if (player.isInWater()) {
                    Vec3 pull = player.position().add(0.0D, 1.0D, 0.0D).subtract(target.position()).normalize().scale(0.7D);
                    target.setDeltaMovement(pull.x, pull.y, pull.z);
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0), player);
                    player.level().addParticle(ParticleTypes.BUBBLE,
                            target.getX(), target.getY() + 0.5D, target.getZ(), 0.0D, 0.1D, 0.0D);
                }
            }
            case TRAPDOOR -> {
                if (player.isShiftKeyDown()) {
                    target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0), player);
                    target.knockback(0.5D, player.getX() - target.getX(), player.getZ() - target.getZ());
                    player.level().addParticle(ParticleTypes.SMOKE,
                            target.getX(), target.getY() + 0.6D, target.getZ(), 0.0D, 0.1D, 0.0D);
                }
            }
            case SWORD -> {
                if (player.getRandom().nextFloat() < 0.30F) {
                    // 剑刃风暴：横扫周围多目标。
                    AABB box = target.getBoundingBox().inflate(3.0D);
                    for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class, box,
                            e -> e != target && e.isAlive() && e instanceof Enemy)) {
                        entity.hurt(player.level().damageSources().sweetBerryBush(), 3.0F);
                    }
                    player.level().addParticle(ParticleTypes.SWEEP_ATTACK,
                            target.getX(), target.getY() + 0.5D, target.getZ(), 0.0D, 0.0D, 0.0D);
                }
            }
            case SHIELD -> {
                if (player.isShiftKeyDown()) {
                    target.knockback(1.4D, player.getX() - target.getX(), player.getZ() - target.getZ());
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0), player);
                    player.playSound(SoundEvents.SHIELD_BLOCK, 0.9F, 1.1F);
                }
            }
            case STICKY_PISTON -> {
                target.knockback(1.8D, player.getX() - target.getX(), player.getZ() - target.getZ());
                player.playSound(SoundEvents.PISTON_EXTEND, 0.9F, 1.0F);
            }
            default -> { }
        }
    }

    static void onKill(Player player, ItemStack worn) {
        if (worn.getItem() instanceof BriefsArmorItem item && item.kind() == BriefsMaterialKind.PROMOTION) {
            addTag(worn, "rank", 1, 0, RANK_MAX);
            promotionTick(player, worn);
        }
    }

    // ---------------------------------------------------------------- 主动技能（空手右键）
    static void activeRightClick(Player player) {
        BriefsMaterialKind kind = wornKind(player);
        if (kind == null) return;
        ItemStack worn = wornStack(player);
        switch (kind) {
            case SPICY -> {
                int heat = tag(worn, "heat");
                if (heat >= 8) {
                    setTag(worn, "heat", heat - 6);
                    AABB box = player.getBoundingBox().inflate(5.0D);
                    for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class, box,
                            e -> e != player && e.isAlive() && e instanceof Enemy)) {
                        entity.setSecondsOnFire(3);
                        entity.knockback(0.8D, entity.getX() - player.getX(), entity.getZ() - player.getZ());
                    }
                    player.playSound(SoundEvents.FIRECHARGE_USE, 1.0F, 1.0F);
                    for (int i = 0; i < 8; i++) {
                        player.level().addParticle(ParticleTypes.FLAME,
                                player.getX() + (player.getRandom().nextDouble() - 0.5D), player.getY() + 0.8D,
                                player.getZ() + (player.getRandom().nextDouble() - 0.5D), 0.0D, 0.08D, 0.0D);
                    }
                }
            }
            case EDIBLE -> {
                int full = tag(worn, "full");
                if (full > 0) {
                    setTag(worn, "full", 0);
                    player.heal(full * 2.0F);
                    player.playSound(SoundEvents.GENERIC_EAT, 0.9F, 1.1F);
                }
            }
            default -> { }
        }
    }

    private static void spawnSilverfishPlayer(Player player, LivingEntity attacker) {
        int count = 1 + player.getRandom().nextInt(2);
        for (int i = 0; i < count; i++) {
            Silverfish silverfish = EntityType.SILVERFISH.create(player.level());
            if (silverfish == null) continue;
            silverfish.moveTo(player.getX() + (player.getRandom().nextDouble() - 0.5D), player.getY(),
                    player.getZ() + (player.getRandom().nextDouble() - 0.5D),
                    player.getRandom().nextFloat() * 360.0F, 0.0F);
            silverfish.setTarget(attacker);
            player.level().addFreshEntity(silverfish);
        }
    }
}
