package network.wilburn.pinataparty.entity;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.Level;

import network.wilburn.pinataparty.PinataParty;

/**
 * A critter in a bottle (or in a pinata) is kept as the mob's own saved data, minus where it was
 * and anything tying it to the world, so a released chicken keeps its name, age and variant.
 */
public final class CritterData {
    /** Mobs a glass bottle can catch. Data-driven: add modded mobs in a datapack. */
    public static final TagKey<EntityType<?>> BOTTLEABLE =
            TagKey.create(Registries.ENTITY_TYPE, PinataParty.id("bottleable"));
    /** Critters that vanish a while after a pinata party instead of staying around. */
    public static final TagKey<EntityType<?>> LEAVES_AFTER_PARTY =
            TagKey.create(Registries.ENTITY_TYPE, PinataParty.id("leaves_after_party"));

    /** Scoreboard tag on every critter that came out of a pinata. */
    public static final String PARTY_TAG = "pinataparty.party_critter";
    /** Persistent-data keys on party critters: when a bee's anger ends, and when a critter leaves. */
    public static final String ANGRY_UNTIL = "pinataparty.angry_until";
    public static final String LEAVE_AT = "pinataparty.leave_at";

    public static final ResourceLocation BEE = ResourceLocation.withDefaultNamespace("bee");

    /** Saved-data keys that describe the world around the mob rather than the mob itself. */
    private static final List<String> WORLD_KEYS = List.of(
            "UUID", "Pos", "Motion", "Rotation", "FallDistance", "Fire", "OnGround", "PortalCooldown",
            "Passengers", "leash", "Leash", "AngerTime", "AngryAt", "HasStung", "TicksSincePollination",
            "CannotEnterHiveTicks");

    private CritterData() {}

    /** Why a mob can't be bottled, or null if it can. */
    public static Component refusal(Entity entity) {
        if (!entity.getType().is(BOTTLEABLE) || !entity.isAlive()) {
            return Component.translatable("message.pinataparty.cant_bottle");
        }
        if (entity instanceof OwnableEntity owned && owned.getOwnerUUID() != null) {
            return Component.translatable("message.pinataparty.cant_bottle_pet");
        }
        if (entity instanceof Slime slime && slime.getSize() > 1) {
            return Component.translatable("message.pinataparty.cant_bottle_big");
        }
        if (entity.isVehicle() || entity.isPassenger()) {
            return Component.translatable("message.pinataparty.cant_bottle");
        }
        return null;
    }

    /** The mob's data for a bottle, or null if it won't save. Doesn't remove the mob. */
    public static CompoundTag capture(Entity entity) {
        if (entity instanceof Mob mob && mob.isLeashed()) {
            mob.dropLeash(true, true);
        }
        CompoundTag tag = new CompoundTag();
        if (!entity.save(tag)) {
            return null;
        }
        WORLD_KEYS.forEach(tag::remove);
        clearParty(tag);
        return tag;
    }

    /** A bare critter of a type, as a spawn egg or a beehive gives us. */
    public static CompoundTag ofType(EntityType<?> type) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", EntityType.getKey(type).toString());
        return tag;
    }

    /** A bee from a beehive's occupant data. */
    public static CompoundTag ofBeeOccupant(CompoundTag occupant) {
        CompoundTag tag = occupant.copy();
        WORLD_KEYS.forEach(tag::remove);
        tag.putString("id", BEE.toString());
        return tag;
    }

    public static ResourceLocation typeId(CompoundTag critter) {
        ResourceLocation id = ResourceLocation.tryParse(critter.getString("id"));
        return id != null ? id : BEE;
    }

    public static Optional<EntityType<?>> type(CompoundTag critter) {
        return EntityType.byString(critter.getString("id"));
    }

    public static boolean isBee(CompoundTag critter) {
        return BEE.equals(typeId(critter));
    }

    /** "Chicken", "Bee", or the modded mob's name. */
    public static Component typeName(CompoundTag critter) {
        return type(critter).map(EntityType::getDescription).orElse(Component.literal(critter.getString("id")));
    }

    /** Makes the mob from bottled data at a position. Not yet added to the level. */
    public static Optional<Entity> create(Level level, CompoundTag critter, double x, double y, double z, float yaw) {
        Optional<Entity> made = EntityType.create(critter.copy(), level);
        made.ifPresent(e -> {
            e.moveTo(x, y, z, yaw, 0.0f);
            e.removeTag(PARTY_TAG);
            e.getPersistentData().remove(ANGRY_UNTIL);
            e.getPersistentData().remove(LEAVE_AT);
        });
        return made;
    }

    /** Strips pinata-party markers from saved data, so a re-bottled party critter starts fresh. */
    private static void clearParty(CompoundTag tag) {
        if (tag.contains("Tags", 9)) {
            var tags = tag.getList("Tags", 8);
            for (int i = tags.size() - 1; i >= 0; i--) {
                if (PARTY_TAG.equals(tags.getString(i)) || "pinataparty.party_bee".equals(tags.getString(i))) {
                    tags.remove(i);
                }
            }
        }
        if (tag.contains("NeoForgeData", 10)) {
            CompoundTag nf = tag.getCompound("NeoForgeData");
            nf.remove(ANGRY_UNTIL);
            nf.remove(LEAVE_AT);
        }
    }
}
