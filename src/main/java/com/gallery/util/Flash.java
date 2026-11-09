package com.gallery.util;

/** One-shot message shown after a redirect. */
public class Flash {

    private final String text;
    private final boolean error;

    public Flash(String text, boolean error) {
        this.text = text;
        this.error = error;
    }

    public String getText() {
        return text;
    }

    public boolean isError() {
        return error;
    }
}
