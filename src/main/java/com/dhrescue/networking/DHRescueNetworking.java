package com.dhrescue.networking;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.block.IDhApiBlockStateWrapper;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;


public class DHRescueNetworking {
    public static final Logger LOGGER = LoggerFactory.getLogger("dhrescue_network");
    public static final Identifier SET_BLOCK_PACKET_ID = Identifier.of("dhrescue", "set_block");

    /** Latches keyed by chunk coords — worker threads wait on these until the server acks the chunk. */
    public static final ConcurrentHashMap<Long, CountDownLatch> pendingChunks = new ConcurrentHashMap<>();

    /** Encode a chunk X/Z pair into a single long key. */
    public static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    public static void register() {
        // C2S
        PayloadTypeRegistry.playC2S().register(SetBlockPayload.ID, SetBlockPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ChunkDonePayload.ID, ChunkDonePayload.CODEC);
        // S2C
        PayloadTypeRegistry.playS2C().register(ChunkAckPayload.ID, ChunkAckPayload.CODEC);
    }

    /** Freeze server ticks directly via the tick manager. Safe to call on the server thread. */
    public static void freezeTicks(MinecraftServer server) {
        server.getTickManager().setFrozen(true);
        LOGGER.info("[DHRescue] Ticks frozen.");
    }

    /** Call this only on the client side. */
    public static void registerClient() {
        // When the server acks a chunk, release the waiting worker thread.
        ClientPlayNetworking.registerGlobalReceiver(ChunkAckPayload.ID, (payload, context) -> {
            long key = chunkKey(payload.chunkX(), payload.chunkZ());
            CountDownLatch latch = pendingChunks.get(key);
            if (latch != null) latch.countDown();
        });
    }

    public static void register_SV() {
        LOGGER.info("SV registered!");

        // Place a single block — no neighbor notifications (flag 1), no physics cascade.
        // Flags: 2 = NOTIFY_LISTENERS (clients see it), 16 = FORCE_STATE (bypass validity checks).
        ServerPlayNetworking.registerGlobalReceiver(SetBlockPayload.ID, (payload, context) -> {
            MinecraftServer server = context.server();
            server.execute(() -> {
                BlockPos thePos = payload.blockPos();
                String theID = payload.blockID();

                IDhApiLevelWrapper levelWrapper = DhApi.Delayed.worldProxy.getSinglePlayerLevel();
                if (levelWrapper == null) return;

                try {
                    IDhApiBlockStateWrapper blockStateDh = DhApi.Delayed.wrapperFactory.getDefaultBlockStateWrapper(theID, levelWrapper);
                    BlockState bState = (BlockState) blockStateDh.getWrappedMcObject();
                    server.getOverworld().setBlockState(thePos, bState, 2 | 16);
                } catch (Exception e) {
                    // silently skip blocks that fail to place
                }
            });
        });

        // Once all blocks for a chunk have been sent, the client sends ChunkDone.
        // We reply with ChunkAck so the client knows placement is complete.
        ServerPlayNetworking.registerGlobalReceiver(ChunkDonePayload.ID, (payload, context) -> {
            MinecraftServer server = context.server();
            server.execute(() -> {
                // All preceding block packets are guaranteed to have been queued before this
                // (same connection, in-order delivery). Reply with ack.
                ServerPlayNetworking.send(context.player(), new ChunkAckPayload(payload.chunkX(), payload.chunkZ()));
            });
        });
    }
}
