package com.or3n.onepressprint;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;

import com.or3n.onepressprint.R;
import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Vibrator;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

/**
 * This {@code IntentService} does the app's actual work.
 * {@code SampleAlarmReceiver} (a {@code WakefulBroadcastReceiver}) holds a
 * partial wake lock for this service while the service does its work. When the
 * service is finished, it calls {@code completeWakefulIntent()} to release the
 * wake lock.
 * 
 * See - http://developer.android.com/training/scheduling/wakelock.html#wakeful 
 */
public class PhotoSchedulingService extends IntentService {
	public static final String TAG = MainActivity.TAG;

	// Vibrate for 300ms when a new photo is detected
	private static final int VIB_MS = 300;
	
	// Native dimentions for a PoGo printer
	private static final int PRINT_H = 640;
	private static final int PRINT_W = 960;
	
	// Define max memory for loading an image. It appears that some devices can't handle a 20mb JPEG file..
	private static final int IMAGE_MAX_SIZE = 2100000; //1200000;
	
	public PhotoSchedulingService() {
		super("SchedulingService");
	}

	// The app will monitor the Eye-Fi directory
	private static final String pathToWatch = android.os.Environment.getExternalStorageDirectory().toString()+ "/Eye-Fi/";

	// An ID used to post the notification.
	public static final int NOTIFICATION_ID = 1;
	private NotificationManager mNotificationManager;
	NotificationCompat.Builder builder;

	@Override
	protected void onHandleIntent(Intent intent) {
		//create folder if not exists
		File folder = new File(pathToWatch);
		Log.d(TAG, "Path: " + folder);

		// look for unprocessed photos
		final File[] files = folder.listFiles( new FilenameFilter() {

			@Override
			public boolean accept(File dir, String filename) {
					return !filename.matches( ".*processed\\..*" ) && filename.toLowerCase().matches(".*\\.jpg");
				}
		});

		Log.d("TAG", "Files waiting: "+ files.length);
		
		// iterate on files to send
		for (File file : files) {
			vib();
			
			String filename = file.getAbsolutePath();
			Log.d(TAG, "Resizing "+filename);
			
			// resize. First, read a resized version that fits in memory, and then
			// resize it further to fit the native resolution on the printer
			Bitmap bMap = readBitmapWithMemoryLimit("file:"+filename);
			Log.d(TAG, "Image size before final resize, H: "+bMap.getHeight()+", W: "+bMap.getWidth());

			Bitmap newbMap = resizeBitmapForPrinter(bMap); 
			Log.d(TAG, "Image size before sending, H: "+newbMap.getHeight()+", W: "+newbMap.getWidth());

			String smallFilename = filename; //overwrite
			saveBitmap(newbMap,smallFilename);
			Log.d(TAG, "saved "+smallFilename);
						
			// send to BT
			Log.d(TAG, "Sending to BT [" + smallFilename + "]");
			sendBT(smallFilename);
			
			// clean up
			markAsProcessed(new File(smallFilename)); // we overwritten it with the small file...
			sendNotification("Sent "+ file.getName());			
		}
	
		// Release the wake lock provided by the BroadcastReceiver.
		PhotoAlarmReceiver.completeWakefulIntent(intent);
	}
	
	/**
	 * Marks the file are "sent" by renaming it
	 * @param file
	 */
	private void markAsProcessed(File file) {
		String fName = file.getName();		
		String processedFName = file.getParent() + "/processed." + file.getName(); //file.getAbsolutePath()+".processed";

		Log.d(TAG, "Renaming: " + fName+" to "+processedFName);
		Boolean b = file.renameTo(new File(processedFName));
		Log.d(TAG, b.toString());
		
		// add file to the gallery (so it can be easily deleted)
		addToMedia(fName); 			//add file
	}

	// vib to let me know I got a new photo 
	private void vib() {
		Vibrator vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
		vib.vibrate(VIB_MS);
	}
	
	/**
	 * Sends a photo over BT. See - http://innodroid.com/blog/post/how-to-print-via-bluetooth-on-android
	 * @param file
	 */
	public void sendBT(String file) {
		Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);  
		sharingIntent.setType("image/jpeg");  
		sharingIntent.setComponent(new ComponentName("com.android.bluetooth", "com.android.bluetooth.opp.BluetoothOppLauncherActivity"));
		sharingIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse("file:"+file));  
		sharingIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); 
		startActivity(sharingIntent);  
	}
	
	/**
	 * Adds the given file to the Media Library so it can be managed using the gallery app
	 * @param filename
	 */
	private void addToMedia(String filename) {
		Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
	    Uri contentUri = Uri.parse("file:"+filename);
	    mediaScanIntent.setData(contentUri);
	    this.sendBroadcast(mediaScanIntent);
	    
	    File f = new File(filename);
	    sendBroadcast(new Intent(Intent.ACTION_MEDIA_MOUNTED, Uri.parse("file:"+ f.getParent())));
	}

	private void saveBitmap(Bitmap bm, String filename) {
	    File file = new File (filename);
	    if (file.exists ()) file.delete (); 
	    try {
	           FileOutputStream out = new FileOutputStream(file);
	           bm.compress(Bitmap.CompressFormat.JPEG, 90, out);
	           out.flush();
	           out.close();

	    } catch (Exception e) {
	           e.printStackTrace();
	    }
	}
	
	/**
	 * Resizes the bitmap to fit the native resolution of the printer
	 * http://stackoverflow.com/questions/6908604/android-crop-center-of-bitmap/17733530#17733530
	 * @param bm
	 * @return
	 */
	private Bitmap resizeBitmapForPrinter(Bitmap bm) {
	    int width = bm.getWidth();
	    int height = bm.getHeight();
	    Bitmap b;
	    if (width > height) { 
	    	// "landscape"
	    	b = ThumbnailUtils.extractThumbnail(bm, PRINT_W, PRINT_H);
	    } else {
	    	// "portrait"    	
	    	b = ThumbnailUtils.extractThumbnail(bm, PRINT_H, PRINT_W);
	    }
	    bm.recycle();
	    return b;
	}
	
	/**
	 * Reads the bitmap while resizing it to fit in memory. Since i ended up running this code on an old
	 * device, I wanted to make sure the image will fit in memory when I load it 
	 * See - http://stackoverflow.com/questions/3331527/android-resize-a-large-bitmap-file-to-scaled-output-file
	 * @param path
	 * @return
	 */
	private Bitmap readBitmapWithMemoryLimit(String path) {
		Uri uri = Uri.parse(path);
		InputStream in = null;
		try {
		    in = getContentResolver().openInputStream(uri);

		    // Decode image size
		    BitmapFactory.Options o = new BitmapFactory.Options();
		    o.inJustDecodeBounds = true;
		    BitmapFactory.decodeStream(in, null, o);
		    in.close();

		    int scale = 1;
		    while ((o.outWidth * o.outHeight) * (1 / Math.pow(scale, 2)) > 
		          IMAGE_MAX_SIZE) {
		       scale++;
		    }
		    Log.d(TAG, "scale = " + scale + ", orig-width: " + o.outWidth + ",orig-height: " + o.outHeight);

		    Bitmap b = null;
		    in = getContentResolver().openInputStream(uri);
		    if (scale > 1) {
		        scale--;
		        // scale to max possible inSampleSize that still yields an image
		        // larger than target
		        o = new BitmapFactory.Options();
		        o.inSampleSize = scale;
		        b = BitmapFactory.decodeStream(in, null, o);

		        System.gc();
		    } else {
		        b = BitmapFactory.decodeStream(in);
		    }
		    in.close();

		    Log.d(TAG, "bitmap size - width: " +b.getWidth() + ", height: " + 
		       b.getHeight());
		    return b;
		} catch (IOException e) {
		    Log.e(TAG, e.getMessage(),e);
		    return null;
		}
	}
	
	// Post a notification 
	private void sendNotification(String msg) {
		mNotificationManager = (NotificationManager)
				this.getSystemService(Context.NOTIFICATION_SERVICE);

		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
				new Intent(this, MainActivity.class), 0);

		NotificationCompat.Builder mBuilder =
				new NotificationCompat.Builder(this)
		.setSmallIcon(R.drawable.ic_launcher)
		.setContentTitle(getString(R.string.photo_alert))
		.setStyle(new NotificationCompat.BigTextStyle()
		.bigText(msg))
		.setContentText(msg);

		mBuilder.setContentIntent(contentIntent);
		mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());
	}

}
