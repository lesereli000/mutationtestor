function eitherPositive(a, b) {
  // should mutate here
  return a > 0 || b > 0;
}

console.log("Either positive?", eitherPositive(2, -3));
// should not mutate in this string: "left || right"
