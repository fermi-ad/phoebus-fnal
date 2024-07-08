//
//  Class org.phoebus.pv.acsys.ACsys_PV 
//
//  An instance of this class is created by Phoebus whenever a widget wants a 
//    variable starting with "acsys://"
//  This class registers the device with ACsys_PVConn which actually does 
//    the ACsys/DPM communications.
//

package org.phoebus.pv.acsys;

// Java classes
import java.util.StringTokenizer;
import java.util.HashMap;
import java.util.logging.Logger;
import java.util.logging.Level;

// Using JOptionPane to confirm settings, not sure if this is the right place
import javax.swing.JOptionPane;

// EPICS and Phoebus classes
import org.epics.vtype.VType;
import org.epics.vtype.VDouble;
import org.epics.vtype.VNumber;
import org.epics.vtype.VString;
import org.phoebus.pv.PV;


/** ACsys Process Variable
 *  @author William Badgett
 */

public class ACsys_PV extends PV
{
  public String  fullName;
  public String  deviceName;
  public char    requestSymbol;
  public int     index;
  public long    dpmIndex;
  public VType   lastValue;
  public static ACsys_PV settingsPV = null;

  private static HashMap<String,ACsys_PV> devices = new HashMap<>();

  // Factory method; each device specification must only have one instance and may optionally
  //   include and array index.  In the case of arrays, multiple devices here 
  //   may map to the same DPM request in ACsys_PVConn
  //
  public static ACsys_PV fetchDevice(String name, String base_name)
  {
    ACsys_PV pv = devices.get(name);
    if ( pv == null )
    {
      pv = new ACsys_PV(name,base_name);
      devices.put(base_name,pv); // If an array, we must retain the index
                                 // after the slash 
    }
    return(pv);
  }
    
  // Constructor [never call directly]
  private ACsys_PV(final String name, final String base_name) 
  {
    super(name);
    fullName = name;

    StringTokenizer t = new StringTokenizer(base_name,"/");
    deviceName = t.nextToken();

    // Very special virtual device to enable ACsys settings
    if ( deviceName.equals("enableSettings"))
    {
      notifyListenersOfValue(VType.toVType(new String("Enable Settings")));
      if ( settingsPV == null )
      {
        settingsPV = this;
      }
    }

    // Optional, index to an array
    if ( t.hasMoreTokens() ) { index = Integer.parseInt(t.nextToken()); } 
    else                     { index = -1;}

    notifyListenersOfValue(VType.toVType(new String(deviceName)));
    logger.log(Level.CONFIG, "ACsys "+name+" "+base_name+" "+deviceName);

    // If the same request already exists in the ACsys_PVConn world, this will reuse the
    // existing DPM request.  Hence we drop the slash and the index here.
    ACsys_PVConn.addListenerRequest(deviceName,this);
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
	ACsys_PVConn.enableSettings(role);
        logger.log(Level.WARNING,"Settings Enabled with role "+role);
      }
      else
      {
        logger.log(Level.WARNING,"Settings Enable cancelled");
      }
    }
    else // Regular write
    {
      logger.log(Level.WARNING,"Write on "+fullName+" "+newValue.toString());
      ACsys_PVConn.newValue(this,newValue);
    }
  }

  public void notify(Object value)
  {
    VType newValue = VType.toVType(value);
    if ( lastValue != null )
    {
      if ( ( newValue instanceof VNumber ) || ( newValue instanceof VString ))
      {
	if ( !newValue.equals(lastValue))
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
    }
    else
    {
      notifyListenersOfValue(newValue);
      lastValue = newValue;
    }
  }

  protected void close()
  {
    ACsys_PVConn.removeListenerRequest(this);
  }
}

