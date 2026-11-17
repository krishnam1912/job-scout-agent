package dev.phoenix.domain;

public record Referral(String name, String connection, String company, String jobRef, String status,
                       String lastContact, String followUp, String note) {
}
