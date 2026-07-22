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
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;

public class RestoreInArea {

    private static final int THREADS = 10;
    // Base blocks sent per tick — multiplied by speedMultiplier
    private static final int BLOCKS_PER_TICK = 256;
    // How many ticks to wait between batches (server catch-up time)
    private static final int TICKS_BETWEEN_BATCHES = 2;
    // Adjustable speed multiplier, changed via /dhrescue_speed
    private static volatile int speedMultiplier = 1;

    private static final AtomicBoolean isRunning = new AtomicBoolean(false);

    // Shared send queue drained by the tick event
    private static final ConcurrentLinkedQueue<SetBlockPayload> sendQueue = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger totalQueued = new AtomicInteger(0);
    private static final AtomicInteger totalSent = new AtomicInteger(0);
    private static final AtomicBoolean collectingDone = new AtomicBoolean(false);
    private static int tickCooldown = 0;

    public static void register() {
        // Tick event drains the send queue in small batches, waiting TICKS_BETWEEN_BATCHES ticks between each
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!isRunning.get()) return;

            if (tickCooldown > 0) {
                tickCooldown--;
                return;
            }

            int sent = 0;
            int limit = BLOCKS_PER_TICK * speedMultiplier;
            while (sent < limit && !sendQueue.isEmpty()) {
                SetBlockPayload payload = sendQueue.poll();
                if (payload == null) break;
                ClientPlayNetworking.send(payload);
                sent++;
                totalSent.incrementAndGet();
            }

            if (sent > 0) {
                tickCooldown = TICKS_BETWEEN_BATCHES;
            }

            // Report progress every ~5 seconds (100 ticks)
            int sent2 = totalSent.get();
            int queued = totalQueued.get();
            if (queued > 0 && sent2 % (BLOCKS_PER_TICK * 20) < BLOCKS_PER_TICK) {
                int pct = (int)((sent2 / (float) queued) * 100);
                if (client.player != null)
                    client.player.sendMessage(Text.of("[DHRescue] " + sent2 + " / " + queued + " blocks sent (" + pct + "%)"), true);
            }

            // Done when queue empty and all collectors finished
            if (sendQueue.isEmpty() && collectingDone.get()) {
                isRunning.set(false);
                if (client.player != null)
                    client.player.sendMessage(Text.of("[DHRescue] Done! Placed " + totalSent.get() + " blocks."), false);
            }
        });

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

                        // Build chunk list (each chunk is 16x16 block columns)
                        int chunkMinX = Math.floorDiv(minX, 16);
                        int chunkMinZ = Math.floorDiv(minZ, 16);
                        int chunkMaxX = Math.floorDiv(maxX - 1, 16);
                        int chunkMaxZ = Math.floorDiv(maxZ - 1, 16);

                        int totalChunks = (chunkMaxX - chunkMinX + 1) * (chunkMaxZ - chunkMinZ + 1);

                        // Reset state
                        sendQueue.clear();
                        totalQueued.set(0);
                        totalSent.set(0);
                        collectingDone.set(false);
                        tickCooldown = 0;
                        isRunning.set(true);

                        player.sendMessage(Text.of("[DHRescue] Starting restore — " + totalChunks + " chunks across " + THREADS + " threads..."), false);

                        // Fill a work queue with chunk coordinates
                        ConcurrentLinkedQueue<int[]> chunkWork = new ConcurrentLinkedQueue<>();
                        for (int cx = chunkMinX; cx <= chunkMaxX; cx++)
                            for (int cz = chunkMinZ; cz <= chunkMaxZ; cz++)
                                chunkWork.add(new int[]{cx, cz});

                        AtomicInteger chunksProcessed = new AtomicInteger(0);
                        CountDownLatch latch = new CountDownLatch(THREADS);

                        ExecutorService pool = Executors.newFixedThreadPool(THREADS, r -> {
                            Thread t = new Thread(r, "DHRescue-Worker");
                            t.setDaemon(true);
                            return t;
                        });

                        for (int i = 0; i < THREADS; i++) {
                            pool.submit(() -> {
                                try {
                                    IDhApiTerrainDataCache cache = DhApi.Delayed.terrainRepo.createSoftCache();

                                    int[] chunk;
                                    while ((chunk = chunkWork.poll()) != null) {
                                        int cxStart = chunk[0] * 16;
                                        int czStart = chunk[1] * 16;

                                        for (int x = Math.max(cxStart, minX); x < Math.min(cxStart + 16, maxX); x++) {
                                            for (int z = Math.max(czStart, minZ); z < Math.min(czStart + 16, maxZ); z++) {
                                                DhApiResult<DhApiTerrainDataPoint[]> col =
                                                    DhApi.Delayed.terrainRepo.getColumnDataAtBlockPos(levelWrapper, x, z, cache);
                                                if (col == null || col.payload == null) continue;

                                                for (DhApiTerrainDataPoint dp : col.payload) {
                                                    if (dp == null || dp.blockStateWrapper == null) continue;
                                                    int yStart = Math.max(dp.bottomYBlockPos, minY);
                                                    int yEnd = Math.min(dp.topYBlockPos, maxY - 1);
                                                    if (yStart > yEnd) continue;
                                                    String bID = dp.blockStateWrapper.getSerialString();
                                                    for (int y = yStart; y <= yEnd; y++) {
                                                        sendQueue.add(new SetBlockPayload(new BlockPos(x, y, z), bID));
                                                        totalQueued.incrementAndGet();
                                                    }
                                                }
                                            }
                                        }

                                        int done = chunksProcessed.incrementAndGet();
                                        if (done % 50 == 0 || done == totalChunks) {
                                            int pct = (int)((done / (float) totalChunks) * 100);
                                            mc.execute(() -> {
                                                if (mc.player != null)
                                                    mc.player.sendMessage(Text.of("[DHRescue] Collecting: " + done + "/" + totalChunks + " chunks (" + pct + "%)"), true);
                                            });
                                        }
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                } finally {
                                    latch.countDown();
                                }
                            });
                        }

                        // Wait for all workers then signal done
                        Thread watcher = new Thread(() -> {
                            try { latch.await(); } catch (InterruptedException ignored) {}
                            pool.shutdown();
                            collectingDone.set(true);
                            mc.execute(() -> {
                                if (mc.player != null)
                                    mc.player.sendMessage(Text.of("[DHRescue] Collection done — " + totalQueued.get() + " blocks queued to send."), false);
                            });
                        }, "DHRescue-Watcher");
                        watcher.setDaemon(true);
                        watcher.start();

                        return 1;
                })))))))
            );
        });
    }
}

