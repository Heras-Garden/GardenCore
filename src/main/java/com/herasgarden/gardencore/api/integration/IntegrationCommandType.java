package com.herasgarden.gardencore.api.integration;

/** Commands written by trusted external integrations such as Iris and consumed by Garden plugins. */
public enum IntegrationCommandType {
    MODERATION_WARN,
    MODERATION_MUTE,
    MODERATION_UNMUTE,
    MODERATION_KICK,
    MODERATION_TEMPBAN,
    MODERATION_BAN,
    MODERATION_UNBAN
}
