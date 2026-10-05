package com.pigeon.boitenoire.generator;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Random;

import org.bson.types.ObjectId;

final class ObjectIds {

    private ObjectIds() {
    }

    static ObjectId at(Instant instant, Random random) {
        byte[] bytes = new byte[12];
        ByteBuffer.wrap(bytes).putInt((int) instant.getEpochSecond());
        byte[] tail = new byte[8];
        random.nextBytes(tail);
        System.arraycopy(tail, 0, bytes, 4, 8);
        return new ObjectId(bytes);
    }
}
