package org.six11.sf;

import static org.six11.util.Debug.bug;
import static org.six11.util.Debug.warn;

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.six11.util.gui.Components;
import org.six11.util.gui.Cursors;
import org.six11.util.gui.Strokes;
import org.six11.util.pen.PenEvent;
import org.six11.util.pen.PenListener;
import org.six11.util.pen.Pt;
import org.six11.util.pen.Vec;
import org.six11.util.solve.ConstraintSolver.State;

public class FastGlassPane extends JComponent implements MouseListener {

  public enum ActivityMode {
    /**
     * 'None' means mouse events are passed through to the components below.
     */
    None,
    /**
     * 'DragSelection' means the user is dragging a selection from the drawing pane.
     */
    DragSelection,
    /**
     * 'DragPage' means the user is dragging a scrap from the scrap grid.
     */
    DragPage
  };

  public enum CursorMode {
    MovePoint, DrawAny, Draw, Solving, StandardPointer, Undo, Redo, Pan, Zoom
  }

  SkruiFabEditor editor;
  private boolean dragging;
  //  private Timer timer;
  private Timer timer;
  //  private TimerTask tt;
  private Point prevLoc;
  private ActivityMode activity;
  private Component prevComponent;
  private Point prevComponentPoint;
  private Point dragPoint;
  private boolean gatherText;
  private StringBuilder numberInput;
  protected boolean onscreen;
  protected boolean focused;

  protected Map<CursorMode, Cursor> cursors;
  protected CursorMode currentCursor;
  private JDialog modalLayer;

  public FastGlassPane(final SkruiFabEditor editor) {
    this.editor = editor;
    this.activity = ActivityMode.None;
    this.cursors = new HashMap<CursorMode, Cursor>();
    cursors.put(CursorMode.MovePoint, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    cursors.put(CursorMode.Draw,
        Cursors.createDotCursor(4, "Drawing Cursor", Color.black, Color.gray));
    cursors.put(CursorMode.Solving,
        Cursors.createDotCursor(4, "Solving Cursor", Color.red.darker().darker(), Color.red));
    cursors.put(CursorMode.Undo,
        Cursors.createArrowCursor(new Vec(-20, 0), "Undo Cursor", Color.black));
    cursors.put(CursorMode.Redo,
        Cursors.createArrowCursor(new Vec(20, 0), "Redo Cursor", Color.black));
    cursors.put(CursorMode.StandardPointer, Cursor.getDefaultCursor());
    cursors.put(CursorMode.Pan, Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
    cursors.put(CursorMode.Zoom,
        Cursors.createMagnifyingGlassCursor(6, 10, "Zoom Cursor", Color.black, Color.white));
    // set current cursor to draw
    currentCursor = CursorMode.Draw;
    setCursor(cursors.get(currentCursor));
    timer = new Timer(10, new ActionListener() {
      public void actionPerformed(ActionEvent ev) {
        PointerInfo info = MouseInfo.getPointerInfo();
        Point loc = info.getLocation();
        placePoint(loc);
      }
    });
    timer.setCoalesce(true);
    timer.setRepeats(true);
    bug("Made timer with auto repeat delay of " + timer.getDelay());

    addComponentListener(new ComponentAdapter() {
      public void componentHidden(ComponentEvent ev) {
        onscreen = false;
        whackMouseTimer();
      }

      public void componentShown(ComponentEvent ev) {
        onscreen = true;
        whackMouseTimer();
      }
    });
    editor.getApplicationFrame().addFocusListener(new FocusAdapter() {
      public void focusGained(FocusEvent ev) {
        focused = true;
        whackMouseTimer();
      }

      public void focusLost(FocusEvent ev) {
        focused = false;
        whackMouseTimer();
      }
    });
    addMouseListener(this);
    numberInput = new StringBuilder();
    long eventMask = AWTEvent.KEY_EVENT_MASK;
    Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
      public void eventDispatched(AWTEvent ev) {
        if (gatherText) {
          if ((ev.getID() == KeyEvent.KEY_TYPED) && (ev instanceof KeyEvent)) {
            KeyEvent kev = (KeyEvent) ev;
            boolean ok = false;
            try {
              int val = Integer.parseInt("" + kev.getKeyChar());
              ok = true;
              numberInput.append(val);
            } catch (NumberFormatException ex) {
              // ignore
            }
            if (!ok) {
              bug("dealing with key event char is: " + kev.getKeyChar());
              if ((kev.getKeyChar() == '.') && !numberInput.toString().contains(".")) {
                numberInput.append('.');
                ok = true;
              }
            }

            if (!ok) {
              bug("What is this? key code: " + kev.getKeyCode());
            }
            if (ok) {
              editor.getModel().addTextProgress(numberInput.toString());
            }
          } else if (ev.getID() == KeyEvent.KEY_RELEASED) {
            KeyEvent kev = (KeyEvent) ev;
            switch (kev.getKeyCode()) {
              case KeyEvent.VK_ENTER:
                editor.getModel().addTextFinished(numberInput.toString());
                numberInput.setLength(0);
                kev.consume();
                break;
              case KeyEvent.VK_BACK_SPACE:
              case KeyEvent.VK_DELETE:
                if (numberInput.length() > 0) {
                  numberInput.deleteCharAt(numberInput.length() - 1);
                  editor.getModel().addTextProgress(numberInput.toString());
                }
                kev.consume();
                break;
            }
          }
        }
      }
    }, eventMask);
  }

  public void setCursorMode(CursorMode mode) {
    currentCursor = mode;
    if (currentCursor == CursorMode.DrawAny) {
      whackDrawAny();
    } else {
      setCursor(cursors.get(currentCursor));
    }
  }

  public void whackDrawAny() {
    if (prevComponent == editor.getDrawingSurface() && currentCursor == CursorMode.DrawAny) {
      if (editor.getModel().getConstraints().getSolutionState() == State.Solved) {
        setCursor(cursors.get(CursorMode.Draw));
      } else {
        setCursor(cursors.get(CursorMode.Solving));
      }
    } else if (prevComponent != editor.getDrawingSurface()) {
      setCursor(cursors.get(CursorMode.StandardPointer));
    }
  }

  protected void whackMouseTimer() {
    if (!timer.isRunning() && onscreen && focused) {
      timer.restart();
    } else if (timer.isRunning()) {
      timer.stop();
    }
  }

  public void paintComponent(Graphics g1) {
    Graphics2D g = (Graphics2D) g1;
    Image thumb = null;
    if (activity == ActivityMode.DragSelection) {
      thumb = editor.getModel().getDraggingThumb();
    } else if (activity == ActivityMode.DragPage) {
      thumb = editor.getGrid().getSelectedThumb();
    }
    if (thumb != null) {
      g.drawImage(thumb, dragPoint.x, dragPoint.y, null);
    }
    
    if (modalLayer != null && modalLayer.isShowing()) {
      g.setColor(new Color(0, 0, 0, 0.5f));
      g.fill(getVisibleRect());
    }
  }

  private void givePenEvent(Component component, PenEvent ev) {
    if (component instanceof PenListener) {
      ((PenListener) component).handlePenEvent(ev);
    } else if (component != null) {
      warn(this, "Component not a pen listener: " + component.toString());
    }
  }

  public void setActivity(ActivityMode mode) {
    this.activity = mode;
  }

  public ActivityMode getActivity() {
    return activity;
  }

  public void setGatherText(boolean value) {
    gatherText = value;
    if (!gatherText) {
      numberInput.setLength(0);
    }
  }

  private void placePoint(final Point loc) {
    if (!isVisible()) {
      bug("Not yet visible. Bailage.");
      return;
    }
    final long now = System.currentTimeMillis();
    Component container = editor.getContentPane();
    SwingUtilities.convertPointFromScreen(loc, container);
    boolean sameSpot = false;
    if (prevLoc != null) {
      sameSpot = (prevLoc.x == loc.x) && (prevLoc.y == loc.y);
    }
    prevLoc = loc;
    if (!sameSpot) {
      if (dragging) {
        secretMouseDrag(loc, now);
      } else {
        secretMouseMove(loc, now);
      }
    }
  }

  protected void secretMouseMove(Point loc, long now) {
    MouseEventInfo mei = new MouseEventInfo(loc);
    if (prevComponent != mei.component) {
      if (prevComponent != null) {
        givePenEvent(prevComponent, PenEvent.buildExitEvent(this, (Pt) null));
      }
      if (mei.component != null) {
        givePenEvent(mei.component, PenEvent.buildEnterEvent(this, new Pt(mei.componentPoint, now)));
      }
    }
    if (mei.component != null) {
      givePenEvent(mei.component, PenEvent.buildHoverEvent(this, new Pt(mei.componentPoint, now)));
    }
    prevComponent = mei.component;
    whackDrawAny();
  }

  private void secretMouseDrag(Point loc, long time) {
    MouseEventInfo mei = new MouseEventInfo(loc);

    switch (activity) {
      case DragSelection:
        giveSelectionDrag(mei);
        break;
      case DragPage:
        giveSelectionDrag(mei);
        break;
      case None:
        Point p = mei.componentPoint != null ? mei.componentPoint : prevComponentPoint;
        givePenEvent(mei.component, PenEvent.buildDragEvent(this, new Pt(p, time)));
        break;
    }
    prevComponent = mei.component;
    if (mei.componentPoint != null) {
      prevComponentPoint = mei.componentPoint;
    }
  }

  private void giveSelectionDrag(MouseEventInfo mei) {
    dragPoint = mei.containerPoint;
    Drag.Event ev = new Drag.Event(mei.componentPoint, activity);
    if (prevComponent != mei.component) {
      if (prevComponent instanceof Drag.Listener) {
        ((Drag.Listener) prevComponent).dragExit(ev);
      }
      if (mei.component instanceof Drag.Listener) {
        ((Drag.Listener) mei.component).dragEnter(ev);
      }
    }
    if (mei.component instanceof Drag.Listener) {
      ((Drag.Listener) mei.component).dragMove(ev);
    }
    repaint();
  }

  public void mouseClicked(MouseEvent ev) {
  }

  @Override
  public void mouseEntered(MouseEvent ev) {

  }

  @Override
  public void mouseExited(MouseEvent ev) {
  }

  @Override
  public void mousePressed(MouseEvent ev) {
    dragging = true;
    dragPoint = ev.getPoint();
    editor.getModel().getConstraints().setPaused(true);
    MouseEventInfo mei = new MouseEventInfo(ev);
    givePenEvent(mei.component,
        PenEvent.buildDownEvent(this, new Pt(mei.componentPoint, ev.getWhen()), ev));
    prevComponent = mei.component;
  }

  @Override
  public void mouseReleased(MouseEvent ev) {
    dragging = false;
    editor.getModel().getConstraints().setPaused(false);
    MouseEventInfo mei = new MouseEventInfo(ev);

    switch (activity) {
      case DragSelection:
        if (mei.component instanceof Drag.Listener) {
          Drag.Event dev = new Drag.Event(mei.componentPoint, activity);
          ((Drag.Listener) mei.component).dragDrop(dev);
        }
        editor.getModel().setDraggingSelection(false);
        activity = ActivityMode.None;
        givePenEvent(editor.getModel().getSurface(),
            PenEvent.buildIdleEvent(this, new Pt(mei.componentPoint)));
        break;
      case DragPage:
        if (mei.component instanceof Drag.Listener) {
          Drag.Event dev = new Drag.Event(mei.componentPoint, activity);
          ((Drag.Listener) mei.component).dragDrop(dev);
        }
        activity = ActivityMode.None;
        break;
      case None:
        givePenEvent(mei.component,
            PenEvent.buildIdleEvent(this, new Pt(mei.componentPoint, ev.getWhen())));
        break;
    }

  }

  private class MouseEventInfo {
    Point glassPanePoint;
    Container container;
    Point containerPoint;
    Component component;
    Point componentPoint;

    MouseEventInfo(MouseEvent ev) {
      glassPanePoint = ev.getPoint();
      container = editor.getContentPane();
      containerPoint = SwingUtilities.convertPoint(FastGlassPane.this, glassPanePoint, container);
      component = SwingUtilities.getDeepestComponentAt(container, containerPoint.x,
          containerPoint.y);
      componentPoint = null;
      if (component != null) {
        componentPoint = SwingUtilities.convertPoint(FastGlassPane.this, glassPanePoint, component);
      }
    }

    MouseEventInfo(Point pt) {
      glassPanePoint = pt;
      container = editor.getContentPane();
      containerPoint = SwingUtilities.convertPoint(FastGlassPane.this, glassPanePoint, container);
      component = SwingUtilities.getDeepestComponentAt(container, containerPoint.x,
          containerPoint.y);
      componentPoint = null;
      if (component != null) {
        componentPoint = SwingUtilities.convertPoint(FastGlassPane.this, glassPanePoint, component);
      }
    }
  }

  public void drawAddMeSign(Graphics2D g, float circX, float circY, float circD, Color fill,
      Color linework) {
    Ellipse2D circ = new Ellipse2D.Float(circX, circY, circD, circD);
    g.setColor(fill);
    g.fill(circ);
    g.setColor(linework);
    g.setStroke(Strokes.THIN_STROKE);
    g.draw(circ);
    float r = circD / 2;
    float centerX = circX + r;
    float centerY = circY + r;
    float lineLen = circD - 8;
    float halfLen = lineLen / 2;
    int midX = (int) centerX;
    int topX = (int) (centerX - halfLen);
    int botX = (int) (centerX + halfLen);
    int midY = (int) centerY;
    int topY = (int) (centerY - halfLen);
    int botY = (int) (centerY + halfLen);
    g.drawLine(midX, topY, midX, botY);
    g.drawLine(topX, midY, botX, midY);
  }

  public void showModalLayer(JComponent dialogContent, int w, int h) {
    modalLayer = new JDialog(editor.getApplicationFrame());
    modalLayer.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosed(WindowEvent arg0) {
        editor.getApplicationFrame().repaint();
      }
    });
    modalLayer.add(dialogContent);
    modalLayer.setSize(w, h);
    Components.centerComponent(modalLayer);
    modalLayer.setVisible(true);
    editor.getApplicationFrame().repaint();
  }
  
  public void clearModalLayer() {
    this.modalLayer = null;
    editor.getApplicationFrame().repaint();
  }

}
