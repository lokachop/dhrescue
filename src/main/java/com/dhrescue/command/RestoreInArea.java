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

            // --- /dhrescue_restoreradius <radiusChunks> ---
            dispatcher.register(ClientCommandManager.literal("dhrescue_restoreradius")
                    .then(argument("radiusChunks", IntegerArgumentType.integer(0))
                    .executes(context -> {
                        MinecraftClient mc = MinecraftClient.getInstance();
                        if (mc == null || mc.player == null) { System.out.println("[DHRescue] No player"); return 0; }
                        if (!DhApi.Delayed.worldProxy.worldLoaded()) { mc.player.sendMessage(Text.of("[DHRescue] No World loaded"), false); return 0; }
                        IDhApiLevelWrapper levelWrapper = DhApi.Delayed.worldProxy.getSinglePlayerLevel();
                        if (levelWrapper == null) { mc.player.sendMessage(Text.of("[DHRescue] No Level Wrapper"), false); return 0; }
                        if (isRunning.get()) { mc.player.sendMessage(Text.of("[DHRescue] Already running!"), false); return 0; }

                        int radius = IntegerArgumentType.getInteger(context, "radiusChunks");
                        BlockPos pos = mc.player.getBlockPos();
                        int playerCX = pos.getX() >> 4;
                        int playerCZ = pos.getZ() >> 4;

                        int minX = (playerCX - radius) << 4;
                        int maxX = (playerCX + radius + 1) << 4;
                        int minZ = (playerCZ - radius) << 4;
                        int maxZ = (playerCZ + radius + 1) << 4;

                        mc.player.sendMessage(Text.of(
                            "[DHRescue] Restoring " + (radius * 2 + 1) + "x" + (radius * 2 + 1) +
                            " chunks around chunk (" + playerCX + "," + playerCZ + "), Y 0-400..."), false);
                        runRestore(mc, levelWrapper, minX, 0, minZ, maxX, 400, maxZ);
                        return 1;
                    })));

            // --- /dhrescue_restorearea <minX> <minY> <minZ> <maxX> <maxY> <maxZ> ---
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
                        if (isRunning.get()) { player.sendMessage(Text.of("[DHRescue] Already running! Wait for it to finish."), false); return 0; }

                        int minX = Math.min(IntegerArgumentType.getInteger(context, "minXIn"), IntegerArgumentType.getInteger(context, "maxXIn"));
                        int minY = Math.min(IntegerArgumentType.getInteger(context, "minYIn"), IntegerArgumentType.getInteger(context, "maxYIn"));
                        int minZ = Math.min(IntegerArgumentType.getInteger(context, "minZIn"), IntegerArgumentType.getInteger(context, "maxZIn"));
                        int maxX = Math.max(IntegerArgumentType.getInteger(context, "minXIn"), IntegerArgumentType.getInteger(context, "maxXIn"));
                        int maxY = Math.max(IntegerArgumentType.getInteger(context, "minYIn"), IntegerArgumentType.getInteger(context, "maxYIn"));
                        int maxZ = Math.max(IntegerArgumentType.getInteger(context, "minZIn"), IntegerArgumentType.getInteger(context, "maxZIn"));

                        player.sendMessage(Text.of("[DHRescue] Starting restore from (" + minX + "," + minY + "," + minZ + ") to (" + maxX + "," + maxY + "," + maxZ + ")..."), false);
                        runRestore(mc, levelWrapper, minX, minY, minZ, maxX, maxY, maxZ);
                        return 1;
                    }))))))));
        });
    }

    private static void runRestore(MinecraftClient mc, IDhApiLevelWrapper levelWrapper,
                                   int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        isRunning.set(true);

        // Freeze ticks directly via the server tick manager before any blocks are placed
        mc.execute(() -> {
            if (mc.getServer() != null) {
                DHRescueNetworking.freezeTicks(mc.getServer());
                if (mc.player != null)
                    mc.player.sendMessage(net.minecraft.text.Text.of("[DHRescue] Ticks frozen. Run /tick unfreeze when done."), false);
            }
        });

        int chunkMinX = minX >> 4;
        int chunkMinZ = minZ >> 4;
        int chunkMaxX = (maxX - 1) >> 4;
        int chunkMaxZ = (maxZ - 1) >> 4;
        int totalChunks = (chunkMaxX - chunkMinX + 1) * (chunkMaxZ - chunkMinZ + 1);

        if (mc.player != null)
            mc.player.sendMessage(Text.of("[DHRescue] " + totalChunks + " chunks queued across " + THREAD_COUNT + " threads."), false);

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
                IDhApiTerrainDataCache cache = DhApi.Delayed.terrainRepo.createSoftCache();

                while (true) {
                    int[] chunk = chunkQueue.poll();
                    if (chunk == null) break;

                    int cx = chunk[0], cz = chunk[1];
                    int bx0 = cx << 4, bz0 = cz << 4;
                    List<SetBlockPayload> blocks = new ArrayList<>();

                    for (int bx = bx0; bx < bx0 + 16; bx++) {
                        if (bx < minX || bx >= maxX) continue;
                        for (int bz = bz0; bz < bz0 + 16; bz++) {
                            if (bz < minZ || bz >= maxZ) continue;

                            DhApiResult<DhApiTerrainDataPoint[]> col =
                                    DhApi.Delayed.terrainRepo.getColumnDataAtBlockPos(levelWrapper, bx, bz, cache);
                            if (col == null || col.payload == null) continue;

                            // Sort bottom-to-top so supporting blocks are placed first
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
                        updateProgress(mc, doneChunks, lastReportedPct, totalChunks);
                        continue;
                    }

                    long key = DHRescueNetworking.chunkKey(cx, cz);
                    CountDownLatch latch = new CountDownLatch(1);
                    DHRescueNetworking.pendingChunks.put(key, latch);

                    final int fcx = cx, fcz = cz;
                    mc.execute(() -> {
                        for (SetBlockPayload p : blocks)
                            ClientPlayNetworking.send(p);
                        ClientPlayNetworking.send(new ChunkDonePayload(fcx, fcz));
                    });

                    try {
                        boolean acked = latch.await(30, TimeUnit.SECONDS);
                        if (!acked && mc.player != null)
                            mc.player.sendMessage(Text.of("[DHRescue] Warning: chunk (" + fcx + "," + fcz + ") timed out"), false);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } finally {
                        DHRescueNetworking.pendingChunks.remove(key);
                    }

                    updateProgress(mc, doneChunks, lastReportedPct, totalChunks);
                }
            });
        }

        Thread watcher = new Thread(() -> {
            pool.shutdown();
            try { pool.awaitTermination(1, TimeUnit.HOURS); } catch (InterruptedException ignored) {}
            isRunning.set(false);
            if (mc.player != null)
                mc.player.sendMessage(Text.of("[DHRescue] Done! All " + totalChunks + " chunks processed."), false);
        }, "DHRescue-Watcher");
        watcher.setDaemon(true);
        watcher.start();
    }

    private static void updateProgress(MinecraftClient mc, AtomicInteger doneChunks,
                                       AtomicInteger lastReportedPct, int totalChunks) {
        int done = doneChunks.incrementAndGet();
        if (totalChunks == 0) return;
        int pct = done * 100 / totalChunks;
        int last = lastReportedPct.get();
        if (pct >= last + 5 && lastReportedPct.compareAndSet(last, pct)) {
            if (mc.player != null)
                mc.player.sendMessage(Text.of("[DHRescue] " + done + "/" + totalChunks + " chunks (" + pct + "%)"), false);
        }
    }
}
