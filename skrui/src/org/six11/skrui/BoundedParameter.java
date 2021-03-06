package org.six11.skrui;

import java.awt.Color;
import java.util.HashSet;
import java.util.Set;

import org.six11.util.Debug;
import org.six11.util.gui.Colors;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public abstract class BoundedParameter {

  protected String keyName;
  protected String humanReadableName;
  protected String documentation;

  public static class Boolean extends BoundedParameter {
    private boolean value;

    public Boolean(String keyName, String humanReadableName, String documentation, boolean v) {
      super(keyName, humanReadableName, documentation);
      this.value = v;
    }

    public BoundedParameter copy() {
      return new Boolean(keyName, humanReadableName, documentation, value);
    }

    public String getValueStr() {
      return "" + value;
    }

    public boolean getBoolean() {
      return value;
    }

    public void setBoolean(boolean v) {
      value = v;
    }

    public void setValue(String v) {
      setBoolean(java.lang.Boolean.parseBoolean(v));
    }
  }

  public static class Paint extends BoundedParameter {

    Set<Color> allowedColors;
    Color value;

    public Paint(String keyName, String humanReadableName, String documentation, Color[] colors,
        Color v) {
      super(keyName, humanReadableName, documentation);
      this.allowedColors = new HashSet<Color>();
      for (Color c : colors) {
        this.allowedColors.add(c);
      }
      setPaint(v);
    }

    public void setPaint(Color desired) {
      if (allowedColors.size() > 0 && allowedColors.contains(desired)) {
        value = desired;
      } else if (allowedColors.size() == 0) {
        value = desired;
      }
    }

    public Color getPaint() {
      return value;
    }

    public BoundedParameter copy() {
      return new Paint(keyName, humanReadableName, documentation, allowedColors
          .toArray(new Color[] {}), value);
    }

    public String getValueStr() {
      return value.toString();
    }

    @Override
    public void setValue(String v) {
      setPaint(Colors.decodeHtmlHexTriplet(v));
    }

  }
  
  public static class Integer extends BoundedParameter {
    int value;
    
    public Integer(String keyName, String humanReadableName, String documentation, int value) {
      super(keyName, humanReadableName, documentation);
      this.value = value;
    }
    
    public int getInt() {
      return value;
    }

    @Override
    public BoundedParameter copy() {
      return new Integer(keyName, humanReadableName, documentation, value);
    }

    @Override
    public String getValueStr() {
      return "" + value;
    }

    @Override
    public void setValue(String v) {
      value = java.lang.Integer.parseInt(v);
    }
  }

  public static class Double extends BoundedParameter {

    private double lowerBound;
    private double upperBound;
    private double value;

    public Double(String keyName, String humanReadableName, String documentation,
        double lowerBound, double upperBound, double value) {
      super(keyName, humanReadableName, documentation);
      this.lowerBound = lowerBound;
      this.upperBound = upperBound;
      this.value = value;
      validate();
    }

    public double getLowerBound() {
      return lowerBound;
    }

    public void setLowerBound(double lowerBound) {
      this.lowerBound = lowerBound;
      validate();
    }

    public double getUpperBound() {
      return upperBound;
    }

    public void setUpperBound(double upperBound) {
      this.upperBound = upperBound;
      validate();
    }

    public double getDouble() {
      return value;
    }

    public void setDouble(double value) {
      this.value = value;
      validate();
    }

    private final void validate() {
      if (value > upperBound) {
        value = upperBound;
      }
      if (value < lowerBound) {
        value = lowerBound;
      }
    }

    public BoundedParameter copy() {
      return new Double(keyName, humanReadableName, documentation, lowerBound, upperBound, value);
    }

    @Override
    public String getValueStr() {
      return Debug.num(getDouble());
    }

    @Override
    public void setValue(String v) {
      setDouble(java.lang.Double.parseDouble(v));
    }
  }

  protected BoundedParameter(String keyName, String humanReadableName, String documentation) {
    this.keyName = keyName;
    this.humanReadableName = humanReadableName;
    this.documentation = documentation;
  }

  public String getKeyName() {
    return keyName;
  }

  public void setKeyName(String keyName) {
    this.keyName = keyName;
  }

  public String getHumanReadableName() {
    return humanReadableName;
  }

  public void setHumanReadableName(String humanReadableName) {
    this.humanReadableName = humanReadableName;
  }

  public String getDocumentation() {
    return documentation;
  }

  public void setDocumentation(String documentation) {
    this.documentation = documentation;
  }

  public double getDouble() {
    warn("Double");
    return 0;
  }

  public void setDouble(@SuppressWarnings("unused") double v) {
    warn("Double");
  }
  
  public int getInt() {
    warn("Integer");
    return 0;
  }

  public boolean getBoolean() {
    warn("Boolean");
    return false;
  }

  public void setBoolean(@SuppressWarnings("unused") boolean v) {
    warn("Boolean");
  }

  public Color getPaint() {
    warn("Paint");
    return null;
  }

  public void setPaint(@SuppressWarnings("unused") Color c) {
    warn("Paint");
  }

  private void warn(String as) {
    System.out.println("Warning: using parameter " + getKeyName() + " as " + as
        + ", but it is of type: " + getClass().getName());
    new RuntimeException("ugh").printStackTrace();
  }

  public abstract String getValueStr();

  public abstract BoundedParameter copy();

  public abstract void setValue(String v);
}
