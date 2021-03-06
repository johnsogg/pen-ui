package org.six11.sf.constr;

import org.json.JSONException;
import org.json.JSONObject;
import org.six11.sf.SketchBook;
import org.six11.sf.RecognitionListener.What;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Vec;
import org.six11.util.solve.NumericValue;
import org.six11.util.solve.OrientationConstraint;

public class RightAngleUserConstraint extends UserConstraint {

  /**
   * Index into the getSpots() return array for the angle brace's visual fulcrum.
   */
  public static final int SPOT_FULCRUM = 0;

  /**
   * Index into the getSpots() return array for the 'left' point for the angle brace.
   */
  public static final int SPOT_LEFT = 1;

  /**
   * Index into the getSpots() return array for the 'right' point for the angle brace.
   */
  public static final int SPOT_RIGHT = 2;

  //  public RightAngleUserConstraint(SketchBook model, OrientationConstraint rightAngleConstraint) {
  //    super(model, "RightAngle", rightAngleConstraint);
  //  }

  public RightAngleUserConstraint(SketchBook model, Pt a1, Pt a2, Pt b1, Pt b2) {
    super(model, Type.RightAngle, new OrientationConstraint(a1, a2, b1, b2, new NumericValue(
        Math.toRadians(90))));
  }

  public RightAngleUserConstraint(SketchBook model, JSONObject json) throws JSONException {
    super(model, Type.RightAngle, json);
  }

  public Pt[] getSpots(double braceLen) {
    Pt[] ret = new Pt[3];
    OrientationConstraint c = getOrientationConstraint();
    Pt fulcrum = null;
    Pt left = null;
    Pt right = null;

    if (c.lineA1 == c.lineB1) {
      fulcrum = c.lineA1;
      left = c.lineA2;
      right = c.lineB2;
    } else if (c.lineA1 == c.lineB2) {
      fulcrum = c.lineA1;
      left = c.lineA2;
      right = c.lineB1;
    } else if (c.lineA2 == c.lineB1) {
      fulcrum = c.lineA2;
      left = c.lineA1;
      right = c.lineB2;
    } else if (c.lineA2 == c.lineB2) {
      fulcrum = c.lineA2;
      left = c.lineA1;
      right = c.lineB1;
    }

    if ((fulcrum == null) || (left == null) || (right == null)) {
      // do nothing
    } else {
      Vec leftV = new Vec(fulcrum, left).getUnitVector();
      Vec rightV = new Vec(fulcrum, right).getUnitVector();
      Vec diagonal = Vec.sum(leftV, rightV).getUnitVector();
      double root2 = Math.sqrt(2);
      
      Pt braceCorner = fulcrum.getTranslated(diagonal, root2 * braceLen);
      Pt braceLeft = fulcrum.getTranslated(leftV, braceLen);
      Pt braceRight = fulcrum.getTranslated(rightV, braceLen);
      ret[SPOT_FULCRUM] = braceCorner;
      ret[SPOT_LEFT] = braceLeft;
      ret[SPOT_RIGHT] = braceRight;
    }

    return ret;
  }

  private OrientationConstraint getOrientationConstraint() {
    OrientationConstraint ret = null;
    if (getConstraints().size() > 0) {
      ret = getConstraints().toArray(new OrientationConstraint[1])[0];
    }
    return ret;
  }

  public boolean isValid() {
    OrientationConstraint c = getOrientationConstraint();
    boolean ret = false;
    if (c != null) {
      ret = model.hasSegment(c.lineA1, c.lineA2) && model.hasSegment(c.lineB1, c.lineB2);
    }
    return ret;
  }

  @Override
  public void removeInvalid() {
    OrientationConstraint c = getOrientationConstraint();
    if (c != null) {
      if (model.hasSegment(c.lineA1, c.lineA2) && model.hasSegment(c.lineB1, c.lineB2)) {
        // good.
      } else {
        // not good.
        //        getConstraints().remove(c);
        removeConstraint(c);
      }
    }
  }
  
  public What getRecognitionListenerWhat() {
    return What.RightAngle;
  }

}
