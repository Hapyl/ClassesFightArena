package me.hapyl.fight.game.talents.storage;

import me.hapyl.fight.game.GamePlayer;
import me.hapyl.fight.game.Response;
import me.hapyl.fight.game.effect.GameEffectType;
import me.hapyl.fight.game.heroes.Heroes;
import me.hapyl.fight.game.heroes.storage.Alchemist;
import me.hapyl.fight.game.heroes.storage.extra.CauldronEffect;
import me.hapyl.fight.game.talents.Talent;
import me.hapyl.fight.game.talents.storage.extra.Effect;
import me.hapyl.fight.util.RandomTable;
import me.hapyl.spigotutils.module.chat.Chat;
import me.hapyl.spigotutils.module.player.PlayerLib;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import static org.bukkit.potion.PotionEffectType.*;

public class RandomPotion extends Talent {

    private final RandomTable<Effect> effects = new RandomTable<>();

    public RandomPotion() {
        super("Abyssal Bottle", "A bottle that is capable of creating potions from the &0&lvoid &7itself.", Type.COMBAT);
        this.setItem(Material.POTION);
        this.setCd(50);

        effects.add(new Effect("&b\uD83C\uDF0A", "Speed Boost", SPEED, 60, 1))
                .add(new Effect("ā", "Jump Boost", JUMP, 100, 1))
                .add(new Effect("&cā", "Strength", INCREASE_DAMAGE, 60, 3))
                .add(new Effect("&6š”", "Resistance", DAMAGE_RESISTANCE, 80, 1))
                .add(new Effect("&9āā", "Invisibility") {
                    @Override
                    public void affect(Player player) {
                        GamePlayer.getPlayer(player).addEffect(GameEffectType.INVISIBILITY, 60, true);
                    }
                })
                .add(new Effect("&cā¤", "Healing") {
                    @Override
                    public void affect(Player player) {
                        GamePlayer.getPlayer(player).heal(10);
                    }
                });

    }

    @Override
    public Response execute(Player player) {
        final Alchemist hero = (Alchemist) Heroes.ALCHEMIST.getHero();
        final CauldronEffect effect = hero.getEffect(player);

        hero.addToxinForUsingPotion(player);

        if (effect != null && effect.getDoublePotion() > 0) {
            effect.decrementDoublePotions();
            final Effect firstEffect = this.effects.getRandomElement();
            final Effect secondEffect = this.effects.getRandomElement();
            firstEffect.applyEffectsIgnoreFx(player);
            secondEffect.applyEffectsIgnoreFx(player);
            // Display Improved
            Chat.sendMessage(player, "&eā &a&lDouble Potion has %s changes left", effect.getDoublePotion());
            Chat.sendMessage(
                    player,
                    " &aGained %s &a%s &aand %s &a%s",
                    firstEffect.getEffectChar(),
                    firstEffect.getEffectName(),
                    secondEffect.getEffectChar(),
                    secondEffect.getEffectName()
            );

            Chat.sendTitle(
                    player,
                    "&a%s      &a%s".formatted(firstEffect.getEffectChar(), secondEffect.getEffectChar()),
                    "&6%s    &6%s".formatted(firstEffect.getEffectName(), secondEffect.getEffectName()),
                    5,
                    10,
                    5
            );

            PlayerLib.playSound(player, Sound.ITEM_BOTTLE_FILL, 1.25f);
            return Response.OK;
        }


        this.effects.getRandomElement().applyEffects(player);
        return Response.OK;
    }
}
