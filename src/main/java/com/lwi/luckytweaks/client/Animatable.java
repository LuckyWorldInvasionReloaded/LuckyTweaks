package com.lwi.luckytweaks.client;

/**
 * A critically-damped spring that eases {@link #currentValue} toward {@link #targetValue} over time --
 * used for the smooth HUD lift when the name plaques appear. Ported from Player Locator Plus, which
 * adapted it from the Android Open Source Project (Apache-2.0).
 */
final class Animatable {
    /** Stiffness of the spring; higher snaps faster. */
    private static final float NATURAL_FREQ = 120f;

    public float targetValue;
    public float currentValue;

    private float lastDisplacement = 0f;
    private float lastVelocity = 0f;

    Animatable(float initialValue) {
        this.targetValue = initialValue;
        this.currentValue = initialValue;
    }

    /** Advance the spring by {@code timeElapsedMs} milliseconds. */
    void update(float timeElapsedMs) {
        double adjustedDisplacement = lastDisplacement - targetValue;
        double deltaT = timeElapsedMs / 1000.0;     // seconds

        double coeffA = adjustedDisplacement;
        double coeffB = lastVelocity + NATURAL_FREQ * adjustedDisplacement;
        double nFdT = -NATURAL_FREQ * deltaT;
        double expTerm = Math.exp(nFdT);

        double displacement = (coeffA + coeffB * deltaT) * expTerm;
        double currentVelocity = (coeffA + coeffB * deltaT) * expTerm * (-NATURAL_FREQ) + coeffB * expTerm;

        float newValue = (float) (displacement + targetValue);
        lastDisplacement = newValue;
        lastVelocity = (float) currentVelocity;
        currentValue = newValue;
    }
}
