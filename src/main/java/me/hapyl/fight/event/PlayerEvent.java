package me.hapyl.fight.event;

import me.hapyl.fight.Main;
import me.hapyl.fight.Shortcuts;
import me.hapyl.fight.annotate.Entry;
import me.hapyl.fight.game.*;
import me.hapyl.fight.game.effect.GameEffectType;
import me.hapyl.fight.game.heroes.Hero;
import me.hapyl.fight.game.heroes.Heroes;
import me.hapyl.fight.game.talents.ChargedTalent;
import me.hapyl.fight.game.talents.Talent;
import me.hapyl.fight.game.talents.UltimateTalent;
import me.hapyl.fight.game.ui.DamageIndicator;
import me.hapyl.spigotutils.module.chat.Chat;
import me.hapyl.spigotutils.module.player.PlayerLib;
import me.hapyl.spigotutils.module.util.BukkitUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class PlayerEvent implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void handlePlayerJoin(PlayerJoinEvent ev) {
        final Player player = ev.getPlayer();
        final Main plugin = Main.getPlugin();

        plugin.handlePlayer(player);
        plugin.getTutorial().display(player);

        ev.setJoinMessage(Chat.format(
                "&7[&a+&7] %s%s &ewants to fight!",
                player.isOp() ? "&c" : "",
                player.getName()
        ));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void handlePlayerQuit(PlayerQuitEvent ev) {
        final Player player = ev.getPlayer();

        if (Manager.current().isGameInProgress()) {
            final AbstractGameInstance game = Manager.current().getCurrentGame();
            final GamePlayer gamePlayer = GamePlayer.getAlivePlayer(player);

            if (gamePlayer == null) {
                return;
            }

            Chat.broadcast("&c%s left while fighting and was removed from the game!", player.getName());
            gamePlayer.setSpectator(true);
            game.checkWinCondition();
        }

        // save database
        Shortcuts.getDatabase(player).saveToFile();

        ev.setQuitMessage(Chat.format(
                "&7[&c-&7] %s%s &ehas fallen!",
                player.isOp() ? "&c" : "",
                player.getName()
        ));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void handleItemDropEntity(EntityDropItemEvent ev) {
        ev.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void handleItemDropPlayer(PlayerDropItemEvent ev) {
        ev.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void handleFoodLevel(FoodLevelChangeEvent ev) {
        // Auto-Generated
        ev.setCancelled(true);
        ev.setFoodLevel(20);
    }

    @EventHandler()
    public void handleBlockPlace(BlockPlaceEvent ev) {
        if (ev.getPlayer().getGameMode() != GameMode.CREATIVE) {
            ev.setCancelled(true);
            ev.setBuild(false);
        }
    }

    @EventHandler()
    public void handleBlockBreak(BlockBreakEvent ev) {
        if (ev.getPlayer().getGameMode() != GameMode.CREATIVE) {
            ev.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void handleEntityRegainHealthEvent(EntityRegainHealthEvent ev) {
        ev.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void handleProjectileLand(ProjectileHitEvent ev) {
        final Projectile entity = ev.getEntity();
        if (entity.getType() == EntityType.ARROW) {
            entity.remove();
        }
    }

    private GamePlayer getAlivePlayer(Player player) {
        final Manager manager = Manager.current();
        if (manager.isTrialExistsAndIsOwner(player)) {
            return manager.getTrial().getGamePlayer();
        }

        return GamePlayer.getAlivePlayer(player);
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST)
    public void handlePlayerSwapEvent(PlayerSwapHandItemsEvent ev) {
        ev.setCancelled(true);
        final Player player = ev.getPlayer();
        final Hero hero = Manager.current().getCurrentHero(player);

        if (!Manager.current().isAbleToUse(player)) {
            return;
        }

        final GamePlayer gamePlayer = getAlivePlayer(player);
        if (gamePlayer == null) {
            return;
        }

        if (gamePlayer.isUltimateReady()) {
            final UltimateTalent ultimate = hero.getUltimate();

            if (ultimate.hasCd(player)) {
                sendUltimateFailureMessage(player, "&cUltimate on cooldown for %ss.", BukkitUtils.roundTick(ultimate.getCdTimeLeft(player)));
                return;
            }

            if (!hero.predicateUltimate(player)) {
                sendUltimateFailureMessage(player, "&cUnable to use ultimate! " + hero.predicateMessage());
                return;
            }

            //ultimate.execute0(player);
            hero.useUltimate(player);
            ultimate.startCd(player);
            gamePlayer.setUltPoints(0);

            if (hero.getUltimateDuration() > 0) {
                hero.setUsingUltimate(player, true, hero.getUltimateDuration());
            }

            for (final Player online : Bukkit.getOnlinePlayers()) {
                Chat.sendMessage(
                        online,
                        "&b&l??? &b%s used &l%s&7!".formatted(online == player ? "You" : player.getName(), ultimate.getName())
                );
                PlayerLib.playSound(online, ultimate.getSound(), ultimate.getPitch());
            }
        }
        else if (!hero.isUsingUltimate(player)) {
            Chat.sendTitle(player, "&4&l???", "&cYour ultimate isn't ready!", 5, 15, 5);
            sendUltimateFailureMessage(player, "&cYour ultimate isn't ready!");
        }
        // ignore if using ultimate
    }

    private void sendUltimateSuccessMessage(Player player, String str, Object... objects) {
        Chat.sendMessage(player, "&b&l??? &a" + Chat.format(str, objects));
    }

    private void sendUltimateFailureMessage(Player player, String str, Object... objects) {
        Chat.sendMessage(player, "&4&l??? &c" + Chat.format(str, objects));
        PlayerLib.playSound(player, Sound.ENTITY_ENDERMAN_TELEPORT, 0.0f);
    }

    @Entry(
            name = "Damage calculation."
    )
    @EventHandler(priority = EventPriority.HIGHEST)
    public void handleDamage(EntityDamageEvent ev) {
        final Entity entity = ev.getEntity();
        LivingEntity damagerFinal = null;
        double damage = ev.getDamage();

        // let void damage be ignored
        final EntityDamageEvent.DamageCause cause = ev.getCause();
        if (!(entity instanceof LivingEntity livingEntity) || cause == EntityDamageEvent.DamageCause.VOID) {
            return;
        }

        if (entity instanceof Player player && !Manager.current().isAbleToUse(player)) {
            ev.setCancelled(true);
            return;
        }

        /** Pre event tests */
        if (livingEntity instanceof Player player) {
            final AbstractGamePlayer gamePlayer = GamePlayer.getPlayer(player);

            // fall damage
            if (cause == EntityDamageEvent.DamageCause.FALL && gamePlayer.hasEffect(GameEffectType.FALL_DAMAGE_RESISTANCE)) {
                gamePlayer.removeEffect(GameEffectType.FALL_DAMAGE_RESISTANCE);
                ev.setCancelled(true);
                return;
            }

        }

        // Calculate base damage
        if (ev instanceof EntityDamageByEntityEvent event) {
            final Entity damager = event.getDamager();

            // ignore all this if self damage (fall damage, explosion etc.)
            if (damager != entity) {
                if (damager instanceof Player playerDamager) {
                    final Heroes hero = Manager.current().getSelectedHero(playerDamager);

                    // remove critical hit
                    if (playerDamager.getFallDistance() > 0.0F
                            && !playerDamager.isOnGround()
                            && !playerDamager.hasPotionEffect(PotionEffectType.BLINDNESS)
                            && playerDamager.getVehicle() == null) {
                        damage /= 1.5F;
                    }

                    // decrease damage if hitting with a bow
                    final Material type = hero.getHero().getWeapon().getItem().getType();
                    if (type == Material.BOW || type == Material.CROSSBOW) {
                        damage *= 0.4d;
                    }

                    // assign the damager
                    damagerFinal = playerDamager;
                }
                else if (damager instanceof Projectile projectile) {
                    if (projectile.getShooter() instanceof Player playerDamager) {

                        // increase damage if fully charged shot
                        if (projectile instanceof Arrow arrow) {
                            if (arrow.isCritical()) {
                                damage *= 1.75d;
                            }
                        }

                        // assign the damager
                        damagerFinal = playerDamager;
                    }
                }
                else if (damager instanceof LivingEntity living) {
                    damagerFinal = living;
                }
            }

            /** Apply modifiers for damager */
            if (damager instanceof LivingEntity living) {
                final PotionEffect effectStrength = living.getPotionEffect(PotionEffectType.INCREASE_DAMAGE);
                final PotionEffect effectWeakness = living.getPotionEffect(PotionEffectType.WEAKNESS);

                // add 30% of damage per strength level
                if (effectStrength != null) {
                    damage += ((damage * 3 / 10) * (effectStrength.getAmplifier() + 1));
                }

                // --------------------------------------------reduce damage by 13% for each weakness level
                // reduce damage by half is has weakness effect
                if (effectWeakness != null) {
                    damage /= 2;
                }

                /** Apply GameEffect for damager */
                if (living instanceof Player player) {
                    final AbstractGamePlayer gp = GamePlayer.getPlayer(player);
                    if (gp.hasEffect(GameEffectType.STUN)) {
                        damage = 0.0d;
                    }

                    // lockdown
                    if (gp.hasEffect(GameEffectType.LOCK_DOWN)) {
                        ev.setCancelled(true);
                        gp.sendTitle("&c&lLOCKDOWN", "&cUnable to deal damage.", 0, 20, 0);
                        return;
                    }
                }

            }

            /** Apply modifiers for victim */
            {
                final PotionEffect effectResistance = livingEntity.getPotionEffect(PotionEffectType.DAMAGE_RESISTANCE);
                // reduce damage by 85% if we have resistance
                if (effectResistance != null) {
                    damage *= 0.15d;
                }

                // negate all damage is blocking
                if (livingEntity instanceof Player player) {
                    if (player.isBlocking()) {
                        damage = 0.0d;
                    }

                    /** Apply GameEffect for victim */
                    final AbstractGamePlayer gp = GamePlayer.getPlayer(player);
                    if (gp.hasEffect(GameEffectType.STUN)) {
                        gp.removeEffect(GameEffectType.STUN);
                    }

                    if (gp.hasEffect(GameEffectType.VULNERABLE)) {
                        damage *= 2.0d;
                    }

                }
            }
        }

        if (livingEntity instanceof Player player && damagerFinal != null) {
            GamePlayer.getPlayer(player).setLastDamager(damagerFinal);
        }

        // Process damager and victims hero damage processors
        final List<String> extraStrings = new ArrayList<>();

        // victim
        boolean cancelEvent = false;
        if (livingEntity instanceof Player player) {
            final DamageOutput output = getDamageOutput(player, damagerFinal, cause, damage, false);
            if (output != null) {
                damage = output.getDamage();
                cancelEvent = output.isCancelDamage();
                if (output.hasExtraDisplayStrings()) {
                    extraStrings.addAll(Arrays.stream(output.getExtraDisplayStrings()).toList());
                }
            }
        }

        // damager
        if (damagerFinal instanceof Player player) {
            final DamageOutput output = getDamageOutput(player, livingEntity, cause, damage, true);
            if (output != null) {
                damage = output.getDamage();
                if (!cancelEvent) {
                    cancelEvent = output.isCancelDamage();
                    if (output.hasExtraDisplayStrings()) {
                        extraStrings.addAll(Arrays.stream(output.getExtraDisplayStrings()).toList());
                    }
                }
            }
        }

        // only damage entities other that the player
        ev.setDamage(livingEntity instanceof Player ? 0.0d : damage);

        if (cancelEvent) {
            ev.setCancelled(true);
            return;
        }

        int randomPoint = ThreadLocalRandom.current().nextInt(1, 3);

        // grant ultimate points
        if (damagerFinal instanceof Player player && isEntityOk(entity)) {
            GamePlayer.getPlayer(player).addUltimatePoints(randomPoint);
        }
        else {
            randomPoint = 0;
        }

        // display damage
        if (damage >= 1.0d) {
            final DamageIndicator damageIndicator = new DamageIndicator(entity.getLocation(), damage, randomPoint);
            if (!extraStrings.isEmpty()) {
                damageIndicator.setExtra(extraStrings);
            }

            damageIndicator.display(20);
        }

        // make sure not to kill player but instead put them in spectator
        if (entity instanceof Player player) {
            final GamePlayer gamePlayer = GamePlayer.getAlivePlayer(player);
            // if game player is null means the game is not in progress
            if (gamePlayer != null) {
                final double health = gamePlayer.getHealth();
                gamePlayer.decreaseHealth(damage, damagerFinal);

                // cancel even if player died so there is no real death
                if (damage >= health) {
                    ev.setCancelled(true);
                    return;
                }
            }

            // fail safe for actual health
            if (player.getHealth() <= 0.0d) {
                ev.setCancelled(true);
            }

        }
    }

    private boolean isEntityOk(Entity entity) {
        return entity instanceof LivingEntity living &&
                (living.getType() != EntityType.ARMOR_STAND && !living.isInvisible() && !living.isDead());
    }

    private DamageOutput getDamageOutput(Player player, LivingEntity entity, EntityDamageEvent.DamageCause cause, double damage, boolean asDamager) {
        if (Manager.current().isPlayerInGame(player)) {
            final Hero hero = Manager.current().getSelectedHero(player).getHero();
            final DamageInput input = new DamageInput(player, entity, cause, damage);
            return asDamager ? hero.processDamageAsDamager(input) : hero.processDamageAsVictim(input);
        }
        return null;
    }

    @EventHandler()
    public void handleArmorStandDeath(EntityDeathEvent ev) {
        if (ev.getEntity() instanceof ArmorStand) {
            ev.getDrops().clear();
            ev.setDroppedExp(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void handleInventoryClickEvent(InventoryClickEvent ev) {
        if (Manager.current().isGameInProgress()) {
            ev.setCancelled(true);
        }
    }

    @Entry(
            name = "Talent usage"
    )
    @EventHandler(priority = EventPriority.HIGHEST)
    public void handlePlayerClick(PlayerItemHeldEvent ev) {
        final Player player = ev.getPlayer();
        if (!Manager.current().isAbleToUse(player)) {
            return;
        }

        // 1-2 -> Simple Abilities, 3-5 -> Complex Abilities (Extra)
        // 0 -> Weapon Slot

        final int newSlot = ev.getNewSlot();
        if (newSlot <= 0 || newSlot > 5) {
            return;
        }

        final Hero hero = Manager.current().getCurrentHero(player);
        final PlayerInventory inventory = player.getInventory();

        // don't care if talent is null, either not a talent or not complete
        // null or air item means this skill should be ignored for now (not active)
        final Talent talent = Manager.current().getTalent(hero, newSlot);
        final ItemStack itemOnNewSlot = inventory.getItem(newSlot);

        if (talent == null || !isValidItem(talent, itemOnNewSlot)) {
            return;
        }

        // Execute talent
        checkAndExecuteTalent(player, talent, newSlot);

        ev.setCancelled(true);
        inventory.setHeldItemSlot(0);

    }

    private boolean isValidItem(Talent talent, ItemStack stack) {
        return stack != null && !stack.getType().isAir();
        //return talent.getMaterial() == stack.getType();
    }

    @EventHandler(ignoreCancelled = true)
    public void handleInteraction(PlayerInteractEvent ev) {
        final Player player = ev.getPlayer();
        if (Manager.current().isGameInProgress() || player.getGameMode() != GameMode.CREATIVE) {
            final ItemStack item = ev.getItem();
            final Block clickedBlock = ev.getClickedBlock();

            if (ev.getAction() == Action.PHYSICAL) {
                return;
            }

            if (item != null) {
                // allow to interact with intractable items
                if (isIntractable(item)) {
                    return;
                }
            }

            if (clickedBlock != null) {
                // allow to click at button (secret passages)
                // maybe rework with custom buttons but meh
                if (clickedBlock.getType().name().toLowerCase(Locale.ROOT).contains("button")) {
                    return;
                }
            }

            ev.setCancelled(true);
        }
    }

    private boolean isIntractable(ItemStack stack) {
        final Material type = stack.getType();
        return switch (type) {
            case BOW, CROSSBOW, TRIDENT -> true;
            default -> type.isInteractable();
        };
    }

    @EventHandler()
    public void handleMovement(PlayerMoveEvent ev) {
        final Player player = ev.getPlayer();
        final Location from = ev.getFrom();
        final Location to = ev.getTo();

        if (Manager.current().isGameInProgress()) {
            final AbstractGamePlayer gp = GamePlayer.getPlayer(player);

            if (hasNotMoved(from, to)) {
                return;
            }

            // Amnesia
            if (gp.hasEffect(GameEffectType.AMNESIA)) {

                final double pushSpeed = player.isSneaking() ? 0.05d : 0.1d;
                player.setVelocity(new Vector(
                        new Random().nextBoolean() ? pushSpeed : -pushSpeed,
                        -0.2723,
                        new Random().nextBoolean() ? pushSpeed : -pushSpeed
                ));
            }

            // AFK detection
            gp.markLastMoved();

        }

    }

    private boolean hasNotMoved(Location from, @Nullable Location to) {
        if (to == null) {
            return true;
        }
        return from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ();
    }

    @EventHandler()
    public void handleSlotClick(InventoryClickEvent ev) {
        if (ev.getClick() == ClickType.DROP && ev.getWhoClicked() instanceof Player player && player.getGameMode() == GameMode.CREATIVE) {
            ev.setCancelled(true);
            Chat.sendMessage(player, "&aClicked %s slot.", ev.getRawSlot());
            PlayerLib.playSound(player, Sound.BLOCK_LEVER_CLICK, 2.0f);
        }
    }

    private void checkAndExecuteTalent(Player player, Talent talent, int slot) {
        // null check
        if (talent == null) {
            Chat.sendMessage(player, "&cNullPointerException: talent is null");
            return;
        }

        // cooldown check
        if (talent.hasCd(player)) {
            Chat.sendMessage(player, "&cTalent on cooldown for %ss.", BukkitUtils.roundTick(talent.getCdTimeLeft(player)));
            return;
        }

        // charge check
        if (talent instanceof ChargedTalent chargedTalent) {
            if (chargedTalent.getChargedAvailable(player) <= 0) {
                Chat.sendMessage(player, "&cOut of charges!");
                return;
            }
        }

        // Execute talent and get response
        final Response response = talent.execute0(player);

        if (response.isError()) {
            response.sendError(player);
            return;
        }

        // await stops the code here, basically OK but does not start cooldown nor remove charge if charged talent.
        if (response.isAwait()) {
            return;
        }

        if (talent instanceof ChargedTalent chargedTalent) {
            chargedTalent.setLastKnownSlot(player, slot);
            chargedTalent.removeChargeAndStartCooldown(player);
        }

        talent.startCd(player);
    }

}
