package de.cyface.collector.model;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;

import de.cyface.collector.handler.FormAttributes;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.json.JsonObject;

public final class Measurement {
	private final String deviceIdentifier;
	private final String measurementIdentifier;
	private final String operatingSystemVersion;
	private final String deviceType;
	
	public Measurement(final String deviceIdentifier, final String measurementIdentifier, final String operatingSystemVersion, final String deviceType) {
		this.deviceIdentifier = deviceIdentifier;
		this.measurementIdentifier = measurementIdentifier;
		this.operatingSystemVersion = operatingSystemVersion;
		this.deviceType = deviceType;
	}
	
	public String getDeviceIdentifier() {
		return deviceIdentifier;
	}
	
	public String getMeasurementIdentifier() {
		return measurementIdentifier;
	}
	
	public String getOperatingSystemVersion() {
		return operatingSystemVersion;
	}
	
	public String getDeviceType() {
		return deviceType;
	}

	public JsonObject toJson() {
		JsonObject ret = new JsonObject();
		
		ret.put(FormAttributes.DEVICE_ID.getValue(), deviceIdentifier);
		ret.put(FormAttributes.MEASUREMENT_ID.getValue(), measurementIdentifier);
		ret.put(FormAttributes.OS_VERSION.getValue(), operatingSystemVersion);
		ret.put(FormAttributes.DEVICE_TYPE.getValue(), deviceType);
		
		return ret;
	}
	
	public static MessageCodec<Measurement,Measurement> getCodec() {
		return new MessageCodec<Measurement, Measurement>() {

			@Override
			public void encodeToWire(final Buffer buffer, final Measurement s) {
				final String deviceIdentifier = s.getDeviceIdentifier();
				final String measurementIdentifier = s.getMeasurementIdentifier();
				final String deviceType = s.getDeviceType();
				final String operatingSystemVersion = s.getOperatingSystemVersion();
				
				buffer.appendInt(deviceIdentifier.length());
				buffer.appendInt(measurementIdentifier.length());
				buffer.appendInt(deviceType.length());
				buffer.appendInt(operatingSystemVersion.length());
				buffer.appendString(deviceIdentifier);
				buffer.appendString(measurementIdentifier);
				buffer.appendString(deviceType);
				buffer.appendString(operatingSystemVersion);
			}

			@Override
			public Measurement decodeFromWire(int pos, final Buffer buffer) {
				int deviceIdentifierLength = buffer.getInt(0);
				int measurementIdentifierLength = buffer.getInt(4);
				int deviceTypeLength = buffer.getInt(8);
				int operatingSystemVersionLength = buffer.getInt(12);
				
				int deviceIdentifierEnd = 16+deviceIdentifierLength;
				final String deviceIdentifier = buffer.getString(16, deviceIdentifierEnd);
				int measurementIdentifierEnd = deviceIdentifierEnd+measurementIdentifierLength;
				final String measurementIdentifier = buffer.getString(deviceIdentifierEnd,measurementIdentifierEnd);
				int deviceTypeEnd = measurementIdentifierEnd+deviceTypeLength;
				final String deviceType = buffer.getString(measurementIdentifierEnd, deviceTypeEnd);
				final String operatingSystemVersion = buffer.getString(deviceTypeEnd, deviceTypeEnd+operatingSystemVersionLength);
				
				return new Measurement(deviceIdentifier, measurementIdentifier, operatingSystemVersion, deviceType);
			}

			@Override
			public Measurement transform(final Measurement s) {
				return s;
			}

			@Override
			public String name() {
				return "Measurement";
			}

			@Override
			public byte systemCodecID() {
				return -1;
			}
		};
		
	}

}
