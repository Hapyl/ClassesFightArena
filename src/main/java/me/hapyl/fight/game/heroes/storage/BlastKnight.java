package me.hapyl.fight.game.heroes.storage;

import me.hapyl.fight.event.DamageInput;
import me.hapyl.fight.event.DamageOutput;
import me.hapyl.fight.game.EnumDamageCause;
import me.hapyl.fight.game.GamePlayer;
import me.hapyl.fight.game.PlayerElement;
import me.hapyl.fight.game.heroes.ClassEquipment;
import me.hapyl.fight.game.heroes.Hero;
import me.hapyl.fight.game.heroes.Role;
import me.hapyl.fight.game.heroes.storage.extra.Shield;
import me.hapyl.fight.game.talents.Talent;
import me.hapyl.fight.game.talents.Talents;
import me.hapyl.fight.game.talents.UltimateTalent;
import me.hapyl.fight.game.task.GameTask;
import me.hapyl.fight.game.ui.UIComponent;
import me.hapyl.fight.game.weapons.Weapon;
import me.hapyl.fight.util.ItemStacks;
import me.hapyl.fight.util.Nulls;
import me.hapyl.fight.util.Utils;
import me.hapyl.spigotutils.module.chat.Chat;
import me.hapyl.spigotutils.module.entity.Entities;
import me.hapyl.spigotutils.module.inventory.ItemBuilder;
import me.hapyl.spigotutils.module.player.PlayerLib;
import me.hapyl.spigotutils.module.util.BukkitUtils;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.spigotmc.event.entity.EntityMountEvent;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public class BlastKnight extends Hero implements PlayerElement, UIComponent, Listener {

    private final ItemStack itemShield = new ItemBuilder(Material.SHIELD).setName("&aShield").setUnbreakable().build();
    private final Map<Player, Integer> shieldCharge = new HashMap<>();
    private final Map<Player, Horse> horseMap = new HashMap<>();

    private final Shield shield = new Shield();
    private final Material shieldRechargeCdItem = Material.HORSE_SPAWN_EGG;

    public BlastKnight() {
        super("Blast Knight");
        setRole(Role.MELEE);
        this.setInfo("Royal Knight with high-end technology gadgets.");
        this.setItem(Material.SHIELD);

        final ClassEquipment eq = this.getEquipment();
        eq.setHelmet(
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTJkZmRlNmMyYzhmMGE3YWRmN2FlNGU5NDlhODA0ZmVkZjk1YzZiOTU2Mjc2N2VhZTZjMjJhNDAxY2QwMmNiZCJ9fX0="
        );
        eq.setChestplate(Color.BLUE);
        eq.setLeggings(Material.CHAINMAIL_LEGGINGS);
        eq.setBoots(Material.IRON_BOOTS);

        this.setWeapon(new Weapon(Material.IRON_SWORD).setName("Sword").setDamage(10.0d));
        this.setUltimate(new UltimateTalent(
                "Royal Horse",
                "Call upon the Royal Horse for {duration}. The horse is fast, strong and comfortable. So comfortable in fact that it doubles you damage while riding.",
                60
        ).setCdSec(60).setDuration(1200).setItem(Material.SADDLE));

    }

    @Override
    public void useUltimate(Player player) {
        // Summon Horse
        final Horse oldHorse = horseMap.get(player);
        if (oldHorse != null) {
            oldHorse.remove();
        }

        final Horse horse = Entities.HORSE.spawn(player.getLocation(), me -> {
            Nulls.runIfNotNull(me.getAttribute(Attribute.GENERIC_MAX_HEALTH), at -> at.setBaseValue(100.0d));
            Nulls.runIfNotNull(me.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED), at -> at.setBaseValue(0.25d));
            me.setJumpStrength(1.2d);
            me.setHealth(me.getMaxHealth());

            me.setColor(Horse.Color.WHITE);
            me.setStyle(Horse.Style.WHITE);

            me.setTamed(true);
            me.getInventory().setSaddle(new ItemStack(Material.SADDLE));
            me.setAdult();

            horseMap.put(player, me);
        });

        // Controller
        new GameTask() {
            private int timeLeft = 60;

            @Override
            public void run() {
                if (timeLeft < 0 || horse.isDead()) {
                    if (timeLeft < 0) {
                        horse.getPassengers().forEach(Entity::eject);

                        // Fx
                        final Location location = horse.getEyeLocation();
                        Chat.sendMessage(player, "&aYou horse is gone!");

                        PlayerLib.spawnParticle(location, Particle.SPELL_MOB, 20, 0.5d, 0.5d, 0.5d, 0.1f);
                        PlayerLib.spawnParticle(location, Particle.EXPLOSION_NORMAL, 10, 0.5d, 0.5d, 0.5d, 0.2f);
                        PlayerLib.playSound(location, Sound.ENTITY_HORSE_GALLOP, 0.75f);
                        horse.remove();
                    }
                    this.cancel();
                    return;
                }

                --timeLeft;

                horse.setCustomName(Chat.format(
                        "&6%s's Royal Horse &8(&c&l%s &c???&8, &e&l%s&es&8)",
                        player.getName(),
                        BukkitUtils.decimalFormat(horse.getHealth()),
                        timeLeft
                ));
                horse.setCustomNameVisible(true);


            }
        }.runTaskTimer(20, 20);

    }

    @EventHandler()
    public void handleHorseInteract(EntityMountEvent ev) {
        if (ev.getMount() instanceof Horse horse && ev.getEntity() instanceof Player player) {
            if (horseMap.get(player) == horse) {
                return;
            }

            ev.setCancelled(true);
            Chat.sendMessage(player, "&cThis is not yor Horse!");
            PlayerLib.playSound(player, Sound.ENTITY_HORSE_ANGRY, 1.0f);
        }
    }

    @Override
    public DamageOutput processDamageAsDamager(DamageInput input) {
        final Player player = input.getPlayer();
        final Horse playerHorse = getPlayerHorse(player);
        if (!isUsingUltimate(player)
                || playerHorse == null
                || input.getEntity() == null
                || input.getEntity() == player) {
            return null;
        }

        if (playerHorse.getPassengers().contains(player)) {
            final LivingEntity entity = input.getEntity();
            entity.setVelocity(entity.getLocation().getDirection().normalize().multiply(-1.0d));
            return new DamageOutput(input.getDamage() * 1.5d);
        }

        return null;
    }

    @Override
    public DamageOutput processDamageAsVictim(DamageInput input) {
        final Player player = input.getPlayer();
        if (player.isBlocking()) {
            if (player.hasCooldown(Material.SHIELD)) {
                return null;
            }

            shieldCharge.compute(player, (pl, i) -> i == null ? 1 : i + 1);
            final int charge = getShieldCharge(player); // updated

            PlayerLib.playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f);
            player.setCooldown(Material.SHIELD, 10);
            shield.updateTexture(player, charge);

            if (charge >= 10) {
                explodeShield(player);
            }

            return new DamageOutput(true);
        }
        return null;
    }

    @Nullable
    public Horse getPlayerHorse(Player player) {
        return horseMap.get(player);
    }

    private void explodeShield(Player player) {
        final PlayerInventory inventory = player.getInventory();
        inventory.setItem(EquipmentSlot.OFF_HAND, ItemStacks.AIR);
        shieldCharge.put(player, 0);
        player.setCooldown(shieldRechargeCdItem, 200);

        GameTask.runLater(() -> {
            inventory.setItem(EquipmentSlot.OFF_HAND, itemShield);
            shield.updateTexture(player, 0);
        }, 200);

        // Explode
        Utils.getEntitiesInRange(player.getLocation(), 10.0d).forEach(entity -> {
            if (entity == player) {
                return;
            }

            GamePlayer.damageEntity(entity, 30.0d, player, EnumDamageCause.NOVA_EXPLOSION);
            entity.setVelocity(entity.getLocation().getDirection().multiply(-2.0d));
        });

        // Fx
        final Location location = player.getEyeLocation();
        PlayerLib.spawnParticle(location, Particle.SMOKE_NORMAL, 50, 10.0d, 0.5d, 10.0d, 0.5f);
        PlayerLib.spawnParticle(location, Particle.FIREWORKS_SPARK, 50, 10.0d, 0.5d, 10.0d, 0.5f);
        PlayerLib.spawnParticle(location, Particle.EXPLOSION_LARGE, 1, 0.0d, 0.5d, 0.0d, 0.0f);

        PlayerLib.playSound(location, Sound.ITEM_SHIELD_BREAK, 0.0f);
        PlayerLib.playSound(location, Sound.ENTITY_BLAZE_HURT, 0.0f);

    }

    public int getShieldCharge(Player player) {
        return shieldCharge.getOrDefault(player, 0);
    }

    @Override
    public void onStart(Player player) {
        player.getInventory().setItem(EquipmentSlot.OFF_HAND, itemShield);
        shield.updateTexture(player, 0);
    }

    @Override
    public void onStop() {
        shieldCharge.clear();
        horseMap.values().forEach(Entity::remove);
        horseMap.clear();
    }

    @Override
    public Talent getFirstTalent() {
        return Talents.SPEAR.getTalent();
    }

    @Override
    public Talent getSecondTalent() {
        return Talents.SLOWNESS_POTION.getTalent();
    }

    @Override
    public Talent getPassiveTalent() {
        return Talents.SHIELDED.getTalent();
    }

    @Override
    public String getString(Player player) {
        if (player.hasCooldown(shieldRechargeCdItem)) {
            return "&7???? &l" + BukkitUtils.roundTick(player.getCooldown(shieldRechargeCdItem)) + "s";
        }
        return "&f???? &l" + getShieldCharge(player);
    }
}
