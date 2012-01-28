package org.six11.sf;

import java.awt.Shape;
import java.util.ArrayList;
import java.util.List;

import org.six11.util.gui.shape.ShapeFactory;
import org.six11.util.pen.CircleArc;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Line;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Vec;

import static java.lang.Math.atan2;
import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;

public class CircularArcSegment extends SegmentDelegate {

  private Vec centerParameterization;
  private Vec arcMidParameterization;
//  private int arcSide;

  public CircularArcSegment(Ink ink, List<Pt> points, Pt initialCenter, double initialRadius,
      boolean termA, boolean termB) {
    Pt rawP1 = points.get(0);
    Pt rawP2 = points.get(points.size() / 2);
    Pt rawP3 = points.get(points.size() - 1);
    CircleArc arc = new CircleArc(initialCenter, initialRadius);
    Pt arc1 = Functions.getIntersectionPoint(arc, new Line(rawP1, initialCenter));
    Pt arc2 = Functions.getIntersectionPoint(arc, new Line(rawP2, initialCenter));
    Pt arc3 = Functions.getIntersectionPoint(arc, new Line(rawP3, initialCenter));
    List<Pt> surface = initArc(arc1, arc2, arc3, initialCenter);
    // now need to determine which side the center is on, and what the parametric coordinate of the center is.
    Vec v = new Vec(arc1, arc3);
    double vMag = v.mag();
    Line line = new Line(surface.get(0), surface.get(surface.size() - 1));
////    Pt roughlyArcMid = points.get(points.size() / 2);
//    Pt circleIntersectionPt = Functions.getIntersectionPoint(arc, new Line(roughlyArcMid,
//        initialCenter));
//    arcSide = Functions.getPartition(circleIntersectionPt, arc1, arc3);
    centerParameterization = calculateParameterForPoint(vMag, line, initialCenter);
    arcMidParameterization = calculateParameterForPoint(vMag, line, arc2);
    bug("center param: " + num(centerParameterization) + ", arcMid param: " + num(arcMidParameterization));
    init(ink, surface, termA, termB, Type.CircularArc);
  }

  private double getParam(Pt target, Pt center) {
    return atan2(target.y - center.y, target.x - center.x);
  }
  
  public final List<Pt> initArc(Pt arc1, Pt arc2, Pt arc3, Pt center) {
    double arc1T = getParam(arc1, center);
    double arc2T = getParam(arc2, center);
    double arc3T = getParam(arc3, center);
    List<Pt> surface = new ArrayList<Pt>();
    List<Double> arcParams = Functions.makeMonotonicallyIncreasingAngles(arc1T, arc2T, arc3T);
    double numSteps = 60;
    double start = arcParams.get(0);
    double end = arcParams.get(2);
    double step = (end - start) / numSteps;
    double r = arc1.distance(center);
    for (double t = start; t <= end; t += step) {
      surface.add(getCircularPoint(t, r, center));
    }
    return surface;
  }
  
  /**
   * Returns a point on the circle boundary, parameterized by the given radial angle. If you call
   * this a bunch of times for t=0..2pi you sample the entire circle.
   */
  public Pt getCircularPoint(double t, double r, Pt center) {
    double x = (r * cos(t));
    double y = (r * sin(t));
    Pt ret = new Pt(x + center.x, y + center.y);
    return ret;
  }

  
  @Override
  protected void doPara() {
    if (paraP1Loc == null || paraP2Loc == null || paraPoints == null
        || !paraP1Loc.isSameLocation(p1) || !paraP2Loc.isSameLocation(p2)) {
      paraP1Loc = p1.copyXYT();
      paraP2Loc = p2.copyXYT();
      paraPoints = new ArrayList<Pt>();
      Vec v = new Vec(p1, p2).getUnitVector();
      double fullLen = p1.distance(p2);
      Vec vNorm = v.getNormal().getFlip();
      for (int i = 0; i < pri.length; i++) {
        double priComponent = pri[i] * fullLen;
        double altComponent = alt[i] * fullLen;
        Pt spot = p1.getTranslated(v, priComponent);
        spot = spot.getTranslated(vNorm, altComponent);
        paraPoints.add(i, spot);
      }
      paraPoints.set(0, p1);
      paraPoints.set(paraPoints.size() - 1, p2);
      //      paraShape = null;
      //      paraLength = Functions.getPathLength(paraPoints, 0, paraPoints.size() - 1);
    }
  }

  private Pt getCenter() {
    return getParameterizedPoint(centerParameterization);
  }
  
  private Pt getParameterizedPoint(Vec parameterization) {
    Pt ret = null;
    Vec chord = new Vec(getP1(), getP2()).getUnitVector();
    if (chord.isZero()) {
      ret = getP1().copyXYT();
    } else {
      double fullLen = getP1().distance(getP2());
      Vec vNorm = chord.getNormal().getFlip();
      double cx = parameterization.getX() * fullLen;
      double cy = parameterization.getY() * fullLen;
      Pt spot = getP1().getTranslated(chord, cx);
      spot = spot.getTranslated(vNorm, cy);
      ret = spot;
    }
    return ret;
  }
  
  private Pt getArcMid() {
    return getParameterizedPoint(arcMidParameterization);
  }

  /**
   * Returns a vector tangent to the circle at p2, facing inwards.
   */
  public Vec getEndDir() {
    // TODO: this is almost certainly wrong. have to look at where the center is---left or right of the chord?
    Pt currentCenter = getCenter();
    Vec fromCenter = new Vec(currentCenter, getP2()).getUnitVector();
    return fromCenter.getNormal();
  }

  public Vec getStartDir() {
    // TODO: this is almost certainly wrong
    Pt currentCenter = getCenter();
    Vec fromCenter = new Vec(currentCenter, getP1()).getUnitVector();
    return fromCenter.getNormal();
  }

  public Shape asArc() {
    doPara();
    return ShapeFactory.makeArc(getP1(), getArcMid(), getP2());
  }

}