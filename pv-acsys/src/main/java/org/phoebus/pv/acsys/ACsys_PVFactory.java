//
//  Simple factory and registration class for Fermilab's ACsys protocol
//

package org.phoebus.pv.acsys;

import org.phoebus.pv.PV;
import org.phoebus.pv.PVFactory;
import org.phoebus.pv.acsys.ACsys_PV;

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
    return new ACsys_PV(name, base_name);
  }
}
