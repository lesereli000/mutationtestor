class SharedPit {
    public int Condition(bool flag) { if (flag) return 1; return 0; }
    public bool Negate(bool flag) { return !flag; }
    public string EmptyValue() { return "value"; }
    public string ReturnedCall() { return Compute(); }
    public void VoidCall() {
        Notify();
    }
    public Widget ConstructorCall() { return new Widget(); }
}
