package com.example.cwstataccelerator;

import java.util.Random;

class BandLimitedPinkNoiseGenerator {

    private static final double LOW_CUTOFF = 200.0; // Low cutoff frequency in Hz
    private static final double HIGH_CUTOFF = 2000.0; // High cutoff frequency in Hz

    private static final int SAMPLE_RATE = 44100;
    private static final int FILTER_ORDER = 2;

    private final Random random;
    private double[] filterCoefficients;
    private double[] filterState;

    public BandLimitedPinkNoiseGenerator(int sampleRate) {
        this.random = new Random();
        calculateFilterCoefficients(sampleRate);
        filterState = new double[FILTER_ORDER];
    }

    public void reset() {
        filterState = new double[FILTER_ORDER]; // Reset filter state to ensure consistency
    }

    public double generateSample() {
        double whiteNoise = random.nextGaussian(); // Generate Gaussian white noise
        double bandLimitedNoise = applyBandPassFilter(whiteNoise);
        return bandLimitedNoise;
    }

    private void calculateFilterCoefficients(int sampleRate) {
        double nyquist = sampleRate / 2.0;
        double lowNormalized = LOW_CUTOFF / nyquist;
        double highNormalized = HIGH_CUTOFF / nyquist;

        double bandwidth = highNormalized - lowNormalized;
        double centerFrequency = (highNormalized + lowNormalized) / 2.0;

        filterCoefficients = designBandPassFilter(centerFrequency, bandwidth);
    }

    private double[] designBandPassFilter(double centerFrequency, double bandwidth) {
        double r = 1 - 3 * bandwidth;
        double k = (1 - 2 * r * Math.cos(2 * Math.PI * centerFrequency) + r * r) / (2 - 2 * Math.cos(2 * Math.PI * centerFrequency));
        double a0 = 1 - k;
        double a1 = 2 * (k - r) * Math.cos(2 * Math.PI * centerFrequency);
        double a2 = r * r - k;
        return new double[]{a0, a1, a2};
    }

    private double applyBandPassFilter(double input) {
        double output = filterCoefficients[0] * input + filterCoefficients[1] * filterState[0] + filterCoefficients[2] * filterState[1];
        filterState[1] = filterState[0];
        filterState[0] = input;
        return output;
    }
}
