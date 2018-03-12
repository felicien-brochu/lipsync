package fr.brochu.puppet.lipsync;

import java.util.List;

public class TranscriptWord {
    private String spelling;
    private List<String> phones;

    public TranscriptWord(String spelling, List<String> phones) {
        this.spelling = spelling;
        this.phones = phones;
    }

    public String getSpelling() {
        return spelling;
    }

    public void setSpelling(String spelling) {
        this.spelling = spelling;
    }

    public List<String> getPhones() {
        return phones;
    }

    public void setPhones(List<String> phones) {
        this.phones = phones;
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(spelling);
        builder.append(" (");
        for (int i = 0; i < phones.size(); i++) {
            if (i > 0) {
                builder.append(" ");
            }
            builder.append(phones.get(i));
        }
        builder.append(")");
        return builder.toString();
    }
}