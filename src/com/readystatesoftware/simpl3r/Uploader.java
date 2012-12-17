/***
 * Copyright (c) 2012 readyState Software Ltd
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package com.readystatesoftware.simpl3r;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;
import android.util.Log;

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
	
	/**
	 * Initiate a multipart file upload to Amazon S3
	 * 
	 * @return the URL of a successfully uploaded file
	 */
	public String start() {
		
		// initialize
		List<PartETag> partETags = new ArrayList<PartETag>();
		final long contentLength = file.length();
		long filePosition = 0;
		int startPartNumber = 1;
		
		userInterrupted = false;
		userAborted = false;
		bytesUploaded = 0;
		
		// check if we can resume an incomplete download
		String uploadId = getCachedUploadId();
		
		if (uploadId != null) {
			// we can resume the download
			Log.i(TAG, "resuming upload for " + uploadId);
			
			// get the cached etags
			List<PartETag> cachedEtags = getCachedPartEtags();
			partETags.addAll(cachedEtags);
						
			// calculate the start position for resume
			startPartNumber = cachedEtags.size() + 1;
			filePosition = (startPartNumber -1) * partSize;
			bytesUploaded = filePosition;
			
			Log.i(TAG, "resuming at part " + startPartNumber + " position " + filePosition);
		
		} else {
			// initiate a new multi part upload
			Log.i(TAG, "initiating new upload");
			
	        InitiateMultipartUploadRequest initRequest = new InitiateMultipartUploadRequest(s3bucketName, s3key);
	        configureInitiateRequest(initRequest);
	        InitiateMultipartUploadResult initResponse = s3Client.initiateMultipartUpload(initRequest);
	        uploadId = initResponse.getUploadId();
			
		}
		
		final AbortMultipartUploadRequest abortRequest = new AbortMultipartUploadRequest(s3bucketName, s3key, uploadId);
        
        for (int k = startPartNumber; filePosition < contentLength; k++) {
        	
            long thisPartSize = Math.min(partSize, (contentLength - filePosition));
            
            Log.i(TAG, "starting file part " + k + " with size " + thisPartSize);
            
            UploadPartRequest uploadRequest = new UploadPartRequest().withBucketName(s3bucketName)
                    .withKey(s3key).withUploadId(uploadId)
                    .withPartNumber(k).withFileOffset(filePosition).withFile(file)
                    .withPartSize(thisPartSize);

            ProgressListener s3progressListener = new ProgressListener() {
                public void progressChanged(ProgressEvent progressEvent) {
                    
                	// bail out if user cancelled
                	// TODO calling shutdown too brute force?
                    if (userInterrupted) {
                		s3Client.shutdown(); 
                		throw new UploadIterruptedException("User interrupted");
                	} else if (userAborted) {
                		// aborted requests cannot be resumed, so clear any cached etags
                		clearProgressCache();
                    	s3Client.abortMultipartUpload(abortRequest);
                    	s3Client.shutdown();
                    }
                    
                    bytesUploaded += progressEvent.getBytesTransfered();
                    
                    //Log.d(TAG, "bytesUploaded=" + bytesUploaded);
                    
                    // broadcast progress
                    float fpercent = ((bytesUploaded * 100) / contentLength);
                    int percent = Math.round(fpercent);
                    if (progressListener != null) {
                    	progressListener.progressChanged(progressEvent, bytesUploaded, percent);
                    }
                    
                }
            };
            
            uploadRequest.setProgressListener(s3progressListener);
            
            UploadPartResult result = s3Client.uploadPart(uploadRequest);
            
            partETags.add(result.getPartETag());
            
            // cache the part progress for this upload
            if (k == 1) {
            	initProgressCache(uploadId);
            }
            // store part etag
            cachePartEtag(result);
            
            filePosition += thisPartSize;
        }
        
        CompleteMultipartUploadRequest compRequest = new CompleteMultipartUploadRequest(
        		s3bucketName, s3key, uploadId,
                partETags);

        CompleteMultipartUploadResult result = s3Client.completeMultipartUpload(compRequest);
        bytesUploaded = 0;
        
        Log.i(TAG, "upload complete for " + uploadId);
        
        clearProgressCache();
 
        return result.getLocation();
		
	}

	private String getCachedUploadId() {
		return prefs.getString(s3key + PREFS_UPLOAD_ID, null);
	}
	
	private List<PartETag> getCachedPartEtags() {
		List<PartETag> result = new ArrayList<PartETag>();		
		// get the cached etags
		ArrayList<String> etags = SharedPreferencesUtils.getStringArrayPref(prefs, s3key + PREFS_ETAGS);
		for (String etagString : etags) {
			String partNum = etagString.substring(0, etagString.indexOf(PREFS_ETAG_SEP));
			String partTag = etagString.substring(etagString.indexOf(PREFS_ETAG_SEP) + 2, etagString.length());
						
			PartETag etag = new PartETag(Integer.parseInt(partNum), partTag);
			result.add(etag);
		}
		return result;
	}

	private void cachePartEtag(UploadPartResult result) {
		String serialEtag = result.getPartETag().getPartNumber() + PREFS_ETAG_SEP + result.getPartETag().getETag();
		ArrayList<String> etags = SharedPreferencesUtils.getStringArrayPref(prefs, s3key + PREFS_ETAGS);
		etags.add(serialEtag);
		SharedPreferencesUtils.setStringArrayPref(prefs, s3key + PREFS_ETAGS, etags);
	}

	private void initProgressCache(String uploadId) {
		// store uploadID
		Editor edit = prefs.edit().putString(s3key + PREFS_UPLOAD_ID, uploadId);
		SharedPreferencesCompat.apply(edit);
		// create empty etag array
		ArrayList<String> etags = new ArrayList<String>();
		SharedPreferencesUtils.setStringArrayPref(prefs, s3key + PREFS_ETAGS, etags);
	}

	private void clearProgressCache() {
		// clear the cached uploadId and etags
        Editor edit = prefs.edit();
        edit.remove(s3key + PREFS_UPLOAD_ID);
        edit.remove(s3key + PREFS_ETAGS);
    	SharedPreferencesCompat.apply(edit);
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
		if (partSize < MIN_DEFAULT_PART_SIZE) {
			throw new IllegalStateException("Part size is less than S3 minimum of " + MIN_DEFAULT_PART_SIZE);
		} else {
			this.partSize = partSize;
		}	
	}
	
	public void setProgressListener(UploadProgressListener progressListener) {
		this.progressListener = progressListener;
	}

	public interface UploadProgressListener {
		public void progressChanged(ProgressEvent progressEvent, long bytesUploaded, int percentUploaded);
	}
	
}
