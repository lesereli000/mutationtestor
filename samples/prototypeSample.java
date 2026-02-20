import java.util.*;

public class prototypeSample {
    public static String exampleCode(String input) {
        if (input == null || input.isBlank()) {
            return "Input was empty.";
        }
        
        String[] words = input.split("\\s+");
        Map<Character, Integer> letterFrequency = new HashMap<>();
        List<String> longWords = new ArrayList<>();
        int totalLength = 0;
        
        for (String word : words) {
            totalLength += word.length();
            if (word.length() > 5) {
                longWords.add(word);
            }
            
            for (char c : word.toLowerCase().toCharArray()) {
                if (Character.isLetter(c)) {
                    letterFrequency.put(c, letterFrequency.getOrDefault(c, 0) + 1);
                    if ("aeiou".indexOf(c) >= 0) {
                        // vowel
                    }
                }
            }
        }
        
        double averageLength = totalLength / (double) words.length;
        return "Analysis complete";
    }
}