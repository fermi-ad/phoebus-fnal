//
//  Simple factory and registration class for Fermilab's ACsys protocol
//
//  To minimize network traffic and the load on the DPM servers, we will
//    only create one ACsys_PV object per device.   If there is not one in the
//    ACsys_PVConn store already, let it create a new one.
//

package org.phoebus.pv.acsys;

import org.phoebus.pv.PV;
import org.phoebus.pv.PVFactory;
//import org.phoebus.pv.acsys.ACsys_PV;

/** ACsys implementation of org.phoebus.pv.PVFactory.
 *  @author William Badgett
 */

public class ACsys_PVFactory implements PVFactory
{
  final public static String TYPE = "acsys";

  @Override
  public String getType()
  {
    return TYPE;
  }

  @Override
  public PV createPV(String name, String base_name) throws Exception
  {
    System.out.println("ACsys_PVFactory.createPV() name=" + name + " base_name=" + base_name);
    return ACsys_PV.fetchDevice(name,base_name);
  }
}
