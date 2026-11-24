package network.wilburn.pinataparty;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server config. NeoForge syncs SERVER configs to every client that joins, so the
 * blindfold opacity is the server's choice, not something a player can turn down.
 * The file lives in the world folder: world/serverconfig/pinataparty-server.toml
 */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public enum BeeTargets { EVERYONE, BREAKER }

    static {
        BUILDER.push("bees");
    }

    public static final ModConfigSpec.BooleanValue ANGRY_BEES = BUILDER
            .comment("Bees that burst out of a pinata are angry.")
            .define("angryBees", true);

    public static final ModConfigSpec.EnumValue<BeeTargets> BEE_TARGETS = BUILDER
            .comment("EVERYONE: each bee picks a random survival player within 10 blocks.",
                    "BREAKER: every bee goes after whoever broke the pinata.")
            .defineEnum("beeTargets", BeeTargets.EVERYONE);

    public static final ModConfigSpec.IntValue BEE_ANGER_SECONDS = BUILDER
            .comment("How long released bees stay angry.")
            .defineInRange("beeAngerSeconds", 15, 1, 120);

    public static final ModConfigSpec.BooleanValue PARTY_BEES_CAN_KILL = BUILDER
            .comment("If false, a sting from a pinata bee never takes a player below half a heart.")
            .define("partyBeesCanKill", false);

    public static final ModConfigSpec.IntValue MAX_BEES = BUILDER
            .comment("Most bees one pinata can hold.")
            .defineInRange("maxBees", 12, 0, 64);

    static {
        BUILDER.pop();
        BUILDER.push("pinata");
    }

    public static final ModConfigSpec.IntValue MAX_STACKS = BUILDER
            .comment("Most item stacks one pinata can hold.")
            .defineInRange("maxStacks", 27, 1, 54);

    public static final ModConfigSpec.IntValue MIN_TOUGHNESS = BUILDER
            .comment("A new pinata gets a random toughness between these two numbers.",
                    "A fist does 1, the pinata bat about 5 (a full-strength crit about 7).")
            .defineInRange("minToughness", 15, 1, 500);

    public static final ModConfigSpec.IntValue MAX_TOUGHNESS = BUILDER
            .defineInRange("maxToughness", 30, 1, 500);

    static {
        BUILDER.pop();
        BUILDER.push("blindfold");
    }

    public static final ModConfigSpec.DoubleValue BLINDFOLD_OPACITY = BUILDER
            .comment("1.0 is pitch black. 0.95 lets bright things show through faintly.")
            .defineInRange("opacity", 0.96, 0.0, 1.0);

    public static final ModConfigSpec.BooleanValue BLINDFOLD_SPIN = BUILDER
            .comment("Putting the blindfold on spins you to face a random direction.")
            .define("spinOnPutOn", true);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {}
}
