package com.herasgarden.gardencore.api.cosmetic;

public record CosmeticChatProfile(
        boolean donorTagEnabled,
        String nameHex,
        String fontKey
) {
    public static CosmeticChatProfile empty() {
        return new CosmeticChatProfile(false, null, null);
    }
}
