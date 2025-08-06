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
// For best performance and less confusion, use only ":" with DRF2 qualifiers (properties and
//   fields) following the device name
//

package org.phoebus.pv.acsys;

// Java utility classes
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.prefs.Preferences;
import java.util.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.StringTokenizer;
import javax.security.auth.login.LoginException;

// ACsys/DPM classes
import gov.fnal.controls.service.proto.DPM;
import gov.fnal.controls.service.dpm.DPMList;
import gov.fnal.controls.service.dpm.DPMListTCP;
import gov.fnal.controls.service.dpm.DPMDataHandler;
import gov.fnal.controls.servers.dpm.SettingData;
import gov.fnal.controls.acnet.AcnetErrors;


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

  protected String dpmServer = null;
  public final static String dpmServerDefault = "dpmtest";
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

  public static void addListenerRequest(String requestName, ACsys_PV pv)
  {
    if ( instance == null ) { instance = new ACsys_PVConn();}
    instance.addRequest(requestName,pv);
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
      logger.log(Level.FINER,"applySettings "+pv.fullName+" is Object Type "+newValue.getClass().getName()+
		 " value "+newValue.toString());
      if ( newValue instanceof Double )
      {
	double setting = ((Double)newValue).doubleValue();
	logger.log(Level.FINE,"Device "+pv.fullName+" Double refId "+pv.dpmIndex+" adding setting of "+
		   setting);
	dpmList.addSetting(pv.dpmIndex, setting);
      }
      else if ( newValue instanceof String )
      {	
	logger.log(Level.FINE,"Device "+pv.fullName+"  String refId "+pv.dpmIndex+" adding setting of "+
		   (String)newValue);
	dpmList.addSetting(pv.dpmIndex,(String)newValue);
      }
      else if ( newValue instanceof Long )
      {	
	logger.log(Level.FINE,"Device "+pv.fullName+" Long refId "+pv.dpmIndex+" adding setting of "+
		   (Long)newValue);

	// Ass/u/me this is a CONTROL setting of 2 bytes (should we support 4 bytes?) 
	long newLong =  ((Long)newValue).longValue();
	byte [] newData = new byte [2];
	for ( int i=0; i<newData.length; i++)  {  newData[i] = (byte)(( newLong >> i*8 ) & 0xFF);}
	dpmList.addSetting(pv.dpmIndex,newData);
      }
      
      try
      {
	// Settings must already be enabled at this point for this to work
	dpmList.applySettings(this);
	logger.log(Level.FINE,"Device "+pv.fullName+" refId "+pv.dpmIndex+" setting of "+
		   newValue.toString()+" has been applied");
      }
      catch ( Exception e )
      {
	logger.log(Level.WARNING,"Error enabling settings",e);
      }
    });
  }

  protected void removeDevice(ACsys_PV pv)
  {
    // This may require more thought
    ArrayList<ACsys_PV> pvListByIndex = requestsByIndex.get(pv.dpmIndex);
    if ( pvListByIndex != null )
    {
      pvListByIndex.remove(pv);
      if ( pvListByIndex.size() <= 0 )
      {
	requestsByIndex.remove(pv.dpmIndex);
	dpmList.removeRequest(pv.dpmIndex);
	logger.log(Level.FINE,"Removed device "+pv.fullName+
		   " "+pv.dpmIndex+" from requestByIndex");
      }
    }
    
    String deviceNameIndexed = pv.deviceName;
    if ( pv.index >= 0 ) { deviceNameIndexed = pv.deviceName + "/" + pv.index;}  
    ArrayList<ACsys_PV> pvListByName = requestsByName.get(deviceNameIndexed);
    if ( pvListByName  != null )
    {
      pvListByName.remove(pv);
      if ( pvListByName.size() <= 0 )
      {
	      requestsByName.remove(deviceNameIndexed);
	      logger.log(Level.FINE,"Removed device "+deviceNameIndexed+" " +pv.dpmIndex+" from requestByIndex");
      }
    }
  }

  protected void addRequest(String requestName, ACsys_PV pv)
  {
    // With scalar requests, there should only be one ACsys_PV per ArrayList
    // For vector/array requests, there could be many ACsys_PV objects
    //   per Array list, each with a unique array index

    // Consider requestsByName to be the primary source of truth:
    //  have we ecountered this request before?
    ArrayList<ACsys_PV> pvList = requestsByName.get(requestName);
    if ( pvList == null )
    {
      pvList = new ArrayList<ACsys_PV>();
      requestsByName.put(requestName,pvList);
      
      // We have a brand new request here, so increment our dpmIndex and add
      //   to our dpmList
      dpmIndex++;
      pv.dpmIndex = dpmIndex;
      dpmList.addRequest(dpmIndex,requestName);
      dpmList.start(this);
      logger.log(Level.INFO,"Added request "+requestName+" "+pv.dpmIndex);

      requestsByIndex.put(dpmIndex,pvList);
    }

    if ( !pvList.contains(pv) ) { pvList.add(pv);}
  }
    
  // Constructor
  protected ACsys_PVConn() 
  {
    dpmServer = dpmServerDefault;

    // Optional DPM server instance for connection
    Preferences prefs = Preferences.userRoot().node("org/phoebus/pv/acsys");
    if ( prefs != null )
    {
      String dpmServerRequest = prefs.get("dpmServer",dpmServerDefault);
      if ( dpmServerRequest != null )
      {
        if ( dpmServerRequest.length() > 0 )  { dpmServer = dpmServerRequest;}
        else { dpmServer = null;}
      }
    }
    
    try
    {
      if ( dpmServer == null )
      {
	logger.log(Level.CONFIG,"Using default DPM server");
	dpmList = DPMListTCP.open();
      }
      else
      {
	logger.log(Level.CONFIG,"Using specified DPM server "+dpmServer);
	dpmList = DPMListTCP.open("/"+dpmServer);
      }
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

  public static void enableSettings(String role) throws Exception 
  {
    instance.dpmList.enableSettings(role);
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
      logger.log(Level.FINE, "DPM Reply Device "+pv.fullName+ " ref_id "+ devInfo.ref_id+" " +s.data);    
      pv.notify(s.data,s.timestamp);
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
      pv.notify(s.data[index],s.timestamp);
    });
  }

  public void handle(DPM.Reply.DeviceInfo devInfo, DPM.Reply.BasicStatus s)
  {
    ArrayList<ACsys_PV> pvList = requestsByIndex.get(devInfo.ref_id);
    if ( pvList == null ) { return;}

    pvList.forEach( (pv)->
    {    
      pv.notify(s.on,s.timestamp);
      logger.log(Level.FINER,
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
	pv.notify(s.status,s.timestamp);
      }
      logger.log(Level.FINER,
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
      pv.notify(a.alarm_status,a.timestamp);
      logger.log(Level.FINER,
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
      logger.log(Level.FINER,
		 "Device DigitalAlarm "+pv.deviceName+ " " +
		 " ref_id "+ a.ref_id+" " +a.alarm_enable+" "+a.alarm_status);
      pv.notify(a.alarm_status,a.timestamp);
    });
  }

  public void handle(DPM.Reply.DeviceInfo devInfo, DPM.Reply.Text t)
  {
    ArrayList<ACsys_PV> pvList = requestsByIndex.get(t.ref_id);
    if ( pvList == null ) { return;}

    pvList.forEach( (pv)->
    {
      logger.log(Level.FINER,
	       "Device Text "+pv.fullName+ " ref_id "+ t.ref_id+
	       " " +t.data);
      pv.notify(t.data,t.timestamp);
    });
  }

  public void handle(DPM.Reply.DeviceInfo devInfo, DPM.Reply.TextArray t)
  {
    ArrayList<ACsys_PV> pvList = requestsByIndex.get(t.ref_id);
    if ( pvList == null ) { return;}

    logger.log(Level.FINER, "Received TextArray length "+t.data.length);
    for ( int i =0 ; i<t.data.length; i++)
    { logger.log(Level.FINER, i+ " " + t.data[i]); }

    ArrayList<ACsys_PV> pvListCopy;
    synchronized (pvList) {
      pvListCopy = new ArrayList<>(pvList);  // added by zyuan, from pvList
    }
    
    pvListCopy.forEach( (pv)->
    {
      int index = 0;
      logger.log(Level.FINER, "Notifying "+pv.toString());
		 
      // We cannot serve whole arrays (yet?)
      // Does that even make sense in a widget context?
      if ( pv.index >= 0 ) { index = pv.index;} 
      if ( index < t.data.length ) { pv.notify(t.data[index],t.timestamp); }
      else                         { pv.notify(new String(),0);}
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
        value |= ( bValue << i*8);
      }  

      Long lValue = Long.valueOf(value);
      pv.notify(lValue,r.timestamp);

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
    //    logger.log(Level.INFO, instance.toString());
  }

  public String toString()
  {
    String reply = super.toString();
    reply += "\nRequestsByIndex:\n"+requestsByIndex.toString();
    reply += "\nRequestsByName:\n"+requestsByName.toString();
    return(reply);
  }

}
