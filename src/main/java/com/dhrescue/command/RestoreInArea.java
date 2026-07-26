package com.dhrescue.command;

import com.dhrescue.networking.ChunkDonePayload;
import com.dhrescue.networking.DHRescueNetworking;
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

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;

public class RestoreInArea {

    private static final int THREAD_COUNT = 10;
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
                        if (mc == null) { System.out.println("[DHRescue] No MC"); return 0; }
                        ClientPlayerEntity player = mc.player;
                        if (player == null) { System.out.println("[DHRescue] No Client"); return 0; }
                        if (!DhApi.Delayed.worldProxy.worldLoaded()) { player.sendMessage(Text.of("[DHRescue] No World loaded"), false); return 0; }
                        IDhApiLevelWrapper levelWrapper = DhApi.Delayed.worldProxy.getSinglePlayerLevel();
                        if (levelWrapper == null) { player.sendMessage(Text.of("[DHRescue] No Level Wrapper"), false); return 0; }

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

                        // Build the list of chunk coords that overlap the requested area
                        int chunkMinX = minX >> 4;
                        int chunkMinZ = minZ >> 4;
                        int chunkMaxX = (maxX - 1) >> 4;
                        int chunkMaxZ = (maxZ - 1) >> 4;
                        int totalChunks = (chunkMaxX - chunkMinX + 1) * (chunkMaxZ - chunkMinZ + 1);

                        player.sendMessage(Text.of("[DHRescue] Starting: " + totalChunks + " chunks, " + THREAD_COUNT + " threads..."), false);
                        isRunning.set(true);

                        // Shared queue of chunks to process
                        BlockingQueue<int[]> chunkQueue = new LinkedBlockingQueue<>();
                        for (int cx = chunkMinX; cx <= chunkMaxX; cx++)
                            for (int cz = chunkMinZ; cz <= chunkMaxZ; cz++)
                                chunkQueue.add(new int[]{cx, cz});

                        AtomicInteger doneChunks = new AtomicInteger(0);
                        AtomicInteger lastReportedPct = new AtomicInteger(-1);

                        ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT, r -> {
                            Thread t = new Thread(r, "DHRescue-Worker");
                            t.setDaemon(true);
                            return t;
                        });

                        for (int i = 0; i < THREAD_COUNT; i++) {
                            pool.submit(() -> {
                                // Each thread gets its own DH cache
                                IDhApiTerrainDataCache cache = DhApi.Delayed.terrainRepo.createSoftCache();

                                while (true) {
                                    int[] chunk = chunkQueue.poll();
                                    if (chunk == null) break; // queue empty, thread is done

                                    int cx = chunk[0], cz = chunk[1];

                                    // --- 1. Collect blocks for this chunk, sorted bottom-to-top ---
                                    // Placing bottom-to-top means supporting blocks are in place before
                                    // blocks like sand are placed on top, preventing unnecessary falls.
                                    int bx0 = cx << 4, bz0 = cz << 4;
                                    List<SetBlockPayload> blocks = new ArrayList<>();

                                    for (int bx = bx0; bx < bx0 + 16; bx++) {
                                        if (bx < minX || bx >= maxX) continue;
                                        for (int bz = bz0; bz < bz0 + 16; bz++) {
                                            if (bz < minZ || bz >= maxZ) continue;

                                            DhApiResult<DhApiTerrainDataPoint[]> col =
                                                    DhApi.Delayed.terrainRepo.getColumnDataAtBlockPos(levelWrapper, bx, bz, cache);
                                            if (col == null || col.payload == null) continue;

                                            // DH returns columns top-to-bottom; clone and sort ascending (bottom first)
                                            DhApiTerrainDataPoint[] sorted = col.payload.clone();
                                            Arrays.sort(sorted, Comparator.comparingInt(dp -> dp.bottomYBlockPos));

                                            for (DhApiTerrainDataPoint dp : sorted) {
                                                if (dp == null || dp.blockStateWrapper == null) continue;
                                                String bID = dp.blockStateWrapper.getSerialString();
                                                int yStart = Math.max(dp.bottomYBlockPos, minY);
                                                int yEnd   = Math.min(dp.topYBlockPos,    maxY - 1);
                                                for (int y = yStart; y <= yEnd; y++)
                                                    blocks.add(new SetBlockPayload(new BlockPos(bx, y, bz), bID));
                                            }
                                        }
                                    }

                                    if (blocks.isEmpty()) {
                                        // Nothing to place — count as done immediately
                                        updateProgress(mc, doneChunks, lastReportedPct, totalChunks);
                                        continue;
                                    }

                                    // --- 2. Register latch BEFORE sending anything ---
                                    long key = DHRescueNetworking.chunkKey(cx, cz);
                                    CountDownLatch latch = new CountDownLatch(1);
                                    DHRescueNetworking.pendingChunks.put(key, latch);

                                    // --- 3. Send all blocks + ChunkDone atomically on the main thread ---
                                    // Submitting as a single Runnable ensures no other chunk's packets interleave.
                                    final int fcx = cx, fcz = cz;
                                    mc.execute(() -> {
                                        for (SetBlockPayload p : blocks)
                                            ClientPlayNetworking.send(p);
                                        // ChunkDone is queued AFTER all blocks on the same connection,
                                        // so the server will have processed all blocks before the ack.
                                        ClientPlayNetworking.send(new ChunkDonePayload(fcx, fcz));
                                    });

                                    // --- 4. Wait for server ack before moving to the next chunk ---
                                    try {
                                        boolean acked = latch.await(30, TimeUnit.SECONDS);
                                        if (!acked && mc.player != null)
                                            mc.player.sendMessage(
                                                Text.of("[DHRescue] Warning: chunk (" + fcx + "," + fcz + ") timed out — server may be lagging"), false);
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                        break;
                                    } finally {
                                        DHRescueNetworking.pendingChunks.remove(key);
                                    }

                                    // --- 5. Report progress ---
                                    updateProgress(mc, doneChunks, lastReportedPct, totalChunks);
                                }
                            });
                        }

                        // Watcher thread: waits for all workers then announces completion
                        Thread watcher = new Thread(() -> {
                            pool.shutdown();
                            try { pool.awaitTermination(1, TimeUnit.HOURS); } catch (InterruptedException ignored) {}
                            isRunning.set(false);
                            if (mc.player != null)
                                mc.player.sendMessage(Text.of("[DHRescue] Done! All " + totalChunks + " chunks processed."), false);
                        }, "DHRescue-Watcher");
                        watcher.setDaemon(true);
                        watcher.start();

                        return 1;
                    }))))))));
        });
    }

    private static void updateProgress(MinecraftClient mc, AtomicInteger doneChunks,
                                       AtomicInteger lastReportedPct, int totalChunks) {
        int done = doneChunks.incrementAndGet();
        if (totalChunks == 0) return;
        int pct = done * 100 / totalChunks;
        int last = lastReportedPct.get();
        // Report every 5%
        if (pct >= last + 5 && lastReportedPct.compareAndSet(last, pct)) {
            if (mc.player != null)
                mc.player.sendMessage(Text.of("[DHRescue] " + done + "/" + totalChunks + " chunks (" + pct + "%)"), false);
        }
    }
}

