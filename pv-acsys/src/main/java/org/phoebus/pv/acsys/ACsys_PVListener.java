//
//  Class org.phoebus.pv.acsys.ACsys_PVListener
//  Interface that handles ACsys_PV changes
//
//  Careful!   Don't create listener loops!
//

package org.phoebus.pv.acsys;

public interface ACsys_PVListener
{
  public void notifyACsys_PVListener(ACsys_PV pv, Object value, long timestamp);
}
