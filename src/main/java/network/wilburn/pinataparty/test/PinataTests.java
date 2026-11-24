package network.wilburn.pinataparty.test;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import network.wilburn.pinataparty.PinataParty;
import network.wilburn.pinataparty.entity.CritterData;
import network.wilburn.pinataparty.entity.PinataEntity;
import network.wilburn.pinataparty.item.BottledCritterItem;
import network.wilburn.pinataparty.PartyFireworks;

/**
 * Server-side checks, run by `./gradlew runGameTestServer` (CI does this on every push).
 * Only registered when game tests are switched on, so they never run on the real server.
 */
@GameTestHolder(PinataParty.MODID)
@PrefixGameTestTemplate(false)
public class PinataTests {

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer p = helper.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
        p.teleportTo(at.x, at.y, at.z);
        p.getInventory().clearContent();
        return p;
    }

    private static PinataEntity pinata(GameTestHelper helper) {
        return helper.spawn(PinataParty.PINATA_ENTITY.get(), new Vec3(3.5, 2.0, 3.5));
    }

    @GameTest(template = "arena")
    public static void hangsUnderCeiling(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        // from mid-air in the arena (where the other tests spawn things), go up to the ceiling
        BlockPos ceiling = BlockPos.containing(helper.absoluteVec(new Vec3(3.5, 2.0, 3.5)));
        while (helper.getLevel().getBlockState(ceiling).isAir()) {
            ceiling = ceiling.above();
        }
        ItemStack stack = new ItemStack(PinataParty.PINATA.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(ceiling).add(0, -0.5, 0), Direction.DOWN, ceiling, false);
        net.minecraft.world.InteractionResult result = stack.useOn(new UseOnContext(p, InteractionHand.MAIN_HAND, hit));

        List<PinataEntity> found = helper.getLevel().getEntitiesOfClass(PinataEntity.class,
                new net.minecraft.world.phys.AABB(ceiling).inflate(1.0, 3.0, 1.0));
        List<PinataEntity> anywhere = helper.getLevel().getEntitiesOfClass(PinataEntity.class,
                new net.minecraft.world.phys.AABB(ceiling).inflate(16.0));
        helper.assertTrue(found.size() == 1, "expected one pinata under the ceiling, found " + found.size()
                + " (result " + result + ", ceiling rel " + helper.relativePos(ceiling)
                + " " + helper.getLevel().getBlockState(ceiling) + ", stack " + stack
                + ", nearby " + anywhere.stream().map(e -> helper.relativeVec(e.position()).toString()).toList() + ")");
        PinataEntity pinata = found.get(0);
        double top = pinata.getY() + pinata.getBbHeight();
        helper.assertTrue(top < ceiling.getY() && top > ceiling.getY() - 0.5,
                "pinata top should sit just under the ceiling, was " + (ceiling.getY() - top) + " below");
        helper.assertTrue(stack.isEmpty(), "placing should use up the pinata item");
        helper.assertTrue(pinata.isHanging(), "a pinata placed under a block should be hanging");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void standsOnFloor(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        // from mid-air, go down to the floor
        BlockPos floor = BlockPos.containing(helper.absoluteVec(new Vec3(3.5, 2.0, 3.5)));
        while (helper.getLevel().getBlockState(floor).isAir()) {
            floor = floor.below();
        }
        ItemStack stack = new ItemStack(PinataParty.PINATA.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
        stack.useOn(new UseOnContext(p, InteractionHand.MAIN_HAND, hit));
        List<PinataEntity> found = helper.getLevel().getEntitiesOfClass(PinataEntity.class,
                new net.minecraft.world.phys.AABB(floor).inflate(1.0, 3.0, 1.0));
        helper.assertTrue(found.size() == 1, "expected one pinata on the floor, found " + found.size());
        PinataEntity pinata = found.get(0);
        helper.assertFalse(pinata.isHanging(), "a pinata placed on a floor should be standing");
        helper.assertTrue(Math.abs(pinata.getY() - (floor.getY() + 1)) < 0.01, "it should stand on the floor");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void everyWayToAddBees(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        PinataEntity pinata = pinata(helper);

        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PinataParty.BOTTLED_BEE.get(), 3));
        pinata.interact(p, InteractionHand.MAIN_HAND);
        helper.assertTrue(pinata.getBees() == 1, "bottled bee: expected 1 bee, got " + pinata.getBees());
        helper.assertTrue(p.getMainHandItem().getCount() == 2, "should use one bottled bee from the stack");
        helper.assertTrue(p.getInventory().countItem(Items.GLASS_BOTTLE) == 1, "the empty bottle goes to the inventory");

        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BEE_SPAWN_EGG));
        pinata.interact(p, InteractionHand.MAIN_HAND);
        helper.assertTrue(pinata.getBees() == 2, "spawn egg: expected 2 bees, got " + pinata.getBees());

        ItemStack hive = new ItemStack(Items.BEEHIVE);
        hive.set(DataComponents.BEES, List.of(
                new net.minecraft.world.level.block.entity.BeehiveBlockEntity.Occupant(CustomData.of(new CompoundTag()), 0, 600),
                new net.minecraft.world.level.block.entity.BeehiveBlockEntity.Occupant(CustomData.of(new CompoundTag()), 0, 600)));
        p.setItemInHand(InteractionHand.MAIN_HAND, hive);
        pinata.interact(p, InteractionHand.MAIN_HAND);
        helper.assertTrue(pinata.getBees() == 4, "beehive: expected 4 bees, got " + pinata.getBees());
        helper.assertTrue(p.getMainHandItem().is(Items.BEEHIVE), "the hive stays in hand");
        helper.assertTrue(p.getMainHandItem().getOrDefault(DataComponents.BEES, List.of()).isEmpty(), "the hive is now empty");

        // an empty beehive is just an item
        pinata.interact(p, InteractionHand.MAIN_HAND);
        helper.assertTrue(pinata.getBees() == 4, "an empty hive adds no bees");

        // a pinata that is ticking with bees inside doesn't fall over
        helper.runAfterDelay(40, () -> {
            helper.assertFalse(pinata.isRemoved(), "the pinata should still be there");
            helper.succeed();
        });
    }

    @GameTest(template = "arena")
    public static void bottleABee(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        Bee bee = helper.spawn(EntityType.BEE, new Vec3(2.5, 2.0, 2.5));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));
        p.interactOn(bee, InteractionHand.MAIN_HAND);
        helper.assertTrue(p.getMainHandItem().is(PinataParty.BOTTLED_CRITTER.get())
                && "minecraft:bee".equals(BottledCritterItem.critter(p.getMainHandItem()).getString("id")),
                "hand should hold a bottled bee, holds " + p.getMainHandItem());
        helper.assertTrue(bee.isRemoved(), "the bee should be in the bottle");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void fillAndBurst(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        PinataEntity pinata = pinata(helper);

        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND, 5));
        pinata.interact(p, InteractionHand.MAIN_HAND);
        helper.assertTrue(p.getMainHandItem().isEmpty(), "the diamonds should be inside");

        for (int i = 0; i < 3; i++) {
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PinataParty.BOTTLED_BEE.get()));
            pinata.interact(p, InteractionHand.MAIN_HAND);
            helper.assertTrue(p.getMainHandItem().is(Items.GLASS_BOTTLE), "should get the bottle back");
        }

        pinata.hurt(p.damageSources().playerAttack(p), 1000.0f);
        helper.assertTrue(pinata.isRemoved(), "the pinata should have burst");

        List<ItemEntity> items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, pinata.getBoundingBox().inflate(4.0));
        int diamonds = items.stream().filter(e -> e.getItem().is(Items.DIAMOND)).mapToInt(e -> e.getItem().getCount()).sum();
        helper.assertTrue(diamonds == 5, "expected 5 diamonds to fall out, got " + diamonds);

        List<Bee> bees = helper.getLevel().getEntitiesOfClass(Bee.class, pinata.getBoundingBox().inflate(4.0));
        helper.assertTrue(bees.size() == 3, "expected 3 bees, got " + bees.size());
        for (Bee b : bees) {
            helper.assertTrue(b.getTags().contains(CritterData.PARTY_TAG), "bee should be tagged as a party bee");
            helper.assertTrue(p.getUUID().equals(b.getPersistentAngerTarget()), "bee should be angry at the only player");
            helper.assertTrue(b.getRemainingPersistentAngerTime() > 0, "bee should still be angry");
        }
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void emptyPinataDropsNothing(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        PinataEntity pinata = pinata(helper);
        pinata.hurt(p.damageSources().playerAttack(p), 1000.0f);
        helper.assertTrue(pinata.isRemoved(), "the pinata should have burst");
        List<ItemEntity> items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, pinata.getBoundingBox().inflate(4.0));
        List<Bee> bees = helper.getLevel().getEntitiesOfClass(Bee.class, pinata.getBoundingBox().inflate(4.0));
        helper.assertTrue(items.isEmpty() && bees.isEmpty(), "an empty pinata should drop nothing, got "
                + items.size() + " items and " + bees.size() + " bees");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void partyBeesKeepStinging(GameTestHelper helper) {
        // mob damage to players is zero on Peaceful
        helper.getLevel().getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
        ServerPlayer p = player(helper);
        Bee bee = helper.spawn(EntityType.BEE, new Vec3(1.5, 1.0, 2.0));
        bee.addTag(CritterData.PARTY_TAG);
        bee.getPersistentData().putLong(CritterData.ANGRY_UNTIL, helper.getLevel().getGameTime() + 300);
        bee.setRemainingPersistentAngerTime(300);
        bee.setPersistentAngerTarget(p.getUUID());
        bee.setTarget(p);

        // a player who has just joined can't be hurt for 3 seconds (60 ticks)
        helper.runAfterDelay(70, () -> {
            bee.setRemainingPersistentAngerTime(300);
            bee.setTarget(p);
            float before = p.getHealth();
            bee.doHurtTarget(p);
            helper.assertTrue(p.getHealth() < before, "the sting should hurt (" + before + " -> " + p.getHealth()
                    + ", difficulty " + helper.getLevel().getDifficulty() + ")");
            helper.assertFalse(bee.hasStung(), "a party bee shouldn't lose its stinger");
            helper.assertTrue(bee.isAngry(), "a party bee should still be angry after stinging");
        });
        // the next sting lands once the player's hurt cooldown has passed
        helper.runAfterDelay(90, () -> {
            float mid = p.getHealth();
            bee.doHurtTarget(p);
            helper.assertTrue(p.getHealth() < mid, "a second sting should hurt too");
            helper.assertFalse(bee.hasStung(), "still no lost stinger");
            helper.succeed();
        });
    }

    @GameTest(template = "arena")
    public static void partyBeesLeave(GameTestHelper helper) {
        Bee bee = helper.spawn(EntityType.BEE, new Vec3(2.5, 2.0, 2.5));
        bee.addTag(CritterData.PARTY_TAG);
        bee.getPersistentData().putLong(CritterData.LEAVE_AT, helper.getLevel().getGameTime() + 5);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(bee.isRemoved(), "the party bee should have flown off");
            helper.succeed();
        });
    }

    @GameTest(template = "arena")
    public static void arrowsAndFistsBehave(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        PinataEntity pinata = pinata(helper);
        // non-player damage is ignored
        helper.assertFalse(pinata.hurt(helper.getLevel().damageSources().explosion(null, null), 1000.0f),
                "explosions shouldn't hurt it");
        // one punch doesn't break a fresh pinata
        pinata.hurt(p.damageSources().playerAttack(p), 1.0f);
        helper.assertFalse(pinata.isRemoved(), "one punch shouldn't break it");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void takeDownKeepsFilling(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        PinataEntity pinata = pinata(helper);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.EMERALD, 7));
        pinata.interact(p, InteractionHand.MAIN_HAND);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PinataParty.BOTTLED_BEE.get()));
        pinata.interact(p, InteractionHand.MAIN_HAND);

        p.getInventory().clearContent();
        p.setShiftKeyDown(true);
        pinata.interact(p, InteractionHand.MAIN_HAND);
        p.setShiftKeyDown(false);
        helper.assertTrue(pinata.isRemoved(), "taking it down should remove the entity");

        int slot = p.getInventory().findSlotMatchingItem(new ItemStack(PinataParty.PINATA.get()));
        ItemStack item = slot >= 0 ? p.getInventory().getItem(slot) : ItemStack.EMPTY;
        if (item.isEmpty()) {
            // findSlotMatchingItem compares components, so look by item instead
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                if (p.getInventory().getItem(i).is(PinataParty.PINATA.get())) {
                    item = p.getInventory().getItem(i);
                }
            }
        }
        helper.assertTrue(!item.isEmpty(), "the pinata item should be in the inventory");
        CompoundTag tag = item.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        helper.assertTrue(tag.getList(PinataEntity.TAG_CRITTERS, 10).size() == 1, "the bee should still be inside");
        helper.assertTrue(tag.getList(PinataEntity.TAG_ITEMS, 10).size() == 1, "the emeralds should still be inside");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void partyBeesCantKill(GameTestHelper helper) {
        helper.getLevel().getServer().setDifficulty(net.minecraft.world.Difficulty.HARD, true);
        ServerPlayer p = player(helper);
        Bee bee = helper.spawn(EntityType.BEE, new Vec3(2.5, 2.0, 2.5));
        bee.addTag(CritterData.PARTY_TAG);
        // wait out the just-joined player's 3 seconds of damage immunity
        helper.runAfterDelay(70, () -> {
            p.setHealth(4.0f);
            p.hurt(p.damageSources().sting(bee), 100.0f);
            helper.assertTrue(p.isAlive() && p.getHealth() >= 1.0f, "a party bee sting shouldn't kill, health " + p.getHealth());
            helper.assertTrue(p.getHealth() < 4.0f, "but it should still hurt, health " + p.getHealth());
            helper.succeed();
        });
    }

    // --- critters --------------------------------------------------------------

    private static ItemStack bottle(GameTestHelper helper, ServerPlayer p, net.minecraft.world.entity.Entity mob) {
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));
        p.interactOn(mob, InteractionHand.MAIN_HAND);
        return p.getMainHandItem();
    }

    @GameTest(template = "arena")
    public static void bottleEveryCritter(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        for (var type : List.of(EntityType.CHICKEN, EntityType.RABBIT, EntityType.BAT, EntityType.FROG,
                EntityType.SILVERFISH)) {
            var mob = helper.spawn(type, new Vec3(2.5, 2.0, 2.5));
            ItemStack bottled = bottle(helper, p, mob);
            helper.assertTrue(bottled.is(PinataParty.BOTTLED_CRITTER.get()), type + ": expected a bottled critter, got " + bottled);
            helper.assertTrue(EntityType.getKey(type).toString().equals(BottledCritterItem.critter(bottled).getString("id")),
                    type + ": wrong critter in the bottle");
            helper.assertTrue(mob.isRemoved(), type + ": the mob should be in the bottle");
        }
        // only the smallest slimes fit
        var big = helper.spawn(EntityType.SLIME, new Vec3(2.5, 2.0, 2.5));
        big.setSize(2, true);
        helper.assertTrue(bottle(helper, p, big).is(Items.GLASS_BOTTLE), "a big slime shouldn't fit");
        var small = helper.spawn(EntityType.SLIME, new Vec3(2.5, 2.0, 2.5));
        small.setSize(1, true);
        helper.assertTrue(bottle(helper, p, small).is(PinataParty.BOTTLED_CRITTER.get()), "a small slime should fit");
        // and not everything: a cow stays a cow
        var cow = helper.spawn(EntityType.COW, new Vec3(2.5, 2.0, 2.5));
        helper.assertTrue(bottle(helper, p, cow).is(Items.GLASS_BOTTLE) && !cow.isRemoved(), "cows can't be bottled");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void bottledCritterKeepsNameAndAge(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        var chick = helper.spawn(EntityType.CHICKEN, new Vec3(2.5, 2.0, 2.5));
        chick.setBaby(true);
        chick.setCustomName(net.minecraft.network.chat.Component.literal("Nugget"));
        ItemStack bottled = bottle(helper, p, chick);
        helper.assertTrue(bottled.getHoverName().getString().contains("Chicken"), "name should say Chicken: " + bottled.getHoverName().getString());

        BlockPos floor = BlockPos.containing(helper.absoluteVec(new Vec3(3.5, 2.0, 3.5)));
        while (helper.getLevel().getBlockState(floor).isAir()) {
            floor = floor.below();
        }
        bottled.useOn(new UseOnContext(p, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false)));
        helper.assertTrue(p.getMainHandItem().is(Items.GLASS_BOTTLE), "letting it go gives the bottle back");
        var out = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.animal.Chicken.class,
                new net.minecraft.world.phys.AABB(floor).inflate(2.0));
        helper.assertTrue(out.size() == 1, "expected the chicken back, found " + out.size());
        helper.assertTrue(out.get(0).isBaby(), "still a baby");
        helper.assertTrue(out.get(0).hasCustomName() && "Nugget".equals(out.get(0).getCustomName().getString()), "still called Nugget");
        helper.assertFalse(out.get(0).getTags().contains(CritterData.PARTY_TAG), "let out by hand, not a party critter");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void mixedCrittersBurstOut(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        PinataEntity pinata = pinata(helper);
        for (var type : List.of(EntityType.BEE, EntityType.CHICKEN, EntityType.SILVERFISH, EntityType.BAT)) {
            p.setItemInHand(InteractionHand.MAIN_HAND, BottledCritterItem.of(CritterData.ofType(type)));
            pinata.interact(p, InteractionHand.MAIN_HAND);
        }
        helper.assertTrue(pinata.getCritterCount() == 4 && pinata.getBees() == 1,
                "expected 4 critters, 1 bee; got " + pinata.getCritterCount() + ", " + pinata.getBees());
        pinata.hurt(p.damageSources().playerAttack(p), 1000.0f);

        var box = pinata.getBoundingBox().inflate(4.0);
        var chickens = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.animal.Chicken.class, box);
        var fish = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Silverfish.class, box);
        var bats = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.ambient.Bat.class, box);
        var bees = helper.getLevel().getEntitiesOfClass(Bee.class, box);
        helper.assertTrue(chickens.size() == 1 && fish.size() == 1 && bats.size() == 1 && bees.size() == 1,
                "one of each should come out");
        helper.assertTrue(chickens.get(0).getTags().contains(CritterData.PARTY_TAG), "tagged as a party critter");
        helper.assertTrue(chickens.get(0).getPersistentData().getLong(CritterData.LEAVE_AT) == 0, "chickens stay");
        helper.assertTrue(fish.get(0).getPersistentData().getLong(CritterData.LEAVE_AT) > 0, "silverfish leave");
        helper.assertTrue(bats.get(0).getPersistentData().getLong(CritterData.LEAVE_AT) > 0, "bats leave");
        helper.assertTrue(bees.get(0).isAngry(), "the bee is angry");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void oldBeeCountStillLoads(GameTestHelper helper) {
        PinataEntity pinata = pinata(helper);
        CompoundTag old = new CompoundTag();
        old.putInt(PinataEntity.TAG_BEES_OLD, 3);
        pinata.loadFilling(old, helper.getLevel().registryAccess());
        helper.assertTrue(pinata.getCritterCount() == 3 && pinata.getBees() == 3, "a 1.0.x pinata with 3 bees should still have 3");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void crittersHaveALimit(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        PinataEntity pinata = pinata(helper);
        int max = network.wilburn.pinataparty.Config.MAX_CRITTERS.get();
        for (int i = 0; i < max + 2; i++) {
            p.setItemInHand(InteractionHand.MAIN_HAND, BottledCritterItem.of(CritterData.ofType(EntityType.CHICKEN)));
            pinata.interact(p, InteractionHand.MAIN_HAND);
        }
        helper.assertTrue(pinata.getCritterCount() == max, "should stop at " + max + ", has " + pinata.getCritterCount());
        helper.assertTrue(p.getMainHandItem().is(PinataParty.BOTTLED_CRITTER.get()), "the extra chicken stays in its bottle");
        helper.succeed();
    }

    // --- fireworks ---------------------------------------------------------------

    @GameTest(template = "arena", timeoutTicks = 200)
    public static void rocketsLaunch(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        PinataEntity pinata = pinata(helper);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FIREWORK_ROCKET, 20));
        pinata.interact(p, InteractionHand.MAIN_HAND);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND));
        pinata.interact(p, InteractionHand.MAIN_HAND);
        int before = PartyFireworks.launched;
        var box = pinata.getBoundingBox().inflate(4.0);
        pinata.hurt(p.damageSources().playerAttack(p), 1000.0f);
        helper.runAfterDelay(30, () -> {
            int launched = PartyFireworks.launched - before;
            int max = network.wilburn.pinataparty.Config.MAX_FIREWORKS.get();
            helper.assertTrue(launched == max, "expected " + max + " rockets launched, got " + launched);
            var items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, box);
            int spare = items.stream().filter(e -> e.getItem().is(Items.FIREWORK_ROCKET)).mapToInt(e -> e.getItem().getCount()).sum();
            helper.assertTrue(spare == 20 - max, "the rest should drop as items: " + spare);
            helper.assertTrue(items.stream().anyMatch(e -> e.getItem().is(Items.DIAMOND)), "other items still drop");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 200)
    public static void partyRocketsAreHarmless(GameTestHelper helper) {
        helper.getLevel().getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
        ServerPlayer p = player(helper);
        // wait out the just-joined player's 3 seconds of damage immunity
        helper.runAfterDelay(70, () -> {
            var party = new net.minecraft.world.entity.projectile.FireworkRocketEntity(helper.getLevel(),
                    p.getX(), p.getY(), p.getZ(), new ItemStack(Items.FIREWORK_ROCKET));
            party.addTag(PartyFireworks.ROCKET_TAG);
            float before = p.getHealth();
            p.hurt(p.damageSources().fireworks(party, null), 5.0f);
            helper.assertTrue(p.getHealth() == before, "a party rocket shouldn't hurt: " + before + " -> " + p.getHealth());
            var plain = new net.minecraft.world.entity.projectile.FireworkRocketEntity(helper.getLevel(),
                    p.getX(), p.getY(), p.getZ(), new ItemStack(Items.FIREWORK_ROCKET));
            p.hurt(p.damageSources().fireworks(plain, null), 5.0f);
            helper.assertTrue(p.getHealth() < before, "an ordinary rocket still hurts (so the test means something)");
            helper.succeed();
        });
    }
}
