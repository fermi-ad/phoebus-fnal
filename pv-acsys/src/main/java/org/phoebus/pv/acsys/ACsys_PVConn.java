//
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
//    "$"  Digitial Alarm
//    "~"  Description
//
// For best performance, use only ":" with DRF2 qualifiers following
//

package org.phoebus.pv.acsys;

// Java utility classes
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.io.FileInputStream;

import java.util.Date;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.StringTokenizer;
import java.io.IOException;
import java.lang.Class;
import java.lang.reflect.Field;

// ACsys/DPM classes
import gov.fnal.controls.service.proto.DPM;
import gov.fnal.controls.service.dpm.DPMList;
import gov.fnal.controls.service.dpm.DPMListTCP;
import gov.fnal.controls.service.dpm.DPMDataHandler;
import gov.fnal.controls.servers.dpm.SettingData;

/** ACsys device subscription handler
 *
 *  <p>Dispatches ACsys data to {@link ACsys_PV}s; intended to be a 
 *     singleton class
 *  @author William Badgett
 */

public class ACsys_PVConn implements DPMDataHandler
{
  // Don't connect to DPM unless someone asks
  protected static ACsys_PVConn instance = null; 

  protected final static Logger logger =
      Logger.getLogger(ACsys_PVConn.class.getPackage().getName()); ;
  protected DPMList dpmList; 

  // For non-array request return types, the ArrayList here should only have one entry
  // For array requests, ScalarArray and TextArray, the ArrayList's could have many
  //   element entries, all of which can be serviced in one DPM reply
  //
  // We need two HashMap's here since the DPM handler needs to know them by its own DPM index
  //   while during the registration process we need to know if a request of the same
  //   name already exists so that we do not duplicate DPM requests
  protected final HashMap<Long,ArrayList<ACsys_PV>>  requestsByIndex = new HashMap<>();
  protected final HashMap<String,ArrayList<ACsys_PV>> requestsByName  = new HashMap<>();

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

  public void applySetting(ACsys_PV pvInput, final Object newValue)
  {
    ArrayList<ACsys_PV> pvList = requestsByIndex.get(pvInput.dpmIndex);
    pvList.forEach( (pv)->
    {    
      if ( pv == null ) // Does not exist in DPMList, should not happen
      {
	logger.log(Level.WARNING,"Device "+pv.fullName+" refId "+pv.dpmIndex+
		   " not found");
	logger.log(Level.WARNING,requestsByIndex.toString());
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
    });
  }

  protected void removeDevice(ACsys_PV pv)
  {
    // This first part requires more thought
    ArrayList<ACsys_PV> pvList = requestsByIndex.get(pv.dpmIndex);
    if ( pvList != null ) { requestsByIndex.remove(pv); }

    // We could check for empty vectors and remove them from the HashMaps,
    //   but the cost is small to keep them there and the requests may be 
    //   requested again in the future
    dpmList.removeRequest(pv.dpmIndex);
    logger.log(Level.FINE,"Removed device "+pv.fullName+" "+pv.dpmIndex);
  }

  protected void addDevice(String deviceName, ACsys_PV pv)
  {
    ArrayList<ACsys_PV> pvListByName = requestsByName.get(deviceName);
    if ( pvListByName == null )
    {
      pvListByName = new ArrayList<ACsys_PV>();
      requestsByName.put(deviceName,pvListByName);
    }
    else // If the list already exists, use the first entry to find the DPM index
    {
      ArrayList<ACsys_PV> pvListByIndex = requestsByIndex.get(pvListByName.get(0).dpmIndex);
      if ( pvListByIndex == null )
      {
        pvListByIndex = new ArrayList<ACsys_PV>();
	requestsByIndex.put(dpmIndex,pvListByIndex);

	pv.dpmIndex = dpmIndex;
	pvListByName.add(pv);
	pvListByIndex.add(pv);
	
	// This must be a brand new request!  Add it to our DPM request list
	dpmList.addRequest(dpmIndex,deviceName);
	dpmList.start(this);
	logger.log(Level.INFO,"Added request "+deviceName+" "+pv.dpmIndex);
	dpmIndex++;
      }
    }
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
    ArrayList<ACsys_PV> pvList = requestsByIndex.get(devInfo.ref_id);
    if ( pvList == null ) { return;}

    pvList.forEach( (pv)->
    {
      // use lamba expression here for string concat
      logger.log(Level.FINE, "Device "+pv.fullName+ " ref_id "+ devInfo.ref_id+
		 " " +s.data);
      pv.notify(s.data);
    });
  }

  public void handle(DPM.Reply.DeviceInfo devInfo, DPM.Reply.ScalarArray s)
  {
    ArrayList<ACsys_PV> pvList = requestsByIndex.get(s.ref_id);
    if ( pvList == null ) { return;}

    pvList.forEach( (pv)->
    {    
      int index = 0;

      // We cannot serve whole arrays (yet?) Would this even make sense in a GUI widget context?
      if ( pv.index >= 0 ) { index = pv.index;} 

      logger.log(Level.FINE,"Device ScalarArray "+pv.fullName+ " ref_id "+ 
		 s.ref_id+" " +s.data);
      pv.notify(s.data[index]);
    });
  }

  public void handle(DPM.Reply.DeviceInfo devInfo, DPM.Reply.BasicStatus s)
  {
    ArrayList<ACsys_PV> pvList = requestsByIndex.get(devInfo.ref_id);
    if ( pvList == null ) { return;}

    pvList.forEach( (pv)->
    {    
      pv.notify(s.on);
      logger.log(Level.FINE,
		 "Device BasicStatus "+pv.fullName+ " ref_id "+ devInfo.ref_id+
		 " " +s.on+" "+s.ready);
    });
  }

  public void handle(DPM.Reply.DeviceInfo devInfo, DPM.Reply.Status s)
  {
    ArrayList<ACsys_PV> pvList = requestsByIndex.get(s.ref_id);
    if ( pvList == null ) { return;}

    pvList.forEach( (pv)->
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
    ArrayList<ACsys_PV> pvList = requestsByIndex.get(devInfo.ref_id);
    if ( pvList == null ) { return;}

    pvList.forEach( (pv)->
    {
      pv.notify(a.alarm_status);
      logger.log(Level.FINE,
		 "Device AnalogAlarm "+pv.fullName+ " ref_id "+ a.ref_id+
		 " " +a.alarm_enable+" "+a.alarm_status); 
    });
  }
    
  public void handle(DPM.Reply.DeviceInfo devInfo, DPM.Reply.DigitalAlarm a)
  {
    ArrayList<ACsys_PV> pvList = requestsByIndex.get(a.ref_id);
    if ( pvList == null ) { return;}

    pvList.forEach( (pv)->
    {    
      logger.log(Level.FINE,
		 "Device DigitalAlarm "+pv.deviceName+ " " +
		 " ref_id "+ a.ref_id+" " +a.alarm_enable+" "+a.alarm_status);
      pv.notify(a.alarm_status);
    });
  }

  public void handle(DPM.Reply.DeviceInfo devInfo, DPM.Reply.Text t)
  {
    ArrayList<ACsys_PV> pvList = requestsByIndex.get(t.ref_id);
    if ( pvList == null ) { return;}

    pvList.forEach( (pv)->
    {
      logger.log(Level.FINE,
	       "Device Text "+pv.fullName+ " ref_id "+ t.ref_id+
	       " " +t.data);
      pv.notify(t.data);
    });
  }

  public void handle(DPM.Reply.DeviceInfo devInfo, DPM.Reply.TextArray t)
  {
    ArrayList<ACsys_PV> pvList = requestsByIndex.get(t.ref_id);
    if ( pvList == null ) { return;}

    logger.log(Level.FINE, "Received TextArray length "+t.data.length);
    for ( int i =0 ; i<t.data.length; i++)
    { logger.log(Level.FINE, i+ " " + t.data[i]); }

    pvList.forEach( (pv)->
    {
      int index = 0;

      // We cannot serve whole arrays (yet?)  Does that even make sense in a widget context?
      if ( pv.index >= 0 ) { index = pv.index;} 
      if ( index < t.data.length ) { pv.notify(t.data[index]);}
    });
  }

  public void handle(DPM.Reply.DeviceInfo devInfo, DPM.Reply.Raw r)
  {
    ArrayList<ACsys_PV> pvList = requestsByIndex.get(r.ref_id);
    if ( pvList == null ) { return;}

    pvList.forEach( (pv)->
    {    
      long value = 0;
    
      for (int i=0; i<r.data.length && i<8; i++)
      {
        long bValue = ((long)r.data[i])&0xFF;
        value |= ( bValue << i*4);
      }  

      Long lValue = Long.valueOf(value);
      pv.notify(lValue);

      String message = "Device Raw "+pv.fullName+ " ref_id "+ r.ref_id;
      for ( int i=0; i<r.data.length; i++) { message += " " + r.data[i];}
      logger.log(Level.FINE, message + 
	       " 0x"+Long.toHexString(lValue.longValue()));
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
    // Try to load config/logging.properties if available
    try
    {
      LogManager.getLogManager()
	  .readConfiguration(new FileInputStream("config/logging.properties"));
    }
    catch (IOException e) { logger.log(Level.WARNING,e.toString());}

    ArrayList<ACsys_PV> pvArrayList = new ArrayList<ACsys_PV>();
    for (int i=0; i<arg.length; i++)
    {
      try
      {
        ACsys_PV pv = ACsys_PV.fetchDevice("acsys://"+arg[i],arg[i]);
  	pvArrayList.add(pv);
      }
      catch (Exception e)
      {
        logger.log(Level.SEVERE,"Device "+arg[i]+" error: "+e.toString());
  	e.printStackTrace();
      }
    }
    logger.log(Level.INFO, instance.toString());
  }

  public String toString()
  {
    String reply = super.toString();
    reply += "\nRequestsByIndex:\n"+requestsByIndex.toString();
    reply += "\nRequestsByName:\n"+requestsByName.toString();
    return(reply);
  }

}
