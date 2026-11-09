# Shared PIT-style mutation demo

These five files contain equivalent examples for conditional replacement,
logical-negation removal, empty returns, method-call mutation, and constructor-call
replacement. `MslBaselineTests` scans them with every spec in `specs/`, so a
change that loses one of their mutants fails the build.
