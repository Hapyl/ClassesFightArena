package me.hapyl.fight.game.heroes.storage;

import me.hapyl.fight.game.GamePlayer;
import me.hapyl.fight.game.Manager;
import me.hapyl.fight.game.heroes.ClassEquipment;
import me.hapyl.fight.game.heroes.Hero;
import me.hapyl.fight.game.heroes.Role;
import me.hapyl.fight.game.talents.Talent;
import me.hapyl.fight.game.talents.Talents;
import me.hapyl.fight.game.talents.UltimateTalent;
import me.hapyl.fight.game.weapons.PackedParticle;
import me.hapyl.fight.game.weapons.RangeWeapon;
import me.hapyl.spigotutils.module.reflect.glow.Glowing;
import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.List;

public class KillingMachine extends Hero {
    private final int weaponCd = 35;

    public KillingMachine() {
        super("War Machine");

        setRole(Role.RANGE);

        this.setInfo("A machine of war that was left for scrap, until now...");
        this.setItem(
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWMyZjNkNWQ2MmZkOWJlNmQ2NTRkMzE0YzEyMzM5MGFiZmEzNjk4ZDNkODdjMTUxNmE0NTNhN2VlNGZjYmYifX19"
        );

        final ClassEquipment equipment = this.getEquipment();
        equipment.setHelmet(
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWMyZjNkNWQ2MmZkOWJlNmQ2NTRkMzE0YzEyMzM5MGFiZmEzNjk4ZDNkODdjMTUxNmE0NTNhN2VlNGZjYmYifX19"
        );
        equipment.setChestplate(Material.CHAINMAIL_CHESTPLATE);
        equipment.setLeggings(Material.CHAINMAIL_LEGGINGS);
        equipment.setBoots(Material.CHAINMAIL_BOOTS);

        this.setWeapon(new RangeWeapon(Material.IRON_HORSE_ARMOR, "km_weapon") {
            @Override
            public void onHit(LivingEntity entity) {

            }

            @Override
            public void onMove(Location location) {

            }

            @Override
            public void onShoot(Player player) {
                startCooldown(player, isUsingUltimate(player) ? (weaponCd / 2) : weaponCd);
            }

        }.setSound(Sound.BLOCK_IRON_TRAPDOOR_OPEN, 1.4f)
                               .setParticleTick(new PackedParticle(Particle.END_ROD))
                               .setParticleHit(new PackedParticle(Particle.END_ROD, 1, 0, 0, 0, 0.1f))
                               .setDamage(5.0d)
                               .setName("Rifle"));

        this.setUltimate(new UltimateTalent(
                "Overload",
                "Overload yourself for {duration}. While overloaded, your fire-rate is increased by &b100% &7and all opponents are highlighted.",
                60
        ).setDurationSec(12).setItem(Material.LIGHTNING_ROD).setSound(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.0f));

    }

    @Override
    public void useUltimate(Player player) {
        // Glow Self
        final Glowing glowing = new Glowing(player, ChatColor.RED, getUltimateDuration());
        final List<GamePlayer> alivePlayers = Manager.current().getCurrentGame().getAlivePlayers();

        alivePlayers.forEach(gamePlayer -> {
            final Player alivePlayer = gamePlayer.getPlayer();

            // Add player to see our glowing
            glowing.addViewer(alivePlayer);

            if (alivePlayer == player) {
                return;
            }

            // Highlight other players unless self
            final Glowing glowingOther = new Glowing(alivePlayer, ChatColor.AQUA, getUltimateDuration());
            glowingOther.addViewer(player);
            glowingOther.glow();
        });

        glowing.glow();
    }

    @Override
    public Talent getFirstTalent() {
        return Talents.LASER_EYE.getTalent();
    }

    @Override
    public Talent getSecondTalent() {
        return Talents.GRENADE.getTalent();
    }

    @Override
    public Talent getPassiveTalent() {
        return null;
    }
}
