package fr.brochu.puppet.lipsync;

import edu.cmu.sphinx.alignment.SimpleTokenizer;
import edu.cmu.sphinx.api.Configuration;
import edu.cmu.sphinx.api.Context;
import edu.cmu.sphinx.linguist.acoustic.Unit;
import edu.cmu.sphinx.linguist.dictionary.Dictionary;
import edu.cmu.sphinx.linguist.dictionary.Pronunciation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Transcript {
    private final String text;
    private List<TranscriptWord> words;
    private Dictionary dictionary;

    public Transcript(String transcriptText, String acousticModelPath, String wordDictionaryPath, String g2pModelPath) throws IOException {
        this.text = cleanText(transcriptText);
        this.words = new ArrayList<>();

        Configuration configuration = new Configuration();

        configuration.setAcousticModelPath(acousticModelPath);
        configuration.setDictionaryPath(wordDictionaryPath);

        Context context = new Context(configuration);
        context.setLocalProperty("dictionary->g2pModelPath", g2pModelPath);
        context.setLocalProperty("dictionary->g2pMaxPron", "2");
        context.setLocalProperty("lexTreeLinguist->languageModel", "dynamicTrigramModel");
        this.dictionary = context.getInstance(Dictionary.class);

        expandPhones();
    }

    private static String cleanText(String text) {
        return text.replaceAll("['’]", "' ");
    }

    private void expandPhones() throws IOException {
        dictionary.allocate();

        List<String> textWords = extractWords(this.text);
        for (String word : textWords) {
            List<String> phones = new ArrayList<>();

            Pronunciation pronunciation = dictionary.getWord(word).getPronunciations()[0];
            for (Unit phone : pronunciation.getUnits()) {
                phones.add(phone.getName());
            }

            this.words.add(new TranscriptWord(word, phones));
        }

        dictionary.deallocate();
    }

    private static List<String> extractWords(String text) {
        List<String> sentences = new SimpleTokenizer().expand(text);
        return sentenceToWords(sentences);
    }

    private static List<String> sentenceToWords(List<String> sentences) {
        ArrayList<String> words = new ArrayList<String>();
        for (String sentence : sentences) {
            String[] tokens = sentence.split("\\s+");
            for (String word : tokens) {
                if (word.length() > 0)
                    words.add(word);
            }
        }
        return words;
    }

    public List<TranscriptWord> getWords() {
        return words;
    }


    public void updateWordsPronunciation(List<AlignedWord> alignedWords) {
        for (int i = 0; i < this.words.size(); i++) {
            if (!alignedWords.get(i).deleted) {
                this.words.get(i).getPhones().clear();
                for (Unit phone : alignedWords.get(i).wordResult.getWord().getPronunciations()[0].getUnits()) {
                    this.words.get(i).getPhones().add(phone.getName());
                }
            }
        }
    }

    public String toPhoneString() {
        String phoneString = "";

        for (int i = 0; i < this.words.size(); i++) {
            TranscriptWord word = this.words.get(i);
            for (String phone : word.getPhones()) {
                phoneString += " " + phone;
            }
        }

        return phoneString.trim();
    }

    public String getText() {
        return text;
    }

    public int[] getWordIndexes() {
        int[] wordIndexes = new int[words.size()];
        int phoneIndex = 0;
        for (int i = 0; i < words.size(); i++) {
            TranscriptWord word = words.get(i);
            wordIndexes[i] = phoneIndex;
            phoneIndex += word.getPhones().size();
        }
        return wordIndexes;
    }
}