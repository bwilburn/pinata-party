package network.wilburn.pinataparty.client;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import network.wilburn.pinataparty.entity.PinataEntity;

/**
 * The bees buzzing inside a pinata: vanilla's bee loop, following the pinata around,
 * louder and a bit higher-pitched the more bees there are. Stops when the pinata
 * bursts, is taken down, or runs out of bees.
 */
public class PinataBuzzSound extends AbstractTickableSoundInstance {
    /** Bee count at which the buzz reaches full volume. */
    private static final float FULL_AT = 12.0f;

    private final PinataEntity pinata;

    public PinataBuzzSound(PinataEntity pinata) {
        super(SoundEvents.BEE_LOOP, SoundSource.NEUTRAL, RandomSource.create());
        this.pinata = pinata;
        this.looping = true;
        this.delay = 0;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.x = pinata.getX();
        this.y = pinata.getY() + pinata.getBbHeight() * 0.5;
        this.z = pinata.getZ();
        this.volume = volumeFor(pinata.getBees());
        this.pitch = pitchFor(pinata.getBees());
    }

    static float volumeFor(int bees) {
        // one bee is a faint hum; a full pinata is a full-volume swarm
        return 0.15f + 0.85f * Math.min(bees, FULL_AT) / FULL_AT;
    }

    static float pitchFor(int bees) {
        // muffled by the paper: a touch low, rising as it fills up
        return 0.75f + 0.25f * Math.min(bees, FULL_AT) / FULL_AT;
    }

    @Override
    public void tick() {
        int bees = this.pinata.getBees();
        if (this.pinata.isRemoved() || bees <= 0) {
            this.stop();
            return;
        }
        this.x = this.pinata.getX();
        this.y = this.pinata.getY() + this.pinata.getBbHeight() * 0.5;
        this.z = this.pinata.getZ();
        // ease toward the target so adding a bee swells the buzz instead of jumping
        this.volume += (volumeFor(bees) - this.volume) * 0.1f;
        this.pitch += (pitchFor(bees) - this.pitch) * 0.1f;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
