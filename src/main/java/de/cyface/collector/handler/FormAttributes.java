package de.cyface.collector.handler;

public enum FormAttributes {
	DEVICE_ID("deviceId"), MEASUREMENT_ID("measurementId"), DEVICE_TYPE("deviceType"), OS_VERSION("osVersion");

	private String value;

	private FormAttributes(final String value) {
		this.value = value;
	}

	public String getValue() {
		return value;
	}
}
