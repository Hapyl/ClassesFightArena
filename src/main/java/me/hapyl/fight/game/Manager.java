package me.hapyl.fight.game;

import com.google.common.collect.Maps;
import me.hapyl.fight.Main;
import me.hapyl.fight.annotate.Entry;
import me.hapyl.fight.game.gamemode.Modes;
import me.hapyl.fight.game.heroes.ComplexHero;
import me.hapyl.fight.game.heroes.Hero;
import me.hapyl.fight.game.heroes.Heroes;
import me.hapyl.fight.game.maps.GameMaps;
import me.hapyl.fight.game.profile.PlayerProfile;
import me.hapyl.fight.game.scoreboard.GamePlayerUI;
import me.hapyl.fight.game.setting.Setting;
import me.hapyl.fight.game.talents.ChargedTalent;
import me.hapyl.fight.game.talents.Talent;
import me.hapyl.fight.game.talents.Talents;
import me.hapyl.fight.game.task.GameTask;
import me.hapyl.fight.game.trial.Trial;
import me.hapyl.fight.util.NonNullableElementHolder;
import me.hapyl.fight.util.Nulls;
import me.hapyl.fight.util.ParamFunction;
import me.hapyl.fight.util.Utils;
import me.hapyl.spigotutils.module.chat.Chat;
import me.hapyl.spigotutils.module.entity.Entities;
import me.hapyl.spigotutils.module.player.PlayerLib;
import me.hapyl.spigotutils.module.util.BukkitUtils;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class Manager {

    private boolean isDebug = true;

    private GameInstance gameInstance;

    private final Map<Player, PlayerProfile> profiles;

    protected final NonNullableElementHolder<GameMaps> currentMap = new NonNullableElementHolder<>(GameMaps.ARENA);
    protected final NonNullableElementHolder<Modes> currentMode = new NonNullableElementHolder<>(Modes.FFA);

    private final Map<Integer, ParamFunction<Talent, Hero>> slotPerTalent = new HashMap<>();
    private final Map<Integer, ParamFunction<Talent, ComplexHero>> slotPerComplexTalent = new HashMap<>();

    private Trial trial;
    private boolean teamMode;

    public Manager() {
        profiles = Maps.newHashMap();

        slotPerTalent.put(1, Hero::getFirstTalent);
        slotPerTalent.put(2, Hero::getSecondTalent);
        slotPerComplexTalent.put(3, ComplexHero::getThirdTalent);
        slotPerComplexTalent.put(4, ComplexHero::getFourthTalent);
        slotPerComplexTalent.put(5, ComplexHero::getFifthTalent);

        // load map
        final FileConfiguration config = Main.getPlugin().getConfig();
        currentMap.set(GameMaps.byName(config.getString("current-map"), GameMaps.ARENA));

        // load mode
        currentMode.set(Modes.byName(config.getString("current-mode"), Modes.FFA));
    }

    /**
     * Gets player's current profile or creates new if it doesn't exist yet.
     *
     * @param player - Player.
     */
    public PlayerProfile getProfile(Player player) {
        PlayerProfile profile = profiles.get(player);
        if (profile == null) {
            profile = new PlayerProfile(player);
            profiles.put(player, profile);
            profile.loadData();
        }
        return profile;
    }

    public boolean hasProfile(Player player) {
        return profiles.containsKey(player);
    }

    public void setTeamMode(boolean teamMode) {
        this.teamMode = teamMode;
    }

    public void toggleTeamMode() {
        setTeamMode(!isTeamMode());
    }

    public boolean isTeamMode() {
        return teamMode;
    }

    @Nullable
    public GamePlayerUI getPlayerUI(Player player) {
        return getProfile(player).getPlayerUI();
    }

    public boolean isAbleToUse(Player player) {
        return isGameInProgress() || isTrialExistsAndIsOwner(player);
    }

    public boolean isGameInProgress() {
        return gameInstance != null && !gameInstance.isTimeIsUp();
    }

    /**
     * @return game instance is present.
     */
    @Nullable
    public GameInstance getGameInstance() throws RuntimeException {
        return gameInstance;
    }

    /**
     * @return game instance is present, else abstract version.
     */
    @Nonnull
    public AbstractGameInstance getCurrentGame() {
        return gameInstance == null ? AbstractGameInstance.NULL_GAME_INSTANCE : gameInstance;
    }

    public GameMaps getCurrentMap() {
        return currentMap.getElement();
    }

    public void setCurrentMap(GameMaps maps) {
        currentMap.set(maps);
        // save to config
        Main.getPlugin().getConfig().set("current-map", maps.name().toLowerCase(Locale.ROOT));
    }

    public boolean isDebug() {
        return isDebug;
    }

    public Trial getTrial() {
        return trial;
    }

    public boolean hasTrial() {
        return getTrial() != null;
    }

    public boolean isTrialExistsAndIsOwner(Player player) {
        return hasTrial() && getTrial().getPlayer() == player;
    }

    public void startTrial(Player player, Heroes heroes) {
        if (hasTrial()) {
            return;
        }

        trial = new Trial(getProfile(player), heroes);
        trial.onStart();
        trial.broadcastMessage("&a%s started a trial of %s.", player.getName(), heroes.getHero().getName());
    }

    public void stopTrial() {
        if (!hasTrial()) {
            return;
        }

        trial.broadcastMessage("&a%s has stopped trial challenge.", trial.getPlayer().getName());
        trial.onStop();
        trial = null;
    }

    public Modes getCurrentMode() {
        return currentMode.getElement();
    }

    public void setCurrentMode(Modes mode) {
        if (mode == getCurrentMode()) {
            return;
        }
        currentMode.set(mode);
        Chat.broadcast("&aChanged current game mode to %s.", mode.getMode().getName());

        // save to config
        Main.getPlugin().getConfig().set("current-mode", mode.name().toLowerCase(Locale.ROOT));
    }

    public void setCurrentMap(GameMaps maps, @Nullable Player player) {
        if (getCurrentMap() == maps && player != null) {
            PlayerLib.villagerNo(player, "&cAlready selected!");
            return;
        }

        setCurrentMap(maps);

        final String mapName = maps.getMap().getName();
        if (player == null) {
            Chat.broadcast("&aCurrent map is now &l%s&a.", mapName);
        }
        else {
            Chat.broadcast("&a%s selected &l%s &aas current map!", player.getName(), mapName);
        }
    }

    private void displayError(String message, Object... objects) {
        Chat.broadcast("&c&lUnable to start the game! &c" + message.formatted(objects));
    }

    public void createNewGameInstance() {
        createNewGameInstance(false);
    }

    @Entry(
            name = "Creating new Game Instance."
    )
    public void createNewGameInstance(boolean debug) {
        // Pre game start checks
        final GameMaps currentMap = this.currentMap.getElement();
        if (currentMap == null || !currentMap.isPlayable() || !currentMap.getMap().hasLocation()) {
            displayError("Invalid map!");
            return;
        }

        if (hasTrial()) {
            stopTrial();
        }

        isDebug = debug;

        final int playerRequirements = getCurrentMode().getMode().getPlayerRequirements();

        final Collection<Player> nonSpectatorPlayers = getNonSpectatorPlayers();
        if (nonSpectatorPlayers.size() < playerRequirements && !isDebug) {
            displayError("Not enough players! &l(%s/%s)", nonSpectatorPlayers.size(), playerRequirements);
            return;
        }

        // Create new instance and call onStart methods
        this.gameInstance = new GameInstance(getCurrentMode(), getCurrentMap());
        this.gameInstance.onStart();

        for (final Heroes value : Heroes.values()) {
            Nulls.runIfNotNull(value.getHero(), Hero::onStart);
        }

        for (final Talents value : Talents.values()) {
            Nulls.runIfNotNull(value.getTalent(), Talent::onStart);
        }

        this.gameInstance.getCurrentMap().getMap().onStart();

        for (final GamePlayer gamePlayer : this.gameInstance.getPlayers().values()) {
            final Player player = gamePlayer.getPlayer();
            if (!gamePlayer.isSpectator()) {
                equipPlayer(player, gamePlayer.getHero());
                Utils.hidePlayer(player);
            }

            player.teleport(currentMap.getMap().getLocation());
        }

        if (!isDebug) {
            Chat.broadcast("&a&l??? &aAll players have been hidden!");
            Chat.broadcast(
                    "&a&l??? &aThey have &e%ss &ato spread before being revealed.",
                    BukkitUtils.roundTick(currentMap.getMap().getTimeBeforeReveal())
            );
        }

        GameTask.runLater(() -> {
            Chat.broadcast("&a&l??? &aPlayers have been revealed. &lFIGHT!");
            gameInstance.setGameState(State.IN_GAME);
            gameInstance.getAlivePlayers().forEach(target -> {
                final Player player = target.getPlayer();
                final World world = player.getLocation().getWorld();

                Utils.showPlayer(player);

                if (world != null) {
                    world.strikeLightningEffect(player.getLocation().add(0.0d, 1.0d, 0.0d));
                }
            });
        }, isDebug ? 1 : currentMap.getMap().getTimeBeforeReveal());

    }

    private Collection<Player> getNonSpectatorPlayers() {
        return Bukkit.getOnlinePlayers().stream().filter(player -> !Setting.SPECTATE.isEnabled(player))
                .collect(Collectors.toSet());
    }

    @Entry(
            name = "Stopping current Game Instance."
    )
    public void stopCurrentGame() {
        if (this.gameInstance == null || this.gameInstance.getGameState() == State.POST_GAME) {
            return;
        }

        // Call mode onStop to clear player and assign winners
        final boolean response = gameInstance.getMode().onStop(this.gameInstance);

        if (!response) { // if returns false means mode will add their own winners
            gameInstance.getWinners().addAll(gameInstance.getAlivePlayers());
        }

        gameInstance.calculateEverything();

        // Reset player before clearing the instance
        this.gameInstance.getPlayers().values().forEach(player -> {
            player.updateScoreboard(false);
            player.resetPlayer();
            player.setValid(false);

            // keep winner in survival, so it's clear for them that they have won
            if (!this.gameInstance.isWinner(player.getPlayer())) {
                player.getPlayer().setGameMode(GameMode.SPECTATOR);
            }

            // reset game player
            player.getProfile().resetGamePlayer();
        });

        this.gameInstance.onStop();
        this.gameInstance.setGameState(State.POST_GAME);

        // reset all cooldowns
        for (final Material value : Material.values()) {
            if (value.isItem()) {
                Bukkit.getOnlinePlayers().forEach(player -> player.setCooldown(value, 0));
            }
        }

        // call talents onStop and reset cooldowns
        for (final Talents value : Talents.values()) {
            Nulls.runIfNotNull(value.getTalent(), Talent::onStop);
        }

        // call heroes onStop
        for (final Heroes value : Heroes.values()) {
            Nulls.runIfNotNull(value.getHero(), hero -> {
                hero.onStop();
                hero.clearUsingUltimate();
            });
        }

        // call maps onStop
        currentMap.getElement().getMap().onStop();

        // stop all game tasks
        Main.getPlugin().getTaskList().onStop();

        // remove temp entities
        Entities.killSpawned();

        if (isDebug) {
            onStop();
            return;
        }

        // Spawn Fireworks
        gameInstance.spawnFireworks(true);
    }

    public void equipPlayer(Player player, Hero hero) {
        final PlayerInventory inventory = player.getInventory();
        inventory.setHeldItemSlot(0);
        player.setGameMode(GameMode.SURVIVAL);

        // Apply equipment
        hero.getEquipment().equip(player);
        hero.onStart(player);

        inventory.setItem(0, hero.getWeapon().getItem());
        giveTalentItem(player, hero, 1);
        giveTalentItem(player, hero, 2);

        if (hero instanceof ComplexHero) {
            giveTalentItem(player, hero, 3);
            giveTalentItem(player, hero, 4);
            giveTalentItem(player, hero, 5);
        }

        player.updateInventory();
    }

    public void equipPlayer(Player player) {
        equipPlayer(player, getSelectedHero(player).getHero());
    }

    private void giveTalentItem(Player player, Hero hero, int slot) {
        final PlayerInventory inventory = player.getInventory();
        final Talent talent = getTalent(hero, slot);
        final ItemStack talentItem = talent == null || talent.getItem() == null ? new ItemStack(Material.AIR) : talent.getItem();

        if (talent != null && !talent.isAutoAdd()) {
            return;
        }

        inventory.setItem(slot, talentItem);
        fixTalentItemAmount(player, slot, talent);
    }

    public Talent getTalent(Hero hero, int slot) {
        if (slot >= 1 && slot < 3) {
            final ParamFunction<Talent, Hero> function = slotPerTalent.get(slot);
            return function == null ? null : function.execute(hero);
        }

        else if (hero instanceof ComplexHero complexHero) {
            final ParamFunction<Talent, ComplexHero> function = slotPerComplexTalent.get(slot);
            return function == null ? null : function.execute(complexHero);
        }
        return null;
    }

    private void fixTalentItemAmount(Player player, int slot, Talent talent) {
        if (!(talent instanceof ChargedTalent chargedTalent)) {
            return;
        }
        final PlayerInventory inventory = player.getInventory();
        final ItemStack item = inventory.getItem(slot);
        if (item == null) {
            return;
        }
        item.setAmount(chargedTalent.getMaxCharges());
    }
    // FIXME: 002. 10/02/2021 - multi

    public void onStop() {
        // reset game state
        gameInstance.setGameState(State.FINISHED);
        gameInstance = null;

        // teleport player
        for (final Player player : Bukkit.getOnlinePlayers()) {
            player.setInvulnerable(false);
            player.setHealth(player.getMaxHealth());
            player.setGameMode(GameMode.SURVIVAL);
            player.teleport(GameMaps.SPAWN.getMap().getLocation());
        }
    }

    public void setSelectedHero(Player player, Heroes heroes) {
        if (Manager.current().isGameInProgress()) {
            Chat.sendMessage(player, "&cUnable to change hero during the game!");
            PlayerLib.playSound(player, Sound.ENTITY_ENDERMAN_TELEPORT, 0.0f);
            return;
        }

        if (!heroes.isValidHero()) {
            Chat.sendMessage(player, "&cThis hero is currently disabled. Sorry!");
            PlayerLib.villagerNo(player);
            return;
        }

        if (getSelectedHero(player) == heroes) {
            Chat.sendMessage(player, "&cAlready selected!");
            PlayerLib.villagerNo(player);
            return;
        }

        getProfile(player).setSelectedHero(heroes);
        player.closeInventory();
        PlayerLib.villagerYes(player);
        Chat.sendMessage(player, "&aSelected %s!", heroes.getHero().getName());

        if (Setting.RANDOM_HERO.isEnabled(player)) {
            Chat.sendMessage(player, "");
            Chat.sendMessage(player, "&aKeep in mind &l%s &ais enabled! Use &e/setting", Setting.RANDOM_HERO.getName());
            Chat.sendMessage(player, "&aturn the feature off and play as %s!", heroes.getHero().getName());
            Chat.sendMessage(player, "");
        }

        // save to database
        getProfile(player).getDatabase().getHeroEntry().setSelectedHero(heroes);
    }

    /**
     * @return actual hero player is using right now, trial, lobby or game.
     */
    public Hero getCurrentHero(Player player) {
        if (isTrialExistsAndIsOwner(player)) {
            return getTrial().getHeroes().getHero();
        }
        else if (isPlayerInGame(player)) {
            final GamePlayer gamePlayer = getCurrentGame().getPlayer(player);
            if (gamePlayer == null) {
                return Heroes.ARCHER.getHero();
            }
            return gamePlayer.getHero();
        }
        return getSelectedHero(player).getHero();
    }

    public Heroes getSelectedHero(Player player) {
        return getProfile(player).getSelectedHero();
    }

    public boolean isPlayerInGame(Player player) {
        return gameInstance != null && gameInstance.getPlayer(player) != null && GamePlayer.getPlayer(player).isAlive();
    }

    public static Manager current() {
        return Main.getPlugin().getManager();
    }

    public void removeProfile(Player player) {
        profiles.remove(player);
    }

    public void createProfile(Player player) {
        getProfile(player);
    }
}
