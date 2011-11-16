package org.six11.sf.rec;

import org.six11.sf.rec.RecognizerPrimitive.Certainty;

//import static org.six11.util.Debug.bug;
//import static org.six11.util.Debug.num;
//import static java.lang.Math.log;

public class EqualLength extends RecognizerConstraint {

  public EqualLength(String name, String... sNames) {
    super(name, sNames);
  }

  @Override
  public Certainty check(RecognizerPrimitive... p) {
    Certainty ret = Certainty.No;
    RecognizerPrimitive primA = p[0];
    RecognizerPrimitive primB = p[1];
    double a = primA.getLength();
    double b = primB.getLength();
    double diff = Math.abs(a - b);
//    double logNumer = log(Math.min(a, b));
//    double logDenom = log(Math.max(a, b));
    double ratio = Math.min(a, b) / Math.max(a, b);
//    double logRatio = (logNumer / logDenom);
    if (ratio > 0.85 || diff < 20) {
      ret = Certainty.Yes;
    } else if (ratio > 0.7 || diff < 30) {
      ret = Certainty.Maybe;
    }
//    bug("lengths: " + num(a) + ", " + num(b) + ", distance ratio: " + num(ratio) + ", log ratio: " + num(logRatio) + " log of max: " + num(logDenom) + " ==> " + ret);
    return ret;
  }

}