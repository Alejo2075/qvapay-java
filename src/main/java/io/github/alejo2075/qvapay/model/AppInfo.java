package io.github.alejo2075.qvapay.model;

import java.time.Instant;

/**
 * Information about the QvaPay application, returned by {@code POST /v2/info}.
 *
 * <p>Useful to check that credentials work. Fields the API omits or sends as {@code null} are
 * {@code null} here.
 *
 * @param uuid unique identifier of the app
 * @param name app name
 * @param url website URL
 * @param desc app description
 * @param callback default webhook URL for notifications
 * @param successUrl redirect URL after a successful payment
 * @param cancelUrl redirect URL after a cancelled payment
 * @param logo logo path
 * @param active whether the app is active
 * @param enabled whether the app is enabled
 * @param card whether the card feature is enabled
 * @param createdAt creation time
 * @param updatedAt last update time
 * @param appPhotoUrl full logo URL
 */
public record AppInfo(
        String uuid,
        String name,
        String url,
        String desc,
        String callback,
        String successUrl,
        String cancelUrl,
        String logo,
        boolean active,
        boolean enabled,
        boolean card,
        Instant createdAt,
        Instant updatedAt,
        String appPhotoUrl) {}
