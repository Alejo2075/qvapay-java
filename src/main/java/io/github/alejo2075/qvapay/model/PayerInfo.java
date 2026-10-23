package io.github.alejo2075.qvapay.model;

/**
 * The user who paid a transaction (the {@code PaidBy} object).
 *
 * @param uuid payer UUID
 * @param name first name
 * @param username username
 * @param lastname last name
 * @param image avatar path
 * @param vip whether the user is VIP
 * @param kyc KYC verification status as sent by QvaPay (free text, not an enum)
 * @param goldenCheck whether the user has the golden verification
 */
public record PayerInfo(
        String uuid,
        String name,
        String username,
        String lastname,
        String image,
        boolean vip,
        String kyc,
        boolean goldenCheck) {}
