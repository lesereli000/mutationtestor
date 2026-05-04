package mutationtester;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

/**
 * Suppresses ANTLR parse warnings from printing to stderr.
 * The ISL/Racket grammar produces harmless recovery warnings on some
 * valid Racket constructs — tests still pass, we just don't want the noise.
 */
public class QuietAntlrErrorListener extends BaseErrorListener {

    public static final QuietAntlrErrorListener INSTANCE = new QuietAntlrErrorListener();

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer,
                            Object offendingSymbol,
                            int line, int charPositionInLine,
                            String msg,
                            RecognitionException e) {
        // intentionally silent
    }
}