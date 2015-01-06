package com.or3n.onepressprint;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;

/**
 * When the alarm fires, this WakefulBroadcastReceiver receives the broadcast Intent 
 * and then starts the IntentService {@code PhotoSchedulingService} to do some work.
 */
public class PhotoAlarmReceiver extends WakefulBroadcastReceiver {
	public static final String TAG = "Scheduling Demo";
    // The app's AlarmManager, which provides access to the system alarm services.
    private AlarmManager alarmMgr;
    // The pending intent that is triggered when the alarm fires.
    private PendingIntent alarmIntent;
  
    @Override
    public void onReceive(Context context, Intent intent) {   
        Intent service = new Intent(context, PhotoSchedulingService.class);
        
        // Start the service, keeping the device awake while it is launching.
        startWakefulService(context, service);
    }

    /**
     * Sets a repeating alarm 
     * @param context
     */
    public void setAlarm(Context context) {
        alarmMgr = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, PhotoAlarmReceiver.class);
        alarmIntent = PendingIntent.getBroadcast(context, 0, intent, 0);

        alarmMgr.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,  
        		5 * 1000, 10 * 1000, alarmIntent); //wait one minute, create image every 1 minute

        // Enable {@code PhotoBootReceiver} to automatically restart the alarm when the
        // device is rebooted.
        ComponentName receiver = new ComponentName(context, PhotoBootReceiver.class);
        PackageManager pm = context.getPackageManager();

        pm.setComponentEnabledSetting(receiver,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);     
        
        Log.i(TAG, "AlarmStarted");
    }
    // END_INCLUDE(set_alarm)

    /**
     * Cancels the alarm.
     * @param context
     */
    // BEGIN_INCLUDE(cancel_alarm)
    public void cancelAlarm(Context context) {
        // If the alarm has been set, cancel it.
        if (alarmMgr!= null) {
            alarmMgr.cancel(alarmIntent);
            Log.i(TAG, "AlarmCanceled");
        } else {
        	 Log.i(TAG, "No Alarm to cancel..");
        }
        
        // Disable {@code SampleBootReceiver} so that it doesn't automatically restart the 
        // alarm when the device is rebooted.
        ComponentName receiver = new ComponentName(context, PhotoBootReceiver.class);
        PackageManager pm = context.getPackageManager();

        pm.setComponentEnabledSetting(receiver,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }
    // END_INCLUDE(cancel_alarm)
}
