package com.dhrescue.networking;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S: client tells server it has finished sending all blocks for a chunk. */
public record ChunkDonePayload(int chunkX, int chunkZ) implements CustomPayload {
    public static final CustomPayload.Id<ChunkDonePayload> ID =
            new CustomPayload.Id<>(Identifier.of("dhrescue", "chunk_done"));

    public static final PacketCodec<RegistryByteBuf, ChunkDonePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER, ChunkDonePayload::chunkX,
            PacketCodecs.INTEGER, ChunkDonePayload::chunkZ,
            ChunkDonePayload::new
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
