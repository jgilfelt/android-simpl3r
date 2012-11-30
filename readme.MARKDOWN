Simpl3r
=======

Amazon S3 multipart file upload for Android, made simple

![screenshot](https://raw.github.com/jgilfelt/android-simpl3r/master/simpl3r.png "screenshot")

This library provides a simple high level Android API for robust and resumable multipart file uploads using the Amazon S3 service. All the complexity of file chunking, resuming, entity tag caching and interaction with Amazon's S3 API is abstracted from the developer. 

This library manages synchronous file part uploads only. Part uploads performed in parallel are generally not suitable for mobile bandwidths.
