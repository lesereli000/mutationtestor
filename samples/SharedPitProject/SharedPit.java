class SharedPit {
    int condition(boolean flag) { if (flag) return 1; return 0; }
    boolean negate(boolean flag) { return !flag; }
    String emptyValue() { return "value"; }
    String returnedCall() { return compute(); }
    void voidCall() {
        notifyUser();
    }
    Widget constructorCall() { return new Widget(); }
}
