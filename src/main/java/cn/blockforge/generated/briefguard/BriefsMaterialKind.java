package cn.blockforge.generated.briefguard;

public enum BriefsMaterialKind {
    LEATHER(1.0D, 0.0D, 0.0D),
    COPPER(2.0D, 0.5D, 0.0D),
    CHAIN(3.0D, 0.0D, 0.0D),
    IRON(4.0D, 0.0D, 0.0D),
    GOLD(3.0D, 0.0D, 1.0D),
    DIAMOND(5.0D, 0.5D, 0.0D),
    NETHERITE(7.0D, 1.0D, 0.0D),

    // 扩展：十四种以机制为主的内裤
    DRAGON_HEAD(6.0D, 0.5D, 0.0D),      // 龙首内裤：火焰免疫 + 岩浆漂浮 + 点燃目标
    CHASTITY(4.0D, 0.0D, 0.0D),         // 贞操带内裤：几率格挡全部伤害
    SLIME(2.0D, 0.0D, 0.0D),            // 史莱姆内裤：免疫摔伤 + 弹跳
    SPICY(2.0D, 0.0D, 0.0D),            // 辣条内裤：恒定加速 + 点燃目标
    POOP(1.0D, 0.0D, 0.0D),             // 大粪内裤：攻击者中毒
    SILVERFISH(2.0D, 0.0D, 0.0D),       // 蠹虫内裤：受击几率生成蠹虫反击
    TENTACLE(3.0D, 0.0D, 0.0D),         // 触手内裤：水下呼吸 + 游泳加速
    EDIBLE(1.0D, 0.0D, 0.0D),           // 可食用内裤：饥饿时自动进食
    TRAPDOOR(2.0D, 0.0D, 0.0D),         // 活版门内裤：潜行时隐身隐藏
    PROMOTION(3.0D, 0.0D, 2.0D),        // 晋升内裤：运气提升 + 击杀经验加成
    STICKY_PISTON(5.0D, 2.0D, 0.0D),    // 粘性活塞内裤：强力击退 + 物品吸铁石
    GASEOUS(2.0D, 0.0D, 0.0D),          // 气态内裤：跳跃提升 + 缓降
    SWORD(8.0D, 0.5D, 0.0D),            // 剑型内裤：极高攻击伤害
    SHIELD(4.0D, 1.0D, 0.0D);           // 盾牌内裤：承受伤害减免

    private final double attackDamage;
    private final double attackKnockback;
    private final double luck;

    BriefsMaterialKind(double attackDamage, double attackKnockback, double luck) {
        this.attackDamage = attackDamage;
        this.attackKnockback = attackKnockback;
        this.luck = luck;
    }

    public double attackDamage() { return attackDamage; }
    public double attackKnockback() { return attackKnockback; }
    public double luck() { return luck; }
    public boolean usesBriefsSlot() { return this != LEATHER; }
}
