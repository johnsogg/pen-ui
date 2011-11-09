package org.six11.sf;

import java.awt.Color;
import java.awt.geom.Area;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.six11.sf.Ink.Type;
import org.six11.util.data.Lists;
import org.six11.util.pen.DrawingBuffer;
import org.six11.util.pen.DrawingBufferRoutines;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Sequence;
import org.six11.util.solve.ConstraintSolver;

import static org.six11.util.Debug.bug;

public class SketchBook {

  List<Sequence> scribbles; // raw ink, as the user provided it.
  List<Ink> ink;

  private GestureController gestures;
  private DrawingBufferLayers layers;
  private List<Ink> selection;
  private List<Ink> selectionCopy;
  private GraphicDebug guibug;
  private Set<Segment> geometry;
  private ConstraintAnalyzer constraintAnalyzer;
  private ConstraintSolver solver;
  private int pointCounter = 1;
  
  public SketchBook(GlassPane glass) {
    this.scribbles = new ArrayList<Sequence>();
    this.selection = new ArrayList<Ink>();
    this.selectionCopy = new ArrayList<Ink>();
    this.gestures = new GestureController(this, glass);
    this.geometry = new HashSet<Segment>();
    this.ink = new ArrayList<Ink>();
    this.constraintAnalyzer = new ConstraintAnalyzer(this);
    this.solver = new ConstraintSolver();
    solver.runInBackground();
    solver.createUI();
  }

  public List<Ink> getSelectionCopy() {
    return selectionCopy;
  }

  public List<Ink> getSelection() {
    return selection;
  }

  public DrawingBufferLayers getLayers() {
    return layers;
  }

  public GraphicDebug getGuiBug() {
    return guibug;
  }

  public GestureController getGestures() {
    return gestures;
  }

  public void setGuibug(GraphicDebug gb) {
    this.guibug = gb;
  }

  public void addInk(Ink newInk) {
    ink.add(newInk);
    gestures.clearGestureTimer();
    DrawingBuffer buf = layers.getLayer(GraphicDebug.DB_UNSTRUCTURED_INK);
    Sequence scrib = newInk.getSequence();
    DrawingBufferRoutines.drawShape(buf, scrib.getPoints(), DrawingBufferLayers.DEFAULT_COLOR,
        DrawingBufferLayers.DEFAULT_THICKNESS);
    layers.repaint();
  }

  public void removeInk(Ink oldInk) {
    ink.remove(oldInk);
    // TODO: remove from drawing buffer and redraw
    bug("Not implemented");
  }

  /**
   * The 'scribble' is ink that is currently being drawn, or is the most recently completed stroke.
   */
  public Sequence startScribble(Pt pt) {
    Sequence scrib = new Sequence();
    scrib.add(pt);
    scribbles.add(scrib);
    return scrib;
  }

  public Sequence addScribble(Pt pt) {
    Sequence scrib = (Sequence) Lists.getLast(scribbles);
    if (!scrib.getLast().isSameLocation(pt)) { // Avoid duplicate point in
      // scribble
      scrib.add(pt);
    }
    return scrib;
  }

  public Sequence endScribble(Pt pt) {
    Sequence scrib = (Sequence) Lists.getLast(scribbles);
    return scrib;
  }

  public void setLayers(DrawingBufferLayers layers) {
    this.layers = layers;
  }

  public List<Ink> getUnanalyzedInk() {
    List<Ink> ret = new ArrayList<Ink>();
    for (Ink stroke : ink) {
      if (!stroke.isAnalyzed()) {
        ret.add(stroke);
      }
    }
    return ret;
  }

  /**
   * Returns a list of Ink that is contained (partly or wholly) in the target area.
   */
  public List<Ink> search(Area target) {
    List<Ink> ret = new ArrayList<Ink>();
    for (Ink eenk : ink) {
      if (eenk.getOverlap(target) > 0.5) {
        ret.add(eenk);
      }
    }
    return ret;
  }

  public void clearSelection() {
    setSelected(null);
  }

  public void setSelected(Collection<Ink> selectUs) {
    selection.clear();
    if (selectUs != null) {
      selection.addAll(selectUs);
    }
    DrawingBuffer db = layers.getLayer(GraphicDebug.DB_SELECTION);
    db.clear();
    for (Ink eenk : selection) {
      guibug.ghostlyOutlineShape(db, eenk.getSequence().getPoints(), Color.CYAN.darker());
    }
  }

  public void addGeometry(Segment seg) {
    geometry.add(seg);
  }

  public Set<Segment> getGeometry() {
    return geometry;
  }

  public ConstraintAnalyzer getConstraintAnalyzer() {
    return constraintAnalyzer;
  }
  
  public ConstraintSolver getConstraints() {
    return solver;
  }

  public void replace(Pt capPt, Pt spot) {
    // segment geometry
    for (Segment seg : geometry) {
      seg.replace(capPt, spot);
    }
    // points and constraints
    solver.replacePoint(capPt, nextPointName(), spot);
  }

  String nextPointName() {
    return "P" + pointCounter++;
  }

}
