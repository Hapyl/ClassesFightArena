package me.hapyl.fight.game.maps.maps;

import me.hapyl.fight.game.EnumDamageCause;
import me.hapyl.fight.game.GamePlayer;
import me.hapyl.fight.game.Manager;
import me.hapyl.fight.game.maps.GameMap;
import me.hapyl.fight.game.maps.MapFeature;
import me.hapyl.fight.game.maps.Size;
import me.hapyl.fight.util.Utils;
import me.hapyl.spigotutils.module.math.Numbers;
import me.hapyl.spigotutils.module.player.PlayerLib;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DragonsGorge extends GameMap {

	public DragonsGorge() {
        super("Dragon's Gorge", Material.DARK_OAK_BOAT, 100);
        this.setInfo("...");
		this.setSize(Size.MEDIUM);
		this.addFeature(new MapFeature("Sheer Cold", "This water is so cold! Better keep an eye on your cold-o-meter!") {

			private final Map<Player, Float> coldMeter = new HashMap<>();
			private final float maxColdValue = 100.0f;

			@Override
			public void onStop() {
				coldMeter.clear();
			}

			@Override
			public void tick(int tick) {
				final List<GamePlayer> players = Manager.current().getCurrentGame().getAlivePlayers();

				players.forEach(gp -> {
					final Player player = gp.getPlayer();
					if (!gp.isAlive()) {
						coldMeter.remove(player);
						return;
					}

					final float newValue = addColdValue(player, player.isInWater() ? 0.25f : -0.1f);
					if (newValue < 0) {
						return;
					}

					// Punish
					if (tick == 0) {
						// Display cold meter
						if (newValue > 0) {
							final Utils.ProgressBar builder = new Utils.ProgressBar("❄", ChatColor.AQUA, 15);
							gp.sendTitle("", builder.build((int)(newValue * builder.getMax() / maxColdValue)), 0, 25, 5);
						}

						// For FX
						player.setFreezeTicks((int)Math.min(player.getMaxFreezeTicks(), newValue));

						if (isBetween(newValue, 25, 50)) { // Low hitting ticks
							GamePlayer.damageEntity(player, 4.0d, null, EnumDamageCause.FREEZE);
						}
						else if (isBetween(newValue, 50, 100)) { // High hitting ticks and warning
							GamePlayer.damageEntity(player, 6.0d, null, EnumDamageCause.FREEZE);
						}
						else if (newValue >= maxColdValue) { // Instant Death
							GamePlayer.damageEntity(player, 1000.0d, null, EnumDamageCause.FREEZE);
						}

						// Fx
						if (newValue >= 60) {
							PlayerLib.playSound(
									player,
									Sound.BLOCK_GLASS_BREAK,
									Numbers.clamp(1.0f - newValue / maxColdValue, 0.0f, 2.0f)
							);
						}
					}

				});
			}

			private boolean isBetween(float value, float min, float max) {
				return value >= min && value < max;
			}

			private float addColdValue(Player player, float value) {
				coldMeter.put(player, Numbers.clamp(getColdValue(player) + value, 0.0f, maxColdValue));
				return getColdValue(player);
			}

			private float getColdValue(Player player) {
				return coldMeter.getOrDefault(player, 0.0f);
			}

		});

		this.addLocation(-143, 64, 86);
		this.addLocation(-150, 64, 100);
		this.addLocation(-172, 64, 119);
	}
}
