package org.six11.sf;

import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.six11.sf.SegmentDelegate.Type;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Vec;
import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.num;

public class Stencil {
  private List<Pt> path;
  private List<SegmentDelegate> segs;
  private SketchBook model;
  private Set<Stencil> children;

  public Stencil(SketchBook model, List<Pt> path, List<SegmentDelegate> segs) {
    this.model = model;
    this.path = new ArrayList<Pt>(path);
    if (path.get(0) != path.get(path.size() - 1)) {
      path.add(path.get(0));
    }
    this.segs = new ArrayList<SegmentDelegate>(segs);
    this.children = new HashSet<Stencil>();
  }

  public boolean hasPath(List<SegmentDelegate> otherSegPath) {
    return segs.containsAll(otherSegPath);
  }

  public void removeGeometry(SegmentDelegate seg) {
    if (segs.contains(seg)) {
      segs.remove(seg);
    } else {
      Set<Stencil> doomed = new HashSet<Stencil>();
      for (Stencil c : children) {
        c.removeGeometry(seg);
        if (!c.isValid()) {
          doomed.add(c);
        }
      }
      children.removeAll(doomed);
    }
  }

  public Shape getShape(boolean needCCW) {
    Path2D shape = new Path2D.Double();
    shape.setWindingRule(Path2D.WIND_NON_ZERO);
    List<Pt> allPoints = getAllPoints();
    boolean cw = isClockwise();
    if ((cw && needCCW) || (!cw && !needCCW)) {
      Collections.reverse(allPoints);
    }
    for (int i = 0; i < allPoints.size(); i++) {
      Pt pt = allPoints.get(i);
      if (i == 0) {
        shape.moveTo(pt.getX(), pt.getY());
      } else {
        shape.lineTo(pt.getX(), pt.getY());
      }
    }
    for (Stencil kid : children) {
      shape.append(kid.getShape(!needCCW), false);
    }
    return shape;
  }

  private boolean isClockwise() {
    List<Pt> turns = getTurnPath();
    Pt c = Functions.getMean(turns);
    double crossProd = 0;
    for (int i = 0; i < turns.size() - 1; i++) {
      Vec a = new Vec(c, turns.get(i));
      Vec b = new Vec(c, turns.get(i + 1));
      crossProd = crossProd + a.cross(b);
    }
    if (Math.abs(crossProd) < 0.01) {
      bug("cross product too close to zero to be meaningful.");
    }
    boolean ret = crossProd > 0;
    return ret;
  }

  private List<Pt> getTurnPath() {
    List<Pt> ret = path;
    if (segs.size() == 2) {
      ret = new ArrayList<Pt>();
      for (int i=0; i < path.size(); i++) {
        ret.add(path.get(i));
        if (segs.get(i).type != Type.Line) {
          ret.add(segs.get(i).getVisualMidpoint());
        }
      }
    } else if (segs.size() == 1) {
      bug("stencil with 1 segs...");
      SegmentDelegate seg = segs.get(0);
      if (seg.type != Type.Line) {
        List<Pt> source = seg.asPolyline();
        int sz = source.size();
        int idx1 = sz / 3;
        int idx2 = (2 * sz) / 3;
        ret.add(source.get(0));
        ret.add(source.get(idx1));
        ret.add(source.get(idx2));
        ret.add(source.get(source.size() - 1));
      }
      bug("...ends up with " + ret.size() + " points to do math with.");
    }
    return ret;
  }

  public Area intersect(Area area) {
    Area myArea = new Area(getOuterShape());
    myArea.intersect(area);
    return myArea;
  }

  private Shape getOuterShape() {
    Path2D shape = new Path2D.Double();
    // the path list hold segment endpoints only. If there are curved segments, we 
    // also need those curvy bits. That's why we use allPoints and not just path.
    List<Pt> allPoints = getAllPoints();
    for (int i = 0; i < allPoints.size(); i++) {
      Pt pt = allPoints.get(i);
      if (i == 0) {
        shape.moveTo(pt.getX(), pt.getY());
      } else {
        shape.lineTo(pt.getX(), pt.getY());
      }
    }
    return shape;
  }

  /**
   * Returns the geometry defining the outside of this stencil.
   * 
   * @return
   */
  private List<Pt> getAllPoints() {
    List<Pt> allPoints = new ArrayList<Pt>();
    for (int i = 0; i < segs.size(); i++) {
      Pt p = path.get(i);
      SegmentDelegate seg = segs.get(i);
      List<Pt> nextPoints = seg.getPointList();
      if (seg.getP2().equals(p)) {
        Collections.reverse(nextPoints);
      }
      for (Pt np : nextPoints) {
        if (allPoints.isEmpty() || allPoints.get(allPoints.size() - 1) != np) {
          allPoints.add(np);
        }
      }
    }
    return allPoints;
  }

  public boolean isValid() {
    boolean ret = true;
    for (int i = 0; i < path.size() - 1; i++) {
      Pt a = path.get(i);
      Pt b = path.get(i + 1);
      SegmentDelegate s = model.getSegment(a, b);
      if (s == null || !segs.contains(s)) {
        ret = false;
        break;
      }
    }
    for (SegmentDelegate s : segs) {
      if (!model.hasSegment(s)) {
        ret = false;
        break;
      }
    }
    return ret;
  }

  public Collection<Stencil> getChildren() {
    return children;
  }

  /**
   * Replaces the older point with the newer in any children and in this stencil. This only affects
   * the point list and NOT the segment list, which should be updated elsewhere.
   * 
   * @param older
   * @param newer
   */
  public void replacePoint(Pt older, Pt newer) {
    for (Stencil c : children) {
      c.replacePoint(older, newer);
    }
    for (int i = 0; i < path.size(); i++) {
      if (path.get(i).equals(older)) {
        path.remove(i);
        path.add(i, newer);
      }
    }
  }

  public List<Pt> getPath() {
    return path;
  }

  public boolean isSame(Stencil other) {
    return hasPath(other.segs);
  }

  public boolean isSuperset(Stencil other) {
    return path.size() > other.path.size() && path.containsAll(other.getPath());
  }

  public boolean surrounds(Stencil c) {
    boolean ret = false;
    Area childArea = new Area(c.getOuterShape());
    Area myArea = new Area(getOuterShape());
    myArea.intersect(childArea);
    ret = myArea.equals(childArea);
    return ret;
  }

  public void add(Set<Stencil> kids) {
    Set<Stencil> no = new HashSet<Stencil>();
    for (Stencil k : kids) {
      for (Stencil c : children) {
        if (k.isSame(c)) {
          no.add(k);
        }
        //        boolean samePath = k.getPath().containsAll(c.getPath());
        //        if (samePath) {
        //          no.add(k);
        //        }
      }
    }
    kids.removeAll(no);
    children.addAll(kids);
  }

  public List<SegmentDelegate> getSegs() {
    return segs;
  }

}