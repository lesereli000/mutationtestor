import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TipTest {

    @Test
    void keepsCentsWhenSplitting() {
        // 46.10 split three ways is 1536 cents each, not a whole-dollar amount
        assertEquals(1536, Tip.centsEach(46.10, 3));
    }
}
