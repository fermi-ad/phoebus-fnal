//
//  Class org.phoebus.pv.acsys.ACsys_PV 
//
//  An instance of this class is created by Phoebus whenever a widget wants a 
//    variable starting with "acsys://"
//  This class registers the device with ACsys_PVConn which actually does 
//    the ACsys/DPM communications.
//

package org.phoebus.pv.acsys;

import java.util.StringTokenizer;

import org.epics.vtype.VType;
import org.epics.vtype.VDouble;
import org.phoebus.pv.PV;

import java.util.logging.Logger;
import java.util.logging.Level;

import javax.swing.JOptionPane;

/** ACsys Process Variable
 *  @author William Badgett
 */

public class ACsys_PV extends PV
{
  public String fullName;
  public String deviceName;
  public char   requestSymbol;
  public int    index;
  public long    dpmIndex;
  public static ACsys_PV settingsPV = null;
  // Constructor
  protected ACsys_PV(final String name, final String base_name) throws Exception
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
    ACsys_PVConn.addListenerRequest(deviceName,this);
  }

  @Override
  public void write(final Object newValue) throws Exception
  {
    if ( deviceName.equals("enableSettings") )
    {
      // Prompt for ACsys role in ordder to do settings
      String defaultRole = System.getProperty("user.name");
      logger.log(Level.WARNING,"Enabling settings; user is "+defaultRole);

      String role = JOptionPane.showInputDialog("Role name for ACsys settings?",
						"testing");
      ACsys_PVConn.enableSettings(role);
      logger.log(Level.WARNING,"Settings Enabled with role "+role);
    }
    else // Regular write
    {
      logger.log(Level.WARNING,"Write on "+fullName+" "+newValue.toString());
      ACsys_PVConn.newValue(this,newValue);
    }
  }

  public void notify(Object value)
  {
    notifyListenersOfValue(VType.toVType(value));
  }

  protected void close()
  {
    ACsys_PVConn.removeListenerRequest(this);
  }
}

