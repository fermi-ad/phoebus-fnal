//
//  Class org.phoebus.pv.acsys.ACsys_PV   [w.badgett]
//
//  An instance of this class is created by Phoebus whenever a widget wants a 
//    variable starting with "acsys://"
//  This class registers the device with the ACsys_PVConn instance which actually does 
//    the ACsys/DPM communications.
//


package org.phoebus.pv.acsys;

// Java classes
import java.util.StringTokenizer;
import java.util.HashMap;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.time.Instant;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.io.File;
import java.io.IOException;
import javax.security.auth.login.LoginException;

// Using JOptionPane to confirm settings, not sure if this is the right place to
//  do graphics operations...
import javax.swing.JOptionPane;

// EPICS and Phoebus classes
import org.epics.vtype.VType;
import org.epics.vtype.VDouble;
import org.epics.vtype.VNumber;
import org.epics.vtype.VString;
import org.epics.vtype.Time;
import org.epics.vtype.Display;
import org.epics.vtype.Alarm;
import org.epics.vtype.AlarmSeverity;
import org.epics.vtype.AlarmStatus;
import org.epics.util.stats.Range;
import org.phoebus.pv.PV;
//import org.phoebus.ui.application.ApplicationLauncherService;

/** ACsys Process Variable
 *  @author William Badgett
 */

public class ACsys_PV extends PV implements ACsys_PVListener
{
  public ArrayList<ACsys_PVListener> listeners = new ArrayList<>();

  public String  fullName;
  public String  deviceName;
  public char    requestSymbol;
  public String  request;
  public String  rawDeviceName;
  public String  qualifiers = new String();
  public int     index;
  public String  event;
  public long    dpmIndex;
  public VType   lastValue;
  public long    lastTimestamp;

  public boolean isRegular    =  false; // Simple reading request
  public Range   displayRange =  Range.of(-100,100); // Reading min/max
  public Range   alarmRange   =  Range.of(-100,100);
  public Range   warningRange =  Range.of(-100,100); // for now, duplicate of alarmRange
  public Range   controlRange =  Range.of(-100,100); // Setting min/max
  public DecimalFormat numberFormat = new DecimalFormat();
  public Alarm   alarm = Alarm.of(AlarmSeverity.NONE, AlarmStatus.NONE, "None");
  public String  units        = "";
  public String  description  = "Description";
  public int     digitalStatus;
  public int     analogStatus;
  public int     digitalStatusLast;
  public int     analogStatusLast;

  public final static String [] dpmFields = { ".ANALOG.STATUS" ,
      ".DIGITAL.STATUS" ,
      ".ANALOG.MIN",
      ".ANALOG.MAX",
      ".SETTING.MIN",
      ".SETTING.MAX",
      ".READING.MIN",
      ".READING.MAX",
      ".UNITS",
      ".DESCRIPTION"};
  public int dpmFieldsIndex = -1;
  
  public static ACsys_PV settingsPV = null;

  // Unlike the request maps in the ACsys_PVConn class, devices here include the
  //   DRF2 request string, plus an optional "/" plus and array index
  private static HashMap<String,ACsys_PV> devices = new HashMap<>();

  // Factory method; each device specification must only have one instance
  ///  and may optionally
  //   include and array index.  In the case of arrays, multiple devices here 
  //   may map to the same DPM request in ACsys_PVConn
  //
  public static ACsys_PV fetchDevice(String name, String base_name)
  {
    logger.log(Level.FINER, "Look for device "+name);
    String key = base_name;
    ACsys_PV pv = devices.get(key);
    if ( pv == null )
    {
      logger.log(Level.INFO , "Did not find device "+key+", creating a new PV object");
      pv = new ACsys_PV(name,base_name);
      devices.put(key ,pv); // If an array, we must retain the index
                                 // after the slash 
    }
    return(pv);
  }
    
  // Constructor [never call directly]
  private ACsys_PV(final String name, final String base_name) 
  {
    super(name);
    fullName = name;

    StringTokenizer t = new StringTokenizer(base_name,"/");   // update from "/"
    deviceName = t.nextToken(); // Should be DRF2 request without the optional array index

    // Very special virtual device to enable ACsys settings
    if ( deviceName.equals("enableSettings"))
    {
      notifyListenersOfValue(VType.toVType(new String("Enable Settings")));
      if ( settingsPV == null )
      {
        settingsPV = this;
      }
    }

    logger.log(Level.FINER, "ACsys_PV "+name+" base_name="+base_name+" dev="+deviceName);


    // Optional, index to an array
    if ( t.hasMoreTokens() ) { index = Integer.parseInt(t.nextToken());} 
    else                     { index = -1;}

    // Suss out what the event is: TCLK or rate if any
    StringTokenizer tt = new StringTokenizer(deviceName,"@");
    request = tt.nextToken();
    if ( tt.hasMoreTokens() ) { event = tt.nextToken();}
    else                      { event = new String();}

    // See if we are a "regular" request without properties or fields
    StringTokenizer ttt = new StringTokenizer(request,".");
    rawDeviceName = ttt.nextToken();
    if ( ttt.hasMoreTokens() )
    {
       while ( ttt.hasMoreTokens() ) { qualifiers += ttt.nextToken();}
    }
    else
    {
      qualifiers = new String();
      if ( deviceName.charAt(1) == ':' ) { isRegular = true;} // Look for colon as a regular symbol
    }

    // Detect if this a "regular" request of the format G:AMANAD@...
    //  without any property or field qualifiers
    
    notifyListenersOfValue(VType.toVType(new String(deviceName)));
    logger.log(Level.CONFIG, "ACsys "+name+" "+base_name+" "+deviceName);

    if ( isRegular )
    {
      logger.log(Level.CONFIG,"Adding requests for regular device request "+deviceName);
      for (int i=0; i<dpmFields.length; i++)
      {
	      String baseName = deviceName+dpmFields[i];
        logger.log(Level.CONFIG,"Adding dpmFields request  "+baseName+" " +i);
	      ACsys_PV pvNew = fetchDevice(ACsys_PVFactory.TYPE+"://"+baseName, baseName);
	      pvNew.dpmFieldsIndex = i;
	      pvNew.addACsys_PVListener(this);
      }
    }

    // If the same request already exists in the ACsys_PVConn world, this
    //   will reuse the
    // existing DPM request.  Hence we drop the slash and the index here.

    ACsys_PVConn.addListenerRequest(deviceName,this);

    // If this is a "regular" device request, subscribe to additional informations

  }

  @Override
  public void write(final Object newValue) throws Exception
  {
    if ( deviceName.equals("enableSettings") )
    {
      // Prompt for ACsys role in order to do settings
      String defaultRole = System.getProperty("user.name");
      logger.log(Level.WARNING,"Enabling settings; user is "+defaultRole);

      String role = JOptionPane.showInputDialog("Role name for ACsys settings?",
						"testing");
      logger.log(Level.WARNING,"Settings Enable request with role "+role);
      if ( role != null )
      {
	try
        {
	  // This method declares throwing 3 types of exceptions, but inside it catches
	  // all of them.   How do we know if things worked ok or not?
	  ACsys_PVConn.enableSettings(role);
	}
	catch ( Exception e)
	{
	  JOptionPane.showMessageDialog(null, e.toString(),
					  "Error Connecting for ACsys Settings",
					  JOptionPane.ERROR_MESSAGE);
	}
        logger.log(Level.WARNING,"Settings Enabled with role "+role);
      }
      else
      {
        logger.log(Level.WARNING,"Settings Enable cancelled");
      }
    }
    else if ( deviceName.equals("launchDisplay") )
    {
      // Prompt for ACsys device name to display
      String device = JOptionPane.showInputDialog("Name of ACsys device?", "Z:ACLTST");
      logger.log(Level.INFO,"Launching ACsys display of "+device);
      if ( device != null )
      {
	  File file = new File("/scratch/badgett/phoebus-fnal/examples/z_acltst.bob");
	  //ApplicationLauncherService.openFile(file, false,null);
	  logger.log(Level.WARNING,"Launched ACsys display of "+device);
      }
      else
      {
        logger.log(Level.WARNING,"Launch ACsys display cancelled");
      }
    }
    else // Regular write
    {
      logger.log(Level.WARNING,"Write on "+fullName+" "+newValue.toString());
      ACsys_PVConn.newValue(this,newValue);
    }
  }

  protected VType constructVType(Object value, long timestamp)
  {
    return(VType.toVType(value,alarm,Time.of(Instant.ofEpochMilli(timestamp)),
				   Display.of(displayRange,alarmRange,warningRange,
					      controlRange,units,numberFormat,description)));
  }
    
  public void notify(Object value, long timestamp)
  {
    VType newValue = constructVType(value,timestamp);
    if ( lastValue != null )
    {
      if ( ( newValue instanceof VNumber ) || ( newValue instanceof VString ))
      {
	if ( !newValue.equals(lastValue))
	{
          notifyListenersOfValue(newValue);
          lastValue = newValue;
	  lastTimestamp = timestamp;
	}
      }
      else
      {
        notifyListenersOfValue(newValue);
        lastValue = newValue;
      }
    }
    else
    {
      notifyListenersOfValue(newValue);
      lastValue = newValue;
    }

    listeners.forEach( (pv)->{ pv.notifyACsys_PVListener(this,value,timestamp); });
  }

  // This may need more thought
  // We ass/u/me here that Phoebus only calls the call() method once all GUI
  //   widget listeners have closed down.  If this is not true, chaos would ensue.
  protected void close()
  {
    ACsys_PVConn.removeListenerRequest(this);
  }

  public String toString()
  {
    String reply = super.toString();
    reply  += " " + fullName +" " +deviceName + " " + index +" " +dpmIndex;
    return(reply);
  }

  // ACsys_PVListener instances are intended for inter-PV communications
  public void addACsys_PVListener(ACsys_PVListener pvl)
  {
    listeners.add(pvl);
    ACsys_PV pv = (ACsys_PV)pvl;
    logger.log(Level.FINE,"Device "+pv.deviceName+" is listening to "+deviceName);
  }

  @Override
  public void notifyACsys_PVListener(ACsys_PV pv, Object value, long timestamp)
  {
    logger.log(Level.FINE,deviceName+" Got update from "+pv.deviceName+" quals " + qualifiers + " FieldIndex "+
	       pv.dpmFieldsIndex + " dpmIndex " + pv.dpmIndex + " value "+value.toString()+ " "+timestamp);
    double min,max;
    switch ( pv.dpmFieldsIndex )
    {
      case 0: // .ANALOG.STATUS
	analogStatusLast = analogStatus;
	analogStatus =  ((Number)value).intValue();
	updateAlarm();
      break;

      case 1: // .DIGITAL.STATUS
	digitalStatusLast = digitalStatus;
        digitalStatus = ((Number)value).intValue();
	updateAlarm();
      break;

      case 2: // .ANALOG.MIN
         min = ((Number)value).doubleValue();
	 max = alarmRange.getMaximum();
         alarmRange = Range.of(min,max);
	 warningRange = Range.of(min,max);
      break;

      case 3: // .ANALOG.MAX
	 min = alarmRange.getMinimum();
	 max = ((Number)value).doubleValue();
 	 alarmRange = Range.of(min,max);
	 warningRange = Range.of(min,max);
      break;

      case 4: // .SETTING.MIN
         min = ((Number)value).doubleValue();
	 max = controlRange.getMaximum();
         controlRange = Range.of(min,max);
      break;

      case 5: // .SETTING.MAX
	 min = controlRange.getMinimum();
	 max = ((Number)value).doubleValue();
 	 controlRange = Range.of(min,max);
      break;

      case 6: // .READING.MIN
         min = ((Number)value).doubleValue();
	 max = displayRange.getMaximum();
         displayRange = Range.of(min,max);
      break;

      case 7: // .READING.MAX
	 min = displayRange.getMinimum();
	 max = ((Number)value).doubleValue();
 	 displayRange = Range.of(min,max);
      break;

      case 8: // .UNITS
        if ( value instanceof String ) { units = (String)value;}
      break;

      case 9: // .DESCRIPTION
	if ( value instanceof String ) { description = (String)value;}
      break;

    }
  }

  public double getValue()
  {
    double reply = 0.0;
    if ( lastValue instanceof VDouble )
    {
      VDouble dValue = (VDouble)lastValue;
      reply = (dValue.getValue()).doubleValue();
    }
    return(reply);
  }
    
  public void updateAlarm()
  {
    if  ( ( digitalStatus != 0 ) || (analogStatus != 0 ))
    {
      String statusString = new String();
      if ( analogStatus != 0 )
      {
	statusString = "HIHI";
	if ( getValue() < alarmRange.getMinimum()) { statusString = "LOLO";}
      }
      if ( digitalStatus != 0 )
      {
	if ( statusString.length() > 0 ) { statusString +="+";}
	 statusString += "DIGI";
      }
      alarm = Alarm.of(AlarmSeverity.MAJOR, AlarmStatus.DEVICE, statusString);
    }
    else
    {
      alarm = Alarm.of(AlarmSeverity.NONE, AlarmStatus.NONE, "None");
    }
    if ( ( digitalStatusLast != digitalStatus ) ||
	 ( analogStatusLast  != analogStatus ) )
    {
      if ( lastValue instanceof VDouble )
      {
	VDouble dValue = (VDouble)lastValue;
	VType newValue = constructVType(dValue.getValue(),lastTimestamp);
	notifyListenersOfValue(newValue);
	lastValue = newValue;
      }
    }
  }
}

