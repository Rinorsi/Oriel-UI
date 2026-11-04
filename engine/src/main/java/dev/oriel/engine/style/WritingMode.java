package dev.oriel.engine.style;

/**
 * The {@code writing-mode} property. A vertical mode sets the text of a box in columns, characters upright; its
 * block-level children still stack top to bottom.
 */
public enum WritingMode {
    HORIZONTAL_TB, VERTICAL_RL, VERTICAL_LR;

    public boolean vertical() {
        return this != HORIZONTAL_TB;
    }
}
