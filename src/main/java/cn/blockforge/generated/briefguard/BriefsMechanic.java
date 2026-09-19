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

    // 十二种新内裤的积蓄上限
    private static final int POWER_MAX = 20;
    private static final int CURSE_MAX = 7;
    private static final int TEETH_MAX = 5;
    private static final int SPARK_MAX = 20;
    private static final int SHELL_MAX = 3;
    private static final int FLAW_MAX = 5;
    private static final int TEAR_MAX = 5;
    private static final int WEIGHT_MAX = 20;
    private static final int HUE_MAX = 6;
    private static final int CURRY_MAX = 8;
    private static final int PEARL_MAX = 3;
    private static final int HISS_MAX = 10;
    private static final int MIRROR_MAX = 8;

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
            case MECHANICAL -> mechanicalTick(player, worn);
            case SEVEN_CURSES -> sevenCursesTick(player, worn);
            case HI_TEETH -> hiTeethTick(player, worn);
            case FIREWORK -> fireworkTick(player, worn);
            case BRIEFS_BRIEFS -> briefsBriefsTick(player, worn);
            case POOR -> poorTick(player, worn);
            case BROKEN -> brokenTick(player, worn);
            case HEAVY -> heavyTick(player, worn);
            case RAINBOW -> rainbowTick(player, worn);
            case CURRY -> curryTick(player, worn);
            case ENDER_PEARL -> enderPearlTick(player, worn);
            case CREEPER -> creeperTick(player, worn);
            case MIRROR -> mirrorTick(player, worn);
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

    private static void mechanicalTick(Player player, ItemStack worn) {
        // 动力储能：疾跑或移动时累积"动力"，静止则缓慢泄能；满格冒火花。
        int power = tag(worn, "power");
        boolean moving = !player.onGround() || player.getDeltaMovement().horizontalDistanceSqr() > 0.001D;
        if (player.isSprinting()) {
            power = Math.min(POWER_MAX, power + 2);
        } else if (moving) {
            power = Math.min(POWER_MAX, power + 1);
        } else if (player.tickCount % 10 == 0) {
            power = Math.max(0, power - 1);
        }
        setTag(worn, "power", power);
        if (power >= POWER_MAX && player.tickCount % 8 == 0) {
            player.level().addParticle(ParticleTypes.CRIT,
                    player.getX() + (player.getRandom().nextDouble() - 0.5D), player.getY() + 0.7D,
                    player.getZ() + (player.getRandom().nextDouble() - 0.5D), 0.0D, 0.08D, 0.0D);
        }
    }

    private static void sevenCursesTick(Player player, ItemStack worn) {
        // 咒诅状态溢出紫色粒子；爆发时机在 onHurt / activeRightClick。
        if (tag(worn, "curse") > 0 && player.tickCount % 20 == 0) {
            player.level().addParticle(ParticleTypes.WITCH,
                    player.getX() + (player.getRandom().nextDouble() - 0.5D), player.getY() + 0.7D,
                    player.getZ() + (player.getRandom().nextDouble() - 0.5D), 0.0D, 0.05D, 0.0D);
        }
    }

    private static void hiTeethTick(Player player, ItemStack worn) {
        // 牙齿反咬反馈；攒满后下次攻击撕咬（见 onAttack）。
        if (tag(worn, "teeth") > 0 && player.tickCount % 16 == 0) {
            player.level().addParticle(ParticleTypes.CRIT,
                    player.getX() + (player.getRandom().nextDouble() - 0.5D), player.getY() + 0.8D,
                    player.getZ() + (player.getRandom().nextDouble() - 0.5D), 0.0D, 0.1D, 0.0D);
        }
    }

    private static void fireworkTick(Player player, ItemStack worn) {
        // 空中积蓄烟火星，落地缓慢泄能；满格喷花。
        int spark = tag(worn, "spark");
        if (!player.onGround()) {
            spark = Math.min(SPARK_MAX, spark + 1);
        } else if (player.tickCount % 8 == 0) {
            spark = Math.max(0, spark - 1);
        }
        setTag(worn, "spark", spark);
        if (spark >= SPARK_MAX && player.tickCount % 6 == 0) {
            player.level().addParticle(ParticleTypes.FIREWORK,
                    player.getX() + (player.getRandom().nextDouble() - 0.5D), player.getY() + 1.1D,
                    player.getZ() + (player.getRandom().nextDouble() - 0.5D), 0.0D, 0.15D, 0.0D);
        }
    }

    private static void briefsBriefsTick(Player player, ItemStack worn) {
        // 内衬护壳：未受击时缓慢回满；有壳时脚下冒出内衬云。
        int shell = tag(worn, "shell");
        if (shell < SHELL_MAX && player.tickCount % 40 == 0) {
            setTag(worn, "shell", shell + 1);
        }
        if (shell > 0 && player.tickCount % 20 == 0) {
            player.level().addParticle(ParticleTypes.CLOUD,
                    player.getX() + (player.getRandom().nextDouble() - 0.5D), player.getY() + 0.3D,
                    player.getZ() + (player.getRandom().nextDouble() - 0.5D), 0.0D, 0.04D, 0.0D);
        }
    }

    private static void poorTick(Player player, ItemStack worn) {
        // 廉价货开线走火的冒烟反馈；故障时机见 onAttack / onHurt。
        if (tag(worn, "flaw") > 0 && player.tickCount % 30 == 0) {
            player.level().addParticle(ParticleTypes.SMOKE,
                    player.getX() + (player.getRandom().nextDouble() - 0.5D), player.getY() + 0.4D,
                    player.getZ() + (player.getRandom().nextDouble() - 0.5D), 0.0D, 0.05D, 0.0D);
        }
    }

    private static void brokenTick(Player player, ItemStack worn) {
        // 破损裂缝冒烟；伤害泄漏与散架爆发见 onHurt。
        if (tag(worn, "tear") > 0 && player.tickCount % 12 == 0) {
            player.level().addParticle(ParticleTypes.SMOKE,
                    player.getX() + (player.getRandom().nextDouble() - 0.5D), player.getY() + 0.5D,
                    player.getZ() + (player.getRandom().nextDouble() - 0.5D), 0.0D, 0.06D, 0.0D);
        }
    }

    private static void heavyTick(Player player, ItemStack worn) {
        // 重量累积：移动增重，静止泄重；越重移动越沉。
        int weight = tag(worn, "weight");
        boolean moving = player.getDeltaMovement().horizontalDistanceSqr() > 0.001D;
        if (player.onGround() && moving) {
            weight = Math.min(WEIGHT_MAX, weight + 1);
        } else if (player.tickCount % 20 == 0) {
            weight = Math.max(0, weight - 2);
        }
        setTag(worn, "weight", weight);
        if (weight > 12 && player.onGround() && !player.isSprinting()) {
            player.setDeltaMovement(player.getDeltaMovement().x * 0.93D, player.getDeltaMovement().y, player.getDeltaMovement().z * 0.93D);
        }
        if (weight >= WEIGHT_MAX && player.tickCount % 10 == 0) {
            player.level().addParticle(ParticleTypes.ITEM_SLIME,
                    player.getX(), player.getY() + 0.5D, player.getZ(), 0.0D, -0.08D, 0.0D);
        }
    }

    private static void rainbowTick(Player player, ItemStack worn) {
        // 虹彩轮换：每 20 tick 切换一种元素；攻击附加对应效果（见 onAttack）。
        setTag(worn, "hue", (tag(worn, "hue") + 1) % HUE_MAX);
        player.level().addParticle(ParticleTypes.END_ROD,
                player.getX() + (player.getRandom().nextDouble() - 0.5D), player.getY() + 0.6D,
                player.getZ() + (player.getRandom().nextDouble() - 0.5D), 0.0D, 0.06D, 0.0D);
    }

    private static void curryTick(Player player, ItemStack worn) {
        // 咖喱力：吃到东西时大幅增加（通过食物等级上升识别），平时缓慢发酵。
        int curry = tag(worn, "curry");
        int lastFood = tag(worn, "lastFood");
        int food = player.getFoodData().getFoodLevel();
        if (lastFood != 0 && food > lastFood) {
            curry = Math.min(CURRY_MAX, curry + 2);
        }
        setTag(worn, "lastFood", food);
        if (curry < CURRY_MAX && player.tickCount % 30 == 0) {
            curry = Math.min(CURRY_MAX, curry + 1);
        }
        setTag(worn, "curry", curry);
        if (curry > 0 && player.tickCount % 30 == 0) {
            player.level().addParticle(ParticleTypes.SMOKE,
                    player.getX(), player.getY() + 0.5D, player.getZ(), 0.0D, 0.05D, 0.0D);
        }
    }

    private static void enderPearlTick(Player player, ItemStack worn) {
        // 末影珍珠充能：每 80 tick 回一格；受击闪现 / 定向跃迁消费。
        int pearl = tag(worn, "pearl");
        if (pearl < PEARL_MAX && player.tickCount % 80 == 0) {
            setTag(worn, "pearl", pearl + 1);
        }
    }

    private static void creeperTick(Player player, ItemStack worn) {
        // 嘶嘶蓄爆：附近有敌人时蓄能，否则缓慢泄能；蓄满受击/右键引发震撼。
        int hiss = tag(worn, "hiss");
        AABB box = player.getBoundingBox().inflate(4.0D);
        boolean nearEnemy = !player.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive() && e instanceof Enemy).isEmpty();
        if (nearEnemy) {
            hiss = Math.min(HISS_MAX, hiss + 1);
            if (hiss >= HISS_MAX && player.tickCount % 8 == 0) {
                player.level().addParticle(ParticleTypes.EXPLOSION,
                        player.getX() + (player.getRandom().nextDouble() - 0.5D), player.getY() + 0.7D,
                        player.getZ() + (player.getRandom().nextDouble() - 0.5D), 0.0D, 0.1D, 0.0D);
            }
        } else if (player.tickCount % 20 == 0) {
            hiss = Math.max(0, hiss - 1);
        }
        setTag(worn, "hiss", hiss);
    }

    private static void mirrorTick(Player player, ItemStack worn) {
        // 复印伤害暂存不动，只有高于一定的层数才隐约反光提示在身。
        int copy = tag(worn, "copy");
        if (copy > 0 && player.tickCount % 14 == 0) {
            player.level().addParticle(ParticleTypes.END_ROD,
                    player.getX() + (player.getRandom().nextDouble() - 0.5D), player.getY() + 0.6D,
                    player.getZ() + (player.getRandom().nextDouble() - 0.5D), 0.0D, 0.04D, 0.0D);
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
            case MECHANICAL -> {
                // 机架缓冲：满动力受击时卸力并反震攻击者。
                if (tag(worn, "power") >= POWER_MAX) {
                    setTag(worn, "power", POWER_MAX - 4);
                    event.setAmount(event.getAmount() * 0.5F);
                    if (attacker != null) attacker.knockback(1.2D, player.getX() - attacker.getX(), player.getZ() - attacker.getZ());
                    player.playSound(SoundEvents.ANVIL_LAND, 0.8F, 1.2F);
                }
            }
            case SEVEN_CURSES -> {
                int curse = tag(worn, "curse");
                setTag(worn, "curse", Math.min(CURSE_MAX, curse + 1));
                // 咒诅缠身：随机一种短负面。
                switch (player.getRandom().nextInt(3)) {
                    case 0 -> player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0));
                    case 1 -> player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
                    default -> player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 60, 0));
                }
                if (curse + 1 >= CURSE_MAX) {
                    setTag(worn, "curse", 0);
                    // 七咒爆发：重创四周并短暂强化自身。
                    explodeAround(player, 4.5D, 5.0F, 1.3D);
                    player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 80, 0, false, false));
                    player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 80, 0, false, false));
                    player.playSound(SoundEvents.WITHER_SPAWN, 0.9F, 1.2F);
                    player.level().addParticle(ParticleTypes.TOTEM_OF_UNDYING,
                            player.getX(), player.getY() + 1.0D, player.getZ(), 0.0D, 0.6D, 0.0D);
                }
            }
            case HI_TEETH -> {
                // 牙齿反咬：几率咬住攻击者，使其受伤减速。
                if (attacker != null && player.getRandom().nextFloat() < 0.45F) {
                    setTag(worn, "teeth", Math.min(TEETH_MAX, tag(worn, "teeth") + 1));
                    attacker.hurt(player.level().damageSources().sweetBerryBush(), 2.0F);
                    attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0), player);
                    player.playSound(SoundEvents.GENERIC_HURT, 0.8F, 1.4F);
                }
            }
            case BRIEFS_BRIEFS -> {
                // 内衬护壳：吸收大部分伤害，消耗一层壳。
                int shell = tag(worn, "shell");
                if (shell > 0) {
                    setTag(worn, "shell", shell - 1);
                    event.setAmount(event.getAmount() * 0.45F);
                    player.playSound(SoundEvents.ARMOR_EQUIP_LEATHER, 0.8F, 1.2F);
                }
            }
            case POOR -> {
                // 廉价故障：受击时随机"开线"，积累过载后短暂强化。
                int flaw = tag(worn, "flaw");
                if (player.getRandom().nextFloat() < 0.35F) {
                    setTag(worn, "flaw", Math.min(FLAW_MAX, flaw + 1));
                    player.playSound(SoundEvents.SHIELD_BREAK, 0.5F, 1.6F);
                    if (flaw + 1 >= FLAW_MAX) {
                        setTag(worn, "flaw", 0);
                        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 60, 0, false, false));
                        player.playSound(SoundEvents.ANVIL_LAND, 0.6F, 2.0F);
                    }
                }
            }
            case BROKEN -> {
                // 伤害泄漏：把这次伤害的一部分化为溅射击伤四周敌人。
                int tear = tag(worn, "tear");
                setTag(worn, "tear", Math.min(TEAR_MAX, tear + 1));
                AABB box = player.getBoundingBox().inflate(3.0D);
                float leak = event.getAmount() * 0.4F;
                for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class, box,
                        e -> e != player && e.isAlive() && e instanceof Enemy)) {
                    entity.hurt(player.level().damageSources().sweetBerryBush(), leak);
                }
                player.level().addParticle(ParticleTypes.SMOKE,
                        player.getX(), player.getY() + 1.0D, player.getZ(), 0.0D, 0.2D, 0.0D);
                if (tear + 1 >= TEAR_MAX) {
                    setTag(worn, "tear", 0);
                    explodeAround(player, 4.0D, 5.0F, 1.4D);
                    player.playSound(SoundEvents.SHIELD_BREAK, 0.9F, 0.8F);
                }
            }
            case HEAVY -> {
                // 沉重：蓄得越重，越能卸掉伤害。
                int weight = tag(worn, "weight");
                if (weight > 0) {
                    event.setAmount(event.getAmount() * (1.0F - Math.min(0.30F, weight * 0.015F)));
                }
            }
            case ENDER_PEARL -> {
                // 末影闪现：受击时几率瞬移躲开。
                int pearl = tag(worn, "pearl");
                if (pearl > 0 && player.getRandom().nextFloat() < 0.35F) {
                    setTag(worn, "pearl", pearl - 1);
                    blink(player);
                }
            }
            case CREEPER -> {
                // 苦力怕震撼：蓄满嘶嘶后受击引爆。
                if (tag(worn, "hiss") >= HISS_MAX) {
                    setTag(worn, "hiss", 0);
                    explodeAround(player, 4.0D, 6.0F, 1.6D);
                    player.level().addParticle(ParticleTypes.EXPLOSION_EMITTER,
                            player.getX(), player.getY() + 1.0D, player.getZ(), 0.0D, 0.0D, 0.0D);
                    player.playSound(SoundEvents.GENERIC_EXPLODE, 1.0F, 0.9F);
                }
            }
            case MIRROR -> {
                // 复印伤害：把这次实际受到的伤害"复印"进内裤，攻击时原样奉还。
                float amount = event.getAmount();
                if (amount >= 1.0F) {
                    int layers = amount >= 6.0F ? 2 : 1;
                    addTag(worn, "copy", layers, 0, MIRROR_MAX);
                    player.playSound(SoundEvents.GLASS_HIT, 0.7F, 1.5F);
                    player.level().addParticle(ParticleTypes.END_ROD,
                            player.getX(), player.getY() + 1.0D, player.getZ(), 0.0D, 0.3D, 0.0D);
                }
            }
            default -> { }
        }
    }

    // ---------------------------------------------------------------- 坠落反应
    static void onFall(Player player, LivingFallEvent event) {
        BriefsMaterialKind kind = wornKind(player);
        if (kind == null) return;
        ItemStack worn = wornStack(player);
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
        } else if (kind == BriefsMaterialKind.HEAVY && event.getDistance() > 2.0F) {
            int weight = tag(worn, "weight");
            setTag(worn, "weight", 0);
            double power = 0.2D + event.getDistance() * 0.05D + weight * 0.02D;
            explodeAround(player, 3.5D, (float) power, 1.2D);
            player.playSound(SoundEvents.ANVIL_LAND, 1.0F, 0.8F);
            player.level().addParticle(ParticleTypes.EXPLOSION,
                    player.getX(), player.getY() + 0.4D, player.getZ(), 0.0D, 0.1D, 0.0D);
        } else if (kind == BriefsMaterialKind.FIREWORK && event.getDistance() > 2.0F) {
            int spark = tag(worn, "spark");
            if (spark >= 8) {
                setTag(worn, "spark", spark - 8);
                event.setCanceled(true);
                player.setDeltaMovement(player.getDeltaMovement().x, 0.6D, player.getDeltaMovement().z);
                player.level().addParticle(ParticleTypes.FIREWORK,
                        player.getX(), player.getY() + 0.5D, player.getZ(), 0.0D, 0.0D, 0.0D);
                player.playSound(SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.9F, 1.1F);
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
            case MECHANICAL -> {
                int power = tag(worn, "power");
                if (power >= 12) {
                    setTag(worn, "power", power - 12);
                    target.knockback(1.0D, player.getX() - target.getX(), player.getZ() - target.getZ());
                    AABB box = target.getBoundingBox().inflate(3.0D);
                    for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class, box,
                            e -> e != target && e.isAlive() && e instanceof Enemy)) {
                        entity.hurt(player.level().damageSources().sweetBerryBush(), 4.0F);
                        entity.knockback(0.8D, player.getX() - entity.getX(), player.getZ() - entity.getZ());
                    }
                    player.playSound(SoundEvents.IRON_GOLEM_ATTACK, 0.9F, 1.1F);
                    player.level().addParticle(ParticleTypes.CRIT,
                            target.getX(), target.getY() + 0.6D, target.getZ(), 0.0D, 0.0D, 0.0D);
                }
            }
            case SEVEN_CURSES -> {
                if (tag(worn, "curse") >= CURSE_MAX) {
                    setTag(worn, "curse", 0);
                    explodeAround(player, 4.5D, 5.0F, 1.3D);
                    player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 80, 0, false, false));
                    player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 80, 0, false, false));
                    player.playSound(SoundEvents.WITHER_SPAWN, 0.9F, 1.2F);
                }
            }
            case HI_TEETH -> {
                if (tag(worn, "teeth") >= TEETH_MAX) {
                    setTag(worn, "teeth", 0);
                    target.hurt(player.level().damageSources().sweetBerryBush(), 6.0F);
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1), player);
                    target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0), player);
                    player.playSound(SoundEvents.GENERIC_EAT, 0.9F, 1.1F);
                }
            }
            case BRIEFS_BRIEFS -> {
                if (tag(worn, "shell") >= SHELL_MAX && player.getRandom().nextFloat() < 0.40F) {
                    setTag(worn, "shell", SHELL_MAX - 1);
                    target.knockback(1.2D, player.getX() - target.getX(), player.getZ() - target.getZ());
                    player.playSound(SoundEvents.ARMOR_EQUIP_LEATHER, 0.8F, 1.3F);
                }
            }
            case POOR -> {
                // 廉价故障：攻击时一半几率强化、一半几率反向故障。
                if (player.getRandom().nextFloat() < 0.40F) {
                    if (player.getRandom().nextBoolean()) {
                        target.hurt(player.level().damageSources().sweetBerryBush(), 3.0F);
                    } else {
                        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0), player);
                    }
                    setTag(worn, "flaw", Math.min(FLAW_MAX, tag(worn, "flaw") + 1));
                }
            }
            case BROKEN -> {
                int tear = tag(worn, "tear");
                if (tear > 0) {
                    target.hurt(player.level().damageSources().sweetBerryBush(), tear * 0.8F);
                    setTag(worn, "tear", Math.max(0, tear - 1));
                }
            }
            case HEAVY -> {
                int weight = tag(worn, "weight");
                if (weight > 0) {
                    setTag(worn, "weight", Math.max(0, weight - 4));
                    target.knockback(1.0D + weight * 0.05D, player.getX() - target.getX(), player.getZ() - target.getZ());
                    player.playSound(SoundEvents.ANVIL_LAND, 0.9F, 1.0F);
                }
            }
            case RAINBOW -> {
                switch (tag(worn, "hue")) {
                    case 0 -> target.setSecondsOnFire(3);
                    case 1 -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0), player);
                    case 2 -> {
                        target.hurt(player.level().damageSources().lightningBolt(), 2.0F);
                        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0), player);
                    }
                    case 3 -> target.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0), player);
                    case 4 -> {
                        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1), player);
                        target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 80, 0), player);
                    }
                    default -> target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0), player);
                }
            }
            case CURRY -> {
                if (tag(worn, "curry") >= 5) {
                    target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0), player);
                    target.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0), player);
                    setTag(worn, "curry", tag(worn, "curry") - 2);
                }
            }
            case ENDER_PEARL -> {
                if (player.getRandom().nextFloat() < 0.25F && tag(worn, "pearl") > 0) {
                    setTag(worn, "pearl", tag(worn, "pearl") - 1);
                    blink(player);
                    target.hurt(player.level().damageSources().sweetBerryBush(), 2.0F);
                }
            }
            case CREEPER -> {
                if (tag(worn, "hiss") >= HISS_MAX) {
                    player.playSound(SoundEvents.CREEPER_PRIMED, 0.9F, 1.0F);
                }
            }
            case MIRROR -> {
                // 原样奉还：把复印的伤害返还给当前攻击的目标，并清空。
                int copy = tag(worn, "copy");
                if (copy > 0) {
                    setTag(worn, "copy", 0);
                    target.hurt(player.level().damageSources().sweetBerryBush(), copy * 1.5F);
                    player.playSound(SoundEvents.GLASS_HIT, 0.8F, 1.0F);
                    player.level().addParticle(ParticleTypes.END_ROD,
                            target.getX(), target.getY() + 0.6D, target.getZ(), 0.0D, 0.0D, 0.0D);
                }
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
            case MECHANICAL -> {
                int power = tag(worn, "power");
                if (power >= 8) {
                    setTag(worn, "power", power - 8);
                    explodeAround(player, 4.0D, 4.0F + power * 0.2F, 1.4D);
                    player.playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.0F, 1.0F);
                    player.level().addParticle(ParticleTypes.SWEEP_ATTACK,
                            player.getX(), player.getY() + 0.6D, player.getZ(), 0.0D, 0.0D, 0.0D);
                }
            }
            case SEVEN_CURSES -> {
                if (tag(worn, "curse") >= CURSE_MAX) {
                    setTag(worn, "curse", 0);
                    explodeAround(player, 4.5D, 5.0F, 1.3D);
                    player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 80, 0, false, false));
                    player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 80, 0, false, false));
                    player.playSound(SoundEvents.WITHER_SPAWN, 0.9F, 1.2F);
                }
            }
            case FIREWORK -> {
                int spark = tag(worn, "spark");
                if (spark >= 8) {
                    setTag(worn, "spark", spark - 8);
                    Vec3 dir = player.getLookAngle();
                    player.setDeltaMovement(dir.x * 1.6D, Math.max(0.8D, dir.y * 1.6D + 0.8D), dir.z * 1.6D);
                    player.hurtMarked = true;
                    AABB box = player.getBoundingBox().inflate(3.0D);
                    for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class, box,
                            e -> e != player && e.isAlive() && e instanceof Enemy)) {
                        entity.hurt(player.level().damageSources().sweetBerryBush(), 4.0F);
                        entity.knockback(0.8D, entity.getX() - player.getX(), entity.getZ() - player.getZ());
                    }
                    player.playSound(SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.0F, 1.0F);
                    for (int i = 0; i < 10; i++) {
                        player.level().addParticle(ParticleTypes.FIREWORK,
                                player.getX(), player.getY(), player.getZ(), 0.0D, 0.1D, 0.0D);
                    }
                }
            }
            case CURRY -> {
                int curry = tag(worn, "curry");
                if (curry >= 4) {
                    setTag(worn, "curry", curry - 4);
                    explodeAround(player, 4.0D, 3.0F, 1.0D);
                    AABB box = player.getBoundingBox().inflate(4.0D);
                    for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class, box,
                            e -> e != player && e.isAlive())) {
                        entity.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0));
                        entity.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0));
                    }
                    player.playSound(SoundEvents.HONEY_DRINK, 0.9F, 0.7F);
                    player.level().addParticle(ParticleTypes.SMOKE,
                            player.getX(), player.getY() + 1.0D, player.getZ(), 0.2D, 0.1D, 0.0D);
                }
            }
            case ENDER_PEARL -> {
                if (tag(worn, "pearl") > 0) {
                    setTag(worn, "pearl", tag(worn, "pearl") - 1);
                    lookTeleport(player);
                }
            }
            case CREEPER -> {
                if (tag(worn, "hiss") >= HISS_MAX) {
                    setTag(worn, "hiss", 0);
                    explodeAround(player, 4.0D, 6.0F, 1.6D);
                    player.level().addParticle(ParticleTypes.EXPLOSION_EMITTER,
                            player.getX(), player.getY() + 1.0D, player.getZ(), 0.0D, 0.0D, 0.0D);
                    player.playSound(SoundEvents.GENERIC_EXPLODE, 1.0F, 0.9F);
                }
            }
            case MIRROR -> {
                // 镜像爆发：把复印的伤害复制给四周所有敌对生物。
                int copy = tag(worn, "copy");
                if (copy >= 6) {
                    setTag(worn, "copy", 0);
                    explodeAround(player, 4.0D, copy * 1.5F, 1.0D);
                    player.playSound(SoundEvents.GLASS_HIT, 0.9F, 0.8F);
                    player.level().addParticle(ParticleTypes.END_ROD,
                            player.getX(), player.getY() + 1.0D, player.getZ(), 0.0D, 0.5D, 0.0D);
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

    // ---------------------------------------------------------------- 通用小工具
    /** 以玩家为圆心，击伤并击退四周的敌对生物。 */
    private static void explodeAround(Player player, double radius, float damage, double knockback) {
        AABB box = player.getBoundingBox().inflate(radius);
        for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive() && e instanceof Enemy)) {
            entity.hurt(player.level().damageSources().sweetBerryBush(), damage);
            entity.knockback(knockback, entity.getX() - player.getX(), entity.getZ() - player.getZ());
        }
    }

    /** 短距离末影闪现：随机方向瞬移，并留下末影粒子。 */
    private static void blink(Player player) {
        double angle = player.getRandom().nextDouble() * Math.PI * 2.0D;
        double dist = 4.0D + player.getRandom().nextDouble() * 3.0D;
        double nx = player.getX() + Math.cos(angle) * dist;
        double nz = player.getZ() + Math.sin(angle) * dist;
        double ny = player.getY();
        player.teleportTo(nx, ny, nz);
        for (int i = 0; i < 6; i++) {
            player.level().addParticle(ParticleTypes.PORTAL,
                    player.getX() + (player.getRandom().nextDouble() - 0.5D), player.getY() + 1.0D,
                    player.getZ() + (player.getRandom().nextDouble() - 0.5D), 0.0D, 0.2D, 0.0D);
        }
    }

    /** 定向末影跃迁：沿视线方向瞬移。 */
    private static void lookTeleport(Player player) {
        Vec3 look = player.getLookAngle();
        double dist = 10.0D;
        double tx = player.getX() + look.x * dist;
        double tz = player.getZ() + look.z * dist;
        double ty = player.getY() + Math.max(0.0D, look.y * dist);
        player.teleportTo(tx, ty, tz);
        for (int i = 0; i < 8; i++) {
            player.level().addParticle(ParticleTypes.PORTAL,
                    tx + (player.getRandom().nextDouble() - 0.5D), ty + 0.5D,
                    tz + (player.getRandom().nextDouble() - 0.5D), 0.0D, 0.2D, 0.0D);
        }
    }

    // ---------------------------------------------------------------- 积蓄提示
    /** 外部（如换气扇）给穿着的内裤积蓄 +amount，并封顶在该内裤的上限。 */
    static void boostWorn(ItemStack worn, int amount) {
        if (!(worn.getItem() instanceof BriefsArmorItem item)) return;
        switch (item.kind()) {
            case DRAGON_HEAD -> addTag(worn, "fire", amount, 0, FIRE_MAX);
            case CHASTITY -> addTag(worn, "guard", amount, 0, GUARD_MAX);
            case SLIME -> addTag(worn, "spring", amount, 0, SPRING_MAX);
            case SPICY -> addTag(worn, "heat", amount, 0, HEAT_MAX);
            case EDIBLE -> addTag(worn, "full", amount, 0, FULL_MAX);
            case PROMOTION -> addTag(worn, "rank", amount, 0, RANK_MAX);
            case MECHANICAL -> addTag(worn, "power", amount, 0, POWER_MAX);
            case SEVEN_CURSES -> addTag(worn, "curse", amount, 0, CURSE_MAX);
            case HI_TEETH -> addTag(worn, "teeth", amount, 0, TEETH_MAX);
            case FIREWORK -> addTag(worn, "spark", amount, 0, SPARK_MAX);
            case BRIEFS_BRIEFS -> addTag(worn, "shell", amount, 0, SHELL_MAX);
            case POOR -> addTag(worn, "flaw", amount, 0, FLAW_MAX);
            case BROKEN -> addTag(worn, "tear", amount, 0, TEAR_MAX);
            case HEAVY -> addTag(worn, "weight", amount, 0, WEIGHT_MAX);
            case RAINBOW -> addTag(worn, "hue", amount, 0, HUE_MAX);
            case CURRY -> addTag(worn, "curry", amount, 0, CURRY_MAX);
            case ENDER_PEARL -> addTag(worn, "pearl", amount, 0, PEARL_MAX);
            case CREEPER -> addTag(worn, "hiss", amount, 0, HISS_MAX);
            case MIRROR -> addTag(worn, "copy", amount, 0, MIRROR_MAX);
            default -> { }
        }
    }

    /** 返回当前穿着的内裤对应的积蓄值 [current, max]，供 HUD 展示。 */
    static int[] gauge(ItemStack worn) {
        if (!(worn.getItem() instanceof BriefsArmorItem item)) return new int[]{0, 0};
        switch (item.kind()) {
            case DRAGON_HEAD: return new int[]{tag(worn, "fire"), FIRE_MAX};
            case CHASTITY: return new int[]{tag(worn, "guard"), GUARD_MAX};
            case SLIME: return new int[]{tag(worn, "spring"), SPRING_MAX};
            case SPICY: return new int[]{tag(worn, "heat"), HEAT_MAX};
            case EDIBLE: return new int[]{tag(worn, "full"), FULL_MAX};
            case PROMOTION: return new int[]{tag(worn, "rank"), RANK_MAX};
            case MECHANICAL: return new int[]{tag(worn, "power"), POWER_MAX};
            case SEVEN_CURSES: return new int[]{tag(worn, "curse"), CURSE_MAX};
            case HI_TEETH: return new int[]{tag(worn, "teeth"), TEETH_MAX};
            case FIREWORK: return new int[]{tag(worn, "spark"), SPARK_MAX};
            case BRIEFS_BRIEFS: return new int[]{tag(worn, "shell"), SHELL_MAX};
            case POOR: return new int[]{tag(worn, "flaw"), FLAW_MAX};
            case BROKEN: return new int[]{tag(worn, "tear"), TEAR_MAX};
            case HEAVY: return new int[]{tag(worn, "weight"), WEIGHT_MAX};
            case RAINBOW: return new int[]{tag(worn, "hue"), HUE_MAX};
            case CURRY: return new int[]{tag(worn, "curry"), CURRY_MAX};
            case ENDER_PEARL: return new int[]{tag(worn, "pearl"), PEARL_MAX};
            case CREEPER: return new int[]{tag(worn, "hiss"), HISS_MAX};
            case MIRROR: return new int[]{tag(worn, "copy"), MIRROR_MAX};
            default: return new int[]{0, 0};
        }
    }

    /** 是否有主动技能（决定 tooltip / HUD 是否提示"蹲下+空手右键"）。 */
    static boolean hasActive(BriefsMaterialKind kind) {
        return switch (kind) {
            case SPICY, EDIBLE, MECHANICAL, SEVEN_CURSES, FIREWORK, CURRY, ENDER_PEARL, CREEPER, MIRROR -> true;
            default -> false;
        };
    }
}
