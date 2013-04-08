Simpl3r
=======

Amazon S3 multipart file upload for Android, made simple

![screenshot](https://raw.github.com/jgilfelt/android-simpl3r/master/simpl3r.png "screenshot")

This library provides a simple high level Android API for robust and resumable multipart file uploads using the Amazon S3 service. All the complexity of file chunking, resuming, entity tag caching and interaction with Amazon's S3 API is abstracted from the developer. 

This library manages synchronous file part uploads only. Part uploads performed in parallel are generally not suitable for mobile bandwidths.

Usage
-----

Uploads can be initiated as follows:

```java
AmazonS3Client s3Client = new AmazonS3Client(
    new BasicAWSCredentials(YOUR_S3_ACCESS_KEY, YOUR_S3_SECRET));

File file = new File("path/to/some.file");
String s3Key = file.getPath();

// create a new uploader for this file
Uploader uploader = new Uploader(this, s3Client, YOUR_S3_BUCKETNAME, s3Key, file);
    
// register listener for upload progress updates 
uploader.setProgressListener(new UploadProgressListener() {  		
    @Override
    public void progressChanged(ProgressEvent progressEvent, 
            long bytesUploaded, int percentUploaded) {
        // broadcast/notify ...
    }
});

// initiate the upload
String urlLocation = uploader.start();
```

Subsequent `Uploader` instances or calls to `start()` using the same `s3key` will attempt to resume the upload from the beginning of the last part that was uploaded successfully. A `SharedPreferences` instance for the supplied `Context` is used to cache the part ETags, or you can supply your own. You can also supply your own part size to the `Uploader`, but note that the minimum for the S3 API is 5 megabytes.

This project contains a working example project which more fully demonstrates its usage.

Dependencies
------------

This library depends upon the [AWS SDK for Android](http://aws.amazon.com/sdkforandroid/), specifically the following components:

* `aws-android-sdk-<VERSION>-core.jar`
* `aws-android-sdk-<VERSION>-s3.jar`

Building
--------

Run `ant jar` from the project directory or simply download a pre-built version from the `builds` directory of this GitHub repository.

Credits
-------

Author: [Jeff Gilfelt](https://github.com/jgilfelt)

License
-------

    Copyright 2012 readyState Software Limited

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
