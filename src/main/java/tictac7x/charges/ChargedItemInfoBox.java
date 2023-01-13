package tictac7x.charges;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.infobox.InfoBox;
import tictac7x.charges.triggers.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.Color;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChargedItemInfoBox extends InfoBox {
    protected final int item_id;
    protected final Client client;
    protected final ClientThread client_thread;
    protected final ItemManager items;
    protected final ConfigManager configs;

    @Nullable protected ItemContainer inventory;
    @Nullable protected ItemContainer equipment;
    @Nullable protected String menu_option;

    protected boolean needs_to_be_equipped_for_infobox;
    protected boolean equipped;
    @Nullable protected Integer animation;

    @Nullable protected String config_key;
    @Nullable protected TriggerChatMessage[] triggers_chat_messages;
    @Nullable protected TriggerAnimation[] triggers_animations;
    @Nullable protected TriggerHitsplat[] triggers_hitsplats;
    @Nullable protected TriggerItem[] triggers_items;
    @Nullable protected TriggerWidget[] triggers_widgets;
    @Nullable protected String[] extra_groups;

    protected int charges = -1;
    private boolean render = false;

    public ChargedItemInfoBox(final int item_id, final Client client, final ClientThread client_thread, final ConfigManager configs, final ItemManager items, final Plugin plugin) {
        super(items.getImage(item_id), plugin);
        this.item_id = item_id;
        this.client = client;
        this.client_thread = client_thread;
        this.configs = configs;
        this.items = items;
        loadChargesFromConfig();
    }

    @Override
    public String getName() {
        return super.getName() + "_" + item_id;
    }

    @Override
    public String getText() {
        return this.charges == -1 ? "?" : needs_to_be_equipped_for_infobox && !equipped ? "0" : String.valueOf(charges);
    }

    @Override
    public String getTooltip() {
        return items.getItemComposition(item_id).getName() + (needs_to_be_equipped_for_infobox && !equipped ? " - Needs to be equipped" : "");
    }

    @Override
    public Color getTextColor() {
        return getText().equals("?") ? Color.orange : getText().equals("0") ? Color.red : Color.white;
    }

    @Override
    public boolean render() {
        return render;
    }

    public void onItemContainersChanged(@Nonnull final ItemContainer inventory, @Nonnull final ItemContainer equipment) {
        // Save inventory and equipment item containers for other uses.
        this.inventory = inventory;
        this.equipment = equipment;

        // No trigger items to detect charges.
        if (triggers_items == null) return;

        boolean equipped = false;
        boolean render = false;

        for (final TriggerItem trigger_item : triggers_items) {
            // Find out if infobox should be rendered.
            if (inventory.contains(trigger_item.item_id) || equipment.contains(trigger_item.item_id)) {
                render = true;
            }

            // Find out if item is equipped.
            if (equipment.contains(trigger_item.item_id)) {
                equipped = true;
            }

            // Check if inventory or equipment contains trigger item.
            if (!inventory.contains(trigger_item.item_id) && !equipment.contains(trigger_item.item_id)) continue;

            // Find out charges for the item.
            if (trigger_item.charges != null) {
                int charges = 0;
                charges += inventory.count(trigger_item.item_id) * trigger_item.charges;
                charges += equipment.count(trigger_item.item_id) * trigger_item.charges;
                this.charges = charges;
            }
        }

        this.equipped = equipped;
        this.render = render;
    }

    public void onChatMessage(final ChatMessage event) {
        if (
            event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM ||
            this.config_key == null ||
            triggers_chat_messages == null
        ) return;

        final String message = event.getMessage().replaceAll("</?col.*?>", "");

        for (final TriggerChatMessage chat_message : triggers_chat_messages) {
            final Pattern regex = Pattern.compile(chat_message.message);
            final Matcher matcher = regex.matcher(message);
            if (!matcher.find()) continue;

            // Check default "charges" group.
            setCharges(chat_message.charges != null
                // Charges amount is fixed.
                ? chat_message.charges
                // Charges amount is dynamic and extracted from the message.
                : Integer.parseInt(matcher.group("charges").replaceAll(",", ""))
            );

            // Check extra matches groups.
            if (extra_groups != null) {
                for (final String extra_group : extra_groups) {
                    final String extra = matcher.group(extra_group);
                    if (extra != null) setConfiguration(config_key + "_" + extra_group, extra);
                }
            }

            return;
        }
    }
    public void onConfigChanged(final ConfigChanged event) {
        if (event.getGroup().equals(ChargesImprovedConfig.group) && event.getKey().equals(config_key)) {
            this.charges = Integer.parseInt(event.getNewValue());
        }
    }

    public void onAnimationChanged(final AnimationChanged event) {
        if (inventory == null || equipment == null || triggers_animations == null || event.getActor() != client.getLocalPlayer() || this.charges == -1 || this.triggers_items == null) return;

        this.animation = event.getActor().getAnimation();
        System.out.println("ANIMATION SAVED: " + animation);

        for (final TriggerAnimation trigger_animation : triggers_animations) {
            boolean valid = true;

            if (trigger_animation.animation_id == event.getActor().getAnimation()) {
                // Check for correct menu option.
                if (trigger_animation.menu_option != null && menu_option != null && !menu_option.equals(trigger_animation.menu_option)) {
                    valid = false;
                }

                // Check for unallowed items.
                if (valid && trigger_animation.unallowed_items != null) {
                    for (final int item_id : trigger_animation.unallowed_items) {
                        if (inventory.contains(item_id) || equipment.contains(item_id)) {
                            valid = false;
                            break;
                        }
                    }
                }

                // Check if equipped.
                if (trigger_animation.equipped) {
                    boolean equipped = false;
                    for (final TriggerItem trigger_item : triggers_items) {
                        if (equipment.contains(trigger_item.item_id)) {
                            equipped = true;
                        }
                    }
                    if (!equipped) valid = false;
                }

                // Invalid trigger, don't modify charges.
                if (!valid) continue;

                // Valid trigger, modify charges.
                if (trigger_animation.decrease_charges) {
                    decreaseCharges(trigger_animation.charges);
                } else {
                    increaseCharges(trigger_animation.charges);
                }
            }
        }
    }

    public void onHitsplatApplied(final HitsplatApplied event) {
        if (triggers_hitsplats == null) return;

        System.out.println("CHECK ANIMATION: " + this.animation);

        for (final TriggerHitsplat hitsplat : triggers_hitsplats) {
            // Player check.
            if (hitsplat.self && event.getActor() != client.getLocalPlayer()) continue;

            // Enemy check.
            if (!hitsplat.self && (event.getActor() == client.getLocalPlayer() || event.getHitsplat().isOthers())) continue;

            // Successful hit check.
            if (hitsplat.successful && event.getHitsplat().getAmount() == 0) continue;

            // Equipped check.
            if (hitsplat.equipped && triggers_items != null && equipment != null) {
                boolean equipped = false;
                for (final TriggerItem trigger_item : triggers_items) {
                    if (equipment.contains(trigger_item.item_id)) {
                        equipped = true;
                    }
                }
                if (!equipped) continue;
            }

            // Valid hitsplat, modify charges.
            decreaseCharges(hitsplat.discharges);
        }
    }


    public void onWidgetLoaded(final WidgetLoaded event) {
        if (triggers_widgets == null || config_key == null) return;

        client_thread.invokeLater(() -> {
            for (final TriggerWidget trigger_widget : triggers_widgets) {
                final Widget widget = client.getWidget(trigger_widget.widget_group, trigger_widget.widget_child);
                if (widget == null) continue;

                final Pattern regex = Pattern.compile(trigger_widget.message);
                final String message = widget.getText().replaceAll("<br>", " ");
                final Matcher matcher = regex.matcher(message);
                if (!matcher.find()) continue;


                // Check default "charges" group.
                setCharges(trigger_widget.charges != null
                    // Charges amount is fixed.
                    ? trigger_widget.charges
                    // Charges amount is dynamic and extracted from the message.
                    : Integer.parseInt(matcher.group("charges").replaceAll(",", ""))
                );

                // Check extra matches groups.
                if (extra_groups != null) {
                    for (final String extra_group : extra_groups) {
                        final String extra = matcher.group(extra_group);
                        if (extra != null) setConfiguration(config_key + "_" + extra_group, extra);
                    }
                }
            }
        });
    }

    public void onMenuOptionClicked(final MenuOptionClicked event) {
        this.menu_option = event.getMenuOption();
    }

    private void loadChargesFromConfig() {
        client_thread.invokeLater(() -> {
            if (config_key == null) return;
            this.charges = Integer.parseInt(configs.getConfiguration(ChargesImprovedConfig.group, config_key));
        });
    }

    private void setCharges(final int charges) {
        if (config_key == null) return;
        this.charges = charges;
        setConfiguration(config_key, charges);
    }

    private void decreaseCharges(final int charges) {
        if (this.charges - charges < 0) return;
        setCharges(this.charges - charges);
    }

    private void increaseCharges(final int charges) {
        if (this.charges == -1) return;
        setCharges(this.charges + charges);
    }

    private void setConfiguration(final String key, @Nonnull final String value) {
        configs.setConfiguration(ChargesImprovedConfig.group, key, value);
    }

    private void setConfiguration(final String key, final int value) {
        configs.setConfiguration(ChargesImprovedConfig.group, key, value);
    }
}

