import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

public class Language {
    HashMap<String, String> translations;

    private static final String[] syntax = {"line_comment", "block_comment_open", "block_comment_close", "single_string", "double_string", "escape_character"};
    public static final int LINE_COMMENT = 0;
    public static final int BLOCK_COMMENT_OPEN = 1;
    public static final int BLOCK_COMMENT_CLOSE = 2;
    public static final int SINGLE_STRING = 3;
    public static final int DOUBLE_STRING = 4;
    public static final int ESCAPE_CHARACTER = 5;

    // This will create a new Language object based on the given file's type extension (.java for Java, .cs for C#)
    public Language(String fileName) {
        translations = new HashMap<>();
        String fileType = fileName.substring(fileName.lastIndexOf(".") + 1);
        Path dictPath = Path.of("dictionaries\\" + fileType + ".dict");
        try{
            for(String Line : Files.readString(dictPath).split("\n")){
                String key = Line.split("\s*=\s*")[0]; // regex "\s*=\s*" searches for an = with any number of whitespace characters on either side
                String value = Line.split("\s*=\s*")[1];
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
        if (type >= syntax.length) {
            return new char[0];
        }
        String retval = translations.get(syntax[type]);
        if (retval == null) {
            return new char[0];
        }
        return retval.toCharArray();
    }
}
