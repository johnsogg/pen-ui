package org.six11.sf.rec;

import java.awt.Color;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import org.six11.sf.Segment;
import org.six11.sf.SegmentFilter;
import org.six11.sf.SketchBook;
import org.six11.sf.rec.RecognizerPrimitive.Certainty;
import org.six11.sf.rec.RecognizerPrimitive.Type;
import org.six11.util.math.Interval;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.DrawingBufferRoutines;
import org.six11.util.pen.Functions;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Vec;
import org.six11.util.solve.Constraint;
import org.six11.util.solve.DistanceConstraint;
import org.six11.util.solve.NumericValue;
import org.six11.util.solve.VariableBank;
import org.six11.util.solve.VariableBank.ConstraintFilter;

import static org.six11.util.Debug.num;
import static org.six11.util.Debug.bug;

public class SameLengthGesture extends RecognizedItemTemplate {

  private static final String TARGET_A = "targetA";
  private static final String TARGET_B = "targetB";

  public SameLengthGesture(SketchBook model) {
    super(model, "SameLengthGesture");
    addPrimitive("line1", Type.Line);
    addPrimitive("line2", Type.Line);
    addConstraint(new LineLengthConstraint("c1", new Interval(0, 100), "line1"));
    addConstraint(new LineLengthConstraint("c2", new Interval(0, 100), "line2"));
    addConstraint(new EqualLength("c3", "line1", "line2"));
  }

  @Override
  public RecognizedItem makeItem(Stack<String> slots, Stack<RecognizerPrimitive> prims) {
    RecognizedItem item = new RecognizedItem(this, slots, prims);
    // nothing interesting required.
    return item;
  }

  @Override
  public Certainty checkContext(RecognizedItem item, Collection<RecognizerPrimitive> in) {
    Certainty ret = Certainty.No;
    Set<Segment> allSegs = model.getGeometry();
    allSegs = SegmentFilter.makeCohortFilter(in).filter(allSegs);
    RecognizerPrimitive line1 = item.getSubshape("line1");
    RecognizerPrimitive line2 = item.getSubshape("line2");

    // 1) use a filter that only selects lines that are the sole intersecter of line1/line2.
    // (so if line1 intersects more than one thing, nothing passes. it must intersect exactly one thing.)
    Set<Segment> segs1 = SegmentFilter.makeIntersectFilter(line1).filter(allSegs);
    Set<Segment> segs2 = SegmentFilter.makeIntersectFilter(line2).filter(allSegs);
    if (segs1.size() == 1 && segs2.size() == 1) {
      // 2) use a filter that only selects lines whose midpoint is near line1 or line2's midpoint
      segs1 = SegmentFilter.makeMidpointFilter(line1, 0.3).filter(segs1);
      segs2 = SegmentFilter.makeMidpointFilter(line2, 0.3).filter(segs2);
    }
    if (segs1.size() == 1 && segs2.size() == 1) {
      Segment[] seg1 = segs1.toArray(new Segment[1]);
      Segment[] seg2 = segs2.toArray(new Segment[1]);
      item.addTarget(SameLengthGesture.TARGET_A, seg1[0]);
      item.addTarget(SameLengthGesture.TARGET_B, seg2[0]);
      ret = Certainty.Yes;
    }
    bug("segs1: " + num(segs1, " "));
    bug("segs2: " + num(segs2, " "));

    return ret;
  }

  @Override
  public void create(RecognizedItem item, SketchBook model) {
    Segment s1 = item.getSegmentTarget(TARGET_A);
    Segment s2 = item.getSegmentTarget(TARGET_B);

    // see if either s1 or s2 has an existing length constraint
    Set<ConstraintFilter> filters = new HashSet<ConstraintFilter>();
    filters.add(VariableBank.getTypeFilter(DistanceConstraint.class));
    Set<Constraint> results = model.getConstraints().getVars().searchConstraints(filters);
    Set<Constraint> existing = new HashSet<Constraint>();
    for (Constraint distConst : results) {
      if (distConst.involves(s1.getP1()) && distConst.involves(s1.getP2())) {
        existing.add(distConst);
      }
      if (distConst.involves(s2.getP1()) && distConst.involves(s2.getP2())) {
        existing.add(distConst);
      }
    }
    Set<Constraint> addUs = new HashSet<Constraint>();
    if (existing.size() == 0) {
      NumericValue dist = new NumericValue(s1.length(), s2.length());
      addUs.add(new DistanceConstraint(s1.getP1(), s1.getP2(), dist));
      addUs.add(new DistanceConstraint(s2.getP1(), s2.getP2(), dist));
      // model.getConstraints().addConstraint();
      //      model.getConstraints().addConstraint();
    } else if (existing.size() == 1) {
      bug("Adding to the existing distance constraint.");
      // use existing numeric value
      DistanceConstraint distConst = (DistanceConstraint) existing.toArray(new Constraint[1])[0];
      RecognizedItem otherDistItem = model.getConstraintItem(distConst);
      model.setFriends(otherDistItem, item);
      if (distConst.involves(s1.getP1()) && distConst.involves(s1.getP2())) {
        // s1 already constrained. incorporate s2's length and give it constraint
        distConst.getValue().addValue(s2.length());
        addUs.add(new DistanceConstraint(s2.getP1(), s2.getP2(), distConst.getValue()));
        //        model.getConstraints().addConstraint();
      } else {
        // same as above but reverse the segments.
        distConst.getValue().addValue(s1.length());
        addUs.add(new DistanceConstraint(s1.getP1(), s1.getP2(), distConst.getValue()));
        //        model.getConstraints().addConstraint();
      }
    } else if (existing.size() > 1) {
      bug("Warning: creating this distance constraint would conflict with " + existing.size()
          + " current constraints. I refuse.");
    }
    for (Constraint c : addUs) {
      model.registerConstraint(item, c);
    }
  }

  public void draw(Constraint c, RecognizedItem item, DrawingBuffer buf, Pt hoverPoint) {
    if (hoverPoint != null) {
      Segment s1 = item.getSegmentTarget(TARGET_A);
      Segment s2 = item.getSegmentTarget(TARGET_B);
      double d1 = Functions.getDistanceBetweenPointAndSegment(hoverPoint, s1.asLine());
      double d2 = Functions.getDistanceBetweenPointAndSegment(hoverPoint, s2.asLine());
      double d = Math.min(d1, d2);
      if (d < 50) {
        double alpha = getAlpha(d, 5, 50);
        Color color = new Color(1, 0, 0, (float) alpha);
        Set<RecognizedItem> f = model.findFriends(item);
        Set<Segment> allSegs = new HashSet<Segment>();
        if (f == null) {
          allSegs.add(s1);
          allSegs.add(s2);
        } else {
          for (RecognizedItem ri : f) {
            allSegs.add(ri.getSegmentTarget(TARGET_A));
            allSegs.add(ri.getSegmentTarget(TARGET_B));
          }
        }
        for (Segment seg : allSegs) {
          Pt mid = Functions.getMean(seg.getP1(), seg.getP2());
          DrawingBufferRoutines.cross(buf, mid, 5, color);
        }
      }
    }
  }
}