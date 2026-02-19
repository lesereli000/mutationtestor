using System;
using System.Collections.Generic;
using System.Text;

public class Example
{
    public static string ExampleCode(string input)
    {
        if (string.IsNullOrWhiteSpace(input))
        {
            return "Input was empty.";
        }

        string[] words = input.Split((char[])null, StringSplitOptions.RemoveEmptyEntries);
        Dictionary<char, int> letterFrequency = new Dictionary<char, int>();
        List<string> longWords = new List<string>();

        int totalLength = 0;
        int vowelCount = 0;

        foreach (string word in words)
        {
            totalLength += word.Length;

            if (word.Length > 5)
            {
                longWords.Add(word.ToUpper());
            }

            foreach (char c in word.ToLower())
            {
                if (char.IsLetter(c))
                {
                    if (letterFrequency.ContainsKey(c))
                    {
                        letterFrequency[c]++;
                    }
                    else
                    {
                        letterFrequency[c] = 1;
                    }

                    if ("aeiou".IndexOf(c) >= 0)
                    {
                        vowelCount++;
                    }
                }
            }
        }

        double averageLength = totalLength / (double)words.Length;

        List<KeyValuePair<char, int>> sortedLetters = new List<KeyValuePair<char, int>>(letterFrequency);
        sortedLetters.Sort((a, b) => b.Value - a.Value);

        StringBuilder result = new StringBuilder();

        result.AppendLine("=== Text Analysis ===");
        result.AppendLine("Word count: " + words.Length);
        result.AppendLine("Average word length: " + averageLength.ToString("F2"));
        result.AppendLine("Total vowels: " + vowelCount);
        result.AppendLine();
        result.AppendLine("Top 5 most frequent letters:");

        for (int i = 0; i < Math.Min(5, sortedLetters.Count); i++)
        {
            var entry = sortedLetters[i];
            result.AppendLine(entry.Key + " -> " + entry.Value);
        }

        result.AppendLine();
        result.AppendLine("Long words (uppercase):");

        foreach (string w in longWords)
        {
            result.AppendLine("- " + w);
        }

        if (averageLength > 6)
        {
            result.AppendLine();
            result.Append("Text complexity: HIGH");
        }
        else if (averageLength > 4)
        {
            result.AppendLine();
            result.Append("Text complexity: MEDIUM");
        }
        else
        {
            result.AppendLine();
            result.Append("Text complexity: LOW");
        }

        return result.ToString();
    }
}
