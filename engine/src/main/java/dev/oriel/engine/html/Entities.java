package dev.oriel.engine.html;

final class Entities {
    private Entities() {}
    static String decode(String text, boolean attribute) { return CharacterReferences.decode(text, attribute, (offset, issue) -> {}); }
}
