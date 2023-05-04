// Class  org.phoebus.pv.acsys.ACsys_PVConn    
//
// Phoebus plug-in for Fermilab's ACsys control system (W.Badgett)
// Requests must be prefixed with acsys://
// Following the prefix can be any DRF2 compatible request, with an optional 
//   slash followed by an index for array devices.
//
// Intended to be a singlet class to reduce the traffic between 
//   Phoebus and ACsys/DPM
//
// Request symbols
//    ":"  Reading
//    "_"  Setting
//    "|"  Basic Status
//    "&"  Basic Control
//    "@"  Analog Alarm
//    "$"  Digitial Alarm                                                       //    "~"  Description
//
// consider ArrayList and HashMap containers

package org.phoebus.pv.acsys;

// Phoebus classes
import java.util.logging.Logger;
import java.util.logging.Level;

// ACsys classes
import java.util.Date;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.StringTokenizer;
import java.io.IOException;
import java.lang.Class;
import java.lang.reflect.Field;

import gov.fnal.controls.service.proto.DPM;
import gov.fnal.controls.service.dpm.DPMList;
import gov.fnal.controls.service.dpm.DPMListTCP;
import gov.fnal.controls.service.dpm.DPMDataHandler;
import gov.fnal.controls.servers.dpm.SettingData;

/** ACsys device subscription handler
 *
 *  <p>Dispatches ACsys data to {@link ACsys_PV}s, designed to be a 
 *     singlet class
 *  @author William Badgett
 */

public class ACsys_PVConn implements DPMDataHandler
{
  // Don't connect to DPM unless someone asks
  protected static ACsys_PVConn instance = null; 

  protected final static Logger logger =
      Logger.getLogger(ACsys_PVConn.class.getPackage().getName()); ;
  protected DPMList dpmList; 

  protected final HashMap<String,ArrayList<ACsys_PV>> listeners 
      = new HashMap<>();
  protected final HashMap<Long,ArrayList<ACsys_PV>> devices 
      = new HashMap<>();

  protected long enableSettings = 0;
  protected long dpmIndex = 0; // Running DPM device index, never goes down
                               // when a listener/device is removed

  public static void addListenerRequest(String device, ACsys_PV pv)
  {
    if ( instance == null ) { instance = new ACsys_PVConn();}
    instance.addDevice(device,pv);
  }

  public static void removeListenerRequest(ACsys_PV pv)
  {
    if ( instance != null ) { instance.removeDevice(pv);}
  }

  public static void newValue(ACsys_PV pv, final Object newValue)
  {
    if ( instance == null ) { instance = new ACsys_PVConn();}
    instance.applySetting(pv,newValue);
  }

  public void applySetting(ACsys_PV pv, final Object newValue)
  {
    ArrayList<ACsys_PV> entries = devices.get(pv.dpmIndex);
    if ( entries == null ) // Does not exist in DPMList
    {
      logger.log(Level.WARNING,"Device "+pv.fullName+" refId "+pv.dpmIndex+
		 " not found");
      logger.log(Level.WARNING,devices.toString());
      logger.log(Level.WARNING,listeners.toString());
      return;
    }

    if ( newValue instanceof Double )
    {
      dpmList.addSetting(pv.dpmIndex,
			   ((Double)newValue).doubleValue());
    }
    else if ( newValue instanceof String )
    {
      dpmList.addSetting(pv.dpmIndex,(String)newValue);
    }

    try
    {
      // Settings must be enabled at this point for this to work
      dpmList.applySettings(this);
    }
    catch ( Exception e )
    {
      logger.log(Level.WARNING,"Error enabling settings",e);
    }
  }

  protected void removeDevice(ACsys_PV pv)
  {
    final ArrayList<ACsys_PV> refArrayList = devices.get(pv.dpmIndex);
    if ( refArrayList != null ) { refArrayList.remove(pv); }

    final ArrayList<ACsys_PV> pvArrayList = listeners.get(pv.deviceName);
    if ( pvArrayList != null ) 
    { 
      if ( !pvArrayList.remove(pv) )
      { logger.log(Level.WARNING,"Cannot find device "+pv.deviceName);}
    }
    else 
    { logger.log(Level.WARNING,"Cannot find device vector "+pv.deviceName);}

    // We could check for empty vectors and remove them from the HashMaps,
    //   but the cost is small to keep them there and the devices may be 
    //   requested again in the future
    dpmList.removeRequest(pv.dpmIndex);
    logger.log(Level.FINE,"Removed device "+pv.fullName+" "+pv.dpmIndex);
  }

  protected void addDevice(String device,ACsys_PV pv)
  {
    // First look for existing device
    ArrayList<ACsys_PV> pvArrayList = listeners.get(device);
    if (pvArrayList == null )
    {
      pvArrayList = new ArrayList<ACsys_PV>();
      listeners.put(device,pvArrayList);
    }

    ArrayList<ACsys_PV> refArrayList = devices.get(dpmIndex);
    if ( refArrayList == null )
    {
      refArrayList = new ArrayList<ACsys_PV>();
      devices.put(dpmIndex,refArrayList);
    }
   
    pvArrayList.add(pv);
    refArrayList.add(pv);
    dpmList.addRequest(dpmIndex,device);
    dpmList.start(this);
    pv.dpmIndex = dpmIndex;
    logger.log(Level.INFO,"Added request "+device+" "+pv.dpmIndex);
    dpmIndex++;
  }

  // Constructor
  protected ACsys_PVConn() 
  {
    try
    {
      dpmList = DPMListTCP.open("/dpmtest");
    }
    catch ( IOException e )
    {
      logger.log(Level.SEVERE,"Failed to connect to ACsys DPM",e);
      dpmList = null;
    }
  }

  public static void stop() 
  {
    if ( instance != null ) 
    { 
      try { instance.dpmList.stop();}
      catch ( Exception e ) 
      { 
	logger.log(Level.SEVERE,"Failed to disconnect from ACsys DPM",e);
      }
    }
  }

  public static void enableSettings(String role)
  {
    try 
    {
      instance.dpmList.enableSettings(role);
    }
    catch (Exception e)
    {
      logger.log(Level.WARNING,"Unable to enable settings",e);
    }
  }

  public void handle(DPM.Reply.DeviceInfo devInfo[], DPM.Reply.ApplySettings m)
  {
    logger.log(Level.INFO,"Settings Complete: "+m.status.length);
    for (int i=0; i<m.status.length; i++)
    {
      logger.log(Level.INFO,"Setting complete refId "+ m.status[i].ref_id+
		 " status "+m.status[i].status);
    }
  }

  public void handle(DPM.Reply.DeviceInfo devInfo, DPM.Reply.Scalar s)
  {
    ArrayList<ACsys_PV> refArrayList = devices.get(devInfo.ref_id);
    if ( refArrayList == null ) { return;}

    refArrayList.forEach( (pv) -> 
    {
      // use lamba expression here for string concat
      logger.log(Level.FINE, "Device "+pv.fullName+ " ref_id "+ devInfo.ref_id+
		 " " +s.data);
      pv.notify(s.data);
    });
  }

  public void handle(DPM.Reply.DeviceInfo devInfo, DPM.Reply.ScalarArray s)
  {
    ArrayList<ACsys_PV> refArrayList = devices.get(s.ref_id);
    if ( refArrayList == null ) { return;}

    refArrayList.forEach( (pv) -> 
    {
      int index = 0;

      // We cannot serve whole arrays (yet?)
      if ( pv.index >= 0 ) { index = pv.index;} 

      logger.log(Level.FINE,"Device ScalarArray "+pv.fullName+ " ref_id "+ 
		 s.ref_id+" " +s.data);
      pv.notify(s.data[index]);
    });
  }

  public void handle(DPM.Reply.DeviceInfo devInfo, DPM.Reply.BasicStatus s)
  {
    ArrayList<ACsys_PV> refArrayList = devices.get(devInfo.ref_id);
    if ( refArrayList == null ) { return;}

    refArrayList.forEach( (pv) -> 
    {
      pv.notify(s.on);
      logger.log(Level.FINE,
		 "Device BasicStatus "+pv.fullName+ " ref_id "+ devInfo.ref_id+
		 " " +s.on+" "+s.ready);
    });
  }

  public void handle(DPM.Reply.DeviceInfo devInfo, DPM.Reply.Status s)
  {
    ArrayList<ACsys_PV> refArrayList = devices.get(s.ref_id);
    if ( refArrayList == null ) { return;}

    refArrayList.forEach( (pv) -> 
    {
      if ( pv.deviceName.equals("enableSettings"))
      {
	enableSettings = s.status;
	return;
      }
      else
      {
	pv.notify(s.status);
      }
      logger.log(Level.FINE,
		 "Device Status "+pv.fullName+ " ref_id "+ s.ref_id+
		 " " +s.status);
    });
  }

  public void handle(DPM.Reply.DeviceInfo devInfo, DPM.Reply.AnalogAlarm a)
  {
    ArrayList<ACsys_PV> refArrayList = devices.get(devInfo.ref_id);
    if ( refArrayList == null ) { return;}

    refArrayList.forEach( (pv) -> 
    {
      pv.notify(a.alarm_status);
      logger.log(Level.FINE,
		 "Device AnalogAlarm "+pv.fullName+ " ref_id "+ a.ref_id+
		 " " +a.alarm_enable+" "+a.alarm_status); 
    });
  }
    
  public void handle(DPM.Reply.DeviceInfo devInfo, DPM.Reply.DigitalAlarm a)
  {
    ArrayList<ACsys_PV> refArrayList = devices.get(a.ref_id);
    if ( refArrayList == null ) { return;}

    refArrayList.forEach( (pv) -> 
    {
      logger.log(Level.FINE,
		 "Device DigitalAlarm "+pv.deviceName+ " " +
		 " ref_id "+ a.ref_id+" " +a.alarm_enable+" "+a.alarm_status);
      pv.notify(a.alarm_status);
    });
  }

  public void handle(DPM.Reply.DeviceInfo devInfo, DPM.Reply.Text t)
  {
    ArrayList<ACsys_PV> refArrayList = devices.get(t.ref_id);
    if ( refArrayList == null ) { return;}

    refArrayList.forEach( (pv) -> 
    {
      logger.log(Level.FINE,
		 "Device Text "+pv.fullName+ " ref_id "+ t.ref_id+
		 " " +t.data);
      pv.notify(t.data);
    });
  }

  public void handle(DPM.Reply.DeviceInfo devInfo, DPM.Reply.TextArray t)
  {
    ArrayList<ACsys_PV> refArrayList = devices.get(t.ref_id);
    if ( refArrayList == null ) { return;}

    refArrayList.forEach( (pv) -> 
    {
      int index = 0;

      // We cannot serve whole arrays (yet?)
      if ( pv.index >= 0 ) { index = pv.index;} 
      pv.notify(t.data[index]);
    });
  }

  public void handle(DPM.Reply.DeviceInfo devInfo, DPM.Reply.Raw r)
  {
    ArrayList<ACsys_PV> refArrayList = devices.get(r.ref_id);
    if ( refArrayList == null ) { return;}

    refArrayList.forEach( (pv) -> 
    {
      long [] values = new long [4];
      long value = 0;
      if ( r.data.length == 4 )
      { 
        for (int i=0; i<values.length; i++) 
	{ values[i] = ((long)r.data[i])&0xFF;}
	value = values[0]|(values[1] << 8)|(values[2] << 16)|(values[3] << 24); 
      }
      Long lValue = Long.valueOf(value);
      pv.notify(lValue);

      logger.log(Level.FINE,
		 "Device Raw "+pv.fullName+ " ref_id "+ r.ref_id+
		 " " +r.data[0]+
		 " " +r.data[1]+
		 " " +r.data[2]+
		 " " +r.data[3]+
		 " "+Long.toHexString(lValue.longValue()));
    });
  }


  public void serviceFound(DPM.Reply.ServiceDiscovery m)
  {
    logger.log(Level.INFO,
	       "DPM Service Found: " + m.serviceLocation);
  }

  public void serviceNotFound()
  {
    logger.log(Level.SEVERE,"DPM Service Not Found");
  }

  public static void main(String arg[])
  {
    ArrayList<ACsys_PV> pvArrayList = new ArrayList<ACsys_PV>();
    for (int i=0; i<arg.length; i++)
    {
      try
      {
  	ACsys_PV pv = new ACsys_PV("acsys://"+arg[i],arg[i]);
  	pvArrayList.add(pv);
      }
      catch (Exception e)
      {
        logger.log(Level.SEVERE,"Device "+arg[i]+" error: "+e.toString());
  	e.printStackTrace();
      }
    }
  }
}
