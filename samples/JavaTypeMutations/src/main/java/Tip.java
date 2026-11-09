/** Splits a restaurant bill. The number types matter: each one is a mutation target. */
public class Tip {

    /** Each person's share in whole cents: 46.10 split three ways is 1536. */
    public static long centsEach(double total, int people) {
        return (long) (total * 100) / people;
    }
}
