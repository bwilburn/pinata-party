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
import network.wilburn.pinataparty.entity.PinataEntity;

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
        helper.assertTrue(p.getMainHandItem().is(PinataParty.BOTTLED_BEE.get()),
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
            helper.assertTrue(b.getTags().contains(PinataEntity.PARTY_BEE_TAG), "bee should be tagged as a party bee");
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
        bee.addTag(PinataEntity.PARTY_BEE_TAG);
        bee.getPersistentData().putLong(PinataEntity.ANGRY_UNTIL, helper.getLevel().getGameTime() + 300);
        bee.setRemainingPersistentAngerTime(300);
        bee.setPersistentAngerTarget(p.getUUID());
        bee.setTarget(p);

        float before = p.getHealth();
        bee.doHurtTarget(p);
        helper.assertTrue(p.getHealth() < before, "the sting should hurt (" + before + " -> " + p.getHealth()
                + ", difficulty " + helper.getLevel().getDifficulty() + ")");
        helper.assertFalse(bee.hasStung(), "a party bee shouldn't lose its stinger");
        helper.assertTrue(bee.isAngry(), "a party bee should still be angry after stinging");

        // the next sting lands once the player's hurt cooldown has passed
        helper.runAfterDelay(15, () -> {
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
        bee.addTag(PinataEntity.PARTY_BEE_TAG);
        bee.getPersistentData().putLong(PinataEntity.LEAVE_AT, helper.getLevel().getGameTime() + 5);
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
        helper.assertTrue(tag.getInt(PinataEntity.TAG_BEES) == 1, "the bee should still be inside");
        helper.assertTrue(tag.getList(PinataEntity.TAG_ITEMS, 10).size() == 1, "the emeralds should still be inside");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void partyBeesCantKill(GameTestHelper helper) {
        helper.getLevel().getServer().setDifficulty(net.minecraft.world.Difficulty.HARD, true);
        ServerPlayer p = player(helper);
        p.setHealth(4.0f);
        Bee bee = helper.spawn(EntityType.BEE, new Vec3(2.5, 2.0, 2.5));
        bee.addTag(PinataEntity.PARTY_BEE_TAG);
        p.hurt(p.damageSources().sting(bee), 100.0f);
        helper.assertTrue(p.isAlive() && p.getHealth() >= 1.0f, "a party bee sting shouldn't kill, health " + p.getHealth());
        helper.assertTrue(p.getHealth() < 4.0f, "but it should still hurt, health " + p.getHealth());
        helper.succeed();
    }
}
