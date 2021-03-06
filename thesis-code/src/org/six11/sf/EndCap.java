package org.six11.sf;

import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.HashSet;
import java.util.Set;

import org.six11.util.pen.Functions;
import org.six11.util.pen.IntersectionData;
import org.six11.util.pen.Line;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Vec;

import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;

public class EndCap {
  
  private static double endcapLengthMultiplier = 1.0 / 10.0;

  public static class Group {
    Set<EndCap.Intersection> intersections = new HashSet<EndCap.Intersection>();

    public Group(Intersection ix) {
      intersections.add(ix);
    }

    public void add(EndCap.Intersection ix) {
      intersections.add(ix);
    }

    public boolean has(Group other) {
      boolean ret = false;
      for (Intersection ix : intersections) {
        for (Intersection otherIx : other.intersections) {
          if (ix.isRelated(otherIx)) {
            ret = true;
            break;
          }
        }
      }
      return ret;
    }

    public void merge(Group other) {
      for (Intersection ix : other.intersections) {
        add(ix);
      }
    }

    public Pt adjustMembers() {
      Pt ret = null;
      double sumX = 0;
      double sumY = 0;
      int n = 0;
      for (Intersection ix : intersections) {
        if (ix.intersects) {
          n = n + 1;
          sumX = sumX + ix.pt.getX();
          sumY = sumY + ix.pt.getY();
        }
      }
      if (n > 0) {
        double midX = sumX / n;
        double midY = sumY / n;
        ret = new Pt(midX, midY);
      }
      return ret;
    }

    public Set<Pt> getPoints() {
      Set<Pt> ret = new HashSet<Pt>();
      for (Intersection ix: intersections) {
        ret.add(ix.c1.getPt());
        ret.add(ix.c2.getPt());
      }
      return ret;
    }
  }

  public static enum WhichEnd {
    Start, End
  }

  private SegmentDelegate seg;
  private Pt pt;
  private Vec dir;
//  private Area area;
  private Path2D shape;
  private Rectangle2D bounds;
  private Line line;
  Line lineSegment;
  private WhichEnd end;
  private double halfLength;

  public EndCap(SegmentDelegate seg, WhichEnd end) {
    this.seg = seg;
    this.end = end;
    this.pt = (end == WhichEnd.Start ? seg.getP1() : seg.getP2());
    this.dir = (end == WhichEnd.Start ? seg.getStartDir() : seg.getEndDir()).getFlip();
    double dist = seg.getP1().distance(seg.getP2());
    double rad = dist * endcapLengthMultiplier;
//    this.area = new Area();
//    area.add(new Area(new Circle(pt, rad)));
//    area.add(makeTriangle(pt, dir, rad, rad * 6));
    halfLength = rad * 6;
//    this.shape = new Path2D.Double();
//    shape.append(area.getPathIterator(null), false);
//    this.bounds = area.getBounds2D();
    this.line = new Line(pt, dir);
    Pt segStart = pt.getTranslated(dir, rad);
    Pt segEnd = pt.getTranslated(dir.getFlip(), rad);
    this.lineSegment = new Line(segStart, segEnd);
  }

  public double getHalfLength() {
    return halfLength;
  }
  
//  private Area makeTriangle(Pt end, Vec dir, double halfHeight, double width) {
//    Vec norm1 = dir.getNormal().getVectorOfMagnitude(halfHeight);
//    Vec norm2 = norm1.getFlip();
//    Pt p1 = norm1.add(end);
//    Pt p2 = norm2.add(end);
//    Pt p3 = dir.getVectorOfMagnitude(width).add(end);
//    Path2D retPath = new Path2D.Double();
//    retPath.moveTo(p1.getX(), p1.getY());
//    retPath.lineTo(p2.getX(), p2.getY());
//    retPath.lineTo(p3.getX(), p3.getY());
//    retPath.closePath();
//    Area ret = new Area(retPath);
//    return ret;
//  }

  public SegmentDelegate getSegment() {
    return seg;
  }

  public Pt getPt() {
    return pt;
  }

  public Vec getDir() {
    return dir;
  }

//  public Area getArea() {
//    return area;
//  }

  public Path2D getShape() {
    return shape;
  }

  public Rectangle2D getBounds() {
    return bounds;
  }

  public Line getLine() {
    return line;
  }

  public WhichEnd getEnd() {
    return end;
  }

  public IntersectionData intersect(EndCap other) {
    return Functions.getIntersectionData(getLine(), other.getLine());
  }

  /**
   * Returns true if these two endcaps have the same ink and end (using getInk() and getEnd()).
   */
  public boolean same(EndCap other) {
    return (seg == other.getSegment()) && (end == other.getEnd());
  }

  public String toString() {
    return seg.toString() + " " + end;
  }

  public EndCap.Intersection intersectInCap(EndCap c2) {
    EndCap.Intersection ret = new EndCap.Intersection(this, c2);
    if (!same(c2)) {
      IntersectionData id = Functions.getIntersectionData(lineSegment, c2.lineSegment);
      if (id.intersectsInSegments()) {
        // The two segments we are comparing might have very different scales. If we just accepted
        // all cases where the endcaps intersect, we would have problems. For example, it would
        // be difficult to place a notch near the end of a long segment. 
        
        // So to handle this, we essentially scale the longer line to be the same length as 
        // the other. Since we already have the intersection data, we check to see if the
        // intersection parameters are 
        
        double l1 = lineSegment.getLength();
        double l2 = c2.lineSegment.getLength();
        double[] l1Range = new double[] { 0, 1 };
        double[] l2Range = new double[] { 0, 1 };
        if (l1 > l2) {
          double scale = l2 / l1;
          double half = scale / 2;
          l1Range[0] = 0.5 - half;
          l1Range[1] = 0.5 + half;
        } else {
          double scale = l1 / l2;
          double half = scale / 2;
          l2Range[0] = 0.5 - half;
          l2Range[1] = 0.5 + half;
        }
        double r1 = id.getLineOneParam();
        double r2 = id.getLineTwoParam();
        boolean ok1 = (r1 > l1Range[0] && r1 < l1Range[1]);
        boolean ok2 = (r2 > l2Range[0] && r2 < l2Range[1]);
        boolean retVal = ok1 && ok2;
        ret.setResult(retVal, id.getIntersection());
      }
    }
    return ret;
  }

  public static class Intersection {
    EndCap c1, c2;
    Pt pt;
    boolean intersects;

    public Intersection(EndCap c1, EndCap c2) {
      this.c1 = c1;
      this.c2 = c2;
      this.intersects = false;
      this.pt = null;
    }

    public void adjustMembers(double midX, double midY) {
      c1.pt.setLocation(midX, midY);
      c2.pt.setLocation(midX, midY);
    }

    private void setResult(boolean intersects, Pt pt) {
      this.intersects = intersects;
      this.pt = pt;
    }

    public boolean usesSameCaps(EndCap e1, EndCap e2) {
      return ((e1.same(c1) && e2.same(c2)) || (e1.same(c2) && e2.same(c1)));
    }

    public boolean isRelated(Intersection other) {
      boolean ret = false;
      if (intersects && other.intersects) {
        ret = c1.same(other.c1) || c1.same(other.c2) || c2.same(other.c1) || c2.same(other.c2);
      }
      return ret;
    }
  }
}
