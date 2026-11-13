package com.maitreyoshi.mcavie.phone.client;

/**
 * Outils de cadrage des photos. (En 26.x l'objet photo n'a plus de rendu spécial : icône fixe pour l'instant.)
 */
public final class PhotoItemRenderer {
    private PhotoItemRenderer() {
    }

    public static float[] cover(PhonePhotos.Picture picture, float target) {
        float ratio = picture.width() / (float) picture.height();
        if (ratio > target) {
            float span = target / ratio;
            return new float[]{(1 - span) / 2, 0, (1 + span) / 2, 1};
        }
        float span = ratio / target;
        return new float[]{0, (1 - span) / 2, 1, (1 + span) / 2};
    }
}
