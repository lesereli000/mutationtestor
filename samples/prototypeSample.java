public static String exampleCode(String input) {

    if (input == null || input.isBlank()) {
        return "Input was empty.";
    }

    String[] words = input.split("\\s+");
    Map<Character, Integer> letterFrequency = new HashMap<>();
    List<String> longWords = new ArrayList<>();

    int totalLength = 0;
    int vowelCount = 0;

    for (String word : words) {

        totalLength += word.length();

        if (word.length() > 5) {
            longWords.add(word.toUpperCase());
        }

        for (char c : word.toLowerCase().toCharArray()) {

            if (Character.isLetter(c)) {

                letterFrequency.put(c,
                        letterFrequency.getOrDefault(c, 0) + 1);

                if ("aeiou".indexOf(c) >= 0) {
                    vowelCount++;
                }
            }
        }
    }

    double averageLength = totalLength / (double) words.length;

    List<Map.Entry<Character, Integer>> sortedLetters =
            new ArrayList<>(letterFrequency.entrySet());

    sortedLetters.sort((a, b) -> b.getValue() - a.getValue());

    StringBuilder result = new StringBuilder();

    result.append("=== Text Analysis ===\n");
    result.append("Word count: ").append(words.length).append("\n");
    result.append("Average word length: ")
            .append(String.format("%.2f", averageLength))
            .append("\n");
    result.append("Total vowels: ").append(vowelCount).append("\n\n");

    result.append("Top 5 most frequent letters:\n");

    for (int i = 0; i < Math.min(5, sortedLetters.size()); i++) {
        Map.Entry<Character, Integer> entry = sortedLetters.get(i);
        result.append(entry.getKey())
                .append(" -> ")
                .append(entry.getValue())
                .append("\n");
    }

    result.append("\nLong words (uppercase):\n");

    for (String w : longWords) {
        result.append("- ").append(w).append("\n");
    }

    if (averageLength > 6) {
        result.append("\nText complexity: HIGH");
    } else if (averageLength > 4) {
        result.append("\nText complexity: MEDIUM");
    } else {
        result.append("\nText complexity: LOW");
    }

    return result.toString();
}