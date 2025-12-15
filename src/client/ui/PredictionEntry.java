package client.ui;

/**
 * Data class for storing prediction history entries
 */
public class PredictionEntry {
    private final String guess;
    private final int strike;
    private final int ball;

    public PredictionEntry(String guess, int strike, int ball) {
        this.guess = guess;
        this.strike = strike;
        this.ball = ball;
    }

    public String getGuess() {
        return guess;
    }

    public int getStrike() {
        return strike;
    }

    public int getBall() {
        return ball;
    }
}
