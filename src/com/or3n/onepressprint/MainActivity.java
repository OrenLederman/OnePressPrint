package com.or3n.onepressprint;


import com.or3n.onepressprint.R;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

// http://www.javacodegeeks.com/2013/09/bluetooth-data-transfer-with-android.html 
public class MainActivity extends Activity {
	public static final String TAG = "OnePressPrint";
	
	TextView statusText;
	BluetoothAdapter btAdapter;
	PhotoAlarmReceiver alarm = new PhotoAlarmReceiver();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		statusText = (TextView) findViewById(R.id.status);

		btAdapter = BluetoothAdapter.getDefaultAdapter();

		if (btAdapter == null) {
			Toast.makeText(this, R.string.blu_notsetup, Toast.LENGTH_SHORT).show();
		} else {

		}
			
		statusText.setText("Ready!");

	}

	public void startSendPhoto(View view) {
		alarm.setAlarm(this);
        statusText.setText("Started");
	}

	public void stopSendPhoto(View view) {
        alarm.cancelAlarm(this);
        statusText.setText("Stopped");
	}	
}
