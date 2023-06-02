package tictac7x.charges.infoboxes;

import net.runelite.api.Client;
import net.runelite.api.GraphicID;
import net.runelite.api.ItemID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import tictac7x.charges.ChargedItemInfoBox;
import tictac7x.charges.ChargesImprovedConfig;
import tictac7x.charges.ChargesItem;
import tictac7x.charges.triggers.TriggerChatMessage;
import tictac7x.charges.triggers.TriggerGraphic;
import tictac7x.charges.triggers.TriggerItem;
import tictac7x.charges.triggers.TriggerWidget;

public class W_BryophytasStaff extends ChargedItemInfoBox {
    public W_BryophytasStaff(
        final Client client,
        final ClientThread client_thread,
        final ConfigManager configs,
        final ItemManager items,
        final InfoBoxManager infoboxes,
        final ChatMessageManager chat_messages,
        final ChargesImprovedConfig config,
        final Plugin plugin
    ) {
        super(ChargesItem.BRYOPHYTAS_STAFF, ItemID.BRYOPHYTAS_STAFF, client, client_thread, configs, items, infoboxes, chat_messages, config, plugin);
        this.config_key = ChargesImprovedConfig.bryophytas_staff;
        this.triggers_items = new TriggerItem[]{
            new TriggerItem(ItemID.BRYOPHYTAS_STAFF_UNCHARGED).fixedCharges(0),
            new TriggerItem(ItemID.BRYOPHYTAS_STAFF)
        };
        this.triggers_chat_messages = new TriggerChatMessage[]{
            new TriggerChatMessage("The nature staff has (?<charges>.+) charges?."),
            new TriggerChatMessage("Your staff saved you a nature rune.").increaseCharges(1),
        };
        this.triggers_widgets = new TriggerWidget[]{
            new TriggerWidget("Your Bryophyta's staff now has (?<charges>.+) charges?.")
        };
        this.triggers_graphics = new TriggerGraphic[]{
            new TriggerGraphic(112).decreaseCharges(1).equipped(),
            new TriggerGraphic(113).decreaseCharges(1).equipped(),
        };
    }
}