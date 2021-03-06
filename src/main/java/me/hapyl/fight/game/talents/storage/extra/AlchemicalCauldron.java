package me.hapyl.fight.game.talents.storage.extra;

import me.hapyl.fight.game.GamePlayer;
import me.hapyl.fight.game.heroes.Heroes;
import me.hapyl.fight.game.heroes.storage.Alchemist;
import me.hapyl.fight.game.talents.Talents;
import me.hapyl.fight.game.task.GameTask;
import me.hapyl.fight.util.Utils;
import me.hapyl.spigotutils.module.chat.Chat;
import me.hapyl.spigotutils.module.entity.Entities;
import me.hapyl.spigotutils.module.math.Geometry;
import me.hapyl.spigotutils.module.math.gometry.Draw;
import me.hapyl.spigotutils.module.math.gometry.Quality;
import me.hapyl.spigotutils.module.player.PlayerLib;
import me.hapyl.spigotutils.module.util.BukkitUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

public class AlchemicalCauldron {

    private final Player owner;
    private final Location location;
    private final ArmorStand standBar;
    private final ArmorStand standOwner;

    private double progress;
    private Status status;

    public AlchemicalCauldron(Player owner, Location location) {
        this.owner = owner;
        this.location = location;
        this.createCauldron();
        this.progress = 0.0d;
        this.status = Status.NEUTRAL;

        this.standOwner = createStand(location.clone().add(0.5d, 1.25d, 0.5d));
        this.standBar = createStand(location.clone().add(0.5d, 1.0d, 0.5d));

        updateName();
        startTask();
    }

    private void startTask() {
        new GameTask() {
            @Override
            public void run() {
                if (GamePlayer.getPlayer(owner).isDead()) {
                    this.cancel();
                    return;
                }

                if (progress > 100) {
                    status = Status.FINISHED;
                    playSound(Sound.BLOCK_BREWING_STAND_BREW, 1.0f);
                    this.cancel();
                }

                updateName();

                if (status == Status.PAUSED || status == Status.FINISHED) {
                    return;
                }

                if (status == Status.BREWING) {
                    progress += 1.0d;

                    if (progress % 15 == 0) {
                        playSound(Sound.AMBIENT_UNDERWATER_EXIT, 1.5f);
                    }

                    // Draw particles on top
                    spawnParticle(location);

                    // Draw the zone
                    Geometry.drawCircle(location, 4.5d, Quality.HIGH, new Draw(Particle.SPELL) {
                        @Override
                        public void draw(Location location) {
                            spawnParticle(location);
                        }
                    });

                    // Damage players in zone
                    Utils.getPlayersInRange(location, 4.5d).forEach(player -> {
                        if (player == owner) {
                            Chat.sendTitle(player, "", "&cIntoxication Warning!", 0, 20, 0);
                            ((Alchemist) Heroes.ALCHEMIST.getHero()).addToxin(player, 8);
                        }
                        else {
                            PlayerLib.addEffect(player, PotionEffectType.POISON, 1, 5);
                        }
                    });
                }

            }
        }.runTaskTimer(0, 10);
    }

    private void playSound(Sound sound, float pitch) {
        PlayerLib.playSound(location, sound, pitch);
    }

    private void spawnParticle(Location location) {
        final World world = location.getWorld();
        if (world == null) {
            return;
        }

        world.spawnParticle(Particle.SPELL_MOB, location.getX() + 0.5d, location.getY(), location.getZ() + 0.5d, 0, 0.000, 0.471, 0.031, 1);
    }

    // just check for the distance at this point
    public boolean compareBlock(Block other) {
        return this.location.distance(other.getLocation()) < 2.0d;
    }

    private ArmorStand createStand(Location location) {
        return Entities.ARMOR_STAND.spawn(location, me -> {
            me.setMarker(true);
            me.setSmall(true);
            me.setInvisible(true);
            me.setCustomNameVisible(true);
        });
    }

    public void setStatus(Status status) {
        if (owner.hasCooldown(Material.STICK)) {
            return;
        }
        owner.setCooldown(Material.STICK, 10);
        this.status = status;
    }

    public Status getStatus() {
        return status;
    }

    public void updateName() {
        if (this.standBar == null || this.standOwner == null) {
            return;
        }
        this.standBar.setCustomName(Chat.format(
                status == Status.FINISHED ? status.getStatusString() : "&e%s%% %s",
                BukkitUtils.decimalFormat(progress),
                this.status.getStatusString()
        ));
        this.standOwner.setCustomName(Chat.format("&a%s's Cauldron", owner.getName()));
    }

    private void createCauldron() {
        final Block block = this.location.getBlock();
        block.setType(Material.CAULDRON, false);
    }

    public void finish() {
        PlayerLib.addEffect(owner, PotionEffectType.SPEED, 30, 2);
        PlayerLib.playSound(owner, Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 2.0f);
        Chat.sendMessage(owner, "&aYou have gained the Cauldron Buff!");
        Talents.CAULDRON.getTalent().startCd(owner);
        ((Alchemist) Heroes.ALCHEMIST.getHero()).startCauldronBoost(owner);
    }

    public void clear() {
        this.location.getBlock().setType(Material.AIR, false);
        this.standOwner.remove();
        this.standBar.remove();
    }

    public enum Status {

        NEUTRAL(""),
        PAUSED("&e&lPAUSED"),
        BREWING("&a&lBREWING..."),
        FINISHED("&6&lFINISHED, CLICK TO COLLECT");

        private final String statusString;

        Status(String statusString) {
            this.statusString = statusString;
        }

        public String getStatusString() {
            return statusString;
        }

        public boolean shouldTakeStick() {
            return this != BREWING && this != FINISHED;
        }

    }

}
