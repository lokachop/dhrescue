package com.dhrescue.networking;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** S2C: server acknowledges that it has processed all blocks for a chunk. */
public record ChunkAckPayload(int chunkX, int chunkZ) implements CustomPayload {
    public static final CustomPayload.Id<ChunkAckPayload> ID =
            new CustomPayload.Id<>(Identifier.of("dhrescue", "chunk_ack"));

    public static final PacketCodec<RegistryByteBuf, ChunkAckPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER, ChunkAckPayload::chunkX,
            PacketCodecs.INTEGER, ChunkAckPayload::chunkZ,
            ChunkAckPayload::new
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
