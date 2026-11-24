package network.wilburn.pinataparty;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Rockets that were inside a pinata, launched a few at a time after it bursts. They're tagged,
 * and GameEvents cancels any damage their explosions would do.
 */
public final class PartyFireworks {
    public static final String ROCKET_TAG = "pinataparty.party_rocket";

    private record Pending(ServerLevel level, Vec3 at, ItemStack rocket, long due) {}

    private static final List<Pending> PENDING = new ArrayList<>();
    /** Total launched since the server started; the game tests read it. */
    public static int launched;

    private PartyFireworks() {}

    public static void queue(ServerLevel level, Vec3 at, ItemStack rocket, int delayTicks) {
        PENDING.add(new Pending(level, at, rocket, level.getGameTime() + delayTicks));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) {
            return;
        }
        Iterator<Pending> it = PENDING.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            if (p.level().getServer() != event.getServer()) {
                it.remove(); // left over from a world that has since closed
                continue;
            }
            if (p.level().getGameTime() < p.due()) {
                continue;
            }
            it.remove();
            FireworkRocketEntity rocket = new FireworkRocketEntity(p.level(), p.at().x, p.at().y, p.at().z, p.rocket());
            var random = p.level().getRandom();
            // a gentle fan: rockets speed up sideways as they climb, so keep the nudge small
            rocket.setDeltaMovement(random.nextGaussian() * 0.012, 0.05, random.nextGaussian() * 0.012);
            rocket.addTag(ROCKET_TAG);
            p.level().addFreshEntity(rocket);
            launched++;
        }
    }
}
