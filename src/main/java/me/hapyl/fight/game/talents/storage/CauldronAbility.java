package me.hapyl.fight.game.talents.storage;

import me.hapyl.fight.game.Manager;
import me.hapyl.fight.game.Response;
import me.hapyl.fight.game.heroes.Heroes;
import me.hapyl.fight.game.talents.Talent;
import me.hapyl.fight.game.talents.storage.extra.AlchemicalCauldron;
import me.hapyl.fight.game.task.GameTask;
import me.hapyl.fight.util.Nulls;
import me.hapyl.spigotutils.module.inventory.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.Map;

public class CauldronAbility extends Talent implements Listener {

	private final Map<Player, AlchemicalCauldron> cauldrons = new HashMap<>();

	public CauldronAbility() {
		super(
				"Brewing Pot",
				"Place a Brewing Cauldron to brew a Magic Potion. Put your Brewing Stick in it and wait! Once ready, claim you potion and enhance yourself with following effects:____&a- &7Drinking a potion will grant double effects. &8(5 charges)____&a- &7Hitting an enemy will apply random effect. &8(10 charges)",
				Type.COMBAT
		);
		this.setItem(Material.CAULDRON);
		this.setCdSec(120);
	}

	@EventHandler()
	public void handleInteraction(PlayerInteractEvent ev) {
		if (!Manager.current().isGameInProgress()) {
			return;
		}

		final Player player = ev.getPlayer();
		final Block clickedBlock = ev.getClickedBlock();

		if (ev.getHand() == EquipmentSlot.OFF_HAND
				|| ev.getAction() != Action.RIGHT_CLICK_BLOCK
				|| clickedBlock == null
				|| clickedBlock.getType() != Material.CAULDRON) {
			return;
		}

		if (!Heroes.ALCHEMIST.isSelected(player)) {
			return;
		}

		final AlchemicalCauldron cauldron = cauldrons.get(player);
		if (cauldron == null || !cauldron.compareBlock(clickedBlock)) {
			return;
		}

		ev.setCancelled(true);
		switch (cauldron.getStatus()) {

			case NEUTRAL, PAUSED -> {
				cauldron.setStatus(AlchemicalCauldron.Status.BREWING);
				changeItem(player, false);
			}

			case BREWING -> {
				cauldron.setStatus(AlchemicalCauldron.Status.PAUSED);
				changeItem(player, true);
			}

			case FINISHED -> {
				cauldron.finish();
				cauldron.clear();
				changeItem(player, true);
			}

		}
	}

	private void changeItem(Player player, boolean flag) {
		final PlayerInventory inventory = player.getInventory();
		GameTask.runLater(() -> {
			if (flag) {
				inventory.setItem(0, Heroes.ALCHEMIST.getHero().getWeapon().getItem());
			}
			else {
				inventory.setItem(0, new ItemBuilder(Material.CLAY_BALL).setName("&aStick is Missing")
						.setSmartLore("Your stick is currently brewing a potion! Click the cauldron to get it back.")
						.toItemStack());
			}
		}, 1);
	}

	@Override
	public void onStop() {
		cauldrons.values().forEach(AlchemicalCauldron::clear);
		cauldrons.clear();
	}

	@Override
	public void onDeath(Player player) {
		Nulls.runIfNotNull(cauldrons.get(player), AlchemicalCauldron::clear);
		cauldrons.remove(player);
	}

	@Override
	public Response execute(Player player) {
		final Block targetBlock = getTargetBlock(player);

		if (targetBlock == null) {
			return Response.error("Invalid target block!");
		}

		if (!targetBlock.getType().isAir()) {
			return Response.error("Target block is occupied!");
		}

		if (cauldrons.containsKey(player)) {
			return Response.error("You already have a cauldron!");
		}

		cauldrons.put(player, new AlchemicalCauldron(player, targetBlock.getLocation().clone()));
		return Response.OK;

	}

	private Block getTargetBlock(Player player) {
		final Block targetBlock = player.getTargetBlockExact(5);
		if (targetBlock == null) {
			return null;
		}
		return targetBlock.getRelative(BlockFace.UP);
	}

}
