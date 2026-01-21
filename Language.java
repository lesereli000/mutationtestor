import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

public class Language {
    HashMap<String, String> translations;

    // To add more syntax, put the syntax name at the end of the syntax array, and add an int corresponding to its index
    private static final String[] syntax = {"line_comment", "block_comment_open", "block_comment_close", "single_string",
            "double_string", "escape_character", "plus", "minus", "greater_than", "greater_equals", "equals",
            "equal_to", "not_equal", "strictly_euqals_to", "strictly_not_equal_to", "logical_and", "logical_or"};
    public static final int LINE_COMMENT = 0;
    public static final int BLOCK_COMMENT_OPEN = 1;
    public static final int BLOCK_COMMENT_CLOSE = 2;
    public static final int SINGLE_STRING = 3;
    public static final int DOUBLE_STRING = 4;
    public static final int ESCAPE_CHARACTER = 5;
    public static final int PLUS = 6;
    public static final int MINUS = 7;
    public static final int GREATER_THAN = 8;
    public static final int GREATER_EQUALS = 9;
    public static final int EQUALS = 10;
    public static final int EQUAL_TO = 11;
    public static final int NOT_EQUAL = 12;
    public static final int STRICTLY_EUQALS_TO = 13;
    public static final int STRICTLY_NOT_EQUAL_TO = 14;
    public static final int LOGICAL_AND = 15;
    public static final int LOGICAL_OR = 16;

    // This will create a new Language object based on the given file's type extension (.java for Java, .cs for C#)
    public Language(String fileName) {
        translations = new HashMap<>();
        String fileType = fileName.substring(fileName.lastIndexOf(".") + 1);
        Path dictPath = Path.of("dictionaries\\" + fileType + ".dict");
        try{
            for(String Line : Files.readString(dictPath).split("\r\n")){
                Line = Line.replaceFirst("=", " = ");
                String key = Line.split("\s+=\s+")[0]; // regex "\s*=\s*" searches for an = with any number of whitespace characters on either side
                String value = Line.split("\s+=\s+")[1];
                translations.put(key, value);
            }
        } catch(IOException e){
            System.out.println("Error reading dictionary file " + dictPath.toString());
            e.printStackTrace();
        }
    }

    // Mutator can make calls to the Language object to get Language-specific character arrays
    // Must be called with a valid syntax type, see line 10 in this file for a list. These public ints can be used from other class to make code readable
    // returns an empty character array (new char[0]) if the type is invalid or if no result was found.
    public char[] getSyntax(int type) {
        if (type >= syntax.length || type < 0) {
            // Invalid type
            return new char[0];
        }
        String retval = translations.get(syntax[type]);
        if (retval == null) {
            // No valid entry in dictionary file
            return new char[0];
        }
        return retval.toCharArray();
    }
}
