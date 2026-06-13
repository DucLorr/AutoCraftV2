package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Items;

public class OreMiner extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Boolean> autoStartMining = sgGeneral.add(new BooleanSetting.Builder()
        .name("auto-start-mining")
        .description("Automatically start mining ores when enabled.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoShulker = sgGeneral.add(new BooleanSetting.Builder()
        .name("auto-shulker")
        .description("Automatically use shulker boxes from slots 8 and 9.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> tickDelay = sgGeneral.add(new IntSetting.Builder()
        .name("tick-delay")
        .description("Delay between actions in ticks.")
        .defaultValue(2)
        .range(0, 20)
        .build()
    );

    private int tickCounter = 0;
    private boolean isMining = false;
    private boolean inventoryFull = false;
    private int craftingDelay = 0;

    public OreMiner() {
        super(AddonTemplate.CATEGORY, "ore-miner", "Automatically mines all ores, crafts them into slabs, and stores them in shulkers.");
    }

    @Override
    public void onActivate() {
        isMining = autoStartMining.get();
        inventoryFull = false;
        tickCounter = 0;
        craftingDelay = 0;
    }

    @Override
    public void onDeactivate() {
        isMining = false;
        inventoryFull = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        tickCounter++;
        if (tickCounter < tickDelay.get()) return;
        tickCounter = 0;

        if (mc.player == null || mc.player.containerMenu == null) return;

        // Handle crafting delay
        if (craftingDelay > 0) {
            craftingDelay--;
            return;
        }

        // Check if inventory is full
        if (!inventoryFull && isInventoryFull()) {
            inventoryFull = true;
            isMining = false;
            craftAndStore();
            craftingDelay = 60; // 3 seconds delay before next action
            return;
        }

        // Continue mining
        if (isMining && mc.player != null) {
            mc.player.swing(mc.player.getUsedItemHand());
        }
    }

    private boolean isInventoryFull() {
        if (mc.player == null) return false;
        
        // Count empty slots (slots 0-34, excluding offhand)
        int emptySlots = 0;
        for (int i = 0; i < 35; i++) {
            if (mc.player.containerMenu.getSlot(i).getItem().isEmpty()) {
                emptySlots++;
            }
        }
        
        return emptySlots == 0;
    }

    private void craftAndStore() {
        if (mc.player == null) return;

        // Step 1: Send /craft command
        if (mc.player.connection != null) {
            mc.player.connection.sendChat("/craft");
            AddonTemplate.LOG.info("Sent /craft command");
        }
        
        // Step 2: Use shulker boxes after delay
        if (autoShulker.get()) {
            scheduleShulkerSwap();
        }
    }

    private void scheduleShulkerSwap() {
        if (mc.player == null) return;

        new Thread(() -> {
            try {
                Thread.sleep(1500);
                
                if (mc.player != null && mc.player.containerMenu != null) {
                    // Take shulker from slot 8 (index 8)
                    clickSlot(8);
                    Thread.sleep(150);
                    
                    // Place in slot 2
                    clickSlot(2);
                    Thread.sleep(150);
                    
                    // Take shulker from slot 9 (index 9)
                    clickSlot(9);
                    Thread.sleep(150);
                    
                    // Place in slot 3
                    clickSlot(3);
                    Thread.sleep(150);
                    
                    // Cover remaining inventory with slabs
                    coverInventoryWithSlabs();
                    
                    AddonTemplate.LOG.info("Shulker swap completed");
                }
            } catch (InterruptedException e) {
                AddonTemplate.LOG.error("Shulker swap interrupted", e);
            }
        }).start();
    }

    private void clickSlot(int slot) {
        if (mc.player == null || mc.player.containerMenu == null) return;
        
        try {
            mc.player.containerMenu.clicked(slot, 0, net.minecraft.world.inventory.ClickType.PICKUP, mc.player);
        } catch (Exception e) {
            AddonTemplate.LOG.debug("Error clicking slot: " + e.getMessage());
        }
    }

    private void coverInventoryWithSlabs() {
        if (mc.player == null || mc.player.containerMenu == null) return;

        try {
            // Find slab item stack
            int slabSlot = -1;
            for (int i = 0; i < 35; i++) {
                if (isSlabItem(mc.player.containerMenu.getSlot(i).getItem())) {
                    slabSlot = i;
                    break;
                }
            }

            if (slabSlot == -1) {
                AddonTemplate.LOG.warn("No slabs found in inventory");
                return;
            }

            // Cover inventory slots 4-34 with slabs, leaving one slab
            for (int i = 4; i < 35; i++) {
                if (mc.player.containerMenu.getSlot(i).getItem().isEmpty()) {
                    // Pick up slab
                    clickSlot(slabSlot);
                    Thread.sleep(75);
                    
                    // Place slab in target slot
                    clickSlot(i);
                    Thread.sleep(75);
                    
                    // Stop if we've filled most of inventory
                    if (i > 32) break;
                }
            }
            
            AddonTemplate.LOG.info("Inventory covered with slabs");
        } catch (InterruptedException e) {
            AddonTemplate.LOG.error("Cover inventory interrupted", e);
        }
    }

    private boolean isSlabItem(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) return false;
        String name = stack.getItem().toString().toLowerCase();
        return name.contains("slab");
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (this.isActive()) {
            this.toggle();
        }
    }
}
