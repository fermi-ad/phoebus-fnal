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

/** ACsys Process Variable
 *  @author William Badgett
 */

public class ACsys_PV extends PV
{
  public String fullName;
  public String deviceName;
  public char   requestSymbol;
  public String qualifier;
  public int    index;
  public long    dpmIndex;

  // Constructor
  protected ACsys_PV(final String name, final String base_name) throws Exception
  {
    super(name);
    fullName = name;

    StringTokenizer t = new StringTokenizer(base_name,"/");
    deviceName = t.nextToken();

    if ( t.hasMoreTokens() ) { qualifier = t.nextToken(); } // Required!
    else                     { qualifier = null;}

    // Optional, index to an array
    if ( t.hasMoreTokens() ) { index = Integer.parseInt(t.nextToken()); } 
    else                     { index = -1;}

    notifyListenersOfValue(VType.toVType(new String(deviceName)));
    logger.log(Level.CONFIG, "ACsys "+name+" "+base_name+" "+deviceName+
	       " "+qualifier);
    ACsys_PVConn.addListenerRequest(deviceName,this);
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

