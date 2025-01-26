package com.example.cwstataccelerator;

import java.util.Random;

public class GreenNoiseGenerator {

    private final Random random = new Random();
    private final int sampleRate;
    private double lowPassFilterState;

    public GreenNoiseGenerator(int sampleRate) {
        this.sampleRate = sampleRate;
        this.lowPassFilterState = 0;
    }

    /**
     * Generates a single sample of green noise.
     *
     * @return A double representing the amplitude of the green noise sample.
     */
    public double generateSample() {
        // Generate white noise
        double whiteNoise = random.nextGaussian();

        // Apply a low-pass filter to create green noise
        double smoothingFactor = 1.0 / (sampleRate * 0.01); // Adjust this factor for a realistic frequency response
        lowPassFilterState += smoothingFactor * (whiteNoise - lowPassFilterState);

        return lowPassFilterState;
    }
}
