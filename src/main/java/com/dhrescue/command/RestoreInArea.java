package com.dhrescue.command;

import com.dhrescue.networking.SetBlockPayload;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.data.IDhApiTerrainDataCache;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.objects.DhApiResult;
import com.seibel.distanthorizons.api.objects.data.DhApiTerrainDataPoint;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;

public class RestoreInArea {

    // Packets to send per tick to avoid flooding the server
    private static final int PACKETS_PER_TICK = 500;
    private static final AtomicBoolean isRunning = new AtomicBoolean(false);

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("dhrescue_restorearea")
                    .then(argument("minXIn", IntegerArgumentType.integer())
                    .then(argument("minYIn", IntegerArgumentType.integer())
                    .then(argument("minZIn", IntegerArgumentType.integer())

                    .then(argument("maxXIn", IntegerArgumentType.integer())
                    .then(argument("maxYIn", IntegerArgumentType.integer())
                    .then(argument("maxZIn", IntegerArgumentType.integer())
                    .executes(context -> {
                        MinecraftClient mc = MinecraftClient.getInstance();
                        if (mc == null) { System.out.println("No MC"); return 0; }

                        ClientPlayerEntity player = mc.player;
                        if (player == null) { System.out.println("No Client"); return 0; }

                        if (!DhApi.Delayed.worldProxy.worldLoaded()) { System.out.println("No World loaded"); return 0; }

                        IDhApiLevelWrapper levelWrapper = DhApi.Delayed.worldProxy.getSinglePlayerLevel();
                        if (levelWrapper == null) { System.out.println("No Level Wrapper"); return 0; }

                        if (isRunning.get()) {
                            player.sendMessage(Text.of("[DHRescue] Already running! Wait for it to finish."), false);
                            return 0;
                        }

                        int minX = Math.min(IntegerArgumentType.getInteger(context, "minXIn"), IntegerArgumentType.getInteger(context, "maxXIn"));
                        int minY = Math.min(IntegerArgumentType.getInteger(context, "minYIn"), IntegerArgumentType.getInteger(context, "maxYIn"));
                        int minZ = Math.min(IntegerArgumentType.getInteger(context, "minZIn"), IntegerArgumentType.getInteger(context, "maxZIn"));
                        int maxX = Math.max(IntegerArgumentType.getInteger(context, "minXIn"), IntegerArgumentType.getInteger(context, "maxXIn"));
                        int maxY = Math.max(IntegerArgumentType.getInteger(context, "minYIn"), IntegerArgumentType.getInteger(context, "maxYIn"));
                        int maxZ = Math.max(IntegerArgumentType.getInteger(context, "minZIn"), IntegerArgumentType.getInteger(context, "maxZIn"));

                        player.sendMessage(Text.of("[DHRescue] Starting restore from (" + minX + "," + minY + "," + minZ + ") to (" + maxX + "," + maxY + "," + maxZ + ")..."), false);

                        isRunning.set(true);

                        // Run data collection on a background thread so the render thread doesn't freeze
                        Thread worker = new Thread(() -> {
                            try {
                                IDhApiTerrainDataCache terrainCache = DhApi.Delayed.terrainRepo.createSoftCache();
                                List<SetBlockPayload> payloads = new ArrayList<>();

                                // Use column queries (X/Z loop only) — DH stores data per column, not per block Y
                                for (int x = minX; x < maxX; x++) {
                                    for (int z = minZ; z < maxZ; z++) {
                                        DhApiResult<DhApiTerrainDataPoint[]> colResult =
                                            DhApi.Delayed.terrainRepo.getColumnDataAtBlockPos(levelWrapper, x, z, terrainCache);

                                        if (colResult == null || colResult.payload == null) continue;

                                        for (DhApiTerrainDataPoint dp : colResult.payload) {
                                            if (dp == null || dp.blockStateWrapper == null) continue;
                                            String bID = dp.blockStateWrapper.getSerialString();
                                            // Fill every Y in the range this datapoint covers
                                            int yStart = Math.max(dp.bottomYBlockPos, minY);
                                            int yEnd = Math.min(dp.topYBlockPos, maxY - 1);
                                            for (int y = yStart; y <= yEnd; y++) {
                                                payloads.add(new SetBlockPayload(new BlockPos(x, y, z), bID));
                                            }
                                        }
                                    }
                                }

                                int total = payloads.size();
                                mc.player.sendMessage(Text.of("[DHRescue] Found " + total + " blocks. Sending..."), false);

                                // Send packets in batches on the main thread each tick to avoid freezing
                                final int[] index = {0};
                                mc.execute(new Runnable() {
                                    @Override
                                    public void run() {
                                        int sent = 0;
                                        while (index[0] < payloads.size() && sent < PACKETS_PER_TICK) {
                                            ClientPlayNetworking.send(payloads.get(index[0]));
                                            index[0]++;
                                            sent++;
                                        }
                                        if (index[0] < payloads.size()) {
                                            // Schedule next batch next tick
                                            mc.execute(this);
                                        } else {
                                            isRunning.set(false);
                                            if (mc.player != null)
                                                mc.player.sendMessage(Text.of("[DHRescue] Done! Sent " + total + " blocks."), false);
                                        }
                                    }
                                });

                            } catch (Exception e) {
                                isRunning.set(false);
                                e.printStackTrace();
                                if (mc.player != null)
                                    mc.player.sendMessage(Text.of("[DHRescue] Error: " + e.getMessage()), false);
                            }
                        }, "DHRescue-Worker");

                        worker.setDaemon(true);
                        worker.start();

                        return 1;
            }))))))));
        });
    }
}
