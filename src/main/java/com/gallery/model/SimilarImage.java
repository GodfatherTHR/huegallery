package com.gallery.model;

/** An image together with its similarity score in the range 0.0 to 1.0. */
public class SimilarImage {

    private Image image;
    private double score;

    public SimilarImage() {
    }

    public SimilarImage(Image image, double score) {
        this.image = image;
        this.score = score;
    }

    public Image getImage() {
        return image;
    }

    public void setImage(Image image) {
        this.image = image;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    /** Score as a whole percentage for display. */
    public int getScorePercent() {
        return (int) Math.round(score * 100.0);
    }
}
