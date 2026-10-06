package com.BoiteNoire.model.payloads;

public record PaymentPayload(
    double amount,
    String currency, //could they be enum ?
    String plan,
    String status
) {
}  