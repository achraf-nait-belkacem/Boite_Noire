package com.BoiteNoire.model.payloads;

public record LoginPayload(
    String ip, //is there another type ?
    String device,
    boolean success
) {
    
}
