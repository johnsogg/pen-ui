package org.six11.sf.rec;

import static org.six11.util.Debug.bug;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

import org.six11.sf.Ink;
import org.six11.sf.Segment;
import org.six11.sf.SegmentFilter;
import org.six11.sf.SketchBook;
import org.six11.sf.constr.SameLengthUserConstraint;
import org.six11.sf.constr.UserConstraint;
import org.six11.sf.rec.RecognizerPrimitive.Certainty;
import org.six11.util.math.Interval;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.Pt;
import org.six11.util.solve.Constraint;
import org.six11.util.solve.DistanceConstraint;
import org.six11.util.solve.MultisourceNumericValue;
import org.six11.util.solve.NumericValue;
import org.six11.util.solve.VariableBank;
import org.six11.util.solve.VariableBank.ConstraintFilter;

public class SameLengthGesture extends RecognizedItemTemplate {

  private static final String TARGET_A = "targetA";
  private static final String TARGET_B = "targetB";
  public static final String NAME = "SameLengthGesture";

  public SameLengthGesture(SketchBook model) {
    super(model, NAME);
    addPrimitive("line1", RecognizerPrimitive.Type.Line);
    addPrimitive("line2", RecognizerPrimitive.Type.Line);
    Interval yesInterval = new Interval(0, 40);
    Interval maybeInterval = new Interval(0, 60);
    addConstraint(new LineLengthConstraint(model, "c1", yesInterval, maybeInterval, "line1"));
    addConstraint(new LineLengthConstraint(model, "c2", yesInterval, maybeInterval, "line2"));
    addConstraint(new EqualLength(model, "c3", "line1", "line2"));
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
    if ((segs1.size() == 1) && (segs2.size() == 1)) {
      // 2) use a filter that only selects lines whose midpoint is near line1 or line2's midpoint
      segs1 = SegmentFilter.makeMidpointFilter(line1, 0.3).filter(segs1);
      segs2 = SegmentFilter.makeMidpointFilter(line2, 0.3).filter(segs2);
    }
    if ((segs1.size() == 1) && (segs2.size() == 1)) {
      Segment[] seg1 = segs1.toArray(new Segment[1]);
      Segment[] seg2 = segs2.toArray(new Segment[1]);
      if (seg1[0] != seg2[0]) {
        item.addTarget(SameLengthGesture.TARGET_A, seg1[0]);
        item.addTarget(SameLengthGesture.TARGET_B, seg2[0]);
        ret = Certainty.Yes;
      } else {
        bug("Not going to make a line same length as itself, dawg");
      }
    }
    return ret;
  }

  @Override
  public void create(RecognizedItem item, SketchBook model) {
    Segment s1 = item.getSegmentTarget(TARGET_A);
    Segment s2 = item.getSegmentTarget(TARGET_B);

    // see if either s1 or s2 has an existing length constraint
    Set<ConstraintFilter> filters = new HashSet<ConstraintFilter>();
    filters.add(VariableBank.getTypeFilter(DistanceConstraint.class));
    filters.add(ConstraintFilters.getInvolvesFilter(s1.getEndpointArray(), s2.getEndpointArray()));
    Set<Constraint> results = model.getConstraints().getVars().searchConstraints(filters);
    Set<Constraint> addUs = new HashSet<Constraint>();
    UserConstraint uc = null;
    if (results.size() == 0) {
      MultisourceNumericValue dist = new MultisourceNumericValue(
          SameLengthUserConstraint.mkSource(s1), SameLengthUserConstraint.mkSource(s2));
      SameLengthUserConstraint sluc = new SameLengthUserConstraint(model);
      sluc.addDist(s1.getP1(), s1.getP2(), dist);
      sluc.addDist(s2.getP1(), s2.getP2(), dist);
      uc = sluc;
    } else if (results.size() == 1) {
      bug("Adding to the existing distance constraint.");
      // use existing numeric value
      DistanceConstraint distConst = (DistanceConstraint) results.toArray(new Constraint[1])[0];
      uc = model.getUserConstraint(distConst);
      //      uc.addInk(item.getInk());
      NumericValue numeric = distConst.getValue();
      if (numeric instanceof MultisourceNumericValue) {
        bug("It is numeric.");
        MultisourceNumericValue val = (MultisourceNumericValue) numeric;
        if (distConst.involves(s1.getP1()) && distConst.involves(s1.getP2())) {
          // s1 already constrained. incorporate s2's length and give it constraint
          val.addValue(SameLengthUserConstraint.mkSource(s2));
          addUs.add(new DistanceConstraint(s2.getP1(), s2.getP2(), distConst.getValue()));
        } else {
          // same as above but reverse the segments.
          val.addValue(SameLengthUserConstraint.mkSource(s1));
          addUs.add(new DistanceConstraint(s1.getP1(), s1.getP2(), distConst.getValue()));
        }
      } else {
        bug("The existing distance constraint is numeric. Just copy the numeric value into the new one.");
        if (distConst.involves(s1.getP1()) && distConst.involves(s1.getP2())) {
          addUs.add(new DistanceConstraint(s2.getP1(), s2.getP2(), numeric));
        } else {
          addUs.add(new DistanceConstraint(s1.getP1(), s1.getP2(), numeric));
        }
      }
      for (Constraint addMe : addUs) {
        uc.addConstraint(addMe);
      }
    } else if (results.size() > 1) {
      Set<UserConstraint> ucs = model.getUserConstraints(results);
      merge(ucs); // 'uc' is left null, should have no ill effects. famous last words
    }
    for (Ink eenk : item.getInk()) {
      model.removeRelated(eenk);
    }
    model.addUserConstraint(uc);
  }

  private void merge(Set<UserConstraint> ucs) {
    // found a few user constraints. merge them into one.
    boolean ok = true;
    for (UserConstraint uc : ucs) {
      if (!(uc instanceof SameLengthUserConstraint)) {
        ok = false;
        bug("Error. Expected user constraint of type SameLengthUserConstraint but found "
            + uc.getClass());
        break;
      }
    }
    if (ok) {
      Set<SameLengthUserConstraint> slucs = new HashSet<SameLengthUserConstraint>();
      SameLengthUserConstraint fixedSrc = null;
      for (UserConstraint uc : ucs) {
        SameLengthUserConstraint sluc = (SameLengthUserConstraint) uc;
        slucs.add(sluc);
        if (!sluc.isMultiSource()) {
          if (fixedSrc == null) {
            fixedSrc = sluc;
          } else {
            bug("When merging, I found two different fixed length user constraints. Don't know what to do, so I don't do anything.");
            ok = false;
            break;
          }
        }
      }
      if (ok) {
        // two possibilities: all are multisource, or exactly one is fixed.
        if (fixedSrc == null) {
          // handle the 'all are multisource' first
          bug("All merged user constraints are multisource.");
          SameLengthUserConstraint main = slucs.toArray(new SameLengthUserConstraint[1])[0];
          slucs.remove(main);
          for (SameLengthUserConstraint sluc : slucs) {
            Set<Constraint> replace = new HashSet<Constraint>();
            replace.addAll(sluc.getConstraints());
            model.removeUserConstraint(sluc);
            for (Constraint c : replace) {
              DistanceConstraint dc = (DistanceConstraint) c;
              main.addDist(dc.a, dc.b, dc.getValue());
            }
          }
        } else {
          // exactly one is fixed.
          slucs.remove(fixedSrc);
          for (SameLengthUserConstraint sluc : slucs) {
            Set<Constraint> replace = new HashSet<Constraint>();
            replace.addAll(sluc.getConstraints());
            model.removeUserConstraint(sluc);
            for (Constraint c : replace) {
              DistanceConstraint dc = (DistanceConstraint) c;
              fixedSrc.addDist(dc.a, dc.b, fixedSrc.getValue());
            }
          }
        }
      }
    }
    //    UserConstraint mainVague = ucs.toArray(new UserConstraint[1])[0];
    //    if (mainVague instanceof SameLengthUserConstraint) {
    //      SameLengthUserConstraint main = (SameLengthUserConstraint) mainVague;
    //      ucs.remove(main);
    //      for (UserConstraint other : ucs) {
    //        Set<Constraint> replace = new HashSet<Constraint>();
    //        replace.addAll(other.getConstraints());
    //        model.removeUserConstraint(other);
    //        for (Constraint c : replace) {
    //          DistanceConstraint dc = (DistanceConstraint) c;
    //          main.addDist(dc.a, dc.b, dc.getValue());
    //        }
    //      }
    //    } else {
    //      bug("this user constraint is the wrong type! expected SameLengthUserConstraint but got "
    //          + mainVague.getClass());
    //    }
  }

  public static UserConstraint makeUserConstraint(final SketchBook model, RecognizedItem item,
      Set<Constraint> addUs) {
    UserConstraint ret = new SameLengthUserConstraint(model, addUs.toArray(new Constraint[0]));
    return ret;
  }

  public void draw(Constraint c, RecognizedItem item, DrawingBuffer buf, Pt hoverPoint) {
    //    if (hoverPoint != null) {
    //      Segment s1 = item.getSegmentTarget(TARGET_A);
    //      Segment s2 = item.getSegmentTarget(TARGET_B);
    //      double d1 = Functions.getDistanceBetweenPointAndSegment(hoverPoint, s1.asLine());
    //      double d2 = Functions.getDistanceBetweenPointAndSegment(hoverPoint, s2.asLine());
    //      double d = Math.min(d1, d2);
    //      if (d < 50) {
    //        double alpha = getAlpha(d, 5, 20);
    //        Color color = new Color(1, 0, 0, (float) alpha);
    //        Set<RecognizedItem> f = model.findFriends(item);
    //        Set<Segment> allSegs = new HashSet<Segment>();
    //        if (f == null) {
    //          allSegs.add(s1);
    //          allSegs.add(s2);
    //        } else {
    //          for (RecognizedItem ri : f) {
    //            allSegs.add(ri.getSegmentTarget(TARGET_A));
    //            allSegs.add(ri.getSegmentTarget(TARGET_B));
    //          }
    //        }
    //        for (Segment seg : allSegs) {
    //          Pt mid = Functions.getMean(seg.getP1(), seg.getP2());
    //          DrawingBufferRoutines.acuteHash(buf, mid, seg.getStartDir(), 12, 1.5, color);
    //        }
    //      }
    //    }
    bug("not drawing gesture atm");
  }
}
