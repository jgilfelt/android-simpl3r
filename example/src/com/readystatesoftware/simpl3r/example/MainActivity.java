package com.readystatesoftware.simpl3r.example;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

public class MainActivity extends Activity {

	private static final int FILE_SELECT_CODE = 0;
	
	Button select;
	Button interrupt;
	ProgressBar progress;
	TextView status;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		select = (Button) findViewById(R.id.btn_select);
		interrupt = (Button) findViewById(R.id.btn_interrupt);
		progress = (ProgressBar) findViewById(R.id.progress);
		status = (TextView) findViewById(R.id.status);
		
		select.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// start file chooser
				Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
				intent.setType("*/*");
				intent.addCategory(Intent.CATEGORY_OPENABLE);
				startActivityForResult(
						Intent.createChooser(intent, "Select a file to upload"),
						FILE_SELECT_CODE);
			}
		});
		
		interrupt.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// interrupt any active upload
				Intent intent = new Intent(UploadService.UPLOAD_CANCELLED_ACTION);
				sendBroadcast(intent);
			}
		});
		
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		IntentFilter f = new IntentFilter();
		f.addAction(UploadService.UPLOAD_STATE_CHANGED_ACTION);
		registerReceiver(uploadStateReceiver, f);
	}

	@Override
	protected void onStop() {
		unregisterReceiver(uploadStateReceiver);
		super.onStop();
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == FILE_SELECT_CODE) {
			if (resultCode == RESULT_OK) {  
                // get path of selected file 
                Uri uri = data.getData();
                String path = uri.getPath();
                // initiate the upload
                Intent intent = new Intent(this, UploadService.class);
                intent.putExtra(UploadService.ARG_FILE_PATH, path);
                startService(intent);
            }
		}
		super.onActivityResult(requestCode, resultCode, data);
	}
	 
	private BroadcastReceiver uploadStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
        	Bundle b = intent.getExtras();
        	status.setText(b.getString(UploadService.MSG_EXTRA));
        	int percent = b.getInt(UploadService.PERCENT_EXTRA);
        	progress.setIndeterminate(percent < 0);
        	progress.setProgress(percent);
        }
    };

}
