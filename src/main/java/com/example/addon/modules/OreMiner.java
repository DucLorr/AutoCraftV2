package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class OreMiner extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Double> miningSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("mining-speed")
        .description("Speed of mining actions.")
        .defaultValue(1.0d)
        .range(0.1d, 5.0d)
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
        isMining = true;
        inventoryFull = false;
        tickCounter = 0;
        craftingDelay = 0;
        AddonTemplate.LOG.info("OreMiner activated - mining all ores");
    }

    @Override
    public void onDeactivate() {
        isMining = false;
        inventoryFull = false;
        AddonTemplate.LOG.info("OreMiner deactivated");
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

        // Check if inventory is full (all 35 slots filled)
        if (!inventoryFull && isInventoryFull()) {
            inventoryFull = true;
            isMining = false;
            AddonTemplate.LOG.info("Inventory full - crafting and storing items");
            craftAndStore();
            craftingDelay = 60; // 3 seconds delay
            return;
        }

        // Continue mining
        if (isMining && mc.player != null) {
            mc.player.swing(mc.player.getUsedItemHand());
        }
    }

    private boolean isInventoryFull() {
        if (mc.player == null) return false;
        
        // Count empty slots (slots 0-34)
        int emptySlots = 0;
        for (int i = 0; i < 35; i++) {
            if (mc.player.containerMenu.getSlot(i).getItem().isEmpty()) {
                emptySlots++;
            }
        }
        
        AddonTemplate.LOG.debug("Empty slots: " + emptySlots);
        return emptySlots == 0;
    }

    private void craftAndStore() {
        if (mc.player == null) return;

        // Step 1: Send /craft command
        if (mc.player.connection != null) {
            mc.player.connection.sendChat("/craft");
            AddonTemplate.LOG.info("Sent /craft command");
        }
        
        // Step 2: Use shulker boxes
        scheduleShulkerSwap();
    }

    private void scheduleShulkerSwap() {
        if (mc.player == null) return;

        new Thread(() -> {
            try {
                Thread.sleep(1500);
                
                if (mc.player != null && mc.player.containerMenu != null) {
                    // Take shulker from slot 8
                    clickSlot(8);
                    Thread.sleep(150);
                    
                    // Place in slot 2
                    clickSlot(2);
                    Thread.sleep(150);
                    
                    // Take shulker from slot 9
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
            // Using reflection to avoid import issues with ClickType
            Object clickType = getClickType("PICKUP");
            mc.player.containerMenu.getClass().getMethod("clicked", int.class, int.class, Object.class, Object.class)
                .invoke(mc.player.containerMenu, slot, 0, clickType, mc.player);
        } catch (Exception e) {
            AddonTemplate.LOG.debug("Error clicking slot " + slot + ": " + e.getMessage());
            // Fallback: simple item swap
            mc.player.containerMenu.clicked(slot, 0, null, mc.player);
        }
    }

    private Object getClickType(String name) {
        try {
            Class<?> clickTypeClass = Class.forName("net.minecraft.world.inventory.ClickType");
            return clickTypeClass.getField(name).get(null);
        } catch (Exception e) {
            AddonTemplate.LOG.warn("Could not get ClickType." + name);
            return null;
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

            // Cover slots 4-34 with slabs, leaving at least 1 slab
            int slotsToFill = 0;
            for (int i = 4; i < 35; i++) {
                if (mc.player.containerMenu.getSlot(i).getItem().isEmpty()) {
                    // Pick up slab
                    clickSlot(slabSlot);
                    Thread.sleep(75);
                    
                    // Place slab in target slot
                    clickSlot(i);
                    Thread.sleep(75);
                    
                    slotsToFill++;
                    if (slotsToFill >= 30) break;
                }
            }
            
            AddonTemplate.LOG.info("Covered " + slotsToFill + " inventory slots with slabs");
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
