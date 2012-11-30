package com.readystatesoftware.simpl3r;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;
import android.util.Log;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.AbortMultipartUploadRequest;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.CompleteMultipartUploadRequest;
import com.amazonaws.services.s3.model.CompleteMultipartUploadResult;
import com.amazonaws.services.s3.model.InitiateMultipartUploadRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadResult;
import com.amazonaws.services.s3.model.PartETag;
import com.amazonaws.services.s3.model.ProgressEvent;
import com.amazonaws.services.s3.model.ProgressListener;
import com.amazonaws.services.s3.model.UploadPartRequest;
import com.amazonaws.services.s3.model.UploadPartResult;

import com.readystatesoftware.simpl3r.utils.SharedPreferencesCompat;
import com.readystatesoftware.simpl3r.utils.SharedPreferencesUtils;

public class Uploader {
	
	private static final long MIN_DEFAULT_PART_SIZE = 5 * 1024 * 1024;
	
	private static final String TAG = "Simpl3r";
	private static final String PREFS_UPLOAD_ID = "_uploadId";
	private static final String PREFS_ETAGS = "_etags";
	private static final String PREFS_ETAG_SEP = "~~";
	
	private AmazonS3Client s3Client;
	private String s3bucketName;
	private String s3key;
	private File file;
	
	private SharedPreferences prefs;
	private long partSize = MIN_DEFAULT_PART_SIZE;
	
	private UploadProgressListener progressListener; 
	
	private long bytesUploaded = 0;
	private boolean userInterrupted = false;
	private boolean userAborted = false;
	
	public Uploader(Context context, AmazonS3Client s3Client, String s3bucketName, String s3key, File file) {
		this.s3Client = s3Client;
		this.s3key = s3key;
		this.s3bucketName = s3bucketName;
		this.file = file;
		prefs = PreferenceManager.getDefaultSharedPreferences(context);
	}
	
	public String start() {
		
		// initialise
		List<PartETag> partETags = new ArrayList<PartETag>();
		final long contentLength = file.length();
		long filePosition = 0;
		int startPartNumber = 1;
		
		bytesUploaded = 0;
		
		// check if we can resume an incomplete download
		String uploadId = prefs.getString(s3key + PREFS_UPLOAD_ID, null);
		
		if (uploadId != null) {
			// we can resume the download
			Log.d(TAG, "<< resuming upload");
			
			// get the cached etags
			ArrayList<String> etags = SharedPreferencesUtils.getStringArrayPref(prefs, s3key + PREFS_ETAGS);
			for (String etagString : etags) {
				String partNum = etagString.substring(0, etagString.indexOf(PREFS_ETAG_SEP));
				String partTag = etagString.substring(etagString.indexOf(PREFS_ETAG_SEP) + 2, etagString.length());
				
				Log.d(TAG, "<< cached etag: " + partNum + " - " + partTag);
				
				PartETag etag = new PartETag(Integer.parseInt(partNum), partTag);
				partETags.add(etag);
			}
			
			// calculate the start position for resume
			startPartNumber = etags.size() + 1;
			filePosition = (startPartNumber -1) * partSize;
			bytesUploaded = (int) filePosition;
		
		} else {
			// initiate a new multi part upload
			Log.d(TAG, "<< initiating upload");
			
	        InitiateMultipartUploadRequest initRequest = new InitiateMultipartUploadRequest(s3bucketName, s3key);
	        configureInitiateRequest(initRequest);
	        InitiateMultipartUploadResult initResponse = s3Client.initiateMultipartUpload(initRequest);
	        uploadId = initResponse.getUploadId();
			
		}
		
		final AbortMultipartUploadRequest abortRequest = new AbortMultipartUploadRequest(s3bucketName, s3key, uploadId);
        
        for (int k = startPartNumber; filePosition < contentLength; k++) {
        	
            long thisPartSize = Math.min(partSize, (contentLength - filePosition));
            
            Log.d(TAG, "<< " + uploadId + " init part " + k + " size = " + thisPartSize);
            
            UploadPartRequest uploadRequest = new UploadPartRequest().withBucketName(s3bucketName)
                    .withKey(s3key).withUploadId(uploadId)
                    .withPartNumber(k).withFileOffset(filePosition).withFile(file)
                    .withPartSize(thisPartSize);

            ProgressListener s3progressListener = new ProgressListener() {
                public void progressChanged(ProgressEvent progressEvent) {
                    
                	// bail out if user cancelled
                    if (userInterrupted) {
                		s3Client.shutdown(); 
                		throw new AmazonClientException("User interrupted");
                	} else if (userAborted) {
                    	s3Client.abortMultipartUpload(abortRequest);
                    	s3Client.shutdown();
                    }
                    
                    bytesUploaded += progressEvent.getBytesTransfered();
                    
                    Log.d(TAG, "bytesUploaded=" + bytesUploaded);
                    
                    // broadcast progress
                    int percent = (int) ((bytesUploaded * 100) / contentLength);
                    if (progressListener != null) {
                    	progressListener.progressChanged(progressEvent, percent);
                    }
                    
                }
            };
            
            uploadRequest.setProgressListener(s3progressListener);
            
            UploadPartResult result = s3Client.uploadPart(uploadRequest);
            
            partETags.add(result.getPartETag());
            
            /// start resume code
            if (k == 1) {
            	// first successful part uploaded, store uploadID
            	Editor edit = prefs.edit().putString(s3key + PREFS_UPLOAD_ID, uploadId);
            	SharedPreferencesCompat.apply(edit);
            	// create empty etag array
            	ArrayList<String> etags = new ArrayList<String>();
            	SharedPreferencesUtils.setStringArrayPref(prefs, s3key + PREFS_ETAGS, etags);
            }
            // store part etag
            String serialEtag = result.getPartETag().getPartNumber() + PREFS_ETAG_SEP + result.getPartETag().getETag();
            ArrayList<String> etags = SharedPreferencesUtils.getStringArrayPref(prefs, s3key + PREFS_ETAGS);
            etags.add(serialEtag);
            SharedPreferencesUtils.setStringArrayPref(prefs, s3key + PREFS_ETAGS, etags);
            /// end resume code
            
            filePosition += partSize;
        }
        
        CompleteMultipartUploadRequest compRequest = new CompleteMultipartUploadRequest(
        		s3bucketName, s3key, uploadId,
                partETags);

        CompleteMultipartUploadResult result = s3Client.completeMultipartUpload(compRequest);
        bytesUploaded = 0;
        
        // clear the cached uploadId and etags as file is now complete
        Editor edit = prefs.edit();
        edit.remove(s3key + PREFS_UPLOAD_ID);
        edit.remove(s3key + PREFS_ETAGS);
    	SharedPreferencesCompat.apply(edit);
 
        return result.getLocation();
		
	}
	
	public void interrupt() {
		userInterrupted = true;
	}
	
	public void abort() {
		userAborted = true;
	}
	
	/**
	 * Override to configure the multipart upload request. 
	 * 
	 * By default uploaded files are publicly readable.
	 * 
	 * @param initRequest S3 request object for the file to be uploaded
	 */
	protected void configureInitiateRequest(InitiateMultipartUploadRequest initRequest) {
		initRequest.setCannedACL(CannedAccessControlList.PublicRead);
	}
	
	public void setPrefs(SharedPreferences prefs) {
		this.prefs = prefs;
	}
	
	public long getPartSize() {
		return partSize;
	}

	public void setPartSize(long partSize) {
		if (partSize > MIN_DEFAULT_PART_SIZE) {
			throw new IllegalStateException("Part size exceeds S3 minimum of " + MIN_DEFAULT_PART_SIZE);
		} else {
			this.partSize = partSize;
		}	
	}
	
	public void setProgressListener(UploadProgressListener progressListener) {
		this.progressListener = progressListener;
	}

	public interface UploadProgressListener {
		public void progressChanged(ProgressEvent progressEvent, int percentUploaded);
	}
	
}
