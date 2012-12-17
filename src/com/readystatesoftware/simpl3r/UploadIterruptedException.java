package com.readystatesoftware.simpl3r;

public class UploadIterruptedException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public UploadIterruptedException() {
		super();
	}

	public UploadIterruptedException(String message) {
		super(message);
	}

	public UploadIterruptedException(String message, Throwable cause) {
		super(message, cause);
	}

	public UploadIterruptedException(Throwable cause) {
		super(cause);
	}
	
}
