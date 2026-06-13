package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

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

    public OreMiner() {
        super(AddonTemplate.CATEGORY, "ore-miner", "Automatically mines all ores, crafts them into slabs, and stores them in shulkers.");
    }

    @Override
    public void onActivate() {
        isMining = autoStartMining.get();
        inventoryFull = false;
        tickCounter = 0;
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

        if (!isMining) return;

        // Check if inventory is full
        if (isInventoryFull()) {
            inventoryFull = true;
            isMining = false;
            craftAndStore();
            return;
        }

        // Continue mining
        if (mc.player != null) {
            mc.player.swing(mc.player.getUsedItemHand());
        }
    }

    private boolean isInventoryFull() {
        if (mc.player == null) return false;
        
        // Count empty slots (excluding offhand)
        int emptySlots = 0;
        for (int i = 0; i < 35; i++) {
            if (mc.player.containerMenu.getSlot(i).getItem().isEmpty()) {
                emptySlots++;
            }
        }
        
        return emptySlots == 0;
    }

    private void craftAndStore() {
        // Step 1: Use /craft command
        sendCommand("/craft");
        
        // Step 2: Wait and then use shulkers
        if (autoShulker.get()) {
            scheduleShulkerSwap();
        }
    }

    private void scheduleShulkerSwap() {
        if (mc.player == null) return;

        // Delay before opening shulker
        new Thread(() -> {
            try {
                Thread.sleep(1000);
                
                // Take shulker from slot 8 (9th slot)
                if (mc.player != null) {
                    mc.player.containerMenu.clicked(8, 0, ClickType.PICKUP, mc.player);
                    Thread.sleep(100);
                    
                    // Place shulker on inventory slot 0
                    mc.player.containerMenu.clicked(0, 0, ClickType.PICKUP, mc.player);
                    Thread.sleep(100);
                    
                    // Take shulker from slot 9 if needed
                    mc.player.containerMenu.clicked(9, 0, ClickType.PICKUP, mc.player);
                    Thread.sleep(100);
                    mc.player.containerMenu.clicked(1, 0, ClickType.PICKUP, mc.player);
                    Thread.sleep(100);
                    
                    // Cover remaining inventory with slabs
                    coverInventoryWithSlabs();
                }
            } catch (InterruptedException e) {
                AddonTemplate.LOG.error("Shulker swap interrupted", e);
            }
        }).start();
    }

    private void coverInventoryWithSlabs() {
        if (mc.player == null) return;

        try {
            // Find slab item stack
            int slabSlot = -1;
            for (int i = 0; i < 35; i++) {
                if (isSlabItem(mc.player.containerMenu.getSlot(i).getItem())) {
                    slabSlot = i;
                    break;
                }
            }

            if (slabSlot == -1) return;

            // Cover inventory slots with slabs, leaving one slab
            int slabCount = 0;
            for (int i = 2; i < 35; i++) {
                if (mc.player.containerMenu.getSlot(i).getItem().isEmpty()) {
                    // Click on slab slot to pick it up
                    mc.player.containerMenu.clicked(slabSlot, 0, ClickType.PICKUP, mc.player);
                    Thread.sleep(50);
                    
                    // Click on target slot to place slab
                    mc.player.containerMenu.clicked(i, 0, ClickType.PICKUP, mc.player);
                    Thread.sleep(50);
                    
                    slabCount++;
                    
                    // Leave at least 1 slab
                    if (slabCount >= 33) break;
                }
            }
        } catch (InterruptedException e) {
            AddonTemplate.LOG.error("Cover inventory interrupted", e);
        }
    }

    private boolean isSlabItem(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) return false;
        String name = stack.getItem().toString();
        return name.contains("slab");
    }

    private void sendCommand(String command) {
        if (mc.player == null) return;
        
        // Send command to chat
        mc.player.connection.sendChat(command);
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        this.toggle();
    }
}
