//
//  Class org.phoebus.pv.acsys.ACsys_Double
//
//  Class to contain various ACsys values/attributes belonging to an
//    ACsys device,valid when user requests the value of ACsys device, not 
//    attributes defined in DRF2.
//

package org.phoebus.pv.acsys;

import java.lang.Double ;
import java.text.NumberFormat;

import org.epics.vtype.Alarm ;
import org.epics.vtype.IAlarm ;
import org.epics.vtype.Time ;
import org.epics.vtype.ITime ;
import org.epics.vtype.Display;
import org.epics.vtype.IDisplay;
import org.util.stats.Range;

public final class ACsys_Double extends VDouble
{
  protected Double   value;
  protected IAlarm   alarm;
  protected ITime    time;
  protected IDisplay display;
  
  // Contructor

  ACsys_Double()
  {
    alarm   = new IAlarm("UNKNOWN", "UNKNOWN", "UNKNOWN");
    time    = new ITime(0,0,false);
    display = new IDisplay(new Range(0,100,false),
			   new Range(0,100,false),
			   new Range(0,100,false),
			   new Range(0,100,false),
			   new Range(0,100,false)
			   "units",
			   NumberFormat.getInstance());
			   
  }

  @Override public Double  getValue()   { return value; }
  @Override public Alarm   getAlarm()   { return alarm; }
  @Override public Time    getTime()    { return time; }
  @Override public Display getDisplay() { return display; }
}
