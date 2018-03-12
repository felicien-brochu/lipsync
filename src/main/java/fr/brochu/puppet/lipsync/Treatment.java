package fr.brochu.puppet.lipsync;

public enum Treatment {
    NORMAL,
    INWORD_NORMAL_GAP,
    INWORD_BIG_GAP,
    INWORD_SMALL_GAP,
    MOVE_START,
    MOVE_END,
    SHRINK_TO_START,
    SHRINK_TO_END,
    MISSING_WORD,
}